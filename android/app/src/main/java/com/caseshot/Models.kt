package com.caseshot

data class CaseShotConfig(
    val prefix: String = "",
    val caseDigits: Int = 3,
    val startCaseIndex: Int = 1,
    val outputDir: String = "",
    val captureDelayMs: Long = 300L,
    val hideFloatingWindowBeforeCapture: Boolean = true,
    val imageFormat: String = "png",
    val enableSmb: Boolean = false,
    val smbUrl: String = ""
)

data class CaseShotState(
    val prefix: String = "",
    val caseIndex: Int = 1,
    val shotIndex: Int = 0
)