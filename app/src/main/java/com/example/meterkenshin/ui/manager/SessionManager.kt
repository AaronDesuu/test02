package com.example.meterkenshin.ui.manager

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import com.example.meterkenshin.data.UserDatabase
import com.example.meterkenshin.model.UserSession
import com.example.meterkenshin.model.UserRole
import java.util.Date

class SessionManager private constructor(context: Context) {

    companion object {
        @Volatile
        private var INSTANCE: SessionManager? = null
        private const val PREF_NAME = "MeterKenshinSession"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USERNAME = "username"
        private const val KEY_USER_ROLE = "user_role"
        private const val KEY_LOGIN_TIME = "login_time"
        private const val KEY_EXPIRY_TIME = "expiry_time"
        private const val KEY_IS_LOGGED_IN = "is_logged_in"

        fun getInstance(context: Context): SessionManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SessionManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    private val userDatabase = UserDatabase.getInstance()

    @SuppressLint("UseKtx")
    fun saveSession(session: UserSession) {
        with(sharedPreferences.edit()) {
            putString(KEY_USER_ID, session.userId)
            putString(KEY_USERNAME, session.username)
            putString(KEY_USER_ROLE, session.role.name)
            putLong(KEY_LOGIN_TIME, session.loginTime.time)
            putLong(KEY_EXPIRY_TIME, session.expiryTime.time)
            putBoolean(KEY_IS_LOGGED_IN, true)
            apply()
        }
    }

    fun getSession(): UserSession? {
        if (!sharedPreferences.getBoolean(KEY_IS_LOGGED_IN, false)) {
            return null
        }

        return try {
            val userId = sharedPreferences.getString(KEY_USER_ID, null) ?: return null
            val username = sharedPreferences.getString(KEY_USERNAME, null) ?: return null
            val roleString = sharedPreferences.getString(KEY_USER_ROLE, null) ?: return null
            val loginTime = Date(sharedPreferences.getLong(KEY_LOGIN_TIME, 0))
            val expiryTime = Date(sharedPreferences.getLong(KEY_EXPIRY_TIME, 0))

            val role = UserRole.valueOf(roleString)

            val session = UserSession(
                userId = userId,
                username = username,
                role = role,
                loginTime = loginTime,
                expiryTime = expiryTime
            )

            // Check if session is still valid
            if (session.isValid()) {
                session
            } else {
                clearSession()
                null
            }
        } catch (_: Exception) {
            clearSession()
            null
        }
    }

    @SuppressLint("UseKtx")
    fun clearSession() {
        with(sharedPreferences.edit()) {
            clear()
            apply()
        }
        userDatabase.invalidateSession()
    }

    fun isLoggedIn(): Boolean {
        return getSession() != null
    }

    fun logout() {
        clearSession()
    }

    /**
     * Clear all user-specific cached data (useful for cleanup operations)
     * ✅ NEW: Optional method to clear DLMS logs if needed
     * Note: Not called automatically on logout to preserve user's logs
     */
    fun clearUserDLMSLogs(context: Context) {
        val session = getSession()
        val username = session?.username ?: return

        // Clear DLMS logs for this user
        val dlmsLogPrefs = context.getSharedPreferences("DLMSLog_$username", Context.MODE_PRIVATE)
        dlmsLogPrefs.edit().clear().apply()
    }
}