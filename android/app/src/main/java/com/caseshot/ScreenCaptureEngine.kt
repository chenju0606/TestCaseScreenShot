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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

class ScreenCaptureEngine(private val context: Context) {

    companion object {
        private const val TAG = "ScreenCaptureEngine"
        private const val FIRST_FRAME_TIMEOUT_MS = 2000L
    }

    private var imageReader: ImageReader? = null
    private var virtualDisplay: android.hardware.display.VirtualDisplay? = null
    private var mediaProjection: MediaProjection? = null
    private var projectionCallback: MediaProjection.Callback? = null

    private var captureThread: HandlerThread? = null
    private var captureHandler: Handler? = null

    private val bitmapLock = ReentrantReadWriteLock()
    @Volatile
    private var latestBitmap: Bitmap? = null
    private var firstFrameLatch: CountDownLatch = CountDownLatch(1)

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
                PixelFormat.RGBA_8888, 3
            )
            reader.setOnImageAvailableListener({ r ->
                handleImageAvailable(r)
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
            Log.d(TAG, "VirtualDisplay created, waiting for first frame asynchronously")

            Log.d(TAG, "ScreenCaptureEngine started successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start ScreenCaptureEngine", e)
            stop()
            throw e
        }
    }

    private fun handleImageAvailable(reader: ImageReader?) {
        try {
            while (true) {
                val image = reader?.acquireLatestImage() ?: break
                try {
                    val bitmap = imageToBitmap(image)
                    if (bitmap != null) {
                        bitmapLock.write {
                            val old = latestBitmap
                            latestBitmap = bitmap
                            old?.recycle()
                        }
                        firstFrameLatch.countDown()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error converting image to bitmap", e)
                } finally {
                    image.close()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in handleImageAvailable", e)
        }
    }

    fun capture(): ByteArray {
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
            if (!firstFrameLatch.await(FIRST_FRAME_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                throw IllegalStateException("No frame captured yet. Please wait a moment after starting.")
            }

            val bitmap = bitmapLock.read { latestBitmap }
                ?: throw IllegalStateException("No bitmap available yet. Please wait a moment after starting.")

            val copy = bitmap.copy(Bitmap.Config.ARGB_8888, false)
                ?: throw IllegalStateException("Failed to copy bitmap")

            return try {
                bitmapToPngBytes(copy)
            } finally {
                copy.recycle()
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

        bitmapLock.write {
            latestBitmap?.recycle()
            latestBitmap = null
        }

        firstFrameLatch = CountDownLatch(1)

        mediaProjection = null
        projectionStopped = false
    }

    private fun imageToBitmap(image: Image): Bitmap? {
        try {
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

            return if (bitmap.width != screenWidth || bitmap.height != screenHeight) {
                val cropped = Bitmap.createBitmap(bitmap, 0, 0, screenWidth, screenHeight)
                if (cropped !== bitmap) {
                    bitmap.recycle()
                }
                cropped
            } else {
                bitmap
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in imageToBitmap", e)
            return null
        }
    }

    private fun bitmapToPngBytes(bitmap: Bitmap): ByteArray {
        val output = ByteArrayOutputStream(1024 * 1024)
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        return output.toByteArray()
    }
}
