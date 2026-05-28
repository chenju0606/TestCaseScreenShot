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
import android.widget.Toast

class CaptureService : Service() {
    private lateinit var configRepository: ConfigRepository
    private lateinit var stateRepository: StateRepository
    private val namingService = NamingService()
    private val fileStore = FileStore()
    private var overlayController: OverlayController? = null
    private var mediaProjection: MediaProjection? = null

    override fun onCreate() {
        super.onCreate()
        configRepository = ConfigRepository(this)
        stateRepository = StateRepository(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        if (intent?.hasExtra(EXTRA_RESULT_CODE) == true) {
            val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
            val resultData = intent.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)
            if (resultData != null) {
                val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                mediaProjection = manager.getMediaProjection(resultCode, resultData)
            }
        }
        ensureOverlay()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        overlayController?.remove()
        overlayController = null
        mediaProjection?.stop()
        mediaProjection = null
        super.onDestroy()
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
        Toast.makeText(this, "截屏引擎将在下一任务接入", Toast.LENGTH_SHORT).show()
    }

    private fun markDone() {
        val config = configRepository.load()
        val current = stateRepository.load(config.prefix)
        val next = StateRepository.nextCase(current)
        stateRepository.save(next, config.prefix)
        Toast.makeText(this, "进入用例 ${namingService.buildCaseId(next.prefix, next.caseIndex, config.caseDigits)}", Toast.LENGTH_SHORT).show()
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
            .setContentText("悬浮窗已开启")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .build()
    }

    companion object {
        const val EXTRA_RESULT_CODE = "com.caseshot.RESULT_CODE"
        const val EXTRA_RESULT_DATA = "com.caseshot.RESULT_DATA"
        private const val CHANNEL_ID = "caseshot_capture"
        private const val NOTIFICATION_ID = 49
    }
}