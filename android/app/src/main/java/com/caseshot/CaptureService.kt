package com.caseshot

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.widget.Toast

class CaptureService : Service() {

    companion object {
        private const val TAG = "CaptureService"
        const val EXTRA_RESULT_CODE = "com.caseshot.RESULT_CODE"
        const val EXTRA_RESULT_DATA = "com.caseshot.RESULT_DATA"
        private const val CHANNEL_ID = "caseshot_capture"
        private const val NOTIFICATION_ID = 49
    }

    private lateinit var configRepository: ConfigRepository
    private lateinit var stateRepository: StateRepository
    private val namingService = NamingService()
    private val fileStore = FileStore()

    private var overlayController: OverlayController? = null
    private var captureEngine: ScreenCaptureEngine? = null
    private var mediaProjection: MediaProjection? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")
        configRepository = ConfigRepository(this)
        stateRepository = StateRepository(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand intent=$intent")
        
        startForeground(NOTIFICATION_ID, buildNotification())

        if (intent?.hasExtra(EXTRA_RESULT_CODE) == true) {
            val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
            val resultData: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(EXTRA_RESULT_DATA)
            }

            Log.d(TAG, "resultCode=$resultCode, resultData=$resultData")

            if (resultData != null && mediaProjection == null) {
                val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                val projection = manager.getMediaProjection(resultCode, resultData)

                if (projection != null) {
                    Log.d(TAG, "MediaProjection obtained, initializing engine")
                    mediaProjection = projection
                    try {
                        initCaptureEngine(projection)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to init capture engine", e)
                        Toast.makeText(this, "初始化截屏引擎失败: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                } else {
                    Log.e(TAG, "Failed to get MediaProjection")
                    Toast.makeText(this, "无法获取截屏权限", Toast.LENGTH_SHORT).show()
                    stopSelf()
                    return START_NOT_STICKY
                }
            }
        } else {
            Log.w(TAG, "No EXTRA_RESULT_CODE in intent")
        }

        ensureOverlay()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
        releaseResources()
        super.onDestroy()
    }

    private fun initCaptureEngine(projection: MediaProjection) {
        captureEngine?.stop()
        captureEngine = ScreenCaptureEngine(this).also {
            it.start(projection)
        }
    }

    private fun releaseResources() {
        captureEngine?.stop()
        captureEngine = null

        overlayController?.remove()
        overlayController = null

        mediaProjection?.stop()
        mediaProjection = null
    }

    private fun ensureOverlay() {
        if (overlayController != null) return
        overlayController = OverlayController(
            context = this,
            onScreenshot = { captureCurrentScreen() },
            onDone = { markDone() }
        ).also { it.show() }
    }

    private fun captureCurrentScreen() {
        val engine = captureEngine
        if (engine == null) {
            Toast.makeText(this, "截屏引擎未初始化", Toast.LENGTH_SHORT).show()
            return
        }

        val config = configRepository.load()
        val current = stateRepository.load(config.prefix)
        val filename = namingService.buildFilename(
            current.prefix, current.caseIndex, current.shotIndex, config.caseDigits
        )
        val outputDir = fileStore.resolveOutputDir(this, config)

        try {
            if (config.hideFloatingWindowBeforeCapture) {
                overlayController?.hideForCapture()
                if (config.captureDelayMs > 0) {
                    Thread.sleep(config.captureDelayMs)
                }
            }

            val pngBytes = engine.capture(timeoutMs = 5000)
            val target = fileStore.writePng(outputDir, filename, pngBytes)
            val next = StateRepository.afterCaptureSuccess(current)
            stateRepository.save(next, config.prefix)

            Toast.makeText(this, "已保存: ${target.name}", Toast.LENGTH_SHORT).show()
        } catch (e: IllegalStateException) {
            Log.e(TAG, "Capture failed", e)
            Toast.makeText(this, "截图失败: ${e.message}", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Log.e(TAG, "Capture failed", e)
            Toast.makeText(this, "截图失败: ${e.message}", Toast.LENGTH_LONG).show()
        } finally {
            if (config.hideFloatingWindowBeforeCapture) {
                overlayController?.restoreAfterCapture()
            }
        }
    }

    private fun markDone() {
        val config = configRepository.load()
        val current = stateRepository.load(config.prefix)
        val next = StateRepository.nextCase(current)
        stateRepository.save(next, config.prefix)
        Toast.makeText(
            this,
            "进入用例 ${namingService.buildCaseId(next.prefix, next.caseIndex, config.caseDigits)}",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "CaseShot", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return builder
            .setContentTitle("CaseShot 运行中")
            .setContentText("悬浮窗已开启，可连续截图")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .build()
    }
}
