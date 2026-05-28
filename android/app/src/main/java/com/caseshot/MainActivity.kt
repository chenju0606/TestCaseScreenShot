package com.caseshot

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    companion object {
        private const val REQUEST_CAPTURE = 1001
        private const val SKY_BLUE = "#2196F3"
        private const val SKY_BLUE_DARK = "#1976D2"
        private const val SKY_BLUE_LIGHT = "#BBDEFB"
        private const val BG_COLOR = "#F5F9FF"
        private const val TEXT_PRIMARY = "#1565C0"
        private const val TEXT_SECONDARY = "#42A5F5"
    }

    private lateinit var configRepository: ConfigRepository
    private lateinit var stateRepository: StateRepository
    private lateinit var namingService: NamingService
    private lateinit var projectionManager: MediaProjectionManager

    private lateinit var prefixInput: EditText
    private lateinit var caseDigitsInput: EditText
    private lateinit var startCaseIndexInput: EditText
    private lateinit var outputDirInput: EditText
    private lateinit var captureDelayInput: EditText
    private lateinit var hideOverlayCheck: CheckBox
    private lateinit var targetPreview: TextView
    private lateinit var permissionStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configRepository = ConfigRepository(this)
        stateRepository = StateRepository(this)
        namingService = NamingService()
        projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        buildUi()
        loadConfigIntoUi()
        refreshPreview()
    }

    override fun onResume() {
        super.onResume()
        refreshPreview()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(48, 48, 48, 48)
            setBackgroundColor(Color.parseColor(BG_COLOR))
        }

        val title = TextView(this).apply {
            text = "CaseShot"
            textSize = 28f
            setTextColor(Color.parseColor(SKY_BLUE_DARK))
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, 8)
        }

        val subtitle = TextView(this).apply {
            text = "批量截屏工具"
            textSize = 14f
            setTextColor(Color.parseColor(TEXT_SECONDARY))
            setPadding(0, 0, 0, 32)
        }

        targetPreview = TextView(this).apply {
            textSize = 18f
            setTextColor(Color.parseColor(TEXT_PRIMARY))
            typeface = Typeface.DEFAULT_BOLD
            setPadding(24, 24, 24, 24)
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = 16f
                setStroke(2, Color.parseColor(SKY_BLUE_LIGHT))
            }
        }

        permissionStatus = TextView(this).apply {
            textSize = 13f
            setTextColor(Color.parseColor(TEXT_SECONDARY))
            setPadding(0, 12, 0, 24)
        }

        prefixInput = createStyledEditText("前缀，可留空，例如 049")
        caseDigitsInput = createStyledEditText("用例编号位数", true)
        startCaseIndexInput = createStyledEditText("起始用例编号", true)
        outputDirInput = createStyledEditText("保存目录，留空使用应用目录")
        captureDelayInput = createStyledEditText("截屏延迟毫秒", true)

        hideOverlayCheck = CheckBox(this).apply {
            text = "截图前隐藏悬浮窗"
            setTextColor(Color.parseColor(TEXT_PRIMARY))
            buttonTintList = android.content.res.ColorStateList.valueOf(Color.parseColor(SKY_BLUE))
        }

        val configSection = TextView(this).apply {
            text = "配置"
            textSize = 16f
            setTextColor(Color.parseColor(SKY_BLUE_DARK))
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 24, 0, 12)
        }

        val buttonsLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 24, 0, 0)
        }

        buttonsLayout.addView(createStyledButton("打开悬浮窗权限", false) { openOverlaySettings() })
        buttonsLayout.addView(createStyledButton("保存配置", false) {
            saveConfigFromUi()
            refreshPreview()
            Toast.makeText(this@MainActivity, "配置已保存", Toast.LENGTH_SHORT).show()
        })
        buttonsLayout.addView(createStyledButton("开始服务", true) { startCaptureFlow() })
        buttonsLayout.addView(createStyledButton("停止服务", false) {
            stopService(Intent(this@MainActivity, CaptureService::class.java))
        })

        listOf<View>(
            title,
            subtitle,
            targetPreview,
            permissionStatus,
            configSection,
            prefixInput,
            caseDigitsInput,
            startCaseIndexInput,
            outputDirInput,
            captureDelayInput,
            hideOverlayCheck,
            buttonsLayout
        ).forEach { root.addView(it) }

        setContentView(root)
    }

    private fun createStyledEditText(hint: String, isNumber: Boolean = false): EditText {
        return EditText(this).apply {
            this.hint = hint
            setTextColor(Color.parseColor(TEXT_PRIMARY))
            setHintTextColor(Color.parseColor(TEXT_SECONDARY))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setPadding(32, 24, 32, 24)
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = 12f
                setStroke(1, Color.parseColor(SKY_BLUE_LIGHT))
            }
            if (isNumber) {
                inputType = android.text.InputType.TYPE_CLASS_NUMBER
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 8, 0, 8)
            }
        }
    }

    private fun createStyledButton(text: String, isPrimary: Boolean, onClick: () -> Unit): Button {
        return Button(this).apply {
            this.text = text
            setTextColor(if (isPrimary) Color.WHITE else Color.parseColor(SKY_BLUE))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(32, 20, 32, 20)
            background = GradientDrawable().apply {
                cornerRadius = 12f
                if (isPrimary) {
                    setColor(Color.parseColor(SKY_BLUE))
                    setStroke(0, Color.TRANSPARENT)
                } else {
                    setColor(Color.WHITE)
                    setStroke(2, Color.parseColor(SKY_BLUE))
                }
            }
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 8, 0, 8)
            }
        }
    }

    private fun loadConfigIntoUi() {
        val config = configRepository.load()
        prefixInput.setText(config.prefix)
        caseDigitsInput.setText(config.caseDigits.toString())
        startCaseIndexInput.setText(config.startCaseIndex.toString())
        outputDirInput.setText(config.outputDir)
        captureDelayInput.setText(config.captureDelayMs.toString())
        hideOverlayCheck.isChecked = config.hideFloatingWindowBeforeCapture
    }

    private fun saveConfigFromUi(): CaseShotConfig {
        val config = CaseShotConfig(
            prefix = prefixInput.text.toString(),
            caseDigits = caseDigitsInput.text.toString().toIntOrNull() ?: 3,
            startCaseIndex = startCaseIndexInput.text.toString().toIntOrNull() ?: 1,
            outputDir = outputDirInput.text.toString(),
            captureDelayMs = captureDelayInput.text.toString().toLongOrNull() ?: 300L,
            hideFloatingWindowBeforeCapture = hideOverlayCheck.isChecked
        )
        return configRepository.save(config)
    }

    private fun refreshPreview() {
        val config = configRepository.load()
        val state = stateRepository.load(config.prefix)
        val filename = namingService.buildFilename(state.prefix, state.caseIndex, state.shotIndex, config.caseDigits)
        targetPreview.text = "  下一张: $filename"
        permissionStatus.text = if (Settings.canDrawOverlays(this)) "✓ 悬浮窗权限已允许" else "✗ 悬浮窗权限未允许"
    }

    private fun openOverlaySettings() {
        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
        startActivity(intent)
    }

    private fun startCaptureFlow() {
        val config = saveConfigFromUi()
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "请先允许悬浮窗权限", Toast.LENGTH_SHORT).show()
            openOverlaySettings()
            return
        }
        val currentState = stateRepository.load(config.prefix)
        val resetState = StateRepository.resetToStartCase(currentState, config.prefix, config.startCaseIndex)
        stateRepository.save(resetState, config.prefix)
        refreshPreview()
        startActivityForResult(projectionManager.createScreenCaptureIntent(), REQUEST_CAPTURE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CAPTURE && resultCode == RESULT_OK && data != null) {
            val intent = Intent(this, CaptureService::class.java)
                .putExtra(CaptureService.EXTRA_RESULT_CODE, resultCode)
                .putExtra(CaptureService.EXTRA_RESULT_DATA, data)
            startForegroundService(intent)
        } else if (requestCode == REQUEST_CAPTURE) {
            Toast.makeText(this, "未获得截屏权限", Toast.LENGTH_SHORT).show()
        }
    }
}
