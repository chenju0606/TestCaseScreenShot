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

    data class WriteResult(
        val success: Boolean,
        val file: File? = null,
        val skipped: Boolean = false,
        val error: String? = null
    )

    fun writePngWithConflictResolution(
        outputDir: File,
        filename: String,
        pngBytes: ByteArray,
        conflictResolution: ConflictResolution
    ): WriteResult {
        if (!outputDir.exists() && !outputDir.mkdirs()) {
            return WriteResult(
                success = false,
                error = "Unable to create output directory: ${outputDir.absolutePath}"
            )
        }

        val target = File(outputDir, filename)

        if (target.exists()) {
            return when (conflictResolution) {
                ConflictResolution.SKIP -> {
                    WriteResult(success = true, skipped = true, file = target)
                }
                ConflictResolution.OVERWRITE -> {
                    target.writeBytes(pngBytes)
                    WriteResult(success = true, file = target)
                }
                ConflictResolution.ASK -> {
                    WriteResult(
                        success = false,
                        error = "CONFLICT:${target.absolutePath}",
                        file = target
                    )
                }
            }
        }

        target.writeBytes(pngBytes)
        return WriteResult(success = true, file = target)
    }
}