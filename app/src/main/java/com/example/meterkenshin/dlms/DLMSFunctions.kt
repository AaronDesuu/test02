package com.example.meterkenshin.dlms

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import com.example.meterkenshin.model.Meter
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * DLMS Functions - Extracted from DLMSViewModel
 * Contains reusable DLMS operations for meter registration and data retrieval
 */
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

        val sec = dlmsInitializer.dlms?.CurrentDatetimeSec()?.plus(1) ?: return false

        // FIX: Add 86400 seconds (1 day) to compensate for bug
        val secFixed = sec + 86400

        val rawDatetime = dlmsInitializer.dlms?.SecToRawDatetime(secFixed) ?: return false

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
                val readDate = ""

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
    suspend fun performGetBillingData(): Boolean {
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

    /**
     * Get current year-month for filename
     */
    private fun getCurrentYearMonth(): String {
        val sdf = SimpleDateFormat("yyyyMM", Locale.getDefault())
        return sdf.format(Date())
    }
}