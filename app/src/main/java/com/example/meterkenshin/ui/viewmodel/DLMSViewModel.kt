package com.example.meterkenshin.ui.viewmodel

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.meterkenshin.dlms.DLMSDataAccess
import com.example.meterkenshin.dlms.DLMSFunctions
import com.example.meterkenshin.dlms.DLMSInit
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

@Suppress("SameParameterValue")
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
    private lateinit var dlmsFunctions: DLMSFunctions

    @SuppressLint("StaticFieldLeak")
    private var mContext: Context? = null
    private var Meter: Meter? = null

    private val _currentMeter = MutableStateFlow<Meter?>(null)
    val currentMeter: StateFlow<Meter?> = _currentMeter.asStateFlow()

    // DLMS operation variables
    private var mStep = 0
    private var mTimer = 0

    /**
     * Initialize DLMS - delegates to DLMSInit
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    suspend fun initializeDLMS(context: Context, meter: Meter) {
        mContext = context
        Meter = meter
        dlmsInitializer.initialize(context, meter)
        dlmsDataAccess = DLMSDataAccess(dlmsInitializer)
        dlmsFunctions = DLMSFunctions(dlmsInitializer, dlmsDataAccess, mContext) { appendLog(it) }
        dlmsFunctions.setMeter(meter)
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
                    val challengeRequest =
                        dlmsInitializer.dlms?.Challenge(res, dlmsInitializer.mData)
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
     * Start a DLMS operation
     */
    private fun startOperation(operationName: String) {
        _registrationState.value = _registrationState.value.copy(isRunning = true)
        appendLog("=== $operationName Started ===")
    }

    /**
     * Finish a DLMS operation
     */
    private fun finishOperation() {
        _registrationState.value = _registrationState.value.copy(isRunning = false)
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

    /**
     * Start registration process
     */
    fun registration(meter: Meter) {
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
                    Log.e(
                        TAG,
                        "Timeout: mArrived never became 0, final value=${dlmsInitializer.mArrived}"
                    )
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
                if (dlmsFunctions.performSetClock()) {
                    appendLog("Success to set clock")
                } else {
                    appendLog("ERROR: Failed to set clock")
                    return@launch
                }

                delay(500)

                // Demand reset
                appendLog("Calling demand reset...")
                if (dlmsFunctions.performDemandReset()) {
                    appendLog("Success to call demand reset")
                } else {
                    appendLog("ERROR: Failed to call demand reset")
                    return@launch
                }

                delay(500)

                // Get billing count
                appendLog("Getting billing count...")
                if (dlmsFunctions.performGetBillingCount()) {
                    appendLog("Billing count retrieved")
                } else {
                    appendLog("ERROR: Failed to get billing count")
                    return@launch
                }

                delay(500)

                // Get billing data
                appendLog("Getting billing data...")
                if (dlmsFunctions.performGetBillingDataRegistration()) {
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
     * Read Data - Performs demand reset and billing data retrieval
     * Follows the same pattern as registration
     */
    fun readData(meter: Meter) = viewModelScope.launch {
        if (_registrationState.value.isRunning) {
            appendLog("Read Data already running")
            return@launch
        }

        // Check if DLMS is initialized
        if (!::dlmsDataAccess.isInitialized) {
            appendLog("ERROR: DLMS not initialized - call initializeDLMS first")
            return@launch
        }

        try {
            // Check if initializer is ready
            if (!dlmsInitializer.isReady() || meter.bluetoothId.isNullOrEmpty()) {
                appendLog("ERROR: Not ready (bound: ${dlmsInitializer.isServiceBound}, active: ${dlmsInitializer.isServiceActive})")
                return@launch
            }

            startOperation("Read Data")

            appendLog("Connecting to ${meter.bluetoothId}...")
            dlmsInitializer.mArrived = -1

            if (dlmsInitializer.bluetoothLeService?.connect(meter.bluetoothId) != true) {
                appendLog("ERROR: Failed to start connection")
                finishOperation()
                return@launch
            }

            // Wait for SERVICES_DISCOVERED
            appendLog("Waiting for service discovery...")
            var ready = false
            for (i in 0..100) {
                delay(100)
                if (dlmsInitializer.mArrived == 0) {
                    ready = true

                    break
                }
            }

            if (!ready) {
                appendLog("ERROR: Services not discovered (timeout)")
                finishOperation()
                return@launch
            }

            appendLog("Connected and ready")
            delay(500)

            // Establish DLMS session
            appendLog("Establishing DLMS session...")
            if (!establishSession()) {
                appendLog("ERROR: Failed to establish DLMS session")
                finishOperation()
                return@launch
            }
            appendLog("DLMS session established")
            delay(500)

            // Step 1: Demand reset
            appendLog("Calling demand reset...")
            if (!dlmsFunctions.performDemandReset()) {
                appendLog("ERROR: Failed to call demand reset")
                finishOperation()
                return@launch
            }
            appendLog("Success to call demand reset")
            delay(500)

            // Step 2: Get billing count
            appendLog("Getting billing count...")
            if (!dlmsFunctions.performGetBillingCount()) {
                appendLog("ERROR: Failed to get billing count")
                finishOperation()
                return@launch
            }
            appendLog("Billing count retrieved")
            delay(500)

            // Step 3: Get billing data
            appendLog("Getting billing data...")
            if (!dlmsFunctions.performGetBillingData()) {
                appendLog("ERROR: Failed to get billing data")
                finishOperation()
                return@launch
            }
            appendLog("Success to read data")
            appendLog("✅ Read Data Complete")

        } catch (e: Exception) {
            appendLog("ERROR: ${e.message}")
            Log.e(TAG, "Read Data error", e)
        } finally {
            finishOperation()
        }
    }


    override fun onCleared() {
        super.onCleared()
        mContext?.let { cleanup(it) }
    }
}