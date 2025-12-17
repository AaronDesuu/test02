package com.example.meterkenshin.ui.manager

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object AppPreferences {
    private const val KEY_JSON_SAVING_ENABLED = "json_saving_enabled"
    private const val KEY_PRINTING_ENABLED = "printing_enabled"
    private const val KEY_NOTIFICATION_FROM_TOP = "notification_from_top"
    private const val KEY_DLMS_CONFIRM_ENABLED = "dlms_confirm_enabled"
    private const val KEY_AUTO_SHARE_EXPORT = "auto_share_export"

    private val _notificationFromTop = MutableStateFlow(false)
    val notificationFromTop: StateFlow<Boolean> = _notificationFromTop.asStateFlow()

    // ✅ FIXED: Cache both username AND SharedPreferences instance to prevent recomposition issues
    @Volatile
    private var cachedUsername: String? = null
    @Volatile
    private var cachedPrefs: SharedPreferences? = null

    /**
     * Get current username from SessionManager with caching
     * ✅ FIXED: Cached to prevent excessive SessionManager.getInstance() calls during recomposition
     */
    private fun getCurrentUsername(context: Context): String {
        // Return cached value if available
        cachedUsername?.let { return it }

        // Otherwise, get from SessionManager and cache it
        val sessionManager = SessionManager.getInstance(context.applicationContext)
        val session = sessionManager.getSession()
        val username = session?.username ?: "default"
        cachedUsername = username
        return username
    }

    /**
     * Clear cached username and preferences (call this on logout)
     */
    fun clearCache() {
        cachedUsername = null
        cachedPrefs = null
    }

    /**
     * Get user-specific SharedPreferences with aggressive caching
     * ✅ FIXED: Caches SharedPreferences instance to prevent ANY side effects during recomposition
     */
    private fun getPrefs(context: Context): SharedPreferences {
        // Return cached SharedPreferences if available
        cachedPrefs?.let { return it }

        // Get username and create SharedPreferences
        val username = getCurrentUsername(context.applicationContext)
        val prefs = context.applicationContext.getSharedPreferences("MeterKenshinSettings_$username", Context.MODE_PRIVATE)

        // Cache it for future calls
        cachedPrefs = prefs
        return prefs
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

    /**
     * Clear all preferences for current user
     * ✅ FIXED: Now clears user-specific preferences
     */
    fun clearAll(context: Context) {
        getPrefs(context).edit { clear() }
        _notificationFromTop.value = false
    }

    fun isDlmsConfirmEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_DLMS_CONFIRM_ENABLED, true)
    }

    fun setDlmsConfirmEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit { putBoolean(KEY_DLMS_CONFIRM_ENABLED, enabled) }
    }
}