package com.example.meterkenshin.dlms

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import com.example.meterkenshin.model.Meter
import com.example.meterkenshin.utils.getCurrentYearMonth
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * DLMS Functions - Extracted from DLMSViewModel
 * Contains reusable DLMS operations for meter registration and data retrieval
 */
@Suppress("SameParameterValue")
class DLMSFunctions(
    private val dlmsInitializer: DLMSInit,
    private val dlmsDataAccess: DLMSDataAccess,
    private val context: Context?,
    private val logCallback: (String) -> Unit
) {
    companion object {
        private const val TAG = "DLMSFunctions"
    }

    private var meter: Meter? = null

    fun setMeter(meter: Meter) {
        this.meter = meter
    }

    /**
     * Set clock on the meter
     */
    suspend fun performSetClock(): Boolean {
        dlmsDataAccess.setDataIndex(0)
        dlmsDataAccess.setSelector(0)

        // Get current time using Calendar to avoid DatetimeToSec bug
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.SECOND, 1) // Add 1 second

        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH) + 1 // Calendar.MONTH is 0-based
        val day = calendar.get(Calendar.DAY_OF_MONTH)
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)
        val second = calendar.get(Calendar.SECOND)

        // Format: %04x%02x%02xff%02x%02x%02xff800000
        // This matches SecToRawDatetime format from DLMS.java
        val rawDatetime = String.format(
            "%04x%02x%02xff%02x%02x%02xff800000",
            year, month, day, hour, minute, second
        )

        dlmsDataAccess.setParameter("090c$rawDatetime")

        return dlmsDataAccess.accessData(1, DLMS.IST_DATETIME_NOW, 2, false)
    }

    /**
     * Perform demand reset
     */
    suspend fun performDemandReset(): Boolean {
        dlmsDataAccess.setDataIndex(0)
        dlmsDataAccess.setParameter("120001")
        return dlmsDataAccess.accessData(2, DLMS.IST_DEMAND_RESET, 1, false)
    }

    /**
     * Get billing count
     */
    suspend fun performGetBillingCount(): Boolean {
        dlmsDataAccess.setDataIndex(0)
        dlmsDataAccess.setSelector(0)
        dlmsDataAccess.setParameter("")
        return dlmsDataAccess.accessData(0, DLMS.IST_BILLING_PARAMS, 7, false)
    }

    /**
     * Get billing data for registration
     */
    @SuppressLint("DefaultLocale")
    suspend fun performGetBillingDataRegistration(): Boolean {
        dlmsDataAccess.setDataIndex(0)
        dlmsDataAccess.setSelector(2)

        val mReceive = dlmsDataAccess.getReceive()
        val billingCount = if (mReceive != null && mReceive.size > 1) {
            try {
                mReceive[1].toInt()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse billing count: ${e.message}")
                1
            }
        } else {
            1
        }

        dlmsDataAccess.setParameter(
            String.format("020406%08x06%08x120001120000", billingCount, billingCount)
        )

        val success = dlmsDataAccess.accessData(0, DLMS.IST_BILLING_PARAMS, 2, false)

        if (success) {
            val receive = dlmsDataAccess.getReceive()
            if (receive != null && receive.size >= 10) {
                Log.i(TAG, "Billing data retrieved: ${receive.joinToString(",")}")

                // Parse billing data fields
                val registerDate = receive[0]
                val fixedDate = receive[1]
                val imp = String.format("%.3f", dlmsInitializer.dlms?.Float(1000.0, receive[2]) ?: 0.0)
                val exp = String.format("%.3f", dlmsInitializer.dlms?.Float(1000.0, receive[3]) ?: 0.0)
                val impMaxDemand = String.format("%.3f", dlmsInitializer.dlms?.Float(1000.0, receive[6]) ?: 0.0)
                val expMaxDemand = String.format("%.3f", dlmsInitializer.dlms?.Float(1000.0, receive[7]) ?: 0.0)
                val minVolt = String.format("%.2f", dlmsInitializer.dlms?.Float(100.0, receive[8]) ?: 0.0)
                val alert = receive[9]
                val readDate = receive[1]

                // Get meter info
                val currentMeter = meter
                val uid = currentMeter?.uid ?: "none"
                val activate = "1"
                val serialNo = currentMeter?.serialNumber ?: "none"
                val bluetoothId = currentMeter?.bluetoothId ?: "none"

                // Log registration data
                logCallback("══════════════════")
                logCallback("Registration Successful!")
                logCallback("══════════════════")
                logCallback("UID: $uid")
                logCallback("Activate: $activate")
                logCallback("Serial NO.: $serialNo")
                logCallback("Bluetooth ID: $bluetoothId")
                logCallback("Fixed date: $fixedDate")
                logCallback("Imp [kWh]: $imp")
                logCallback("Exp [kWh]: $exp")
                logCallback("ImpMaxDemand [kW]: $impMaxDemand")
                logCallback("ExpMaxDemand [kW]: $expMaxDemand")
                logCallback("MinVolt [V]: $minVolt")
                logCallback("Alert: $alert")
                logCallback("Read date: $readDate")
                logCallback("Registration date: $registerDate")
                logCallback("══════════════════")

                // Export meter data to CSV
                exportMeterData(
                    uid = uid.toString(),
                    activate = "1",
                    serialNo = serialNo,
                    bluetoothId = bluetoothId,
                    fixedDate = fixedDate,
                    imp = imp,
                    exp = exp,
                    impMaxDemand = impMaxDemand,
                    expMaxDemand = expMaxDemand,
                    minVolt = minVolt,
                    alert = alert,
                    readDate = readDate
                )
            }
        }

        return success
    }

    /**
     * Get billing data (standard read)
     */
    @SuppressLint("DefaultLocale")
    suspend fun performGetSingleBillingData(): Boolean {
        dlmsDataAccess.setDataIndex(0)
        dlmsDataAccess.setSelector(2)

        val mReceive = dlmsDataAccess.getReceive()
        val billingCount = if (mReceive != null && mReceive.size > 1) {
            try {
                mReceive[1].toInt()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse billing count: ${e.message}")
                1
            }
        } else {
            1
        }

        dlmsDataAccess.setParameter(
            String.format("020406%08x06%08x120001120000", billingCount, billingCount)
        )

        val success = dlmsDataAccess.accessData(0, DLMS.IST_BILLING_PARAMS, 2, false)

        if (success) {
            val receive = dlmsDataAccess.getReceive()
            if (receive != null && receive.size >= 10) {
                Log.i(TAG, "Billing data retrieved: ${receive.joinToString(",")}")

                // Parse billing data fields
                val readDate = receive[0]
                val fixedDate = receive[1]
                val imp = String.format("%.3f", dlmsInitializer.dlms?.Float(1000.0, receive[2]) ?: 0.0)
                val exp = String.format("%.3f", dlmsInitializer.dlms?.Float(1000.0, receive[3]) ?: 0.0)
                val impMaxDemand = String.format("%.3f", dlmsInitializer.dlms?.Float(1000.0, receive[6]) ?: 0.0)
                val expMaxDemand = String.format("%.3f", dlmsInitializer.dlms?.Float(1000.0, receive[7]) ?: 0.0)
                val minVolt = String.format("%.2f", dlmsInitializer.dlms?.Float(100.0, receive[8]) ?: 0.0)
                val alert = receive[9]

                // Get meter info
                val currentMeter = meter

                // Log billing data
                logCallback("Imp [kWh]: $imp")
                logCallback("Exp [kWh]: $exp")
                logCallback("ImpMaxDemand [kW]: $impMaxDemand")
                logCallback("ExpMaxDemand [kW]: $expMaxDemand")
                logCallback("MinVolt [V]: $minVolt")
                logCallback("Alert: $alert")
                logCallback("Read date: $readDate")

                // Export meter data to CSV
                exportMeterData(
                    uid = (currentMeter?.uid ?: "none").toString(),
                    activate = "1",
                    serialNo = currentMeter?.serialNumber ?: "none",
                    bluetoothId = currentMeter?.bluetoothId ?: "none",
                    fixedDate = fixedDate,
                    imp = imp,
                    exp = exp,
                    impMaxDemand = impMaxDemand,
                    expMaxDemand = expMaxDemand,
                    minVolt = minVolt,
                    alert = alert,
                    readDate = readDate
                )
            }
        }

        return success
    }

    /**
     * Get the last billing data result from performGetSingleBillingData
     */
    fun getLastBillingDataResult(): ArrayList<String>? {
        return dlmsDataAccess.getReceive()
    }

    /**
     * Perform initial billing data request
     */
    suspend fun performGetBillingData(): Boolean {
        dlmsDataAccess.setDataIndex(0)
        dlmsDataAccess.setSelector(0)
        dlmsDataAccess.setParameter("")
        return dlmsDataAccess.accessData(0, DLMS.IST_BILLING_PARAMS, 2, false)
    }

    /**
     * Perform billing data block continuation request
     */
    suspend fun performGetBillingDataBlock(): Boolean {
        return dlmsDataAccess.accessData(0, DLMS.IST_BILLING_PARAMS, 2, false)
    }

    /**
     * Perform initial load profile request
     */
    suspend fun performGetLoadProfile(): Boolean {
        dlmsDataAccess.setDataIndex(0)
        dlmsDataAccess.setSelector(0)
        dlmsDataAccess.setParameter("")
        return dlmsDataAccess.accessData(0, DLMS.IST_LOAD_PROFILE, 2, false)
    }

    /**
     * Perform load profile block continuation request
     */
    suspend fun performGetLoadProfileBlock(): Boolean {
        return dlmsDataAccess.accessData(0, DLMS.IST_LOAD_PROFILE, 2, false)
    }

    /**
     * Perform initial event log request
     */
    suspend fun performGetEventLog(): Boolean {
        dlmsDataAccess.setDataIndex(0)
        dlmsDataAccess.setSelector(0)
        dlmsDataAccess.setParameter("")
        return dlmsDataAccess.accessData(0, DLMS.IST_POWER_QUALITY, 2, false)
    }

    /**
     * Perform event log block continuation request
     */
    suspend fun performGetEventLogBlock(): Boolean {
        return dlmsDataAccess.accessData(0, DLMS.IST_POWER_QUALITY, 2, false)
    }

    /**
     * Export meter data to CSV file
     */
    private fun exportMeterData(
        uid: String,
        activate: String,
        serialNo: String,
        bluetoothId: String,
        fixedDate: String,
        imp: String,
        exp: String,
        impMaxDemand: String,
        expMaxDemand: String,
        minVolt: String,
        alert: String,
        readDate: String
    ) {
        try {
            // Define file and directory
            val externalFilesDir = context?.getExternalFilesDir(null) ?: return
            val csvDir = File(externalFilesDir, "app_files")
            if (!csvDir.exists()) {
                csvDir.mkdirs()
            }
            val yearMonth = getCurrentYearMonth()
            val filename = "${yearMonth}_meter.csv"
            val meterFile = File(csvDir, filename)

            val newRowData = listOf(
                uid, activate, serialNo, bluetoothId, fixedDate,
                imp, exp, impMaxDemand, expMaxDemand, minVolt, alert, readDate
            )

            // Read existing lines
            val lines = if (meterFile.exists()) meterFile.readLines().toMutableList() else mutableListOf()
            val newRowString = newRowData.joinToString(",")

            if (lines.isEmpty()) {
                logCallback("ERROR: No such meter data exist inside the csv file")
            } else {
                val uidIndex = 0
                val serialNoIndex = 2
                var wasRowUpdated = false

                // Find and replace or keep existing lines
                val updatedLines = lines.map { line ->
                    if (line.startsWith("UID,")) {
                        line // Keep header
                    } else {
                        val columns = line.split(',')
                        if (columns.size > serialNoIndex && columns[uidIndex] == uid && columns[serialNoIndex] == serialNo) {
                            wasRowUpdated = true
                            newRowString // Replace with new data
                        } else {
                            line // Keep existing line
                        }
                    }
                }.toMutableList()

                if (wasRowUpdated) {
                    lines.clear()
                    lines.addAll(updatedLines)
                    logCallback("Overwriting existing meter data for UID: $uid")

                    if (meter?.activate == 0) {
                        meter = meter?.copy(activate = 1)
                    }
                } else {
                    lines.add(newRowString)
                    logCallback("Appending new meter data for UID: $uid")
                }
            }

            // Write all lines back to file
            meterFile.writeText(lines.joinToString("\n"))
            logCallback("Meter data successfully exported")

        } catch (e: Exception) {
            logCallback("ERROR: Failed to export meter data: ${e.message}")
            Log.e(TAG, "Export error", e)
        }
    }

    fun exportMeterDataWithBillingDate(
        uid: String,
        activate: String,
        serialNo: String,
        bluetoothId: String,
        fixedDate: String,
        imp: String,
        exp: String,
        impMaxDemand: String,
        expMaxDemand: String,
        minVolt: String,
        alert: String,
        readDate: String,
        billingPrintDate: String
    ) {
        try {
            val externalFilesDir = context?.getExternalFilesDir(null) ?: return
            val csvDir = File(externalFilesDir, "app_files")
            if (!csvDir.exists()) {
                csvDir.mkdirs()
            }

            val yearMonth = SimpleDateFormat("yyyyMM", Locale.getDefault()).format(Date())
            val filename = "${yearMonth}_meter.csv"
            val meterFile = File(csvDir, filename)

            if (!meterFile.exists()) {
                logCallback("ERROR: Meter CSV file does not exist")
                return
            }

            // Read all lines
            val lines = meterFile.readLines().toMutableList()

            if (lines.isEmpty()) {
                logCallback("ERROR: Meter CSV is empty")
                return
            }

            // ✅ FIX: Ensure header has billingPrintDate column
            if (lines[0].startsWith("UID,")) {
                val headerColumns = lines[0].split(',')
                if (!headerColumns.contains("billingPrintDate")) {
                    lines[0] = lines[0] + ",billingPrintDate"
                    logCallback("Added billingPrintDate column to CSV header")
                }
            }

            lines[0].split(',').size
            val uidIndex = 0
            val serialNoIndex = 2
            var wasUpdated = false

            // ✅ FIX: Create new row data with all 13 columns
            val newRowData = listOf(
                uid, activate, serialNo, bluetoothId, fixedDate,
                imp, exp, impMaxDemand, expMaxDemand, minVolt,
                alert, readDate, billingPrintDate
            )
            val newRowString = newRowData.joinToString(",")

            // Update ONLY the matching row
            for (i in 1 until lines.size) {  // Start from 1 to skip header
                val columns = lines[i].split(',')
                val csvUid = columns.getOrNull(uidIndex)?.trim()?.removeSurrounding("\"")
                val csvSerialNo = columns.getOrNull(serialNoIndex)?.trim()?.removeSurrounding("\"")

                // ✅ FIX: Match by BOTH uid AND serialNo
                if (csvUid == uid && csvSerialNo == serialNo) {
                    lines[i] = newRowString
                    wasUpdated = true
                    logCallback("✅ Updated meter row - UID: $uid, Serial: $serialNo")
                    break  // Stop after first match
                }
            }

            if (!wasUpdated) {
                logCallback("⚠ WARNING: No matching meter found to update (UID: $uid, Serial: $serialNo)")
                logCallback("⚠ This should not happen when printing from SavedBillingDataCard")
                return  // ❌ DO NOT add new row
            }

            // Write back to file
            meterFile.writeText(lines.joinToString("\n"))
            logCallback("✅ Meter CSV updated successfully")

        } catch (e: Exception) {
            logCallback("ERROR: Failed to export meter data: ${e.message}")
            Log.e(TAG, "exportMeterDataWithBillingDate error", e)
        }
    }
}