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
import android.util.Log
import android.view.WindowManager
import java.io.ByteArrayOutputStream
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.BlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class ScreenCaptureEngine(private val context: Context) {

    companion object {
        private const val TAG = "ScreenCaptureEngine"
    }

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
        if (isInitialized.getAndSet(true)) {
            Log.w(TAG, "Already initialized, skipping")
            return
        }

        try {
            mediaProjection = projection

            val metrics = DisplayMetrics()
            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(metrics)
            screenWidth = metrics.widthPixels
            screenHeight = metrics.heightPixels
            screenDpi = metrics.densityDpi
            Log.d(TAG, "Screen: ${screenWidth}x${screenHeight} @ ${screenDpi}dpi")

            captureThread = HandlerThread("ScreenCaptureThread").apply { start() }
            captureHandler = Handler(captureThread!!.looper)

            val reader = ImageReader.newInstance(
                screenWidth, screenHeight,
                PixelFormat.RGBA_8888, 2
            )
            reader.setOnImageAvailableListener({ reader ->
                try {
                    val image = reader?.acquireLatestImage()
                    if (image != null) {
                        if (!imageQueue.offer(image)) {
                            image.close()
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error acquiring image", e)
                }
            }, captureHandler)
            imageReader = reader

            projectionCallback = object : MediaProjection.Callback() {
                override fun onStop() {
                    Log.d(TAG, "MediaProjection stopped")
                    projectionStopped = true
                }
            }
            projection.registerCallback(projectionCallback!!, captureHandler)

            val display = projection.createVirtualDisplay(
                "CaseShotCapture",
                screenWidth, screenHeight, screenDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.surface,
                null,
                captureHandler
            )
            if (display == null) {
                throw IllegalStateException("Failed to create VirtualDisplay")
            }
            virtualDisplay = display

            Log.d(TAG, "ScreenCaptureEngine started successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start ScreenCaptureEngine", e)
            stop()
            throw e
        }
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

        Log.d(TAG, "Stopping ScreenCaptureEngine")
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
