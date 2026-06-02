package com.caseshot

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout

class OverlayController(
    private val context: Context,
    private val onScreenshot: () -> Unit,
    private val onDone: () -> Unit
) {
    companion object {
        private const val SKY_BLUE = "#2196F3"
        private const val OVERLAY_BG = "#E3F2FD"
        private const val BUTTON_SIZE_PX = 140
        private const val BUTTON_CORNER_PX = 70f
        private const val CONTAINER_CORNER_PX = 80f
        private const val RIGHT_MARGIN_PX = 24
        private const val BELOW_CENTER_OFFSET_PX = 300
        private const val CONTAINER_PADDING_PX = 12
        private const val BUTTON_GAP_PX = 12
        private const val STROKE_PX = 2
    }

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var view: View? = null
    private var params: WindowManager.LayoutParams? = null

    private fun createIconButton(iconRes: Int, onClick: () -> Unit): ImageButton {
        return ImageButton(context).apply {
            setImageResource(iconRes)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(20, 20, 20, 20)
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = BUTTON_CORNER_PX
            }
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(BUTTON_SIZE_PX, BUTTON_SIZE_PX).apply {
                setMargins(0, 0, 0, 12)
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    fun show() {
        if (view != null) return

        val displayMetrics = context.resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels

        val containerWidth =
            BUTTON_SIZE_PX + CONTAINER_PADDING_PX * 2 + STROKE_PX * 2
        val containerHeight =
            BUTTON_SIZE_PX * 2 + CONTAINER_PADDING_PX * 2 + BUTTON_GAP_PX + STROKE_PX * 2
        val initialX = screenWidth - containerWidth - RIGHT_MARGIN_PX
        val initialY = (screenHeight - containerHeight) / 2 + BELOW_CENTER_OFFSET_PX

        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(CONTAINER_PADDING_PX, CONTAINER_PADDING_PX, CONTAINER_PADDING_PX, CONTAINER_PADDING_PX)
            background = GradientDrawable().apply {
                setColor(Color.parseColor(OVERLAY_BG))
                cornerRadius = CONTAINER_CORNER_PX
                setStroke(STROKE_PX, Color.parseColor(SKY_BLUE))
            }
        }

        layout.addView(createIconButton(R.drawable.screenshot, onScreenshot))
        layout.addView(createIconButton(R.drawable.arrow_circle_right, onDone))

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
            x = initialX
            y = initialY
        }

        var touchStartLayoutX = 0
        var touchStartLayoutY = 0
        var touchStartRawX = 0f
        var touchStartRawY = 0f
        var isDragging = false

        layout.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    touchStartLayoutX = layoutParams.x
                    touchStartLayoutY = layoutParams.y
                    touchStartRawX = event.rawX
                    touchStartRawY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - touchStartRawX
                    val dy = event.rawY - touchStartRawY
                    if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                        isDragging = true
                    }
                    if (isDragging) {
                        layoutParams.x = touchStartLayoutX + dx.toInt()
                        layoutParams.y = touchStartLayoutY + dy.toInt()
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
