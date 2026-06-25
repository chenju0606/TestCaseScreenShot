package com.caseshot

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.Space
import android.widget.TextView

class OverlayController(
    private val context: Context,
    private val onScreenshot: () -> Unit,
    private val onDone: () -> Unit,
    private val windowType: Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
    } else {
        @Suppress("DEPRECATION")
        WindowManager.LayoutParams.TYPE_PHONE
    }
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
    private var controlsLayout: LinearLayout? = null
    private var screenshotButton: ImageButton? = null
    private var doneButton: ImageButton? = null

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    private fun createControlsBackground(): GradientDrawable =
        GradientDrawable().apply {
            setColor(Color.parseColor(OVERLAY_BG))
            cornerRadius = CONTAINER_CORNER_PX
            setStroke(STROKE_PX, Color.parseColor(SKY_BLUE))
        }

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
            background = createControlsBackground()
        }

        controlsLayout = layout
        restoreControls()

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            windowType,
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
                    } else if (screenshotButton?.let { isWithinButton(event.rawX, event.rawY, it) } == true) {
                        onScreenshot()
                    } else if (doneButton?.let { isWithinButton(event.rawX, event.rawY, it) } == true) {
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

    fun showExistingScreenshotPanel(
        filename: String,
        onSkip: () -> Unit,
        onOverwrite: () -> Unit
    ) {
        controlsLayout?.apply {
            removeAllViews()
            screenshotButton = null
            doneButton = null
            background = context.getDrawable(R.drawable.bg_float_panel)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            minimumWidth = dp(230)
            addView(TextView(context).apply {
                text = "截图已存在"
                textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(context.getColor(R.color.evidence_text_primary))
            })
            addView(TextView(context).apply {
                text = "$filename 已存在，请选择处理方式"
                textSize = 13f
                setTextColor(context.getColor(R.color.evidence_text_secondary))
                maxWidth = dp(230)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.MIDDLE
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, dp(4), 0, 0)
                }
            })
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, dp(10), 0, 0)
                }
                addView(createConflictAction("跳过", R.drawable.bg_btn_skip) {
                    restoreControls()
                    onSkip()
                })
                addView(Space(context).apply {
                    layoutParams = LinearLayout.LayoutParams(dp(8), 1)
                })
                addView(createConflictAction("覆盖", R.drawable.bg_btn_overwrite) {
                    restoreControls()
                    onOverwrite()
                })
            })
        }
    }

    private fun createConflictAction(text: String, backgroundRes: Int, onClick: () -> Unit): TextView =
        TextView(context).apply {
            this.text = text
            gravity = Gravity.CENTER
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            setBackgroundResource(backgroundRes)
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(0, dp(36), 1f)
        }

    fun restoreControls() {
        val layout = controlsLayout ?: return
        layout.removeAllViews()
        layout.background = createControlsBackground()
        layout.setPadding(CONTAINER_PADDING_PX, CONTAINER_PADDING_PX, CONTAINER_PADDING_PX, CONTAINER_PADDING_PX)
        layout.minimumWidth = 0
        screenshotButton = createIconButton(R.drawable.screenshot)
        doneButton = createIconButton(R.drawable.arrow_circle_right)
        layout.addView(screenshotButton)
        layout.addView(doneButton)
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
