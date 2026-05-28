package com.caseshot

class NamingService {
    fun normalizePrefix(prefix: String?): String = prefix.orEmpty().trim()

    fun formatCaseIndex(caseIndex: Int, caseDigits: Int): String {
        val safeIndex = caseIndex.coerceAtLeast(1)
        val safeDigits = caseDigits.coerceAtLeast(1)
        return safeIndex.toString().padStart(safeDigits, '0')
    }

    fun buildCaseId(prefix: String?, caseIndex: Int, caseDigits: Int): String {
        val cleanPrefix = normalizePrefix(prefix)
        val paddedCase = formatCaseIndex(caseIndex, caseDigits)
        return if (cleanPrefix.isEmpty()) paddedCase else "$cleanPrefix-$paddedCase"
    }

    fun buildFilename(prefix: String?, caseIndex: Int, shotIndex: Int, caseDigits: Int): String {
        val base = buildCaseId(prefix, caseIndex, caseDigits)
        val safeShot = shotIndex.coerceAtLeast(0)
        return if (safeShot == 0) "$base.png" else "$base-${safeShot + 1}.png"
    }
}