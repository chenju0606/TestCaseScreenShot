package com.caseshot

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityService.ScreenshotResult
import android.accessibilityservice.AccessibilityService.TakeScreenshotCallback
import android.graphics.Bitmap
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

class CaptureAccessibilityService : AccessibilityService() {
    private lateinit var configRepository: ConfigRepository
    private lateinit var stateRepository: StateRepository
    private val namingService = NamingService()
    private val fileStore = FileStore()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val mainExecutor = Executor { command -> mainHandler.post(command) }

    private var overlayController: OverlayController? = null
    private var captureInProgress = false
    private var lastCaptureTime = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        configRepository = ConfigRepository(this)
        stateRepository = StateRepository(this)
        showOverlay()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
    }

    override fun onInterrupt() {
    }

    override fun onDestroy() {
        overlayController?.remove()
        overlayController = null
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
            Toast.makeText(this, "当前系统不支持无障碍截图", Toast.LENGTH_SHORT).show()
            return
        }
        if (!canCaptureNow()) {
            Toast.makeText(this, "截图太频繁，请稍后再试", Toast.LENGTH_SHORT).show()
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
        val overlayRemoved = overlayController?.temporarilyRemoveForCapture() == true
        captureInProgress = true

        runAfterFrames(2) {
            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                mainExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(screenshot: ScreenshotResult) {
                        try {
                            val bitmap = screenshot.toBitmap()
                            if (bitmap == null) {
                                notifyCaptureFailure("截图结果为空")
                                return
                            }
                            val result = fileStore.writePngWithConflictResolution(
                                outputDir,
                                filename,
                                bitmap.toPngBytes(),
                                config.conflictResolution
                            )
                            if (result.success) {
                                val next = StateRepository.afterCaptureSuccess(current)
                                stateRepository.save(next, config.prefix)
                                Toast.makeText(
                                    this@CaptureAccessibilityService,
                                    "截图已保存: ${result.file?.name ?: filename}",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                notifyCaptureFailure(result.error ?: "保存失败")
                            }
                            bitmap.recycle()
                        } finally {
                            completeCapture(overlayRemoved)
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        notifyCaptureFailure("无障碍截图失败: $errorCode")
                        completeCapture(overlayRemoved)
                    }
                }
            )
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
        Toast.makeText(this, "已进入用例 $caseId", Toast.LENGTH_SHORT).show()
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
    }
}
