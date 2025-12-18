package com.example.meterkenshin.data

import android.content.Context
import android.util.Log
import com.example.meterkenshin.model.Billing
import com.example.meterkenshin.ui.manager.SessionManager
import com.example.meterkenshin.utils.UserFileManager
import com.google.gson.Gson
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Per-Meter CSV Billing Data Repository
 * Creates separate CSV file for each meter: {serialNumber}_billing.csv
 * Stored in: app_files/{username}/billing/
 *
 * Each user account has their own isolated billing directory
 * Each meter's CSV accumulates all billing records over time
 * Format: Timestamp,Period,BillingJSON,RatesJSON
 *
 * Advantages:
 * - One file per meter (easy to manage)
 * - Accumulates historical billing data
 * - Easy to backup/export individual meters
 * - No cross-meter data conflicts
 * - User-specific data isolation
 */
class BillingDataCSVRepository(
    private val context: Context,
    private val sessionManager: SessionManager
) {

    companion object {
        private const val TAG = "BillingDataCSVRepo"

        // File locks for thread-safe CSV operations
        private val fileLocks = mutableMapOf<String, Any>()

        @Synchronized
        private fun getLockForFile(filePath: String): Any {
            return fileLocks.getOrPut(filePath) { Any() }
        }
    }

    /**
     * Save billing data to meter-specific CSV
     * Appends new billing record to existing file
     */
    fun saveBillingData(billing: Billing, rates: FloatArray): Boolean {
        return try {
            val serialNumber = billing.SerialNumber
            if (serialNumber.isNullOrEmpty()) {
                Log.e(TAG, "Serial number is null or empty")
                return false
            }

            // Get user-specific billing directory
            val billingDir = UserFileManager.getBillingDir(context, sessionManager)
            Log.d(TAG, "Using billing directory: ${billingDir.absolutePath}")

            val filename = "${serialNumber}_billing.csv"
            val csvFile = File(billingDir, filename)

            val gson = Gson()
            val billingJson = gson.toJson(billing)
            val ratesJson = gson.toJson(rates)
            val timestamp = System.currentTimeMillis()
            val period = billing.Period ?: ""

            // Thread-safe file operations using file-specific lock
            synchronized(getLockForFile(csvFile.absolutePath)) {
                // Atomic check-and-create with header
                if (!csvFile.exists()) {
                    try {
                        csvFile.parentFile?.mkdirs() // Ensure directory exists
                        csvFile.writeText("Timestamp,Period,BillingData,Rates\n")
                        Log.d(TAG, "Created new billing file: ${csvFile.absolutePath}")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to create billing file: ${e.message}", e)
                        throw e
                    }
                }

                // Append new billing record
                val newLine = "$timestamp,\"$period\",\"${escapeJson(billingJson)}\",\"${escapeJson(ratesJson)}\"\n"
                try {
                    csvFile.appendText(newLine)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to append billing data: ${e.message}", e)
                    throw e
                }
            }

            Log.d(TAG, "Billing data saved for $serialNumber in ${csvFile.name}")
            true

        } catch (e: Exception) {
            Log.e(TAG, "Error saving billing data: ${e.message}", e)
            false
        }
    }

    /**
     * Load the most recent billing data for a meter
     */
    fun loadBillingData(serialNumber: String): SavedBillingData? {
        return try {
            // Get user-specific billing directory
            val billingDir = UserFileManager.getBillingDir(context, sessionManager)
            val csvFile = File(billingDir, "${serialNumber}_billing.csv")

            if (!csvFile.exists()) {
                Log.d(TAG, "No billing file found for $serialNumber")
                return null
            }

            val lines = csvFile.readLines()

            // Get the last line (most recent billing record)
            if (lines.size < 2) {
                Log.d(TAG, "No billing records in file for $serialNumber")
                return null
            }

            val lastLine = lines.last()

            // Parse CSV line
            val columns = parseCSVLine(lastLine)

            if (columns.size >= 4) {
                val timestamp = columns[0].toLongOrNull() ?: return null
                val billingJson = columns[2]
                val ratesJson = columns[3]

                val gson = Gson()
                val billing = gson.fromJson(billingJson, Billing::class.java)
                val rates = gson.fromJson(ratesJson, FloatArray::class.java)

                Log.d(TAG, "Loaded most recent billing data for $serialNumber")
                return SavedBillingData(
                    billing = billing,
                    timestamp = timestamp,
                    rates = rates
                )
            }

            null

        } catch (e: Exception) {
            Log.e(TAG, "Error loading billing data for $serialNumber: ${e.message}", e)
            null
        }
    }

    /**
     * Load all billing records for a meter (entire history)
     */
    fun loadAllBillingRecords(serialNumber: String): List<SavedBillingData> {
        return try {
            // Get user-specific billing directory
            val billingDir = UserFileManager.getBillingDir(context, sessionManager)
            val csvFile = File(billingDir, "${serialNumber}_billing.csv")

            if (!csvFile.exists()) {
                return emptyList()
            }

            val lines = csvFile.readLines()
            val result = mutableListOf<SavedBillingData>()

            // Skip header
            for (i in 1 until lines.size) {
                val line = lines[i]
                if (line.isBlank()) continue

                val columns = parseCSVLine(line)

                if (columns.size >= 4) {
                    try {
                        val timestamp = columns[0].toLongOrNull() ?: continue
                        val billingJson = columns[2]
                        val ratesJson = columns[3]

                        val gson = Gson()
                        val billing = gson.fromJson(billingJson, Billing::class.java)
                        val rates = gson.fromJson(ratesJson, FloatArray::class.java)

                        result.add(
                            SavedBillingData(
                                billing = billing,
                                timestamp = timestamp,
                                rates = rates
                            )
                        )
                    } catch (e: Exception) {
                        Log.w(TAG, "Error parsing line $i: ${e.message}")
                    }
                }
            }

            Log.d(TAG, "Loaded ${result.size} billing records for $serialNumber")
            result

        } catch (e: Exception) {
            Log.e(TAG, "Error loading all billing records: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Get list of all meters that have billing data
     */
    fun getAllMetersWithBillingData(): List<String> {
        return try {
            // Get user-specific billing directory
            val billingDir = UserFileManager.getBillingDir(context, sessionManager)

            if (!billingDir.exists()) {
                return emptyList()
            }

            val meterFiles = billingDir.listFiles { file ->
                file.isFile && file.name.endsWith("_billing.csv")
            } ?: return emptyList()

            val meters = meterFiles.map { file ->
                file.name.removeSuffix("_billing.csv")
            }

            Log.d(TAG, "Found ${meters.size} meters with billing data")
            meters

        } catch (e: Exception) {
            Log.e(TAG, "Error getting meters with billing data: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Clear billing data for a specific meter
     */
    fun clearBillingData(serialNumber: String): Boolean {
        return try {
            // Get user-specific billing directory
            val billingDir = UserFileManager.getBillingDir(context, sessionManager)
            val csvFile = File(billingDir, "${serialNumber}_billing.csv")

            if (csvFile.exists()) {
                csvFile.delete()
                Log.d(TAG, "Deleted billing file for $serialNumber")
                true
            } else {
                Log.d(TAG, "No billing file to delete for $serialNumber")
                true
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error clearing billing data: ${e.message}", e)
            false
        }
    }

    /**
     * Clear all billing data (delete all meter billing files)
     */
    fun clearAllBillingData(): Boolean {
        return try {
            // Get user-specific billing directory
            val billingDir = UserFileManager.getBillingDir(context, sessionManager)

            if (billingDir.exists()) {
                billingDir.listFiles()?.forEach { file ->
                    if (file.isFile && file.name.endsWith("_billing.csv")) {
                        file.delete()
                    }
                }
                Log.d(TAG, "Cleared all billing data")
                true
            } else {
                true
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error clearing all billing data: ${e.message}", e)
            false
        }
    }

    /**
     * Get billing data summary for a meter
     */
    fun getBillingDataSummary(serialNumber: String): BillingDataSummary? {
        return try {
            // Get user-specific billing directory
            val billingDir = UserFileManager.getBillingDir(context, sessionManager)
            val csvFile = File(billingDir, "${serialNumber}_billing.csv")

            if (!csvFile.exists()) {
                return null
            }

            val lines = csvFile.readLines()
            val recordCount = lines.size - 1 // Minus header

            if (recordCount <= 0) {
                return null
            }

            // Get first and last record dates
            val firstLine = lines[1]
            val lastLine = lines.last()

            val firstColumns = parseCSVLine(firstLine)
            val lastColumns = parseCSVLine(lastLine)

            val firstTimestamp = firstColumns[0].toLongOrNull() ?: return null
            val lastTimestamp = lastColumns[0].toLongOrNull() ?: return null

            val firstPeriod = if (firstColumns.size > 1) firstColumns[1].removeSurrounding("\"") else ""
            val lastPeriod = if (lastColumns.size > 1) lastColumns[1].removeSurrounding("\"") else ""

            BillingDataSummary(
                serialNumber = serialNumber,
                recordCount = recordCount,
                firstRecordDate = Date(firstTimestamp),
                lastRecordDate = Date(lastTimestamp),
                firstPeriod = firstPeriod,
                lastPeriod = lastPeriod,
                filePath = csvFile.absolutePath
            )

        } catch (e: Exception) {
            Log.e(TAG, "Error getting billing summary: ${e.message}", e)
            null
        }
    }

    /**
     * Check if billing data exists for a meter
     */
    fun hasBillingData(serialNumber: String): Boolean {
        return try {
            // Get user-specific billing directory
            val billingDir = UserFileManager.getBillingDir(context, sessionManager)
            val csvFile = File(billingDir, "${serialNumber}_billing.csv")

            if (!csvFile.exists()) {
                return false
            }

            // Efficiently check for data without loading entire file
            csvFile.useLines { lines ->
                lines.take(2).count() > 1
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking billing data: ${e.message}", e)
            false
        }
    }

    /**
     * Escape JSON for CSV (replace quotes with double quotes)
     */
    private fun escapeJson(json: String): String {
        return json.replace("\"", "\"\"")
    }

    /**
     * Parse CSV line handling quoted fields with commas
     */
    private fun parseCSVLine(line: String): List<String> {
        val result = mutableListOf<String>()
        var current = StringBuilder()
        var inQuotes = false
        var consecutiveQuotes = 0

        for (i in line.indices) {
            val char = line[i]

            when {
                char == '"' -> {
                    consecutiveQuotes++
                    if (consecutiveQuotes == 2) {
                        // Double quote inside quoted field
                        current.append('"')
                        consecutiveQuotes = 0
                    } else if (!inQuotes) {
                        inQuotes = true
                    } else if (i + 1 < line.length && line[i + 1] != '"') {
                        inQuotes = false
                    }
                }
                char == ',' && !inQuotes -> {
                    result.add(current.toString())
                    current = StringBuilder()
                    consecutiveQuotes = 0
                }
                else -> {
                    consecutiveQuotes = 0
                    current.append(char)
                }
            }
        }

        result.add(current.toString())
        return result
    }
}

/**
 * Summary information about billing data for a meter
 */
data class BillingDataSummary(
    val serialNumber: String,
    val recordCount: Int,
    val firstRecordDate: Date,
    val lastRecordDate: Date,
    val firstPeriod: String,
    val lastPeriod: String,
    val filePath: String
) {
    fun getDateRange(): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return "${dateFormat.format(firstRecordDate)} to ${dateFormat.format(lastRecordDate)}"
    }

    fun getPeriodRange(): String {
        return "$firstPeriod to $lastPeriod"
    }
}