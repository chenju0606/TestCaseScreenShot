package com.caseshot

enum class ExistingScreenshotPolicy {
    SKIP,
    OVERWRITE,
    ASK
}

sealed class ScreenshotSaveResult {
    data class Saved(val file: java.io.File) : ScreenshotSaveResult()
    data class Skipped(val file: java.io.File) : ScreenshotSaveResult()
    data class Overwritten(val file: java.io.File) : ScreenshotSaveResult()
    data class Failed(val error: String) : ScreenshotSaveResult()
}

data class CaseShotConfig(
    val prefix: String = "",
    val caseDigits: Int = 3,
    val startCaseIndex: Int = 1,
    val outputDir: String = "",
    val captureDelayMs: Long = 300L,
    val hideFloatingWindowBeforeCapture: Boolean = true,
    val imageFormat: String = "png",
    val enableSmb: Boolean = false,
    val smbUrl: String = "",
    val existingScreenshotPolicy: ExistingScreenshotPolicy = ExistingScreenshotPolicy.ASK
)

data class CaseShotState(
    val prefix: String = "",
    val caseIndex: Int = 1,
    val shotIndex: Int = 0
)
