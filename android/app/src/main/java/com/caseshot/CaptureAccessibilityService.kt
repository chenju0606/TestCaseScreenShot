package com.caseshot

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityService.ScreenshotResult
import android.accessibilityservice.AccessibilityService.TakeScreenshotCallback
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Choreographer
import android.view.Display
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CaptureAccessibilityService : AccessibilityService() {
    private lateinit var configRepository: ConfigRepository
    private lateinit var stateRepository: StateRepository
    private val namingService = NamingService()
    private val fileStore = FileStore()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val mainExecutor = Executor { command -> mainHandler.post(command) }
    private val ioExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    private var overlayController: OverlayController? = null
    private var captureInProgress = false
    private var lastCaptureTime = 0L
    private var resultNotificationId = RESULT_NOTIFICATION_ID

    override fun onServiceConnected() {
        super.onServiceConnected()
        configRepository = ConfigRepository(this)
        stateRepository = StateRepository(this)
        createNotificationChannel()
        showOverlay()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
    }

    override fun onInterrupt() {
    }

    override fun onDestroy() {
        overlayController?.remove()
        overlayController = null
        ioExecutor.shutdown()
        super.onDestroy()
    }

    private fun showOverlay() {
        if (overlayController != null) return
        overlayController = OverlayController(
            context = this,
            onScreenshot = { captureCurrentScreen() },
            onDone = { markDone() },
            windowType = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        ).also { it.show() }
    }

    private fun captureCurrentScreen() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            notifyResult("截图不可用", "当前系统不支持该截图方式", isError = true)
            return
        }
        if (captureInProgress) return

        val config = configRepository.load()
        val current = stateRepository.load(config.prefix)
        val filename = namingService.buildFilename(
            current.prefix,
            current.caseIndex,
            current.shotIndex,
            config.caseDigits
        )
        val outputDir = fileStore.resolveOutputDir(this, config)
        val target = fileStore.targetFile(outputDir, filename)

        if (target.exists()) {
            when (config.existingScreenshotPolicy) {
                ExistingScreenshotPolicy.SKIP -> {
                    captureInProgress = true
                    handleSaveResult(ScreenshotSaveResult.Skipped(target), current, config)
                    captureInProgress = false
                }
                ExistingScreenshotPolicy.OVERWRITE -> {
                    startScreenshotCapture(target, current, config, overwrite = true)
                }
                ExistingScreenshotPolicy.ASK -> {
                    captureInProgress = true
                    overlayController?.showExistingScreenshotPanel(
                        filename = filename,
                        onSkip = {
                            handleSaveResult(ScreenshotSaveResult.Skipped(target), current, config)
                            captureInProgress = false
                        },
                        onOverwrite = {
                            startScreenshotCapture(target, current, config, overwrite = true)
                        }
                    )
                }
            }
            return
        }

        startScreenshotCapture(target, current, config, overwrite = false)
    }

    private fun startScreenshotCapture(
        target: java.io.File,
        current: CaseShotState,
        config: CaseShotConfig,
        overwrite: Boolean
    ) {
        if (!canCaptureNow()) {
            notifyResult("截图太频繁", "请稍后再试", isError = true)
            captureInProgress = false
            return
        }

        val overlayRemoved = overlayController?.temporarilyRemoveForCapture() == true
        captureInProgress = true

        runAfterFrames(2) {
            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                mainExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(screenshot: ScreenshotResult) {
                        ioExecutor.execute {
                            val result = try {
                                val bitmap = screenshot.toBitmap()
                                if (bitmap == null) {
                                    ScreenshotSaveResult.Failed("截图结果为空")
                                } else {
                                    try {
                                        fileStore.writePngAtomically(target, bitmap.toPngBytes(), overwrite)
                                    } finally {
                                        bitmap.recycle()
                                    }
                                }
                            } catch (error: Exception) {
                                ScreenshotSaveResult.Failed(error.message ?: "保存失败")
                            }
                            mainHandler.post {
                                handleSaveResult(result, current, config)
                                completeCapture(overlayRemoved)
                            }
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        notifyCaptureFailure("截图失败: $errorCode")
                        completeCapture(overlayRemoved)
                    }
                }
            )
        }
    }

    private fun handleSaveResult(
        result: ScreenshotSaveResult,
        current: CaseShotState,
        config: CaseShotConfig
    ) {
        when (result) {
            is ScreenshotSaveResult.Saved -> {
                stateRepository.save(StateRepository.afterCaptureSuccess(current), config.prefix)
                notifyResult("截图已保存", "${result.file.name}\n${result.file.absolutePath}")
            }
            is ScreenshotSaveResult.Overwritten -> {
                stateRepository.save(StateRepository.afterCaptureSuccess(current), config.prefix)
                notifyResult("截图已覆盖", "${result.file.name}\n${result.file.absolutePath}")
            }
            is ScreenshotSaveResult.Skipped -> {
                stateRepository.save(StateRepository.afterCaptureSuccess(current), config.prefix)
                notifyResult("截图已跳过", result.file.name)
            }
            is ScreenshotSaveResult.Failed -> {
                notifyCaptureFailure(result.error)
            }
        }
    }

    private fun completeCapture(overlayRemoved: Boolean) {
        captureInProgress = false
        if (overlayRemoved) {
            overlayController?.restoreAfterCaptureRemoval()
        }
    }

    private fun markDone() {
        val config = configRepository.load()
        val current = stateRepository.load(config.prefix)
        val next = StateRepository.nextCase(current)
        stateRepository.save(next, config.prefix)
        val caseId = namingService.buildCaseId(next.prefix, next.caseIndex, config.caseDigits)
        notifyResult("用例切换", "已进入用例 $caseId")
    }

    private fun runAfterFrames(frameCount: Int, block: () -> Unit) {
        var count = 0
        Choreographer.getInstance().postFrameCallback(object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                count += 1
                if (count >= frameCount) {
                    block()
                } else {
                    Choreographer.getInstance().postFrameCallback(this)
                }
            }
        })
    }

    private fun canCaptureNow(): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (now - lastCaptureTime < MIN_CAPTURE_INTERVAL_MS) {
            return false
        }
        lastCaptureTime = now
        return true
    }

    private fun notifyCaptureFailure(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        notifyResult("截图失败", message, isError = true)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                RESULT_CHANNEL_ID,
                "测试证迹助手结果",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "截图保存、失败和用例切换提示"
                enableVibration(true)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun notifyResult(title: String, message: String, isError: Boolean = false) {
        Toast.makeText(this, title, Toast.LENGTH_SHORT).show()
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, RESULT_CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        val notification = builder
            .setContentTitle(title)
            .setContentText(message.lineSequence().firstOrNull().orEmpty())
            .setStyle(Notification.BigTextStyle().bigText(message))
            .setSmallIcon(R.drawable.ic_notification)
            .setLargeIcon(createNotificationLargeIcon())
            .setTimeoutAfter(if (isError) ERROR_NOTIFICATION_TIMEOUT_MS else RESULT_NOTIFICATION_TIMEOUT_MS)
            .setAutoCancel(true)
            .build()
        getSystemService(NotificationManager::class.java).notify(resultNotificationId++, notification)
    }

    private fun createNotificationLargeIcon(): Bitmap {
        val size = (64 * resources.displayMetrics.density).toInt().coerceAtLeast(64)
        val bounds = Rect(0, 0, size, size)
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        BitmapFactory.decodeResource(resources, R.drawable.ic_launcher_background)?.let { background ->
            canvas.drawBitmap(background, null, bounds, null)
            background.recycle()
        }
        BitmapFactory.decodeResource(resources, R.drawable.ic_launcher_foreground)?.let { foreground ->
            canvas.drawBitmap(foreground, null, bounds, null)
            foreground.recycle()
        }
        return output
    }

    private fun ScreenshotResult.toBitmap(): Bitmap? {
        val buffer = hardwareBuffer ?: return null
        return try {
            Bitmap.wrapHardwareBuffer(buffer, colorSpace)?.copy(Bitmap.Config.ARGB_8888, false)
        } finally {
            buffer.close()
        }
    }

    private fun Bitmap.toPngBytes(): ByteArray {
        val output = ByteArrayOutputStream()
        compress(Bitmap.CompressFormat.PNG, 100, output)
        return output.toByteArray()
    }

    companion object {
        private const val MIN_CAPTURE_INTERVAL_MS = 500L
        private const val RESULT_CHANNEL_ID = "caseshot_result"
        private const val RESULT_NOTIFICATION_ID = 100
        private const val RESULT_NOTIFICATION_TIMEOUT_MS = 2_000L
        private const val ERROR_NOTIFICATION_TIMEOUT_MS = 3_000L
    }
}
