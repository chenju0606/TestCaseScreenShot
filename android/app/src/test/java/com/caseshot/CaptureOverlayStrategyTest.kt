package com.caseshot

import org.junit.Assert.assertEquals
import org.junit.Test

class CaptureOverlayStrategyTest {
    @Test
    fun removesOverlayAndUsesConfiguredDelayWhenHideBeforeCaptureIsEnabled() {
        val config = CaseShotConfig(
            hideFloatingWindowBeforeCapture = true,
            captureDelayMs = 450L
        )

        val strategy = CaptureOverlayStrategy.from(config)

        assertEquals(CaptureOverlayStrategy.RemoveForCapture(450L), strategy)
    }

    @Test
    fun removeOverlayWaitsAtLeastLongEnoughForNewFramesBeforeCapture() {
        val config = CaseShotConfig(
            hideFloatingWindowBeforeCapture = true,
            captureDelayMs = 0L
        )

        val strategy = CaptureOverlayStrategy.from(config)

        assertEquals(CaptureOverlayStrategy.RemoveForCapture(120L), strategy)
    }

    @Test
    fun keepsOverlayVisibleAndSkipsDelayWhenHideBeforeCaptureIsDisabled() {
        val config = CaseShotConfig(
            hideFloatingWindowBeforeCapture = false,
            captureDelayMs = 450L
        )

        val strategy = CaptureOverlayStrategy.from(config)

        assertEquals(CaptureOverlayStrategy.KeepVisible, strategy)
    }
}
