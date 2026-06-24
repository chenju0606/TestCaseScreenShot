package com.caseshot

sealed class CaptureOverlayStrategy {
    data object KeepVisible : CaptureOverlayStrategy()
    data class RemoveForCapture(val delayMs: Long) : CaptureOverlayStrategy()

    companion object {
        private const val MIN_REMOVE_SETTLE_DELAY_MS = 80L

        fun from(config: CaseShotConfig): CaptureOverlayStrategy {
            return if (config.hideFloatingWindowBeforeCapture) {
                RemoveForCapture(config.captureDelayMs.coerceAtLeast(MIN_REMOVE_SETTLE_DELAY_MS))
            } else {
                KeepVisible
            }
        }
    }
}
