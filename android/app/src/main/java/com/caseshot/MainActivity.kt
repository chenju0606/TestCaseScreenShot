package com.caseshot

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var configRepository: ConfigRepository
    private lateinit var stateRepository: StateRepository
    private lateinit var namingService: NamingService
    private lateinit var projectionManager: MediaProjectionManager

    private lateinit var prefixInput: EditText
    private lateinit var caseDigitsInput: EditText
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
            setPadding(32, 32, 32, 32)
        }

        targetPreview = TextView(this)
        permissionStatus = TextView(this)
        prefixInput = EditText(this).apply { hint = "前缀，可留空，例如 049" }
        caseDigitsInput = EditText(this).apply { hint = "用例编号位数"; inputType = android.text.InputType.TYPE_CLASS_NUMBER }
        outputDirInput = EditText(this).apply { hint = "保存目录，留空使用应用目录" }
        captureDelayInput = EditText(this).apply { hint = "截屏延迟毫秒"; inputType = android.text.InputType.TYPE_CLASS_NUMBER }
        hideOverlayCheck = CheckBox(this).apply { text = "截图前隐藏悬浮窗" }

        val overlayButton = Button(this).apply {
            text = "打开悬浮窗权限"
            setOnClickListener { openOverlaySettings() }
        }
        val saveButton = Button(this).apply {
            text = "保存配置"
            setOnClickListener {
                saveConfigFromUi()
                refreshPreview()
                Toast.makeText(this@MainActivity, "配置已保存", Toast.LENGTH_SHORT).show()
            }
        }
        val startButton = Button(this).apply {
            text = "开始服务"
            setOnClickListener { startCaptureFlow() }
        }
        val stopButton = Button(this).apply {
            text = "停止服务"
            setOnClickListener { stopService(Intent(this@MainActivity, CaptureService::class.java)) }
        }

        listOf(
            targetPreview,
            permissionStatus,
            prefixInput,
            caseDigitsInput,
            outputDirInput,
            captureDelayInput,
            hideOverlayCheck,
            overlayButton,
            saveButton,
            startButton,
            stopButton
        ).forEach { root.addView(it) }

        setContentView(root)
    }

    private fun loadConfigIntoUi() {
        val config = configRepository.load()
        prefixInput.setText(config.prefix)
        caseDigitsInput.setText(config.caseDigits.toString())
        outputDirInput.setText(config.outputDir)
        captureDelayInput.setText(config.captureDelayMs.toString())
        hideOverlayCheck.isChecked = config.hideFloatingWindowBeforeCapture
    }

    private fun saveConfigFromUi(): CaseShotConfig {
        val config = CaseShotConfig(
            prefix = prefixInput.text.toString(),
            caseDigits = caseDigitsInput.text.toString().toIntOrNull() ?: 4,
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
        targetPreview.text = "下一张: $filename"
        permissionStatus.text = if (Settings.canDrawOverlays(this)) "悬浮窗权限: 已允许" else "悬浮窗权限: 未允许"
    }

    private fun openOverlaySettings() {
        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
        startActivity(intent)
    }

    private fun startCaptureFlow() {
        saveConfigFromUi()
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "请先允许悬浮窗权限", Toast.LENGTH_SHORT).show()
            openOverlaySettings()
            return
        }
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

    companion object {
        private const val REQUEST_CAPTURE = 1001
    }
}