package com.example.meterkenshin.dlms

import android.annotation.SuppressLint
import android.os.Environment
import android.util.Log
import com.example.meterkenshin.model.Billing
import com.example.meterkenshin.model.BillingRecord
import com.google.gson.Gson
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.example.meterkenshin.utils.convertLocalDatetime
import com.example.meterkenshin.utils.dateTimeToMonth
import com.example.meterkenshin.utils.formattedMonthDay
import com.example.meterkenshin.utils.getCurrentDateTime

/**
 * DLMSJSONWriter - Handles JSON export of billing data
 * Uses existing Billing class from model (no custom data class needed)
 */

@Suppress("SameParameterValue")
object DLMSJSONWriter {
    private const val TAG = "DLMSJSONWriter"


    /**
     * Get or create the Downloads/kenshinApp/json directory
     */
    private fun getJSONDirectory(): File? {
        return try {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val kenshinAppDir = File(downloadsDir, "kenshinApp")
            val jsonDir = File(kenshinAppDir, "json")

            if (!jsonDir.exists()) {
                val created = jsonDir.mkdirs()
                if (!created) {
                    Log.e(TAG, "Failed to create directory: ${jsonDir.absolutePath}")
                    return null
                }
            }

            Log.d(TAG, "JSON directory: ${jsonDir.absolutePath}")
            jsonDir
        } catch (e: Exception) {
            Log.e(TAG, "Error creating JSON directory: ${e.message}", e)
            null
        }
    }

    /**
     * Save billing data to JSON file (similar to project01)
     * Uses existing Billing class from model
     */
    @SuppressLint("DefaultLocale")
    fun saveBillingToJSON(
        serialNumber: String?,
        records: List<BillingRecord>,
        rates: FloatArray,
        rateType: String = "LARGE"
    ): Boolean {
        return try {
            if (serialNumber.isNullOrEmpty()) {
                Log.e(TAG, "Serial number is null or empty")
                return false
            }

            if (records.size < 2) {
                Log.e(TAG, "Insufficient records for billing calculation")
                return false
            }

            val externalDir = getJSONDirectory()
            if (externalDir == null) {
                Log.e(TAG, "JSON directory not available")
                return false
            }

            // Get current year-month for filename (yyyyMM format)
            val currentYearMonth = SimpleDateFormat("yyyyMM", Locale.getDefault())
                .format(Date())
            val filename = "${currentYearMonth}_${serialNumber}.json"
            val file = File(externalDir, filename)

            // Build billing data list (process all consecutive billing periods)
            val billingDataList = mutableListOf<Billing>()

            for (i in 1 until records.size) {
                val prevRecord = records[i - 1]
                val currentRecord = records[i]

                // Extract period information from clock string (yyyy/MM/dd HH:mm:ss)
                val period = dateTimeToMonth(prevRecord.clock)
                val periodFrom = convertLocalDatetime(prevRecord.clock)
                val periodTo = convertLocalDatetime(currentRecord.clock)

                // Create and populate Billing object
                val billing = Billing().apply {
                    Period = period
                    Commercial = rateType
                    SerialNumber = serialNumber
                    Multiplier = 1.0f
                    PeriodFrom = periodFrom
                    PeriodTo = periodTo
                    PrevReading = prevRecord.imp
                    PresReading = currentRecord.imp
                    MaxDemand = currentRecord.maxImp / 1000f  // Convert W to kW
                    Discount = 10.0f
                    Interest = 10.0f
                    Reader = "Fuji Taro"
                    ReadDatetime = getCurrentDateTime()
                    val readDate = java.util.Date()
                    DueDate = formattedMonthDay(readDate, 1, 0)
                    DiscoDate = formattedMonthDay(readDate, 1, 15)
                    Version = "v1.00.2"
                }

                // Calculate billing charges (populates remaining fields)
                com.example.meterkenshin.utils.calculateBillingData(billing, rates)

                billingDataList.add(billing)
            }

            // Convert to JSON and save
            val gson = Gson()
            val jsonString = gson.toJson(billingDataList)
            file.writeText(jsonString)

            Log.i(TAG, "Billing JSON saved to: ${file.absolutePath}")
            Log.d(TAG, "JSON content: $jsonString")

            true

        } catch (e: Exception) {
            Log.e(TAG, "Error saving billing JSON: ${e.message}", e)
            false
        }
    }

    /**
     * Save SINGLE billing period to JSON (for readData function)
     * This matches project01's MSG_READER JSON export
     */
    fun saveSingleBillingToJSON(
        serialNumber: String?,
        billing: Billing
    ): Boolean {
        return try {
            if (serialNumber.isNullOrEmpty()) {
                Log.e(TAG, "Serial number is null or empty")
                return false
            }

            val externalDir = getJSONDirectory()
            if (externalDir == null) {
                Log.e(TAG, "JSON directory not available")
                return false
            }

            // Filename: yyyyMM_serialNumber.json
            val currentYearMonth = SimpleDateFormat("yyyyMM", Locale.getDefault())
                .format(Date())
            val filename = "${currentYearMonth}_${serialNumber}.json"
            val file = File(externalDir, filename)

            // Create list with single billing period (matches project01)
            val billingDataList = listOf(billing)

            // Convert to JSON and save
            val gson = Gson()
            val jsonString = gson.toJson(billingDataList)
            file.writeText(jsonString)

            Log.i(TAG, "Billing JSON saved to: ${file.absolutePath}")
            Log.d(TAG, "JSON content: $jsonString")

            true

        } catch (e: Exception) {
            Log.e(TAG, "Error saving billing JSON: ${e.message}", e)
            false
        }
    }

    fun shareJSON(context: Context, serialNumber: String?) {
        if (serialNumber.isNullOrEmpty()) return

        try {
            val currentYearMonth = SimpleDateFormat("yyyyMM", Locale.getDefault())
                .format(Date())
            val filename = "${currentYearMonth}_${serialNumber}.json"

            val externalDir = getJSONDirectory()
            val file = File(externalDir, filename)

            if (!file.exists()) {
                Log.e(TAG, "JSON file not found: ${file.absolutePath}")
                return
            }

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_STREAM, uri)
                type = "application/json"
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(Intent.createChooser(shareIntent, "Share JSON file").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })

        } catch (e: Exception) {
            Log.e(TAG, "Error sharing JSON: ${e.message}", e)
        }
    }

    fun shareMultipleJSON(context: Context, serialNumbers: List<String>) {
        if (serialNumbers.isEmpty()) return

        try {
            val currentYearMonth = SimpleDateFormat("yyyyMM", Locale.getDefault())
                .format(Date())

            val externalDir = getJSONDirectory()
            val files = serialNumbers.mapNotNull { serialNumber ->
                val filename = "${currentYearMonth}_${serialNumber}.json"
                val file = File(externalDir, filename)
                if (file.exists()) file else null
            }

            if (files.isEmpty()) {
                Log.e(TAG, "No JSON files found to share")
                return
            }

            val uris = files.map { file ->
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
            }

            val shareIntent = Intent().apply {
                if (uris.size == 1) {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_STREAM, uris.first())
                } else {
                    action = Intent.ACTION_SEND_MULTIPLE
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                }
                type = "application/json"
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(Intent.createChooser(shareIntent, "Share ${files.size} JSON file(s)").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })

            Log.i(TAG, "Sharing ${files.size} JSON files")

        } catch (e: Exception) {
            Log.e(TAG, "Error sharing multiple JSONs: ${e.message}", e)
        }
    }
}