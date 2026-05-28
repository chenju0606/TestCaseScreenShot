package com.caseshot

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.WindowManager
import java.io.ByteArrayOutputStream

class ScreenCaptureEngine(private val context: Context) {
    fun capturePng(mediaProjection: MediaProjection): ByteArray {
        val metrics = DisplayMetrics()
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)

        val imageReader = ImageReader.newInstance(metrics.widthPixels, metrics.heightPixels, PixelFormat.RGBA_8888, 2)
        val display = mediaProjection.createVirtualDisplay(
            "CaseShotCapture",
            metrics.widthPixels,
            metrics.heightPixels,
            metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader.surface,
            null,
            Handler(Looper.getMainLooper())
        )

        try {
            Thread.sleep(150)
            val image = imageReader.acquireLatestImage() ?: throw IllegalStateException("Unable to acquire screen image")
            image.use {
                val plane = it.planes[0]
                val buffer = plane.buffer
                val pixelStride = plane.pixelStride
                val rowStride = plane.rowStride
                val rowPadding = rowStride - pixelStride * metrics.widthPixels
                val bitmap = Bitmap.createBitmap(
                    metrics.widthPixels + rowPadding / pixelStride,
                    metrics.heightPixels,
                    Bitmap.Config.ARGB_8888
                )
                bitmap.copyPixelsFromBuffer(buffer)
                val cropped = Bitmap.createBitmap(bitmap, 0, 0, metrics.widthPixels, metrics.heightPixels)
                val output = ByteArrayOutputStream()
                cropped.compress(Bitmap.CompressFormat.PNG, 100, output)
                bitmap.recycle()
                cropped.recycle()
                return output.toByteArray()
            }
        } finally {
            display.release()
            imageReader.close()
        }
    }
}