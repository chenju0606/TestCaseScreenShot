package com.caseshot

import android.content.Context

class StateRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences("caseshot_state", Context.MODE_PRIVATE)

    fun load(configPrefix: String): CaseShotState = normalize(
        CaseShotState(
            prefix = prefs.getString("prefix", "").orEmpty(),
            caseIndex = prefs.getInt("caseIndex", 1),
            shotIndex = prefs.getInt("shotIndex", 0)
        ),
        configPrefix
    )

    fun save(state: CaseShotState, configPrefix: String): CaseShotState {
        val normalized = normalize(state, configPrefix)
        prefs.edit()
            .putString("prefix", normalized.prefix)
            .putInt("caseIndex", normalized.caseIndex)
            .putInt("shotIndex", normalized.shotIndex)
            .apply()
        return normalized
    }

    companion object {
        fun normalize(state: CaseShotState, configPrefix: String): CaseShotState =
            state.copy(
                prefix = configPrefix.trim(),
                caseIndex = state.caseIndex.coerceAtLeast(1),
                shotIndex = state.shotIndex.coerceAtLeast(0)
            )

        fun afterCaptureSuccess(state: CaseShotState): CaseShotState =
            state.copy(
                caseIndex = state.caseIndex.coerceAtLeast(1),
                shotIndex = state.shotIndex.coerceAtLeast(0) + 1
            )

        fun nextCase(state: CaseShotState): CaseShotState =
            state.copy(
                caseIndex = state.caseIndex.coerceAtLeast(1) + 1,
                shotIndex = 0
            )
    }
}