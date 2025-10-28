package com.example.meterkenshin.ui.viewmodel

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.meterkenshin.model.BillingRecord
import com.example.meterkenshin.dlms.DLMS
import com.example.meterkenshin.dlms.DLMSDataAccess
import com.example.meterkenshin.dlms.DLMSFunctions
import com.example.meterkenshin.dlms.DLMSInit
import com.example.meterkenshin.dlms.DLMSSessionManager
import com.example.meterkenshin.model.Meter
import com.example.meterkenshin.model.Billing
import com.example.meterkenshin.util.calculateBillingData
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

@Suppress("KotlinConstantConditions")
@SuppressLint("SameParameterValue", "KotlinConstantConditions", "DefaultLocale", "MissingPermission")
class DLMSViewModel : ViewModel() {

    companion object {
        private const val TAG = "DLMSRegistration"
    }

    // State flows
    private val _registrationState = MutableStateFlow(RegistrationState())
    val registrationState: StateFlow<RegistrationState> = _registrationState.asStateFlow()

    private val _dlmsLog = MutableStateFlow("")
    val dlmsLog: StateFlow<String> = _dlmsLog.asStateFlow()

    // DLMS Initializer handles all Bluetooth/service setup
    private val dlmsInit = DLMSInit { appendLog(it) }
    private lateinit var sessionManager: DLMSSessionManager

    // DLMS Data Access Handler
    private lateinit var dlmsDataAccess: DLMSDataAccess
    private lateinit var dlmsFunctions: DLMSFunctions

    @SuppressLint("StaticFieldLeak")
    private var mContext: Context? = null
    private var meter: Meter? = null

    private val _currentMeter = MutableStateFlow<Meter?>(null)
    val currentMeter: StateFlow<Meter?> = _currentMeter.asStateFlow()

    /**
     * Initialize DLMS - delegates to DLMSInit
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    suspend fun initializeDLMS(context: Context, meter: Meter) {
        mContext = context
        this@DLMSViewModel.meter = meter
        dlmsInit.initialize(context, meter)
        dlmsDataAccess = DLMSDataAccess(dlmsInit)
        dlmsFunctions = DLMSFunctions(dlmsInit, dlmsDataAccess, mContext) { appendLog(it) }
        dlmsFunctions.setMeter(meter)
        sessionManager = DLMSSessionManager(dlmsInit)
    }

    private suspend fun establishSession(): Boolean {
        return sessionManager.establishSession()
    }

    private suspend fun closeSession() {
        appendLog("Releasing DLMS session...")

        if (sessionManager.releaseSession()) {
            appendLog("✅ DLMS session released successfully")
        } else {
            appendLog("⚠ Warning: Session release incomplete")
        }

        delay(200) // Small delay before Bluetooth disconnect
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
    private fun appendLog(message: String) {
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
        dlmsInit.cleanup(context)
    }

    /**
     * Start registration process
     */
    fun registration(meter: Meter) {
        viewModelScope.launch {
            try {
                // Check if initializer is ready
                if (!dlmsInit.isReady() || meter.bluetoothId.isNullOrEmpty()) {
                    appendLog("ERROR: Not ready (bound: ${dlmsInit.isServiceBound}, active: ${dlmsInit.isServiceActive})")
                    return@launch
                }

                _registrationState.value = RegistrationState(isRunning = true)
                _dlmsLog.value = ""

                appendLog("Connecting to ${meter.bluetoothId}...")
                dlmsInit.mArrived = -1  // Reset to waiting state
                Log.d(TAG, "Initial mArrived set to: ${dlmsInit.mArrived}")

                if (dlmsInit.bluetoothLeService?.connect(meter.bluetoothId) != true) {
                    appendLog("ERROR: Failed to start connection")
                    return@launch
                }

                // Wait for SERVICES_DISCOVERED (mArrived becomes 0)
                appendLog("Waiting for service discovery...")
                var ready = false
                for (i in 0..100) {
                    delay(100)
                    Log.v(TAG, "Wait loop $i: mArrived=${dlmsInit.mArrived}")
                    if (dlmsInit.mArrived == 0) {
                        ready = true
                        Log.i(TAG, "Service discovered! mArrived=${0} at iteration $i")
                        break
                    }
                }

                if (!ready) {
                    appendLog("ERROR: Services not discovered (timeout) - final mArrived=${dlmsInit.mArrived}")
                    Log.e(
                        TAG,
                        "Timeout: mArrived never became 0, final value=${dlmsInit.mArrived}"
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

                    appendLog("Success to register meter with S/N: ${meter.serialNumber}")

                    closeSession()

                    appendLog("✅ Registration Complete")
                    _registrationState.value = RegistrationState(isComplete = true)

                } else {
                    appendLog("ERROR: Failed to get billing data")
                }

            } catch (e: Exception) {
                appendLog("ERROR: ${e.message}")
                _registrationState.value = RegistrationState(
                    error = e.message ?: "Registration failed"
                )
            } finally {
                // ALWAYS close connection, success or failure
                dlmsInit.bluetoothLeService?.close()
                appendLog("Connection closed")
                finishOperation()
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
            if (!dlmsInit.isReady() || meter.bluetoothId.isNullOrEmpty()) {
                appendLog("ERROR: Not ready (bound: ${dlmsInit.isServiceBound}, active: ${dlmsInit.isServiceActive})")
                return@launch
            }

            startOperation("Read Data")

            appendLog("Connecting to ${meter.bluetoothId}...")
            dlmsInit.mArrived = -1

            if (dlmsInit.bluetoothLeService?.connect(meter.bluetoothId) != true) {
                appendLog("ERROR: Failed to start connection")
                finishOperation()
                return@launch
            }

            // Wait for SERVICES_DISCOVERED
            appendLog("Waiting for service discovery...")
            var ready = false
            for (i in 0..100) {
                delay(100)
                if (dlmsInit.mArrived == 0) {
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
            closeSession()

            appendLog("Success to read data")
            appendLog("✅ Read Data Complete")

        } catch (e: Exception) {
            appendLog("ERROR: ${e.message}")
            Log.e(TAG, "Read Data error", e)
        } finally {
            // ALWAYS close connection, success or failure
            dlmsInit.bluetoothLeService?.close()
            appendLog("Connection closed")
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
            if (!dlmsInit.isReady() || meter.bluetoothId.isNullOrEmpty()) {
                appendLog("ERROR: Not ready (bound: ${dlmsInit.isServiceBound}, active: ${dlmsInit.isServiceActive})")
                return@launch
            }

            startOperation("Load Profile")

            appendLog("Connecting to ${meter.bluetoothId}...")
            dlmsInit.mArrived = -1

            if (dlmsInit.bluetoothLeService?.connect(meter.bluetoothId) != true) {
                appendLog("ERROR: Failed to start connection")
                finishOperation()
                return@launch
            }

            // Wait for SERVICES_DISCOVERED
            appendLog("Waiting for service discovery...")
            var ready = false
            for (i in 0..100) {
                delay(100)
                if (dlmsInit.mArrived == 0) {
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
            if (!mReceive.isNullOrEmpty()) {
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
                if (!mReceive.isNullOrEmpty()) {
                    allLoadProfileData.addAll(mReceive)
                    blockCount++
                    appendLog("Block $blockCount received (${mReceive.size} entries, total: ${allLoadProfileData.size})")
                }

                continueTransfer = dlmsDataAccess.shouldContinueBlockTransfer()
            }

            appendLog("Load profile transfer complete: $blockCount blocks, ${allLoadProfileData.size} total entries")

            closeSession()

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
            // ALWAYS close connection, success or failure
            dlmsInit.bluetoothLeService?.close()
            appendLog("Connection closed")
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

            val serialNumber = meter.serialNumber
            val filename = "${serialNumber}_LP_${timestamp}.csv"

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
     * eventLog - Retrieve ALL power quality event records from meter using block transfer
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
            if (!dlmsInit.isReady() || meter.bluetoothId.isNullOrEmpty()) {
                appendLog("ERROR: Not ready (bound: ${dlmsInit.isServiceBound}, active: ${dlmsInit.isServiceActive})")
                return@launch
            }

            startOperation("Event Log")

            appendLog("Connecting to ${meter.bluetoothId}...")
            dlmsInit.mArrived = -1

            if (dlmsInit.bluetoothLeService?.connect(meter.bluetoothId) != true) {
                appendLog("ERROR: Failed to start connection")
                finishOperation()
                return@launch
            }

            // Wait for SERVICES_DISCOVERED
            appendLog("Waiting for service discovery...")
            var ready = false
            for (i in 0..100) {
                delay(100)
                if (dlmsInit.mArrived == 0) {
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

            // ===== BLOCK TRANSFER LOOP PATTERN =====
            // This is the key difference - we need to loop to get ALL records

            // Accumulate all event data across blocks
            val allEventData = ArrayList<String>()
            var blockCount = 0

            // First request - starts block transfer
            appendLog("Requesting event records...")

            // Initial access to Power Quality event log
            if (!dlmsDataAccess.accessData(0, DLMS.IST_POWER_QUALITY, 2, false)) {
                appendLog("ERROR: Failed to access event records")
                finishOperation()
                return@launch
            }

            // Collect data from first block
            var mReceive = dlmsDataAccess.getReceive()
            if (!mReceive.isNullOrEmpty()) {
                allEventData.addAll(mReceive)
                blockCount++
                appendLog("Block $blockCount received (${mReceive.size} entries)")
            }

            // Continue requesting blocks until complete
            // dlmsDataAccess tracks whether more blocks are available via shouldContinueBlockTransfer()
            // This is based on res[0] = 2 (continue) or res[0] = 0 (done)
            var continueTransfer = dlmsDataAccess.shouldContinueBlockTransfer()

            while (continueTransfer && blockCount < 200) { // Safety limit of 200 blocks
                delay(200) // Small delay between block requests

                // Request next block (DLMS internally tracks mBlockNo)
                if (!dlmsDataAccess.accessData(0, DLMS.IST_POWER_QUALITY, 2, false)) {
                    appendLog("ERROR: Failed to get event log block")
                    break
                }

                mReceive = dlmsDataAccess.getReceive()
                if (!mReceive.isNullOrEmpty()) {
                    allEventData.addAll(mReceive)
                    blockCount++
                    appendLog("Block $blockCount received (${mReceive.size} entries, total: ${allEventData.size})")
                }

                continueTransfer = dlmsDataAccess.shouldContinueBlockTransfer()
            }

            appendLog("Event log transfer complete: $blockCount blocks, ${allEventData.size} total entries")

            closeSession()

            // Save to CSV if we have data
            if (allEventData.size > 3) {
                val success = saveEventLogToCSV(meter.serialNumber, allEventData)

                if (success) {
                    appendLog("Success to get and save ${allEventData.size} event records to file")
                    appendLog("✅ Load Profile Complete")
                } else {
                    appendLog("ERROR: Failed to save event log to CSV")
                }
            } else {
                appendLog("ERROR: Insufficient event data (received ${allEventData.size} entries)")
            }

        } catch (e: Exception) {
            appendLog("ERROR: ${e.message}")
            Log.e(TAG, "Event Log error", e)
        } finally {
            // ALWAYS close connection, success or failure
            dlmsInit.bluetoothLeService?.close()
            appendLog("Connection closed")
            finishOperation()
        }
    }

    /**
     * Save complete event log data to CSV file
     * Format: Clock,Event,Volt V
     * Based on project01's SecondFragment implementation
     */
    private fun saveEventLogToCSV(serialNumber: String?, data: ArrayList<String>): Boolean {
        return try {
            if (serialNumber.isNullOrEmpty()) {
                Log.e(TAG, "Serial ID is null or empty")
                return false
            }

            val externalDir = mContext?.getExternalFilesDir(null)
            if (externalDir == null) {
                Log.e(TAG, "External storage not available")
                return false
            }

            // Extract timestamp from first entry and format filename
            // Example timestamp: "2024/10/24 15:30:45"
            var timestamp = data[0]
            timestamp = timestamp.replace("/", "")
                .replace(":", "")
                .replace(" ", "_")

            // Create filename: {SerialID}_EV_{timestamp}.csv
            val filename = "${serialNumber}_EV_${timestamp}.csv"
            val file = File(externalDir, filename)

            // Create CSV content
            val csvContent = StringBuilder()
            // Header matches project01: "Clock,Event,Volt[V]"
            csvContent.append("Clock,Event,Volt[V]\n")

            // Remove timestamp from data (already used for filename)
            val eventData = ArrayList(data)
            eventData.removeAt(0)

            // Write data rows
            var i = 0
            var rowCount = 0
            while (i < eventData.size) {
                if (i + 2 < eventData.size) {
                    val clock = eventData[i]      // Clock (datetime)
                    val event = eventData[i + 1]  // Event code
                    val volt = eventData[i + 2]   // Voltage

                    csvContent.append("$clock,$event,$volt\n")
                    i += 3
                    rowCount++
                } else {
                    break
                }
            }

            // Write to file
            file.writeText(csvContent.toString())

            Log.i(TAG, "Event log saved to: ${file.absolutePath}")
            appendLog("File saved: $filename ($rowCount events)")

            true
        } catch (e: Exception) {
            Log.e(TAG, "Error saving event log CSV: ${e.message}", e)
            false
        }
    }

    /**
     * Billing Data - Retrieves ALL billing records using block transfer and calculates charges
     */
    fun billingData(meter: Meter, rates: FloatArray) = viewModelScope.launch {
        if (_registrationState.value.isRunning) {
            appendLog("Billing Data already running")
            return@launch
        }

        if (!::dlmsDataAccess.isInitialized) {
            appendLog("ERROR: DLMS not initialized - call initializeDLMS first")
            return@launch
        }

        try {
            if (!dlmsInit.isReady() || meter.bluetoothId.isNullOrEmpty()) {
                appendLog("ERROR: Not ready (bound: ${dlmsInit.isServiceBound}, active: ${dlmsInit.isServiceActive})")
                return@launch
            }

            startOperation("Billing Data")

            appendLog("Connecting to ${meter.bluetoothId}...")
            dlmsInit.mArrived = -1

            if (dlmsInit.bluetoothLeService?.connect(meter.bluetoothId) != true) {
                appendLog("ERROR: Failed to start connection")
                finishOperation()
                return@launch
            }

            // Wait for SERVICES_DISCOVERED
            appendLog("Waiting for service discovery...")
            var ready = false
            for (i in 0..100) {
                delay(100)
                if (dlmsInit.mArrived == 0) {
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

            // ===== BLOCK TRANSFER LOOP FOR BILLING DATA =====
            appendLog("Getting billing data (may take multiple requests)...")
            val allBillingData = ArrayList<String>()
            var blockCount = 0

            // First request - starts block transfer
            if (!performGetBillingData()) {
                appendLog("ERROR: Failed to get billing data")
                finishOperation()
                return@launch
            }

            // Collect data from first block
            var mReceive = dlmsDataAccess.getReceive()
            if (!mReceive.isNullOrEmpty()) {
                allBillingData.addAll(mReceive)
                blockCount++
                appendLog("Block $blockCount received (${mReceive.size} entries)")
            }

            // Continue requesting blocks until complete
            var continueTransfer = dlmsDataAccess.shouldContinueBlockTransfer()

            while (continueTransfer && blockCount < 200) {
                delay(200) // Small delay between requests

                // Request next block
                if (!performGetBillingDataBlock()) {
                    appendLog("ERROR: Failed to get billing data block")
                    break
                }

                mReceive = dlmsDataAccess.getReceive()
                if (!mReceive.isNullOrEmpty()) {
                    allBillingData.addAll(mReceive)
                    blockCount++
                    appendLog("Block $blockCount received (${mReceive.size} entries, total: ${allBillingData.size})")
                }

                continueTransfer = dlmsDataAccess.shouldContinueBlockTransfer()
            }

            appendLog("Billing data transfer complete: $blockCount blocks, ${allBillingData.size} total entries")

            closeSession()

            // Parse and save billing data
            if (allBillingData.size >= 10) {
                // Remove timestamp if present (first entry)
                if (allBillingData.isNotEmpty() && allBillingData[0].contains("/")) {
                    allBillingData.removeAt(0)
                }

                // Parse billing records
                val records = parseBillingRecords(allBillingData)
                appendLog("Parsed ${records.size} billing records")

                // Calculate charges for each record (except first, which has no previous reading)
                if (records.size > 1) {
                    for (i in 1 until records.size) {
                        val prevRecord = records[i - 1]
                        val currentRecord = records[i]

                        // Create Billing object
                        val billing = Billing().apply {
                            PresReading = currentRecord.imp
                            PrevReading = prevRecord.imp
                            MaxDemand = currentRecord.maxImp / 1000f  // Convert W to kW
                        }

                        // Calculate and populate billing fields
                        calculateBillingData(billing, rates)

                        appendLog("Period: ${currentRecord.clock}")
                        appendLog("  Total Use: %.3f kWh".format(billing.TotalUse))
                        appendLog("  Gen/Trans: %.2f".format(billing.GenTransCharges))
                        appendLog("  Total Amount: %.2f".format(billing.TotalAmount))
                    }
                }

                // Save to CSV
                val success = saveBillingToCSV(meter, records, rates)
                if (success) {
                    appendLog("Success to get and save ${records.size} billing records to file")
                    appendLog("✅ Billing Data Complete")
                } else {
                    appendLog("ERROR: Failed to save billing data to CSV")
                }
            } else {
                appendLog("ERROR: Insufficient billing data (received ${allBillingData.size} entries)")
            }

        } catch (e: Exception) {
            appendLog("ERROR: ${e.message}")
            Log.e(TAG, "Billing Data error", e)
        } finally {
            // ALWAYS close connection, success or failure
            dlmsInit.bluetoothLeService?.close()
            appendLog("Connection closed")
            finishOperation()
        }
    }

    /**
     * Perform initial billing data request
     */
    private suspend fun performGetBillingData(): Boolean {
        dlmsDataAccess.setDataIndex(0)
        dlmsDataAccess.setSelector(0)
        dlmsDataAccess.setParameter("")
        return dlmsDataAccess.accessData(0, DLMS.IST_BILLING_PARAMS, 2, false)
    }

    /**
     * Perform billing data block continuation request
     */
    private suspend fun performGetBillingDataBlock(): Boolean {
        return dlmsDataAccess.accessData(0, DLMS.IST_BILLING_PARAMS, 2, false)
    }

    /**
     * Parse billing data entries: Clock, Imp, Exp, Abs, Net, MaxImp, MaxExp, MinVolt, Alert1, Alert2
     * Each record = 10 consecutive entries
     */
    private fun parseBillingRecords(data: ArrayList<String>): List<BillingRecord> {
        val records = mutableListOf<BillingRecord>()

        var i = 0
        while (i + 9 < data.size) {
            records.add(
                BillingRecord(
                    clock = data[i],
                    imp = data[i + 1].toFloatOrNull() ?: 0f,
                    exp = data[i + 2].toFloatOrNull() ?: 0f,
                    abs = data[i + 3].toFloatOrNull() ?: 0f,
                    net = data[i + 4].toFloatOrNull() ?: 0f,
                    maxImp = data[i + 5].toFloatOrNull() ?: 0f,
                    maxExp = data[i + 6].toFloatOrNull() ?: 0f,
                    minVolt = data[i + 7].toFloatOrNull() ?: 0f,
                    alert = data[i + 8]
                )
            )
            i += 10  // Still advance by 10 to skip alert2 in raw data
        }

        return records
    }

    /**
     * Save complete billing data to CSV file
     * CSV format: Clock,Imp,Exp,Abs,Net,ImpMaxDemand,ExpMaxDemand,MinVolt,Alert1,Alert2,
     *             TotalUse[kWh],GenTrans,Distribution,Capex,Other,Universal,VAT,TotalAmount
     */
    private fun saveBillingToCSV(meter: Meter, records: List<BillingRecord>, rates: FloatArray): Boolean {
        return try {
            val timestamp = records.lastOrNull()?.clock
                ?.replace("/", "")
                ?.replace(":", "")
                ?.replace(" ", "_") ?: "unknown"

            val serialNumber = meter.serialNumber
            val filename = "${serialNumber}_BL_${timestamp}.csv"

            val externalDir = mContext?.getExternalFilesDir(null)
            if (externalDir == null) {
                Log.e(TAG, "External storage not available")
                return false
            }

            val file = File(externalDir, filename)

            // Create CSV content
            val csvContent = StringBuilder()
            // Single line header to match project01
            csvContent.append("Clock,Imp[kWh],Exp[kWh],Abs[kWh],Net[kWh],ImpMaxDemand[W],ExpMaxDemand[W],MinVolt[V],Alert,TotalUse[kWh],GenTrans,Distribution,Capex,Other,Universal,VAT,TotalAmount\n")

            // Write data rows with calculated charges
            records.forEachIndexed { index, record ->
                // Base billing data
                csvContent.append("${record.clock},")
                csvContent.append("${record.imp},")
                csvContent.append("${record.exp},")
                csvContent.append("${record.abs},")
                csvContent.append("${record.net},")
                csvContent.append("${record.maxImp},")
                csvContent.append("${record.maxExp},")
                csvContent.append("${record.minVolt},")
                csvContent.append("${record.alert},")

                // Calculate and append charges (skip first record, no previous reading)
                if (index > 0) {
                    val prevRecord = records[index - 1]

                    // Create Billing object and calculate
                    val billing = Billing().apply {
                        PresReading = record.imp
                        PrevReading = prevRecord.imp
                        MaxDemand = record.maxImp / 1000f
                    }

                    // Calculate charges - populates billing fields directly
                    calculateBillingData(billing, rates)

                    // Write calculated values from Billing object
                    csvContent.append("${String.format("%.3f", billing.TotalUse ?: 0f)},")
                    csvContent.append("${String.format("%.2f", billing.GenTransCharges ?: 0f)},")
                    csvContent.append("${String.format("%.2f", billing.DistributionCharges ?: 0f)},")
                    csvContent.append("${String.format("%.2f", billing.SustainableCapex ?: 0f)},")
                    csvContent.append("${String.format("%.2f", billing.OtherCharges ?: 0f)},")
                    csvContent.append("${String.format("%.2f", billing.UniversalCharges ?: 0f)},")
                    csvContent.append("${String.format("%.2f", billing.ValueAddedTax ?: 0f)},")
                    csvContent.append("${String.format("%.2f", billing.TotalAmount ?: 0f)}\n")  // \n at the END
                } else {
                    csvContent.append("0.000,0.00,0.00,0.00,0.00,0.00,0.00,0.00\n")  // \n at the END
                }
            }

            // Write to file
            file.writeText(csvContent.toString())

            Log.i(TAG, "Billing data saved to: ${file.absolutePath}")
            appendLog("File saved: $filename (${records.size} records)")

            true
        } catch (e: Exception) {
            Log.e(TAG, "Error saving billing CSV: ${e.message}", e)
            false
        }
    }


    /**
     * Set Clock on the meter
     */
    fun setClock(meter: Meter) = viewModelScope.launch {
        if (_registrationState.value.isRunning) {
            appendLog("Set Clock already running")
        }

        // Check if DLMS is initialized
        if (!::dlmsDataAccess.isInitialized) {
            appendLog("ERROR: DLMS not initialized - call initializeDLMS first")
        }

        try {
            // Check if initializer is ready
            if (!dlmsInit.isReady() || meter.bluetoothId.isNullOrEmpty()) {
                appendLog("ERROR: Not ready (bound: ${dlmsInit.isServiceBound}, active: ${dlmsInit.isServiceActive})")
            }

            startOperation("Set Clock")

            appendLog("Connecting to ${meter.bluetoothId}...")
            dlmsInit.mArrived = -1

            if (dlmsInit.bluetoothLeService?.connect(meter.bluetoothId) != true) {
                appendLog("ERROR: Failed to start connection")
                finishOperation()
            }

            // Wait for SERVICES_DISCOVERED
            appendLog("Waiting for service discovery...")
            var ready = false
            for (i in 0..100) {
                delay(100)
                if (dlmsInit.mArrived == 0) {
                    ready = true
                    break
                }
            }

            if (!ready) {
                appendLog("ERROR: Services not discovered (timeout)")
                finishOperation()
            }

            appendLog("Connected and ready")
            delay(500)

            // Establish DLMS session
            appendLog("Establishing DLMS session...")
            if (!establishSession()) {
                appendLog("ERROR: Failed to establish DLMS session")
                finishOperation()
            }
            appendLog("DLMS session established")
            delay(500)

            appendLog("Setting clock...")
            if (dlmsFunctions.performSetClock()) {
                val receive = dlmsDataAccess.getReceive()
                if (receive != null && receive.size > 1) {
                    if (receive[1] == "success (0)") {
                        appendLog("Success to set clock")

                        closeSession()

                        appendLog("✅ set Clock Complete")
                    } else {
                        appendLog("ERROR: Failed to set clock - ${receive[1]}")
                    }
                } else {
                    appendLog("✅ Clock set (response parsing incomplete)")
                }
            } else {
                appendLog("ERROR: Failed to set clock")
            }

        } catch (e: Exception) {
            appendLog("ERROR: ${e.message}")
            Log.e(TAG, "Set Clock error", e)
        } finally {
            // ALWAYS close connection, success or failure
            dlmsInit.bluetoothLeService?.close()
            appendLog("Connection closed")
            finishOperation()
        }
    }

    override fun onCleared() {
        super.onCleared()
        mContext?.let { cleanup(it) }
    }
}