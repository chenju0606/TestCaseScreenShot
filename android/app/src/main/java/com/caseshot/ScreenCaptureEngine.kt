package com.caseshot

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.HandlerThread
import android.util.DisplayMetrics
import android.view.WindowManager
import java.io.ByteArrayOutputStream
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.BlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class ScreenCaptureEngine(private val context: Context) {

    private var imageReader: ImageReader? = null
    private var virtualDisplay: android.hardware.display.VirtualDisplay? = null
    private var mediaProjection: MediaProjection? = null
    private var projectionCallback: MediaProjection.Callback? = null

    private var captureThread: HandlerThread? = null
    private var captureHandler: Handler? = null

    private val imageQueue: BlockingQueue<Image> = ArrayBlockingQueue(2)
    private val isCapturing = AtomicBoolean(false)
    private val isInitialized = AtomicBoolean(false)

    private var screenWidth = 0
    private var screenHeight = 0
    private var screenDpi = 0

    @Volatile
    private var projectionStopped = false

    fun start(projection: MediaProjection) {
        if (isInitialized.getAndSet(true)) return

        mediaProjection = projection

        val metrics = DisplayMetrics()
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels
        screenDpi = metrics.densityDpi

        captureThread = HandlerThread("ScreenCaptureThread").apply { start() }
        captureHandler = Handler(captureThread!!.looper)

        imageReader = ImageReader.newInstance(
            screenWidth, screenHeight,
            PixelFormat.RGBA_8888, 2
        ).apply {
            setOnImageAvailableListener({ reader ->
                val image = reader?.acquireLatestImage()
                if (image != null) {
                    if (!imageQueue.offer(image)) {
                        image.close()
                    }
                }
            }, captureHandler)
        }

        projectionCallback = object : MediaProjection.Callback() {
            override fun onStop() {
                projectionStopped = true
            }
        }
        projection.registerCallback(projectionCallback!!, captureHandler)

        virtualDisplay = projection.createVirtualDisplay(
            "CaseShotCapture",
            screenWidth, screenHeight, screenDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader!!.surface,
            null,
            captureHandler
        ) ?: throw IllegalStateException("Unable to create virtual display")
    }

    fun capture(timeoutMs: Long = 3000): ByteArray {
        if (!isInitialized.get()) {
            throw IllegalStateException("ScreenCaptureEngine not initialized. Call start() first.")
        }
        if (projectionStopped) {
            throw IllegalStateException("MediaProjection has been stopped.")
        }

        if (!isCapturing.compareAndSet(false, true)) {
            throw IllegalStateException("Capture already in progress.")
        }

        try {
            val image = imageQueue.poll(timeoutMs, TimeUnit.MILLISECONDS)
                ?: throw IllegalStateException("Capture timeout: no image received within ${timeoutMs}ms")

            return try {
                imageToPngBytes(image)
            } finally {
                image.close()
            }
        } finally {
            isCapturing.set(false)
        }
    }

    fun stop() {
        if (!isInitialized.getAndSet(false)) return

        isCapturing.set(false)

        projectionCallback?.let { cb ->
            mediaProjection?.unregisterCallback(cb)
        }
        projectionCallback = null

        virtualDisplay?.release()
        virtualDisplay = null

        imageReader?.setOnImageAvailableListener(null, null)
        imageReader?.close()
        imageReader = null

        captureThread?.quitSafely()
        try {
            captureThread?.join(500)
        } catch (_: InterruptedException) {
        }
        captureThread = null
        captureHandler = null

        drainImageQueue()

        mediaProjection = null
        projectionStopped = false
    }

    private fun drainImageQueue() {
        while (true) {
            val image = imageQueue.poll() ?: break
            image.close()
        }
    }

    private fun imageToPngBytes(image: Image): ByteArray {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * screenWidth

        val bitmap = Bitmap.createBitmap(
            screenWidth + rowPadding / pixelStride,
            screenHeight,
            Bitmap.Config.ARGB_8888
        )
        bitmap.copyPixelsFromBuffer(buffer)

        val cropped = if (bitmap.width != screenWidth || bitmap.height != screenHeight) {
            Bitmap.createBitmap(bitmap, 0, 0, screenWidth, screenHeight).also {
                if (it !== bitmap) bitmap.recycle()
            }
        } else {
            bitmap
        }

        return try {
            val output = ByteArrayOutputStream(1024 * 1024)
            cropped.compress(Bitmap.CompressFormat.PNG, 100, output)
            output.toByteArray()
        } finally {
            cropped.recycle()
        }
    }
}
