package com.caseshot

import android.content.Context
import java.io.File

class FileStore {
    fun resolveOutputDir(context: Context, config: CaseShotConfig): File {
        val configured = config.outputDir.trim()
        val dir = if (configured.isEmpty()) {
            File(context.getExternalFilesDir(null), "Cases")
        } else {
            File(configured)
        }
        if (!dir.exists() && !dir.mkdirs()) {
            throw IllegalStateException("Unable to create output directory: ${dir.absolutePath}")
        }
        return dir
    }

    fun writePng(outputDir: File, filename: String, pngBytes: ByteArray): File {
        if (!outputDir.exists() && !outputDir.mkdirs()) {
            throw IllegalStateException("Unable to create output directory: ${outputDir.absolutePath}")
        }
        val target = File(outputDir, filename)
        if (target.exists()) {
            throw IllegalStateException("Target file already exists: ${target.absolutePath}")
        }
        target.writeBytes(pngBytes)
        return target
    }
}