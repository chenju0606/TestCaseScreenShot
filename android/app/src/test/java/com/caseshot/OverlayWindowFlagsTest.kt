package com.caseshot

import android.view.WindowManager
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayWindowFlagsTest {
    @Test
    fun defaultOverlayFlagsMarkWindowSecure() {
        val flags = OverlayWindowFlags.defaultFlags()

        assertTrue(flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
    }

    @Test
    fun defaultOverlayFlagsKeepWindowNonFocusable() {
        val flags = OverlayWindowFlags.defaultFlags()

        assertTrue(flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE != 0)
    }
}
