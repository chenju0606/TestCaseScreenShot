package com.caseshot

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
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

    private fun createIconButton(iconRes: Int): ImageButton {
        return ImageButton(context).apply {
            setImageResource(iconRes)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(20, 20, 20, 20)
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = BUTTON_CORNER_PX
            }
            isClickable = false
            isFocusable = false
            layoutParams = LinearLayout.LayoutParams(BUTTON_SIZE_PX, BUTTON_SIZE_PX).apply {
                setMargins(0, 0, 0, 12)
            }
        }
    }

    private fun isWithinButton(rawX: Float, rawY: Float, button: ImageButton): Boolean {
        val loc = IntArray(2)
        button.getLocationOnScreen(loc)
        val centerX = loc[0] + button.width / 2
        val centerY = loc[1] + button.height / 2
        return Math.abs(rawX - centerX) < button.width / 2 &&
            Math.abs(rawY - centerY) < button.height / 2
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

        val screenshotButton = createIconButton(R.drawable.screenshot)
        val doneButton = createIconButton(R.drawable.arrow_circle_right)
        layout.addView(screenshotButton)
        layout.addView(doneButton)

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            OverlayWindowFlags.defaultFlags(),
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = initialX
            y = initialY
        }

        val handler = Handler(Looper.getMainLooper())
        val longPressTimeout = ViewConfiguration.getLongPressTimeout().toLong()
        val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

        var touchStartLayoutX = 0
        var touchStartLayoutY = 0
        var touchStartRawX = 0f
        var touchStartRawY = 0f
        var isLongPressing = false
        val longPressRunnable = Runnable {
            isLongPressing = true
            layout.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }

        layout.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    touchStartLayoutX = layoutParams.x
                    touchStartLayoutY = layoutParams.y
                    touchStartRawX = event.rawX
                    touchStartRawY = event.rawY
                    isLongPressing = false
                    handler.postDelayed(longPressRunnable, longPressTimeout)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - touchStartRawX
                    val dy = event.rawY - touchStartRawY
                    if (isLongPressing) {
                        layoutParams.x = touchStartLayoutX + dx.toInt()
                        layoutParams.y = touchStartLayoutY + dy.toInt()
                        windowManager.updateViewLayout(layout, layoutParams)
                    } else if (Math.abs(dx) > touchSlop || Math.abs(dy) > touchSlop) {
                        handler.removeCallbacks(longPressRunnable)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    handler.removeCallbacks(longPressRunnable)
                    if (isLongPressing) {
                        isLongPressing = false
                    } else if (isWithinButton(event.rawX, event.rawY, screenshotButton)) {
                        onScreenshot()
                    } else if (isWithinButton(event.rawX, event.rawY, doneButton)) {
                        onDone()
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacks(longPressRunnable)
                    isLongPressing = false
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
                windowManager.removeViewImmediate(it)
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
