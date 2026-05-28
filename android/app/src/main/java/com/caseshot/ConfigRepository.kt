package com.caseshot

import android.content.Context

class ConfigRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences("caseshot_config", Context.MODE_PRIVATE)

    fun load(): CaseShotConfig = normalize(
        CaseShotConfig(
            prefix = prefs.getString("prefix", "").orEmpty(),
            caseDigits = prefs.getInt("caseDigits", 4),
            startCaseIndex = prefs.getInt("startCaseIndex", 1),
            outputDir = prefs.getString("outputDir", "").orEmpty(),
            captureDelayMs = prefs.getLong("captureDelayMs", 300L),
            hideFloatingWindowBeforeCapture = prefs.getBoolean("hideFloatingWindowBeforeCapture", true),
            imageFormat = prefs.getString("imageFormat", "png").orEmpty(),
            enableSmb = prefs.getBoolean("enableSmb", false),
            smbUrl = prefs.getString("smbUrl", "").orEmpty()
        )
    )

    fun save(config: CaseShotConfig): CaseShotConfig {
        val normalized = normalize(config)
        prefs.edit()
            .putString("prefix", normalized.prefix)
            .putInt("caseDigits", normalized.caseDigits)
            .putInt("startCaseIndex", normalized.startCaseIndex)
            .putString("outputDir", normalized.outputDir)
            .putLong("captureDelayMs", normalized.captureDelayMs)
            .putBoolean("hideFloatingWindowBeforeCapture", normalized.hideFloatingWindowBeforeCapture)
            .putString("imageFormat", normalized.imageFormat)
            .putBoolean("enableSmb", normalized.enableSmb)
            .putString("smbUrl", normalized.smbUrl)
            .apply()
        return normalized
    }

    companion object {
        fun normalize(config: CaseShotConfig): CaseShotConfig {
            val imageFormat = config.imageFormat.trim().removePrefix(".").lowercase().ifEmpty { "png" }
            return config.copy(
                prefix = config.prefix.trim(),
                caseDigits = config.caseDigits.coerceAtLeast(1),
                startCaseIndex = config.startCaseIndex.coerceAtLeast(1),
                outputDir = config.outputDir.trim(),
                captureDelayMs = config.captureDelayMs.coerceAtLeast(0L),
                imageFormat = imageFormat,
                smbUrl = config.smbUrl.trim()
            )
        }
    }
}