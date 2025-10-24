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
import com.example.meterkenshin.dlms.DLMSFunctions
import com.example.meterkenshin.dlms.DLMSInit
import com.example.meterkenshin.model.Meter
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

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
    private var meter: Meter? = null

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
        this@DLMSRegistrationViewModel.meter = meter
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

    /**
     * loadProfile - Retrieves load profile data from meter and saves to CSV
     *
     * Flow:
     * 1. Connect to meter via Bluetooth
     * 2. Establish DLMS session
     * 3. Retrieve load profile data (IST_LOAD_PROFILE, attribute 2)
     * 4. Save to CSV file: SerialID_LP_timestamp.csv
     */
    /**
     * Load Profile - Retrieves ALL load profile data using block transfer
     */
    fun loadProfile(meter: Meter) = viewModelScope.launch {
        if (_registrationState.value.isRunning) {
            appendLog("Load Profile already running")
            return@launch
        }

        if (!::dlmsDataAccess.isInitialized) {
            appendLog("ERROR: DLMS not initialized - call initializeDLMS first")
            return@launch
        }

        try {
            if (!dlmsInitializer.isReady() || meter.bluetoothId.isNullOrEmpty()) {
                appendLog("ERROR: Not ready (bound: ${dlmsInitializer.isServiceBound}, active: ${dlmsInitializer.isServiceActive})")
                return@launch
            }

            startOperation("Load Profile")

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

            // Get ALL load profile data using block transfer
            appendLog("Getting load profile data (may take multiple requests)...")
            val allLoadProfileData = ArrayList<String>()
            var blockCount = 0

            // First request - starts block transfer
            if (!performGetLoadProfile()) {
                appendLog("ERROR: Failed to get load profile data")
                finishOperation()
                return@launch
            }

            // Collect data from first block
            var mReceive = dlmsDataAccess.getReceive()
            if (mReceive != null && mReceive.isNotEmpty()) {
                allLoadProfileData.addAll(mReceive)
                blockCount++
                appendLog("Block $blockCount received (${mReceive.size} entries)")
            }

            // Continue requesting blocks until complete
            // ret[0] = 2 means continue, ret[0] = 0 means done
            var continueTransfer = dlmsDataAccess.shouldContinueBlockTransfer()

            while (continueTransfer && blockCount < 200) {
                delay(200) // Small delay between requests

                // Request next block
                if (!performGetLoadProfileBlock()) {
                    appendLog("ERROR: Failed to get load profile block")
                    break
                }

                mReceive = dlmsDataAccess.getReceive()
                if (mReceive != null && mReceive.isNotEmpty()) {
                    allLoadProfileData.addAll(mReceive)
                    blockCount++
                    appendLog("Block $blockCount received (${mReceive.size} entries, total: ${allLoadProfileData.size})")
                }

                continueTransfer = dlmsDataAccess.shouldContinueBlockTransfer()
            }

            appendLog("Load profile transfer complete: $blockCount blocks, ${allLoadProfileData.size} total entries")

            // Save to CSV
            if (allLoadProfileData.size > 5) {
                val success = saveLoadProfileToCSV(meter, allLoadProfileData)
                if (success) {
                    appendLog("Success to get and save ${allLoadProfileData.size} load profile records to file")
                    appendLog("✅ Load Profile Complete")
                } else {
                    appendLog("ERROR: Failed to save load profile to CSV")
                }
            } else {
                appendLog("ERROR: Insufficient load profile data (received ${allLoadProfileData.size} entries)")
            }

        } catch (e: Exception) {
            appendLog("ERROR: ${e.message}")
            Log.e(TAG, "Load Profile error", e)
        } finally {
            finishOperation()
        }
    }

    /**
     * Perform initial load profile request
     */
    private suspend fun performGetLoadProfile(): Boolean {
        dlmsDataAccess.setDataIndex(0)
        dlmsDataAccess.setSelector(0)
        dlmsDataAccess.setParameter("")
        return dlmsDataAccess.accessData(0, DLMS.IST_LOAD_PROFILE, 2, false)
    }

    /**
     * Perform load profile block continuation request
     * Used when ret[0] = 2 (continue block transfer)
     */
    private suspend fun performGetLoadProfileBlock(): Boolean {
        // Block continuation uses same parameters but DLMS tracks mBlockNo internally
        return dlmsDataAccess.accessData(0, DLMS.IST_LOAD_PROFILE, 2, false)
    }

    /**
     * Save complete load profile data to CSV file
     *
     * Data format (from your actual output):
     * - First entry [0] = timestamp (for filename)
     * - Repeating groups of 5: Clock, Status, AveVolt[V], BlockImp[kW], BlockExp[kW]
     *
     * Example entry:
     * 2025/05/09 13:30:00,128,23299,183,0
     */
    @SuppressLint("SimpleDateFormat")
    private fun saveLoadProfileToCSV(meter: Meter, data: ArrayList<String>): Boolean {
        return try {
            // Get timestamp from first entry for filename
            val timestamp = data[0]
                .replace("/", "")
                .replace(":", "")
                .replace(" ", "_")

            val serialId = meter.serialNumber
            val filename = "${serialId}_LP_${timestamp}.csv"

            val externalDir = mContext?.getExternalFilesDir(null)
            if (externalDir == null) {
                Log.e(TAG, "External storage not available")
                return false
            }

            val file = File(externalDir, filename)

            // Create CSV content
            val csvContent = StringBuilder()
            csvContent.append("Clock,Status,AveVolt[V],BlockImp[kW],BlockExp[kW]\n")

            // Remove timestamp from data (already used for filename)
            val profileData = ArrayList(data)
            profileData.removeAt(0)

            // Write data rows
            // Each entry: Clock, Status, AveVolt, BlockImp, BlockExp
            var i = 0
            var rowCount = 0
            while (i < profileData.size) {
                if (i + 4 < profileData.size) {
                    csvContent.append("${profileData[i]},")      // Clock (datetime)
                    csvContent.append("${profileData[i+1]},")    // Status (bit flags)
                    csvContent.append("${profileData[i+2]},")    // AveVolt[V]
                    csvContent.append("${profileData[i+3]},")    // BlockImp[kW]
                    csvContent.append("${profileData[i+4]}\n")   // BlockExp[kW]
                    i += 5
                    rowCount++
                } else {
                    break
                }
            }

            // Write to file
            file.writeText(csvContent.toString())

            Log.i(TAG, "Load profile saved to: ${file.absolutePath}")
            appendLog("File saved: $filename ($rowCount rows)")

            true
        } catch (e: Exception) {
            Log.e(TAG, "Error saving load profile CSV: ${e.message}", e)
            false
        }
    }

    /**
     * eventLog
     */
    fun eventLog(meter: Meter) = viewModelScope.launch {
        if (_registrationState.value.isRunning) {
            appendLog("Event Log already running")
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

            startOperation("Event Log")

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

        } catch (e: Exception) {
            appendLog("ERROR: ${e.message}")
            Log.e(TAG, "Event Log error", e)
        } finally {
            finishOperation()
        }
    }

    /**
     * Billing Data
     */
    fun billingData(meter: Meter) = viewModelScope.launch {
        if (_registrationState.value.isRunning) {
            appendLog("Billing Data already running")
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

            startOperation("Billing Data")

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

        } catch (e: Exception) {
            appendLog("ERROR: ${e.message}")
            Log.e(TAG, "Billing Data error", e)
        } finally {
            finishOperation()
        }
    }

    /**
     * setClock
     */
    fun setClock(meter: Meter) = viewModelScope.launch {
        if (_registrationState.value.isRunning) {
            appendLog("set Clock already running")
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

            startOperation("set Clock")

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

        } catch (e: Exception) {
            appendLog("ERROR: ${e.message}")
            Log.e(TAG, "set Clock error", e)
        } finally {
            finishOperation()
        }
    }

    override fun onCleared() {
        super.onCleared()
        mContext?.let { cleanup(it) }
    }
}