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
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Received action: ${intent.action}")

        val caseIndex = intent.getIntExtra("CASE_INDEX", 1)
        val shotIndex = intent.getIntExtra("SHOT_INDEX", 0)
        val prefix = intent.getStringExtra("PREFIX").orEmpty()

        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager.cancel(100 + shotIndex)

        when (intent.action) {
            "ACTION_SKIP" -> {
                Log.d(TAG, "Skipping file for case $caseIndex, shot $shotIndex")
            }
            "ACTION_OVERWRITE" -> {
                val outputDir = intent.getStringExtra("OUTPUT_DIR")
                val filename = intent.getStringExtra("FILENAME")
                if (outputDir != null && filename != null) {
                    Log.d(TAG, "Overwriting file: $filename")
                }
            }
        }
    }
}
