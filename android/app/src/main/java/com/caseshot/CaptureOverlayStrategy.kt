package com.caseshot

sealed class CaptureOverlayStrategy {
    data object KeepVisible : CaptureOverlayStrategy()
    data class HideForCapture(val delayMs: Long) : CaptureOverlayStrategy()

    companion object {
        fun from(config: CaseShotConfig): CaptureOverlayStrategy {
            return if (config.hideFloatingWindowBeforeCapture) {
                HideForCapture(config.captureDelayMs.coerceAtLeast(0L))
            } else {
                KeepVisible
            }
        }
    }
}
