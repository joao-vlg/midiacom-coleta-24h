package com.example.myapplication

import android.content.Context

enum class AppFlowState {
    IDLE,
    COLLECTING,
    WAITING_WIFI,
    UPLOADING,
    COLLECTION_INTERRUPTED,
    UPLOAD_INTERRUPTED
}

object FlowStateStore {
    private const val PREFS_NAME = "flow_state_prefs"
    private const val KEY_STATE = "current_state"

    fun getState(context: Context): AppFlowState {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val stored = prefs.getString(KEY_STATE, AppFlowState.IDLE.name) ?: AppFlowState.IDLE.name
        return try {
            AppFlowState.valueOf(stored)
        } catch (_: IllegalArgumentException) {
            AppFlowState.IDLE
        }
    }

    fun setState(context: Context, state: AppFlowState) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_STATE, state.name)
            .apply()
    }
}
