package com.caseshot

import org.junit.Assert.assertEquals
import org.junit.Test

class StateRepositoryTest {
    @Test
    fun alignsStatePrefixWithConfigPrefix() {
        val state = StateRepository.normalize(CaseShotState(prefix = "old", caseIndex = 3, shotIndex = 2), "049")
        assertEquals(CaseShotState(prefix = "049", caseIndex = 3, shotIndex = 2), state)
    }

    @Test
    fun captureSuccessIncrementsShotIndex() {
        val state = StateRepository.afterCaptureSuccess(CaseShotState(prefix = "049", caseIndex = 1, shotIndex = 0))
        assertEquals(CaseShotState(prefix = "049", caseIndex = 1, shotIndex = 1), state)
    }

    @Test
    fun doneMovesToNextCaseAndClearsShotIndex() {
        val state = StateRepository.nextCase(CaseShotState(prefix = "", caseIndex = 1, shotIndex = 3))
        assertEquals(CaseShotState(prefix = "", caseIndex = 2, shotIndex = 0), state)
    }
}