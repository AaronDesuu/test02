package com.example.meterkenshin.ui.viewmodel

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.meterkenshin.dlms.DLMS
import com.example.meterkenshin.dlms.DLMSDataAccess
import com.example.meterkenshin.model.Meter
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class RegistrationState(
    val isRunning: Boolean = false,
    val currentStage: Int = 0,
    val message: String = "",
    val isComplete: Boolean = false,
    val error: String? = null
)

@SuppressLint("MissingPermission")
class DLMSRegistrationViewModel : ViewModel() {

    companion object {
        private const val TAG = "DLMSRegistration"
    }

    // State flows
    private val _registrationState = MutableStateFlow(RegistrationState())
    val registrationState: StateFlow<RegistrationState> = _registrationState.asStateFlow()

    private val _dlmsLog = MutableStateFlow("")
    val dlmsLog: StateFlow<String> = _dlmsLog.asStateFlow()

    // DLMS Initializer handles all Bluetooth/service setup
    private val dlmsInitializer = DLMSInit { appendLog(it) }

    // DLMS Data Access Handler
    private lateinit var dlmsDataAccess: DLMSDataAccess

    private var mContext: Context? = null
    private var currentMeter: Meter? = null

    // DLMS operation variables
    private var mStep = 0
    private var mTimer = 0

    /**
     * Initialize DLMS - delegates to DLMSInit
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    suspend fun initializeDLMS(context: Context, meter: Meter) {
        mContext = context
        currentMeter = meter
        dlmsInitializer.initialize(context, meter)
        dlmsDataAccess = DLMSDataAccess(dlmsInitializer)
    }

    /**
     * Start registration process
     * NOW ASSUMES SERVICE IS READY (initialized via initializeDLMS)
     */
    fun startRegistration(meter: Meter) {
        viewModelScope.launch {
            try {
                // Check if initializer is ready
                if (!dlmsInitializer.isReady() || meter.bluetoothId.isNullOrEmpty()) {
                    appendLog("ERROR: Not ready (bound: ${dlmsInitializer.isServiceBound}, active: ${dlmsInitializer.isServiceActive})")
                    return@launch
                }

                _registrationState.value = RegistrationState(isRunning = true)
                _dlmsLog.value = ""

                appendLog("Connecting to ${meter.bluetoothId}...")
                dlmsInitializer.mArrived = -1  // Reset to waiting state
                Log.d(TAG, "Initial mArrived set to: ${dlmsInitializer.mArrived}")

                if (dlmsInitializer.bluetoothLeService?.connect(meter.bluetoothId) != true) {
                    appendLog("ERROR: Failed to start connection")
                    return@launch
                }

                // Wait for SERVICES_DISCOVERED (mArrived becomes 0)
                appendLog("Waiting for service discovery...")
                var ready = false
                for (i in 0..100) {
                    delay(100)
                    Log.v(TAG, "Wait loop $i: mArrived=${dlmsInitializer.mArrived}")
                    if (dlmsInitializer.mArrived == 0) {
                        ready = true
                        Log.i(TAG, "Service discovered! mArrived=${0} at iteration $i")
                        break
                    }
                }

                if (!ready) {
                    appendLog("ERROR: Services not discovered (timeout) - final mArrived=${dlmsInitializer.mArrived}")
                    Log.e(TAG, "Timeout: mArrived never became 0, final value=${dlmsInitializer.mArrived}")
                    return@launch
                }

                appendLog("Connected and ready")
                delay(500)

                // Continue with DLMS session establishment
                appendLog("Establishing DLMS session...")
                if (!establishSession()) {
                    appendLog("ERROR: Failed to establish DLMS session")
                    return@launch
                }
                appendLog("DLMS session established")

                delay(500)

                // Set clock
                appendLog("Setting clock...")
                if (performSetClock()) {
                    appendLog("Success to set clock")
                } else {
                    appendLog("ERROR: Failed to set clock")
                    return@launch
                }

                delay(500)

                // Demand reset
                appendLog("Calling demand reset...")
                if (performDemandReset()) {
                    appendLog("Success to call demand reset")
                } else {
                    appendLog("ERROR: Failed to call demand reset")
                    return@launch
                }

                delay(500)

                // Get billing count
                appendLog("Getting billing count...")
                if (performGetBillingCount()) {
                    appendLog("Billing count retrieved")
                } else {
                    appendLog("ERROR: Failed to get billing count")
                    return@launch
                }

                delay(500)

                // Get billing data
                appendLog("Getting billing data...")
                if (performGetBillingData()) {
                    appendLog("Success to register meter")
                    appendLog("Finish!")
                    _registrationState.value = RegistrationState(isComplete = true)
                } else {
                    appendLog("ERROR: Failed to get billing data")
                }

            } catch (e: Exception) {
                appendLog("ERROR: ${e.message}")
                _registrationState.value = RegistrationState(
                    error = e.message ?: "Registration failed"
                )
            }
        }
    }

    /**
     * Establish DLMS session (Open -> Session -> Challenge -> Confirm)
     */
    private suspend fun establishSession(): Boolean {
        mStep = 0
        var sessionEstablished = false
        var timeout = 0

        while (!sessionEstablished && timeout < 100) {
            when (mStep) {
                0 -> {
                    val openRequest = dlmsInitializer.dlms?.Open()
                    if (openRequest != null) {
                        mTimer = 0
                        dlmsInitializer.mArrived = 0
                        dlmsInitializer.bluetoothLeService?.write(openRequest)
                        mStep++
                        Log.i(TAG, "Open: ${openRequest.size}")
                    }
                }
                2 -> {
                    val res = IntArray(2)
                    val sessionRequest = dlmsInitializer.dlms?.Session(res, dlmsInitializer.mData)
                    if (res[0] != 0 && sessionRequest != null) {
                        mTimer = 0
                        dlmsInitializer.mArrived = 0
                        dlmsInitializer.bluetoothLeService?.write(sessionRequest)
                        mStep++
                        Log.i(TAG, "Session: ${sessionRequest.size}")
                    } else {
                        Log.e(TAG, "Failed to connect HDLC")
                        return false
                    }
                }
                4 -> {
                    val res = IntArray(2)
                    val challengeRequest = dlmsInitializer.dlms?.Challenge(res, dlmsInitializer.mData)
                    if (res[0] != 0 && challengeRequest != null) {
                        mTimer = 0
                        dlmsInitializer.mArrived = 0
                        dlmsInitializer.bluetoothLeService?.write(challengeRequest)
                        mStep++
                        Log.i(TAG, "Challenge: ${challengeRequest.size}")
                    } else {
                        Log.e(TAG, "Failed challenge")
                        return false
                    }
                }
                6 -> {
                    val res = IntArray(2)
                    dlmsInitializer.dlms?.Confirm(res, dlmsInitializer.mData)
                    if (res[0] != 0) {
                        sessionEstablished = true
                        Log.i(TAG, "Session established!")
                    } else {
                        Log.e(TAG, "Failed confirm")
                        return false
                    }
                }
                1, 3, 5 -> {
                    mTimer = 0
                    while (dlmsInitializer.mArrived == 0 && mTimer < 300) {
                        delay(10)
                        mTimer++
                    }
                    if (dlmsInitializer.mArrived == 0) {
                        Log.e(TAG, "Timeout at step $mStep")
                        return false
                    } else {
                        mStep++
                    }
                }
            }
            timeout++
            delay(10)
        }

        return sessionEstablished
    }

    /**
     * Set clock on the meter
     */
    private suspend fun performSetClock(): Boolean {
        dlmsDataAccess.setDataIndex(0)
        dlmsDataAccess.setSelector(0)

        val sec = dlmsInitializer.dlms?.CurrentDatetimeSec()?.plus(1) ?: return false
        val rawDatetime = dlmsInitializer.dlms?.SecToRawDatetime(sec) ?: return false

        dlmsDataAccess.setParameter("090c$rawDatetime")

        return dlmsDataAccess.accessData(1, DLMS.IST_DATETIME_NOW, 2, false)
    }

    /**
     * Perform demand reset
     */
    private suspend fun performDemandReset(): Boolean {
        dlmsDataAccess.setDataIndex(0)
        dlmsDataAccess.setParameter("120001")
        return dlmsDataAccess.accessData(2, DLMS.IST_DEMAND_RESET, 1, false)
    }

    /**
     * Get billing count
     */
    private suspend fun performGetBillingCount(): Boolean {
        dlmsDataAccess.setDataIndex(0)
        dlmsDataAccess.setSelector(0)
        dlmsDataAccess.setParameter("")
        return dlmsDataAccess.accessData(0, DLMS.IST_BILLING_PARAMS, 7, false)
    }

    /**
     * Get billing data
     */
    @SuppressLint("DefaultLocale")
    private suspend fun performGetBillingData(): Boolean {
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

                // Parse billing data fields according to mReceive structure:
                // [0] = Read date (first date)
                // [1] = Fixed date (billing date)
                // [2] = Import energy
                // [3] = Export energy
                // [4] = Absolute energy (not logged)
                // [5] = Net energy (not logged)
                // [6] = Import max demand
                // [7] = Export max demand
                // [8] = Minimum voltage
                // [9] = Alert status

                val registerDate = receive[0]
                val fixedDate = receive[1]
                val imp = String.format("%.3f", dlmsInitializer.dlms?.Float(1000.0, receive[2]) ?: 0.0)
                val exp = String.format("%.3f", dlmsInitializer.dlms?.Float(1000.0, receive[3]) ?: 0.0)
                val impMaxDemand = String.format("%.3f", dlmsInitializer.dlms?.Float(1000.0, receive[6]) ?: 0.0)
                val expMaxDemand = String.format("%.3f", dlmsInitializer.dlms?.Float(1000.0, receive[7]) ?: 0.0)
                val minVolt = String.format("%.2f", dlmsInitializer.dlms?.Float(100.0, receive[8]) ?: 0.0)
                val alert = receive[9]
                val readDate = ""

                // Get meter info from currentMeter
                val meter = currentMeter
                val uid = meter?.uid ?: "none"
                val activate = "1"  // Meter is activated after successful registration
                val serialNo = meter?.serialNumber ?: "none"
                val bluetoothId = meter?.bluetoothId ?: "none"

                // Log in the specified order:
                // UID, Activate, Serial NO., Bluetooth ID, Fixed date, Imp [kWh], Exp [kWh],
                // ImpMaxDemand [kW], ExpMaxDemand [kW], MinVolt [V], Alert, Read date
                appendLog("══════════════════")
                appendLog("Registration Successful!")
                appendLog("══════════════════")
                appendLog("UID: $uid")
                appendLog("Activate: $activate")
                appendLog("Serial NO.: $serialNo")
                appendLog("Bluetooth ID: $bluetoothId")
                appendLog("Fixed date: $fixedDate")
                appendLog("Imp [kWh]: $imp")
                appendLog("Exp [kWh]: $exp")
                appendLog("ImpMaxDemand [kW]: $impMaxDemand")
                appendLog("ExpMaxDemand [kW]: $expMaxDemand")
                appendLog("MinVolt [V]: $minVolt")
                appendLog("Alert: $alert")
                appendLog("Read date: $readDate")
                appendLog("Registration date: $registerDate")
                appendLog("══════════════════")

                // Export meter data to CSV
                exportMeterData(
                    uid = (meter?.uid ?: "none").toString(),
                    activate = "1",
                    serialNo = meter?.serialNumber ?: "none",
                    bluetoothId = meter?.bluetoothId ?: "none",
                    fixedDate = fixedDate,
                    imp = imp,
                    exp = exp,
                    impMaxDemand = impMaxDemand,
                    expMaxDemand = expMaxDemand,
                    minVolt = minVolt,
                    alert = receive[9],
                    readDate = readDate
                )
            }
        }

        return success
    }

    private fun exportMeterData(
        uid: String, activate: String, serialNo: String, bluetoothId: String,
        fixedDate: String, imp: String, exp: String, impMaxDemand: String,
        expMaxDemand: String, minVolt: String, alert: String, readDate: String
    ) {
        try {
            // Define file and directory
            val externalFilesDir = mContext?.getExternalFilesDir(null) ?: return
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

            // Read all existing lines from the file. If the file doesn't exist, start with an empty list.
            val lines = if (meterFile.exists()) meterFile.readLines().toMutableList() else mutableListOf()
            val newRowString = newRowData.joinToString(",")

            if (lines.isEmpty()) {

                appendLog("ERROR: No such meter data exist inside the csv file")
            } else {
                // If file exists, find and replace the row or add it if it's not present.
                val uidIndex = 0
                val serialNoIndex = 2
                var wasRowUpdated = false

                // Use mapIndexedNotNull to find and replace the line in one go.
                val updatedLines = lines.map { line ->
                    if (line.startsWith("UID,")) { // Keep header as is
                        line
                    } else {
                        val columns = line.split(',')
                        if (columns.size > serialNoIndex && columns[uidIndex] == uid && columns[serialNoIndex] == serialNo) {
                            wasRowUpdated = true
                            newRowString // Replace with the new data
                        } else {
                            line // Keep the existing line
                        }
                    }
                }.toMutableList()

                if (wasRowUpdated) {
                    // If a row was replaced, our work is done.
                    lines.clear()
                    lines.addAll(updatedLines)
                    appendLog("Overwriting existing meter data for UID: $uid")
                } else {
                    // If no row was updated, it means this is a new entry, so append it.
                    lines.add(newRowString)
                    appendLog("Appending new meter data for UID: $uid")
                }
            }

            // Write all lines (original, updated, and/or appended) back to the file.
            meterFile.writeText(lines.joinToString("\n"))

            appendLog("Meter data successfully exported")

        } catch (e: Exception) {
            appendLog("ERROR: Failed to export meter data: ${e.message}")
            Log.e(TAG, "Export error", e)
        }
    }

    private fun getCurrentYearMonth(): String {
        val sdf = SimpleDateFormat("yyyyMM", Locale.getDefault())
        return sdf.format(Date())
    }

    /**
     * Append message to log
     */
    fun appendLog(message: String) {
        _dlmsLog.value += "$message\n"
        Log.d(TAG, message)
    }

    /**
     * Clear log
     */
    fun clearLog() {
        _dlmsLog.value = ""
    }

    /**
     * Cleanup - delegates to DLMSInit
     */
    fun cleanup(context: Context) {
        dlmsInitializer.cleanup(context)
    }

    override fun onCleared() {
        super.onCleared()
        mContext?.let { cleanup(it) }
    }
}