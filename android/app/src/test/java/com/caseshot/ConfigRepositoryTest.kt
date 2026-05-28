package com.caseshot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigRepositoryTest {
    @Test
    fun normalizesMissingConfigValues() {
        val config = ConfigRepository.normalize(CaseShotConfig())
        assertEquals("", config.prefix)
        assertEquals(3, config.caseDigits)
        assertEquals(1, config.startCaseIndex)
        assertEquals("", config.outputDir)
        assertEquals(300L, config.captureDelayMs)
        assertTrue(config.hideFloatingWindowBeforeCapture)
        assertEquals("png", config.imageFormat)
        assertFalse(config.enableSmb)
        assertEquals("", config.smbUrl)
    }

    @Test
    fun normalizesProvidedConfigValues() {
        val config = ConfigRepository.normalize(
            CaseShotConfig(
                prefix = " 049 ",
                caseDigits = 3,
                startCaseIndex = 5,
                outputDir = " /sdcard/MyCases ",
                captureDelayMs = 500L,
                hideFloatingWindowBeforeCapture = false,
                imageFormat = ".PNG",
                enableSmb = true,
                smbUrl = " smb://192.168.1.10/Cases "
            )
        )

        assertEquals("049", config.prefix)
        assertEquals(3, config.caseDigits)
        assertEquals(5, config.startCaseIndex)
        assertEquals("/sdcard/MyCases", config.outputDir)
        assertEquals(500L, config.captureDelayMs)
        assertFalse(config.hideFloatingWindowBeforeCapture)
        assertEquals("png", config.imageFormat)
        assertTrue(config.enableSmb)
        assertEquals("smb://192.168.1.10/Cases", config.smbUrl)
    }
}