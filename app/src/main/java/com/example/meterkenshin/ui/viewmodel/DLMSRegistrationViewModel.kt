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
                        Log.i(TAG, "Service discovered!")
                        break
                    }
                }

                if (!ready) {
                    appendLog("ERROR: Timeout during service discovery")
                    return@launch
                }

                appendLog("Connection established, starting registration...")

                // Establish DLMS session
                if (!establishSession()) {
                    appendLog("ERROR: Failed to establish session")
                    return@launch
                }
                appendLog("Session established successfully")

                // Retrieve billing data
                val billingSuccess = retrieveBillingData(meter)
                if (billingSuccess) {
                    appendLog("Billing data retrieved successfully")
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
     * Retrieve billing data using accessData
     */
    private suspend fun retrieveBillingData(meter: Meter?): Boolean {
        // Reset data access parameters
        dlmsDataAccess.reset()
        dlmsDataAccess.setParameter("")
        dlmsDataAccess.setSelector(0)
        dlmsDataAccess.setDataIndex(0)

        // Access billing parameters (index 2)
        val success = dlmsDataAccess.accessData(0, DLMS.IST_BILLING_PARAMS, 2, false)

        if (success) {
            val receive = dlmsDataAccess.getReceive()
            if (receive != null && receive.size >= 8) {
                // Parse the billing data
                val uid = receive.getOrNull(0) ?: "none"
                val activate = receive.getOrNull(1) ?: "none"
                val serialNo = receive.getOrNull(2) ?: "none"
                val fixedDate = receive.getOrNull(3) ?: "none"
                val readDate = receive.getOrNull(4) ?: "none"
                val imp = receive.getOrNull(5) ?: "none"
                val exp = receive.getOrNull(6) ?: "none"
                val alert = receive.getOrNull(7) ?: "none"
                val impMaxDemand = receive.getOrNull(8) ?: "none"
                val expMaxDemand = receive.getOrNull(9) ?: "none"
                val minVolt = receive.getOrNull(10) ?: "none"
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
                appendLog("══════════════════")
            }
        }

        return success
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