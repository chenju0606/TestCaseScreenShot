package com.caseshot

import android.content.Context
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

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

    fun targetFile(outputDir: File, filename: String): File = File(outputDir, filename)

    fun resolveExistingScreenshot(target: File, policy: ExistingScreenshotPolicy): ScreenshotSaveResult =
        when (policy) {
            ExistingScreenshotPolicy.SKIP -> ScreenshotSaveResult.Skipped(target)
            ExistingScreenshotPolicy.OVERWRITE -> ScreenshotSaveResult.Overwritten(target)
            ExistingScreenshotPolicy.ASK -> ScreenshotSaveResult.Failed("CONFLICT:${target.absolutePath}")
        }

    fun writePngAtomically(target: File, pngBytes: ByteArray, overwrite: Boolean): ScreenshotSaveResult {
        val parent = target.parentFile
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            return ScreenshotSaveResult.Failed("Unable to create output directory: ${parent.absolutePath}")
        }

        if (target.exists() && !overwrite) {
            return ScreenshotSaveResult.Failed("CONFLICT:${target.absolutePath}")
        }

        val temp = File(target.parentFile, "${target.name}.tmp")
        return try {
            temp.writeBytes(pngBytes)
            if (overwrite) {
                Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
                ScreenshotSaveResult.Overwritten(target)
            } else {
                Files.move(temp.toPath(), target.toPath())
                ScreenshotSaveResult.Saved(target)
            }
        } catch (error: Exception) {
            temp.delete()
            ScreenshotSaveResult.Failed(error.message ?: "Unable to save screenshot")
        }
    }
}
