package com.caseshot

sealed class CaptureOverlayStrategy {
    data object KeepVisible : CaptureOverlayStrategy()
    data class RemoveForCapture(val delayMs: Long) : CaptureOverlayStrategy()

    companion object {
        fun from(config: CaseShotConfig): CaptureOverlayStrategy {
            return if (config.hideFloatingWindowBeforeCapture) {
                RemoveForCapture(config.captureDelayMs.coerceAtLeast(0L))
            } else {
                KeepVisible
            }
        }
    }
}
