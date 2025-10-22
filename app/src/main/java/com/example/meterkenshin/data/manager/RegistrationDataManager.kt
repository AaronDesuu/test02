package com.example.meterkenshin.data.manager

import android.content.Context
import android.util.Log
import com.example.meterkenshin.model.Meter
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Manages meter registration data - writes to registration.csv and YYYYMM_meter.csv
 * Matches project01 CSV format exactly
 */
class RegistrationDataManager(private val context: Context) {

    companion object {
        private const val TAG = "RegistrationDataMgr"
        private const val REGISTRATION_CSV = "registration.csv"
        private const val METER_CSV = "meter.csv"

        // CSV header matching project01
        private const val CSV_HEADER = "UID,Activate,Serial NO.,Bluetooth ID,Fixed date,Imp [kWh],Exp [kWh],ImpMaxDemand [kW],ExpMaxDemand [kW],MinVolt [V],Alert,Read date"
    }

    private val externalFilesDir: File? = context.getExternalFilesDir(null)

    /**
     * Save registration data after successful DLMS operation
     * Note: ViewModel pre-converts hex values using dlms.Float() and formats them
     *
     * @param billingData Processed strings where:
     *   [0] = readDate (datetime string)
     *   [1] = fixedDate (datetime string)
     *   [2-9] = formatted float strings (already converted and formatted by ViewModel)
     */
    fun saveRegistrationData(meter: Meter, billingData: List<String>): Boolean {
        if (externalFilesDir == null) {
            Log.e(TAG, "External storage unavailable")
            return false
        }

        if (billingData.size < 10) {
            Log.e(TAG, "Insufficient billing data: ${billingData.size}")
            return false
        }

        try {
            val readDate = billingData[0]
            val fixedDate = billingData[1]
            val imp = billingData[2].toFloatOrNull() ?: 0f
            val exp = billingData[3].toFloatOrNull() ?: 0f
            val impMax = billingData[6].toFloatOrNull() ?: 0f
            val expMax = billingData[7].toFloatOrNull() ?: 0f
            val minVolt = billingData[8].toFloatOrNull() ?: 0f
            val alert = billingData[9]

            Log.i(TAG, "Saving: ${meter.serialNumber} | Fixed:$fixedDate | Imp:$imp | Read:$readDate")

            updateRegistrationCsv(meter, fixedDate, imp, exp, impMax, expMax, minVolt, alert, readDate)
            createMonthlyMeterCsv(meter, fixedDate, imp, exp, impMax, expMax, minVolt, alert, readDate)

            Log.i(TAG, "✓ Saved to registration.csv and ${SimpleDateFormat("yyyyMM", Locale.getDefault()).format(Date())}_meter.csv")
            return true

        } catch (e: Exception) {
            Log.e(TAG, "Save failed: ${e.message}", e)
            e.printStackTrace()
            return false
        }
    }

    /**
     * Update registration.csv with new data
     * Matches project01 format: finds meter by UID and updates all columns
     */
    private fun updateRegistrationCsv(
        meter: Meter,
        fixedDate: String,
        imp: Float,
        exp: Float,
        impMax: Float,
        expMax: Float,
        minVolt: Float,
        alert: String,
        readDate: String
    ) {
        val file = File(externalFilesDir, REGISTRATION_CSV)
        Log.d(TAG, "Updating registration.csv at: ${file.absolutePath}")

        // Read existing or initialize from meter.csv
        if (!file.exists()) {
            Log.i(TAG, "registration.csv doesn't exist, checking meter.csv")
            val meterCsv = File(externalFilesDir, METER_CSV)
            if (meterCsv.exists()) {
                meterCsv.copyTo(file, overwrite = false)
                Log.i(TAG, "Copied meter.csv to registration.csv")
            } else {
                file.writeText("$CSV_HEADER\n,,,,,,,,,,,\n")
                Log.i(TAG, "Created new registration.csv with header")
            }
        }

        val lines = file.readLines().toMutableList()
        var updated = false

        Log.d(TAG, "registration.csv has ${lines.size} lines, searching for serial ${meter.serialNumber}")

        // Find and update the meter row by Serial NO. (column index 2)
        for (i in 2 until lines.size) {  // Skip header (0) and empty row (1)
            val cols = lines[i].split(",")
            Log.d(TAG, "Line $i: cols.size=${cols.size}, serial=${if(cols.size > 2) cols[2] else "N/A"}")

            if (cols.size > 2 && cols[2] == meter.serialNumber) {
                Log.i(TAG, "Found meter at line $i, updating...")
                lines[i] = "${cols[0]},0,${meter.serialNumber},${meter.bluetoothId ?: cols[3]}," +
                        "$fixedDate," +
                        "${String.format("%.3f", imp)}," +
                        "${String.format("%.3f", exp)}," +
                        "${String.format("%.3f", impMax)}," +
                        "${String.format("%.3f", expMax)}," +
                        "${String.format("%.3f", minVolt)}," +
                        "$alert," +
                        readDate
                updated = true
                break
            }
        }

        if (updated) {
            file.writeText(lines.joinToString("\n"))
            Log.i(TAG, "✓ Updated registration.csv for ${meter.serialNumber}")
        } else {
            Log.w(TAG, "⚠ Meter ${meter.serialNumber} NOT FOUND in registration.csv!")
        }
    }

    /**
     * Create/update YYYYMM_meter.csv (previous month file)
     * Copies from meter.csv and updates the registered meter
     */
    private fun createMonthlyMeterCsv(
        meter: Meter,
        fixedDate: String,
        imp: Float,
        exp: Float,
        impMax: Float,
        expMax: Float,
        minVolt: Float,
        alert: String,
        readDate: String
    ) {
        val monthFormat = SimpleDateFormat("yyyyMM", Locale.getDefault())
        val monthStr = monthFormat.format(Date())
        val monthlyFile = File(externalFilesDir, "${monthStr}_meter.csv")

        Log.d(TAG, "Updating ${monthStr}_meter.csv at: ${monthlyFile.absolutePath}")

        // Copy from meter.csv if monthly file doesn't exist
        if (!monthlyFile.exists()) {
            Log.i(TAG, "${monthStr}_meter.csv doesn't exist, checking meter.csv")
            val meterCsv = File(externalFilesDir, METER_CSV)
            if (meterCsv.exists()) {
                meterCsv.copyTo(monthlyFile, overwrite = false)
                Log.i(TAG, "Copied meter.csv to ${monthStr}_meter.csv")
            } else {
                monthlyFile.writeText("$CSV_HEADER\n,,,,,,,,,,,\n")
                Log.i(TAG, "Created new ${monthStr}_meter.csv with header")
            }
        }

        // Update the specific meter row
        val lines = monthlyFile.readLines().toMutableList()
        var updated = false

        Log.d(TAG, "${monthStr}_meter.csv has ${lines.size} lines, searching for serial ${meter.serialNumber}")

        for (i in 2 until lines.size) {
            val cols = lines[i].split(",")
            Log.d(TAG, "Line $i: cols.size=${cols.size}, serial=${if(cols.size > 2) cols[2] else "N/A"}")

            if (cols.size > 2 && cols[2] == meter.serialNumber) {
                Log.i(TAG, "Found meter at line $i, updating with Activate=1...")
                lines[i] = "${cols[0]},1,${meter.serialNumber},${meter.bluetoothId ?: cols[3]}," +
                        "$fixedDate," +
                        "${String.format("%.3f", imp)}," +
                        "${String.format("%.3f", exp)}," +
                        "${String.format("%.3f", impMax)}," +
                        "${String.format("%.3f", expMax)}," +
                        "${String.format("%.3f", minVolt)}," +
                        "$alert," +
                        readDate
                updated = true
                break
            }
        }

        if (updated) {
            monthlyFile.writeText(lines.joinToString("\n"))
            Log.i(TAG, "✓ Updated ${monthlyFile.name}")
        } else {
            Log.w(TAG, "⚠ Meter ${meter.serialNumber} NOT FOUND in ${monthStr}_meter.csv!")
        }
    }
}