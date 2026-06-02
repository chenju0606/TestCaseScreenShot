package com.caseshot

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import java.io.File

class ConflictActionReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ConflictActionReceiver"
        const val ACTION_SKIP = "com.caseshot.ACTION_SKIP"
        const val ACTION_OVERWRITE = "com.caseshot.ACTION_OVERWRITE"
        const val EXTRA_TEMP_PATH = "TEMP_PATH"
        const val EXTRA_TARGET_PATH = "TARGET_PATH"
        const val EXTRA_CASE_INDEX = "CASE_INDEX"
        const val EXTRA_SHOT_INDEX = "SHOT_INDEX"
        const val EXTRA_PREFIX = "PREFIX"
        const val EXTRA_CONFIG_PREFIX = "CONFIG_PREFIX"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Received action: ${intent.action}")

        val tempPath = intent.getStringExtra(EXTRA_TEMP_PATH).orEmpty()
        val targetPath = intent.getStringExtra(EXTRA_TARGET_PATH).orEmpty()
        val caseIndex = intent.getIntExtra(EXTRA_CASE_INDEX, 1)
        val shotIndex = intent.getIntExtra(EXTRA_SHOT_INDEX, 0)
        val prefix = intent.getStringExtra(EXTRA_PREFIX).orEmpty()
        val configPrefix = intent.getStringExtra(EXTRA_CONFIG_PREFIX).orEmpty()

        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager.cancel(100 + shotIndex)

        val stateRepository = StateRepository(context)

        when (intent.action) {
            ACTION_SKIP -> {
                Log.d(TAG, "Skip: discard screenshot for case=$caseIndex shot=$shotIndex")
                val tempFile = File(tempPath)
                if (tempFile.exists()) {
                    tempFile.delete()
                }
                val current = stateRepository.load(configPrefix)
                val next = current.copy(
                    caseIndex = caseIndex.coerceAtLeast(1),
                    shotIndex = shotIndex + 1
                )
                stateRepository.save(next, configPrefix)
            }
            ACTION_OVERWRITE -> {
                Log.d(TAG, "Overwrite: save screenshot to $targetPath")
                val tempFile = File(tempPath)
                val targetFile = File(targetPath)
                if (tempFile.exists()) {
                    try {
                        targetFile.parentFile?.mkdirs()
                        tempFile.inputStream().use { input ->
                            targetFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                        tempFile.delete()
                        Log.d(TAG, "Overwrite succeeded: $targetPath")
                    } catch (e: Exception) {
                        Log.e(TAG, "Overwrite failed for $targetPath", e)
                    }
                } else {
                    Log.w(TAG, "Temp file no longer exists: $tempPath")
                }
                val current = stateRepository.load(configPrefix)
                val next = current.copy(
                    caseIndex = caseIndex.coerceAtLeast(1),
                    shotIndex = shotIndex + 1
                )
                stateRepository.save(next, configPrefix)
            }
        }
    }
}
