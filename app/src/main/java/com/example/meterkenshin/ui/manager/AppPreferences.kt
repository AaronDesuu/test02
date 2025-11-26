package com.example.meterkenshin.ui.manager

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object AppPreferences {
    private const val PREFS_NAME = "MeterKenshinSettings"
    private const val KEY_JSON_SAVING_ENABLED = "json_saving_enabled"
    private const val KEY_PRINTING_ENABLED = "printing_enabled"
    private const val KEY_NOTIFICATION_FROM_TOP = "notification_from_top"
    private const val KEY_DLMS_CONFIRM_ENABLED = "dlms_confirm_enabled"
    private const val KEY_AUTO_SHARE_EXPORT = "auto_share_export"

    private val _notificationFromTop = MutableStateFlow(false)
    val notificationFromTop: StateFlow<Boolean> = _notificationFromTop.asStateFlow()

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun initializePreferences(context: Context) {
        _notificationFromTop.value = getPrefs(context).getBoolean(KEY_NOTIFICATION_FROM_TOP, false)
    }

    fun isJsonSavingEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_JSON_SAVING_ENABLED, true)
    }

    fun setJsonSavingEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit { putBoolean(KEY_JSON_SAVING_ENABLED, enabled) }
    }

    fun isPrintingEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_PRINTING_ENABLED, true)
    }

    fun isAutoShareExportEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_AUTO_SHARE_EXPORT, true)
    }

    fun setAutoShareExportEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit { putBoolean(KEY_AUTO_SHARE_EXPORT, enabled) }
    }

    fun setPrintingEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit { putBoolean(KEY_PRINTING_ENABLED, enabled) }
    }

    fun isNotificationFromTop(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_NOTIFICATION_FROM_TOP, false)
    }

    fun setNotificationFromTop(context: Context, fromTop: Boolean) {
        getPrefs(context).edit { putBoolean(KEY_NOTIFICATION_FROM_TOP, fromTop) }
        _notificationFromTop.value = fromTop
    }

    fun clearAll(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit { clear() }
        _notificationFromTop.value = false
    }

    fun isDlmsConfirmEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_DLMS_CONFIRM_ENABLED, true)
    }

    fun setDlmsConfirmEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit { putBoolean(KEY_DLMS_CONFIRM_ENABLED, enabled) }
    }
}