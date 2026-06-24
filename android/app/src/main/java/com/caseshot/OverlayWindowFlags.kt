package com.caseshot

import android.view.WindowManager

object OverlayWindowFlags {
    fun defaultFlags(): Int =
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_SECURE
}
