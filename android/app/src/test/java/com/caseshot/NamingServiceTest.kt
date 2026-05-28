package com.caseshot

import org.junit.Assert.assertEquals
import org.junit.Test

class NamingServiceTest {
    private val service = NamingService()

    @Test
    fun formatsCaseIndexWithPadding() {
        assertEquals("0001", service.formatCaseIndex(1, 4))
        assertEquals("0049", service.formatCaseIndex(49, 4))
        assertEquals("12345", service.formatCaseIndex(12345, 4))
    }

    @Test
    fun buildsCaseIdWithoutPrefix() {
        assertEquals("0001", service.buildCaseId("", 1, 4))
        assertEquals("0012", service.buildCaseId("   ", 12, 4))
    }

    @Test
    fun buildsCaseIdWithPrefix() {
        assertEquals("049-0001", service.buildCaseId("049", 1, 4))
        assertEquals("case-012", service.buildCaseId(" case ", 12, 3))
    }

    @Test
    fun buildsFilenames() {
        assertEquals("0001.png", service.buildFilename("", 1, 0, 4))
        assertEquals("0001-2.png", service.buildFilename("", 1, 1, 4))
        assertEquals("049-0001.png", service.buildFilename("049", 1, 0, 4))
        assertEquals("049-0001-3.png", service.buildFilename("049", 1, 2, 4))
    }
}