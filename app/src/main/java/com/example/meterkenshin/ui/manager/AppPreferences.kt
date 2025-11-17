package com.example.meterkenshin.ui.manager

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

object AppPreferences {
    private const val PREFS_NAME = "MeterKenshinSettings"
    private const val KEY_JSON_SAVING_ENABLED = "json_saving_enabled"
    private const val KEY_PRINTING_ENABLED = "printing_enabled"
    private const val KEY_NOTIFICATION_FROM_TOP = "notification_from_top"
    private const val KEY_DLMS_CONFIRM_ENABLED = "dlms_confirm_enabled"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun isJsonSavingEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_JSON_SAVING_ENABLED, true)
    }

    fun setJsonSavingEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit { putBoolean(KEY_JSON_SAVING_ENABLED, enabled) }
    }

    // ADD THESE METHODS
    fun isPrintingEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_PRINTING_ENABLED, true) // default enabled
    }

    fun setPrintingEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit { putBoolean(KEY_PRINTING_ENABLED, enabled) }
    }


    fun isNotificationFromTop(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_NOTIFICATION_FROM_TOP, false)
    }

    fun setNotificationFromTop(context: Context, fromTop: Boolean) {
        getPrefs(context).edit { putBoolean(KEY_NOTIFICATION_FROM_TOP, fromTop) }
    }

    fun clearAll(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit { clear() }
    }

    fun isDlmsConfirmEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_DLMS_CONFIRM_ENABLED, true)
    }

    fun setDlmsConfirmEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit { putBoolean(KEY_DLMS_CONFIRM_ENABLED, enabled) }
    }
}