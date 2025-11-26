package com.example.meterkenshin.dlms

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import com.example.meterkenshin.model.BillingRecord
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DLMSCSVWriter {
    private const val TAG = "DLMSCSVWriter"

    enum class CSVType {
        BILLING,
        LOAD_PROFILE,
        EVENT_LOG
    }

    /**
     * Unified CSV writer for DLMS data
     */
    fun saveToCSV(
        context: Context?,
        type: CSVType,
        serialNumber: String?,
        data: ArrayList<String>,
        additionalData: Any? = null
    ): Boolean {
        return try {
            if (serialNumber.isNullOrEmpty()) {
                Log.e(TAG, "Serial number is null or empty")
                return false
            }

            val externalDir = context?.getExternalFilesDir(null)
            if (externalDir == null) {
                Log.e(TAG, "External storage not available")
                return false
            }

            // Use CURRENT datetime as filename timestamp to ensure unique files
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                .format(Date())

            // Generate filename based on type
            val filePrefix = when (type) {
                CSVType.BILLING -> "BD"
                CSVType.LOAD_PROFILE -> "LP"
                CSVType.EVENT_LOG -> "EL"
            }
            val filename = "${serialNumber}_${filePrefix}_${timestamp}.csv"
            val file = File(externalDir, filename)

            // Generate CSV content based on type
            val csvContent = when (type) {
                CSVType.BILLING -> generateBillingCSV(additionalData)
                CSVType.LOAD_PROFILE -> generateLoadProfileCSV(data)
                CSVType.EVENT_LOG -> generateEventLogCSV(data)
            }

            // Write to file
            file.writeText(csvContent)

            Log.i(TAG, "$type CSV saved to: ${file.absolutePath}")
            true

        } catch (e: Exception) {
            Log.e(TAG, "Error saving $type CSV: ${e.message}", e)
            false
        }
    }

    /**
     * Generate Billing CSV content
     */
    @SuppressLint("DefaultLocale")
    private fun generateBillingCSV(additionalData: Any?): String {
        val csvContent = StringBuilder()

        // Header - FIXED to match expected format
        csvContent.append("Clock,Imp[kWh],Exp[kWh],Abs[kWh],Net[kWh],ImpMaxDemand[W],ExpMaxDemand[W],MinVolt[V],Alert\n")

        // Parse records
        @Suppress("UNCHECKED_CAST")
        val billingData = additionalData as? Pair<List<BillingRecord>, FloatArray>
        val records = billingData?.first ?: return csvContent.toString()

        // Write data rows - FIXED to output only billing record fields
        for (record in records) {
            csvContent.append("${record.clock},")
            csvContent.append("${String.format("%.0f", record.imp)},")      // Changed from %.3f
            csvContent.append("${String.format("%.0f", record.exp)},")      // Changed from %.3f
            csvContent.append("${String.format("%.0f", record.abs)},")      // Changed from %.3f
            csvContent.append("${String.format("%.0f", record.net)},")      // Changed from %.3f
            csvContent.append("${String.format("%.0f", record.maxImp)},")   // Changed from %.3f
            csvContent.append("${String.format("%.0f", record.maxExp)},")   // Changed from %.3f
            csvContent.append("${String.format("%.0f", record.minVolt)},")  // Changed from %.2f
            csvContent.append("${record.alert}\n")                          // Removed Alert2, removed all billing calculations
        }

        return csvContent.toString()
    }

    /**
     * Generate Load Profile CSV content
     * Format: Clock,Status,AveVolt,BlockImp,BlockExp
     */
    private fun generateLoadProfileCSV(data: ArrayList<String>): String {
        val csvContent = StringBuilder()

        // Header
        csvContent.append("Clock,Status,AveVolt[V],BlockImp[kW],BlockExp[kW]\n")

        // Remove timestamp (already used for filename)
        val profileData = ArrayList(data)
        profileData.removeAt(0)

        // Write data rows (5 fields per row)
        var i = 0
        while (i + 4 < profileData.size) {
            csvContent.append("${profileData[i]},")      // Clock
            csvContent.append("${profileData[i+1]},")    // Status
            csvContent.append("${profileData[i+2]},")    // AveVolt
            csvContent.append("${profileData[i+3]},")    // BlockImp
            csvContent.append("${profileData[i+4]}\n")   // BlockExp
            i += 5
        }

        return csvContent.toString()
    }

    /**
     * Generate Event Log CSV content
     * Format: Clock,Event,Volt
     */
    @SuppressLint("DefaultLocale")
    private fun generateEventLogCSV(data: ArrayList<String>): String {
        val csvContent = StringBuilder()

        // Header
        csvContent.append("Clock,Event,Volt[V]\n")

        // Remove timestamp (already used for filename)
        val eventData = ArrayList(data)
        eventData.removeAt(0)

        // Write data rows (3 fields per row)
        var i = 0
        while (i + 2 < eventData.size) {
            csvContent.append("${eventData[i]},")      // Clock
            csvContent.append("${eventData[i+1]},")    // Event
            // Format Volt to 2 decimal places (e.g., 100.00)
            val voltValue = eventData[i+2].toFloatOrNull() ?: 0f
            csvContent.append("${String.format("%.2f", voltValue)}\n")   // Volt
            i += 3
        }

        return csvContent.toString()
    }
}