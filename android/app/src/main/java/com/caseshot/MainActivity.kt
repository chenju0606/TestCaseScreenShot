package com.caseshot

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    companion object {
        private const val REQUEST_DIRECTORY = 1002
        private const val SKY_BLUE = "#2196F3"
        private const val SKY_BLUE_DARK = "#1976D2"
        private const val SKY_BLUE_LIGHT = "#BBDEFB"
        private const val BG_COLOR = "#F5F9FF"
        private const val TEXT_PRIMARY = "#1565C0"
        private const val TEXT_SECONDARY = "#42A5F5"
    }

    private lateinit var configRepository: ConfigRepository
    private lateinit var stateRepository: StateRepository
    private val namingService = NamingService()

    private lateinit var prefixInput: EditText
    private lateinit var caseDigitsInput: EditText
    private lateinit var startCaseIndexInput: EditText
    private lateinit var outputDirInput: EditText
    private lateinit var targetPreview: TextView
    private lateinit var serviceStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configRepository = ConfigRepository(this)
        stateRepository = StateRepository(this)
        buildUi()
        loadConfigIntoUi()
        refreshPreview()
    }

    override fun onResume() {
        super.onResume()
        refreshPreview()
    }

    private fun buildUi() {
        val scrollView = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor(BG_COLOR))
            isFillViewport = true
        }
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
            text = "无障碍截图工具"
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
        serviceStatus = TextView(this).apply {
            textSize = 13f
            setTextColor(Color.parseColor(TEXT_SECONDARY))
            setPadding(0, 12, 0, 24)
        }

        prefixInput = createStyledEditText("用例前缀，可留空，例如：ZYYH-US-88888-")
        caseDigitsInput = createStyledEditText("用例编号位数", true)
        startCaseIndexInput = createStyledEditText("起始用例编号，例如：1，不带前导 0", true)

        val outputDirLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        outputDirInput = createStyledEditText("保存目录，留空使用应用目录").apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        }
        val dirPickerButton = Button(this).apply {
            text = "选择"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(32, 20, 32, 20)
            background = GradientDrawable().apply {
                setColor(Color.parseColor(SKY_BLUE))
                cornerRadius = 12f
            }
            setOnClickListener { openDirectoryPicker() }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(16, 0, 0, 0)
            }
        }
        outputDirLayout.addView(outputDirInput)
        outputDirLayout.addView(dirPickerButton)

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

        buttonsLayout.addView(createStyledButton("打开无障碍权限", true) { openAccessibilitySettings() })
        buttonsLayout.addView(createStyledButton("保存配置", false) {
            val config = saveConfigFromUi()
            resetStateToStart(config)
            refreshPreview()
            Toast.makeText(this@MainActivity, "配置已保存", Toast.LENGTH_SHORT).show()
        })

        listOf<View>(
            title,
            subtitle,
            targetPreview,
            serviceStatus,
            configSection,
            prefixInput,
            caseDigitsInput,
            startCaseIndexInput,
            outputDirLayout,
            buttonsLayout
        ).forEach { root.addView(it) }

        scrollView.addView(
            root,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        setContentView(scrollView)
    }

    private fun createStyledEditText(hint: String, isNumber: Boolean = false): EditText =
        EditText(this).apply {
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

    private fun createStyledButton(text: String, isPrimary: Boolean, onClick: () -> Unit): Button =
        Button(this).apply {
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

    private fun loadConfigIntoUi() {
        val config = configRepository.load()
        prefixInput.setText(config.prefix)
        caseDigitsInput.setText(config.caseDigits.toString())
        startCaseIndexInput.setText(config.startCaseIndex.toString())
        outputDirInput.setText(config.outputDir)
    }

    private fun saveConfigFromUi(): CaseShotConfig =
        configRepository.save(
            CaseShotConfig(
                prefix = prefixInput.text.toString(),
                caseDigits = caseDigitsInput.text.toString().toIntOrNull() ?: 3,
                startCaseIndex = startCaseIndexInput.text.toString().toIntOrNull() ?: 1,
                outputDir = outputDirInput.text.toString()
            )
        )

    private fun resetStateToStart(config: CaseShotConfig) {
        val currentState = stateRepository.load(config.prefix)
        val resetState = StateRepository.resetToStartCase(
            currentState,
            config.prefix,
            config.startCaseIndex
        )
        stateRepository.save(resetState, config.prefix)
    }

    private fun refreshPreview() {
        val config = configRepository.load()
        val state = stateRepository.load(config.prefix)
        val filename = namingService.buildFilename(
            state.prefix,
            state.caseIndex,
            state.shotIndex,
            config.caseDigits
        )
        targetPreview.text = "下一张: $filename"
        serviceStatus.text = if (isAccessibilityServiceEnabled()) {
            "✓ 无障碍服务已开启，悬浮按钮会自动显示"
        } else {
            "✗ 请开启 CaseShot 无障碍服务后使用悬浮截图"
        }
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun openDirectoryPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        startActivityForResult(intent, REQUEST_DIRECTORY)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_DIRECTORY && resultCode == RESULT_OK && data != null) {
            val uri = data.data ?: return
            val path = getRealPathFromDocumentTree(uri)
            if (path != null) {
                outputDirInput.setText(path)
            } else {
                Toast.makeText(this, "无法解析目录路径", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val manager = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        if (!manager.isEnabled) return false
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val expected = "$packageName/${CaptureAccessibilityService::class.java.name}"
        return enabledServices
            .split(':')
            .any { it.equals(expected, ignoreCase = true) }
    }

    private fun getRealPathFromDocumentTree(uri: Uri): String? {
        val docId = DocumentsContract.getTreeDocumentId(uri)
        if (docId.isEmpty()) return null
        val split = docId.split(":")
        if (split.size < 2) return null
        val volumeId = split[0]
        val relativePath = split.subList(1, split.size).joinToString(":")
        val basePath = when (volumeId) {
            "primary" -> Environment.getExternalStorageDirectory().absolutePath
            "external" -> Environment.getExternalStorageDirectory().absolutePath
            else -> "/storage/$volumeId"
        }
        return if (relativePath.isNotEmpty()) "$basePath/$relativePath" else basePath
    }
}
