package com.caseshot

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout

class OverlayController(
    private val context: Context,
    private val onScreenshot: () -> Unit,
    private val onDone: () -> Unit
) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var view: View? = null

    fun show() {
        if (view != null) return
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(8, 8, 8, 8)
            setBackgroundColor(0xCC202124.toInt())
        }
        layout.addView(Button(context).apply {
            text = "截图"
            setOnClickListener { onScreenshot() }
        })
        layout.addView(Button(context).apply {
            text = "完成"
            setOnClickListener { onDone() }
        })

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 20
            y = 220
        }

        windowManager.addView(layout, params)
        view = layout
    }

    fun hideForCapture() {
        view?.visibility = View.INVISIBLE
    }

    fun restoreAfterCapture() {
        view?.visibility = View.VISIBLE
    }

    fun remove() {
        view?.let { windowManager.removeView(it) }
        view = null
    }
}