package com.caseshot

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout

class OverlayController(
    private val context: Context,
    private val onScreenshot: () -> Unit,
    private val onDone: () -> Unit
) {
    companion object {
        private const val SKY_BLUE = "#2196F3"
        private const val SKY_BLUE_DARK = "#1976D2"
        private const val OVERLAY_BG = "#E3F2FD"
    }

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var view: View? = null
    private var params: WindowManager.LayoutParams? = null

    @SuppressLint("ClickableViewAccessibility")
    fun show() {
        if (view != null) return

        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
            background = GradientDrawable().apply {
                setColor(Color.parseColor(OVERLAY_BG))
                cornerRadius = 20f
                setStroke(2, Color.parseColor(SKY_BLUE))
            }
        }

        layout.addView(Button(context).apply {
            text = "截图"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(32, 16, 32, 16)
            background = GradientDrawable().apply {
                setColor(Color.parseColor(SKY_BLUE))
                cornerRadius = 12f
            }
            setOnClickListener { onScreenshot() }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 12)
            }
        })

        layout.addView(Button(context).apply {
            text = "完成"
            setTextColor(Color.parseColor(SKY_BLUE_DARK))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(32, 16, 32, 16)
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = 12f
                setStroke(2, Color.parseColor(SKY_BLUE))
            }
            setOnClickListener { onDone() }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        })

        val layoutParams = WindowManager.LayoutParams(
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

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false

        layout.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                        isDragging = true
                    }
                    if (isDragging) {
                        layoutParams.x = initialX + dx.toInt()
                        layoutParams.y = initialY + dy.toInt()
                        windowManager.updateViewLayout(layout, layoutParams)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        layout.performClick()
                    }
                    true
                }
                else -> false
            }
        }

        windowManager.addView(layout, layoutParams)
        view = layout
        params = layoutParams
    }

    fun hideForCapture() {
        view?.let {
            it.visibility = View.INVISIBLE
            try {
                windowManager.updateViewLayout(it, params)
            } catch (e: Exception) {
                // Ignore update errors
            }
        }
    }

    fun restoreAfterCapture() {
        view?.let {
            it.visibility = View.VISIBLE
            try {
                windowManager.updateViewLayout(it, params)
            } catch (e: Exception) {
                // Ignore update errors
            }
        }
    }

    fun temporarilyRemoveForCapture(): Boolean {
        view?.let {
            try {
                windowManager.removeView(it)
                return true
            } catch (e: Exception) {
                // Ignore remove errors
            }
        }
        return false
    }

    fun restoreAfterCaptureRemoval() {
        view?.let {
            try {
                windowManager.addView(it, params)
            } catch (e: Exception) {
                // Ignore add errors
            }
        }
    }

    fun remove() {
        view?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {
            }
        }
        view = null
        params = null
    }
}
