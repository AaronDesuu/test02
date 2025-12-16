package com.example.meterkenshin.utils

import android.content.Context
import com.example.meterkenshin.ui.manager.SessionManager
import java.io.File

/**
 * Centralized file path manager that provides user-specific directory paths.
 * Each user account gets their own isolated data directory.
 *
 * Directory structure:
 * - app_files/
 *   - admin/           (Admin user data)
 *     - meter.csv
 *     - YYYYMM_meter.csv
 *     - printer.csv
 *     - rate.csv
 *     - billing/
 *       - {serialNumber}_billing.csv
 *   - reader/          (Reader user data)
 *     - meter.csv
 *     - printer.csv
 *     - rate.csv
 *     - billing/
 *       - {serialNumber}_billing.csv
 */
object UserFileManager {

    /**
     * Get the root app_files directory for the current user
     * @param context Application context
     * @param sessionManager Session manager to get current user
     * @return User-specific app_files directory (e.g., app_files/admin/)
     */
    fun getAppFilesDir(context: Context, sessionManager: SessionManager): File {
        val session = sessionManager.getSession()
        val username = session?.username ?: "default"

        // Try external storage first, fallback to internal
        val rootDir = context.getExternalFilesDir(null) ?: context.filesDir
        val appFilesDir = File(rootDir, "app_files")
        val userDir = File(appFilesDir, username)

        // Ensure directory exists
        if (!userDir.exists()) {
            userDir.mkdirs()
        }

        return userDir
    }

    /**
     * Get the root app_files directory for a specific username
     * @param context Application context
     * @param username Username to get directory for
     * @return User-specific app_files directory
     */
    fun getAppFilesDirForUser(context: Context, username: String): File {
        val rootDir = context.getExternalFilesDir(null) ?: context.filesDir
        val appFilesDir = File(rootDir, "app_files")
        val userDir = File(appFilesDir, username)

        if (!userDir.exists()) {
            userDir.mkdirs()
        }

        return userDir
    }

    /**
     * Get the billing subdirectory for the current user
     * @param context Application context
     * @param sessionManager Session manager to get current user
     * @return User-specific billing directory (e.g., app_files/admin/billing/)
     */
    fun getBillingDir(context: Context, sessionManager: SessionManager): File {
        val userDir = getAppFilesDir(context, sessionManager)
        val billingDir = File(userDir, "billing")

        if (!billingDir.exists()) {
            billingDir.mkdirs()
        }

        return billingDir
    }

    /**
     * Get the current user's meter CSV file path
     * @param context Application context
     * @param sessionManager Session manager
     * @param filename Meter CSV filename (e.g., "202501_meter.csv")
     * @return File object for meter CSV
     */
    fun getMeterFile(context: Context, sessionManager: SessionManager, filename: String): File {
        return File(getAppFilesDir(context, sessionManager), filename)
    }

    /**
     * Get the current user's printer.csv file path
     */
    fun getPrinterFile(context: Context, sessionManager: SessionManager): File {
        return File(getAppFilesDir(context, sessionManager), "printer.csv")
    }

    /**
     * Get the current user's rate.csv file path
     */
    fun getRateFile(context: Context, sessionManager: SessionManager): File {
        return File(getAppFilesDir(context, sessionManager), "rate.csv")
    }

    /**
     * Get a billing CSV file for a specific meter serial number
     * @param context Application context
     * @param sessionManager Session manager
     * @param serialNumber Meter serial number
     * @return Billing CSV file for the meter
     */
    fun getBillingFile(context: Context, sessionManager: SessionManager, serialNumber: String): File {
        val billingDir = getBillingDir(context, sessionManager)
        return File(billingDir, "${serialNumber}_billing.csv")
    }

    /**
     * Get export directory (shared across all users - in external storage root)
     * Exports are not user-specific since they can be shared
     */
    fun getExportDir(context: Context): File {
        return context.getExternalFilesDir(null) ?: context.filesDir
    }
}
