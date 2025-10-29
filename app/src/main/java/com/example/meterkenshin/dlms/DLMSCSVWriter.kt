package com.example.meterkenshin.dlms

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import com.example.meterkenshin.model.Billing
import com.example.meterkenshin.model.BillingRecord
import com.example.meterkenshin.util.calculateBillingData
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
                CSVType.EVENT_LOG -> "EV"
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
     * Format: Clock,Imp,Exp,Abs,Net,ImpMaxDemand,ExpMaxDemand,MinVolt,Alert1,Alert2,
     *         TotalUse[kWh],GenTrans,Distribution,Capex,Other,Universal,VAT,TotalAmount
     */
    @SuppressLint("DefaultLocale")
    private fun generateBillingCSV(additionalData: Any?): String {  // ← Removed data param
        val csvContent = StringBuilder()

        // Header
        csvContent.append("Clock,Imp,Exp,Abs,Net,ImpMaxDemand,ExpMaxDemand,MinVolt,Alert1,Alert2,")
        csvContent.append("TotalUse[kWh],GenTrans,Distribution,Capex,Other,Universal,VAT,TotalAmount\n")

        // Parse records and rates
        @Suppress("UNCHECKED_CAST")
        val billingData = additionalData as? Pair<List<BillingRecord>, FloatArray>
        val records = billingData?.first ?: return csvContent.toString()
        val rates = billingData.second

        // Write data rows
        for (record in records) {
            csvContent.append("${record.clock},")
            csvContent.append("${String.format("%.3f", record.imp)},")
            csvContent.append("${String.format("%.3f", record.exp)},")
            csvContent.append("${String.format("%.3f", record.abs)},")
            csvContent.append("${String.format("%.3f", record.net)},")
            csvContent.append("${String.format("%.3f", record.maxImp)},")
            csvContent.append("${String.format("%.3f", record.maxExp)},")
            csvContent.append("${String.format("%.2f", record.minVolt)},")
            csvContent.append("${record.alert},")
            csvContent.append(",") // Alert2 placeholder

            // Calculate billing charges if not first record
            val index = records.indexOf(record)
            if (index > 0) {
                val prevRecord = records[index - 1]
                val billing = Billing().apply {
                    PresReading = record.imp
                    PrevReading = prevRecord.imp
                    MaxDemand = record.maxImp / 1000f
                }
                calculateBillingData(billing, rates)

                csvContent.append("${String.format("%.3f", billing.TotalUse ?: 0f)},")
                csvContent.append("${String.format("%.2f", billing.GenTransCharges ?: 0f)},")
                csvContent.append("${String.format("%.2f", billing.DistributionCharges ?: 0f)},")
                csvContent.append("${String.format("%.2f", billing.SustainableCapex ?: 0f)},")
                csvContent.append("${String.format("%.2f", billing.OtherCharges ?: 0f)},")
                csvContent.append("${String.format("%.2f", billing.UniversalCharges ?: 0f)},")
                csvContent.append("${String.format("%.2f", billing.ValueAddedTax ?: 0f)},")
                csvContent.append("${String.format("%.2f", billing.TotalAmount ?: 0f)}\n")
            } else {
                csvContent.append("0.000,0.00,0.00,0.00,0.00,0.00,0.00,0.00\n")
            }
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
            csvContent.append("${eventData[i+2]}\n")   // Volt
            i += 3
        }

        return csvContent.toString()
    }
}