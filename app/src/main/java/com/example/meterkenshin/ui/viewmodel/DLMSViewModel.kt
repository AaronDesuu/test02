package com.example.meterkenshin.ui.viewmodel

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.meterkenshin.data.BillingDataCSVRepository
import com.example.meterkenshin.data.RegistrationState
import com.example.meterkenshin.data.SavedBillingData
import com.example.meterkenshin.dlms.DLMS
import com.example.meterkenshin.dlms.DLMSCSVWriter
import com.example.meterkenshin.dlms.DLMSDataAccess
import com.example.meterkenshin.dlms.DLMSFunctions
import com.example.meterkenshin.dlms.DLMSInit
import com.example.meterkenshin.dlms.DLMSJSONWriter
import com.example.meterkenshin.dlms.DLMSSessionManager
import com.example.meterkenshin.dlms.ReadDataPrinting
import com.example.meterkenshin.model.Billing
import com.example.meterkenshin.model.BillingRecord
import com.example.meterkenshin.model.Meter
import com.example.meterkenshin.ui.manager.AppPreferences
import com.example.meterkenshin.ui.manager.NotificationManager
import com.example.meterkenshin.ui.manager.SessionManager
import com.example.meterkenshin.utils.*
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@SuppressLint(
    "SameParameterValue",
    "KotlinConstantConditions",
    "DefaultLocale",
    "MissingPermission"
)
class DLMSViewModel : ViewModel() {

    companion object {
        private const val TAG = "DLMSRegistration"
    }

    // State flows
    private val _registrationState = MutableStateFlow(RegistrationState())
    val registrationState: StateFlow<RegistrationState> = _registrationState.asStateFlow()

    // ✅ FIXED: DLMS logs always start empty to prevent cross-user contamination
    // Logs are only loaded when user explicitly initializes DLMS
    private val _dlmsLog = MutableStateFlow("")
    val dlmsLog: StateFlow<String> = _dlmsLog.asStateFlow()

    private val _savedBillingData = MutableStateFlow<SavedBillingData?>(null)
    val savedBillingData: StateFlow<SavedBillingData?> = _savedBillingData.asStateFlow()

    private var billingRepository: BillingDataCSVRepository? = null

    val readDataPrinting = ReadDataPrinting(viewModelScope) { appendLog(it) }

    // DLMS Initializer handles all Bluetooth/service setup
    private val dlmsInit = DLMSInit { appendLog(it) }
    private lateinit var sessionManager: DLMSSessionManager

    // DLMS Data Access Handler
    private lateinit var dlmsDataAccess: DLMSDataAccess
    private lateinit var dlmsFunctions: DLMSFunctions

    @SuppressLint("StaticFieldLeak")
    private var mContext: Context? = null
    private var meter: Meter? = null

    // ✅ FIXED: User-specific AND meter-specific log storage
    private var userSessionManager: SessionManager? = null
    private var currentMeterSerialNumber: String? = null
    private var currentSessionUsername: String? = null // Track current user to detect session changes

    private val _currentMeter = MutableStateFlow<Meter?>(null)
    val currentMeter: StateFlow<Meter?> = _currentMeter.asStateFlow()

    private var meterReadingViewModel: MeterReadingViewModel? = null

    // Add near other StateFlows
    private val _showReadDataOptionsDialog = MutableStateFlow(false)
    val showReadDataOptionsDialog: StateFlow<Boolean> = _showReadDataOptionsDialog.asStateFlow()

    // ✅ NEW: Track pending CSV updates for batch processing
    private val _pendingCsvUpdates = MutableStateFlow(0)
    val pendingCsvUpdates: StateFlow<Int> = _pendingCsvUpdates.asStateFlow()

    fun updateCurrentMeter(updatedMeter: Meter) {
        meter = updatedMeter
        _currentMeter.value = updatedMeter
    }

    fun setPrinterViewModel(viewModel: PrinterBluetoothViewModel) {
        readDataPrinting.setPrinterViewModel(viewModel)
    }

    fun setMeterReadingViewModel(viewModel: MeterReadingViewModel) {
        meterReadingViewModel = viewModel
    }

    // State flow delegations - expose to UI
    val showPrintDialog: StateFlow<Boolean> get() = readDataPrinting.showPrintDialog
    val showSaveDialog: StateFlow<Boolean> get() = readDataPrinting.showSaveDialog
    val showPrinterErrorDialog: StateFlow<Boolean> get() = readDataPrinting.showPrinterErrorDialog
    val printerErrorMessage: StateFlow<String> get() = readDataPrinting.printerErrorMessage
    val pendingBillingData: StateFlow<Billing?> get() = readDataPrinting.pendingBillingData

    fun confirmPrint() {
        mContext?.let { context ->
            readDataPrinting.confirmPrint(context)
        }
    }

    fun skipPrint() {
        mContext?.let { context ->
            readDataPrinting.skipPrint(context)
        }
    }

    fun printReceipt(billing: Billing, rates: FloatArray? = null) =
        readDataPrinting.printReceipt(billing, rates)

    fun skipSave() {
        readDataPrinting.clearPendingBillingData()  // Use delegation
        readDataPrinting.dismissSaveDialog()         // Use delegation
    }

    fun confirmSave() {
        saveReadDataToJSON()
        readDataPrinting.dismissSaveDialog()  // Use delegation
    }

    /**
     * Initialize components needed for printing without Bluetooth connection
     * ✅ FIXED: Now accepts meterReadingViewModel to enable meter reloading after print
     * ✅ FIXED: Does NOT load logs here - logs are loaded per-meter in initializeDLMS
     */
    fun initializeForPrinting(context: Context, meterReadingViewModel: MeterReadingViewModel? = null) {
        mContext = context
        this.meterReadingViewModel = meterReadingViewModel
        val sessionManager = SessionManager.getInstance(context)
        userSessionManager = sessionManager
        billingRepository = BillingDataCSVRepository(context, sessionManager)
        readDataPrinting.setContext(context)

        readDataPrinting.setOnPrintSuccessCallback { serialNumber ->
            updateMeterBillingPrintDate(serialNumber)
        }

        // Note: Logs are NOT loaded here - they're loaded per-meter when needed
    }

    /**
     * Initialize DLMS - delegates to DLMSInit
     * ✅ FIXED: Loads user-specific AND meter-specific DLMS logs
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    suspend fun initializeDLMS(context: Context, meter: Meter) {
        mContext = context
        this@DLMSViewModel.meter = meter

        // ✅ FIXED: Set current meter serial number for per-meter logging
        currentMeterSerialNumber = meter.serialNumber

        dlmsInit.initialize(context, meter)
        dlmsDataAccess = DLMSDataAccess(dlmsInit)
        dlmsFunctions = DLMSFunctions(dlmsInit, dlmsDataAccess, mContext) { appendLog(it) }
        dlmsFunctions.setMeter(meter)
        sessionManager = DLMSSessionManager(dlmsInit)
        userSessionManager = SessionManager.getInstance(context)
        billingRepository = BillingDataCSVRepository(context, userSessionManager!!)
        readDataPrinting.setContext(context)

        readDataPrinting.setOnPrintSuccessCallback { serialNumber ->
            updateMeterBillingPrintDate(serialNumber)
        }

        // ✅ FIXED: Load user-specific AND meter-specific DLMS logs
        loadLogFromPreferences()

        // Load saved billing data if available
        loadSavedBillingData()
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
     * Initialize and load saved billing data from SharedPreferences
     * Call this in initializeDLMS()
     */
    private fun loadSavedBillingData() {
        val serialNumber = meter?.serialNumber
        if (serialNumber.isNullOrEmpty()) {
            Log.d(TAG, "No meter serial number available")
            return
        }

        val data = billingRepository?.loadBillingData(serialNumber)
        Log.d(TAG, "loadSavedBillingData: data = ${data != null} for $serialNumber")
        if (data != null) {
            _savedBillingData.value = data
            appendLog("Loaded billing data for $serialNumber")
        } else {
            Log.d(TAG, "No billing data found in CSV for $serialNumber")
        }
    }

    /**
     * Load billing data for specific serial number (used by BatchPrintManager)
     */
    fun loadBillingDataForMeter(serialNumber: String): SavedBillingData? {
        return billingRepository?.loadBillingData(serialNumber)
    }

    /**
     * Set temporary saved billing data (for batch printing)
     */
    fun setTemporarySavedBillingData(data: SavedBillingData) {
        _savedBillingData.value = data
    }

    /**
     * Save billing data to SharedPreferences with timestamp
     */
    private fun persistBillingData(billing: Billing, rates: FloatArray) {
        val success = billingRepository?.saveBillingData(billing, rates)

        if (success == true) {
            val serialNumber = billing.SerialNumber
            if (!serialNumber.isNullOrEmpty()) {
                val savedData = billingRepository?.loadBillingData(serialNumber)
                _savedBillingData.value = savedData
                Log.d(TAG, "✅ Billing data saved to CSV for $serialNumber")
                appendLog("Billing data saved to CSV")  // ← Changed message
            }
        }
    }
    /**
     * Clear saved billing data manually and reset meter to Not Inspected
     */
    fun clearSavedBillingData() {
        val serialNumber = meter?.serialNumber

        if (!serialNumber.isNullOrEmpty()) {
            billingRepository?.clearBillingData(serialNumber)  // ← Changed method
        }

        _savedBillingData.value = null

        meter?.let { currentMeter ->
            clearMeterReadDate(currentMeter.serialNumber)
        }

        appendLog("Cleared saved billing data")
    }

    /**
     * Clear meter's readDate and billingPrintDate to reset to Not Inspected state
     * FIXED: Also clear all meter reading data to prevent BillingDataAvailable trigger
     */
    private fun clearMeterReadDate(serialNumber: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = mContext ?: return@launch
                val sessionManager = SessionManager.getInstance(context)
                val yearMonth = getCurrentYearMonth()
                val filename = "${yearMonth}_meter.csv"
                val meterFile = UserFileManager.getMeterFile(context, sessionManager, filename)

                if (!meterFile.exists()) {
                    appendLog("⚠ Warning: Meter CSV file not found")
                    return@launch
                }

                val lines = meterFile.readLines().toMutableList()
                val serialNoIndex = 2

                // CSV column indices
                val impKWhIndex = 5           // Clear meter reading data
                val expKWhIndex = 6           // Clear meter reading data
                val impMaxDemandKWIndex = 7   // Clear meter reading data
                val expMaxDemandKWIndex = 8   // Clear meter reading data
                val minVoltVIndex = 9         // Clear meter reading data
                val alertIndex = 10           // Clear meter reading data
                val readDateIndex = 11        // Clear readDate
                val billingPrintDateIndex = 12 // Clear billingPrintDate

                for (i in 1 until lines.size) {
                    val columns = lines[i].split(',').toMutableList()
                    val csvSerialNo = columns.getOrNull(serialNoIndex)
                        ?.trim()
                        ?.removeSurrounding("\"")

                    if (csvSerialNo == serialNumber) {
                        // Clear ALL meter reading data AND status fields
                        if (columns.size > impKWhIndex) {
                            columns[impKWhIndex] = "" // Clear impKWh
                        }
                        if (columns.size > expKWhIndex) {
                            columns[expKWhIndex] = "" // Clear expKWh
                        }
                        if (columns.size > impMaxDemandKWIndex) {
                            columns[impMaxDemandKWIndex] = "" // Clear impMaxDemandKW
                        }
                        if (columns.size > expMaxDemandKWIndex) {
                            columns[expMaxDemandKWIndex] = "" // Clear expMaxDemandKW
                        }
                        if (columns.size > minVoltVIndex) {
                            columns[minVoltVIndex] = "" // Clear minVoltV
                        }
                        if (columns.size > alertIndex) {
                            columns[alertIndex] = "" // Clear alert
                        }
                        if (columns.size > readDateIndex) {
                            columns[readDateIndex] = "" // Clear readDate
                        }
                        if (columns.size > billingPrintDateIndex) {
                            columns[billingPrintDateIndex] = "" // Clear billingPrintDate
                        }

                        lines[i] = columns.joinToString(",")
                        appendLog("✅ Reset meter to Not Inspected: cleared all reading data for $serialNumber")
                        break
                    }
                }

                meterFile.writeText(lines.joinToString("\n"))
                appendLog("✅ CSV updated successfully")

                // Reload meters to update UI
                withContext(Dispatchers.Main) {
                    mContext?.let { ctx ->
                        meterReadingViewModel?.reloadMeters(ctx)
                    }
                }

            } catch (e: Exception) {
                appendLog("❌ Error clearing meter data: ${e.message}")
                Log.e(TAG, "Error clearing meter read date", e)
            }
        }
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
        _registrationState.value = RegistrationState(
            isRunning = false,
            isComplete = false  // ✅ Reset completion state
        )
    }

    /**
     * Append message to log
     * ✅ FIXED: Now saves to user-specific SharedPreferences
     */
    private fun appendLog(message: String) {
        _dlmsLog.value += "$message\n"
        Log.d(TAG, message)
        saveLogToPreferences()
    }

    /**
     * Clear log for current meter
     * ✅ FIXED: Now clears user-specific AND meter-specific SharedPreferences
     */
    fun clearLog() {
        _dlmsLog.value = ""
        saveLogToPreferences()
    }

    /**
     * Clear log for a specific meter
     * ✅ NEW: Allows clearing logs for a specific meter by serial number
     */
    fun clearLogForMeter(context: Context, meterSerialNumber: String) {
        val sessionManager = userSessionManager ?: SessionManager.getInstance(context)
        val session = sessionManager.getSession() ?: return

        val prefsName = "DLMSLog_${session.username}_${meterSerialNumber}"
        val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()

        // If this is the current meter, also clear in-memory log
        if (meterSerialNumber == currentMeterSerialNumber) {
            _dlmsLog.value = ""
        }

        Log.d(TAG, "Cleared DLMS logs for user: ${session.username}, meter: $meterSerialNumber")
    }

    /**
     * Save current log to user-specific AND meter-specific SharedPreferences
     * ✅ FIXED: Persists logs per user AND per meter
     */
    private fun saveLogToPreferences() {
        val context = mContext ?: return
        val sessionManager = userSessionManager ?: return
        val session = sessionManager.getSession() ?: return
        val meterSerial = currentMeterSerialNumber ?: return

        // ✅ FIXED: Use both username and meter serial number in the key
        val prefsName = "DLMSLog_${session.username}_${meterSerial}"
        val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        prefs.edit().putString("log", _dlmsLog.value).apply()

        Log.d(TAG, "Saved DLMS logs for user: ${session.username}, meter: $meterSerial, log length: ${_dlmsLog.value.length}")
    }

    /**
     * Load log from user-specific AND meter-specific SharedPreferences
     * ✅ FIXED: Loads logs specific to current user AND current meter
     * ✅ NEW: Detects session changes and clears logs from previous user
     * This ensures complete isolation between users and meters
     */
    private fun loadLogFromPreferences() {
        val context = mContext ?: return
        val sessionManager = userSessionManager ?: return
        val session = sessionManager.getSession() ?: run {
            Log.w(TAG, "Cannot load logs - no valid session")
            _dlmsLog.value = ""
            currentSessionUsername = null
            return
        }

        val meterSerial = currentMeterSerialNumber ?: run {
            Log.w(TAG, "Cannot load logs - no meter serial number set")
            _dlmsLog.value = ""
            return
        }

        // ✅ NEW: Detect session change and clear logs from previous user
        if (currentSessionUsername != null && currentSessionUsername != session.username) {
            Log.i(TAG, "Session changed from '$currentSessionUsername' to '${session.username}' - clearing previous user's logs")
            _dlmsLog.value = ""
        }

        // Update current session username
        currentSessionUsername = session.username

        // Clear any existing logs in memory to prevent cross-contamination
        _dlmsLog.value = ""

        // ✅ FIXED: Load logs specific to this user AND this meter
        val prefsName = "DLMSLog_${session.username}_${meterSerial}"
        val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        _dlmsLog.value = prefs.getString("log", "") ?: ""

        Log.d(TAG, "Loaded DLMS logs for user: ${session.username}, meter: $meterSerial, log length: ${_dlmsLog.value.length}")
    }

    /**
     * Reset logs when user logs out
     * ✅ NEW: Clears in-memory logs and session tracking
     * This ensures that when a new user logs in, they don't see the previous user's logs
     */
    fun resetLogsForLogout() {
        _dlmsLog.value = ""
        currentSessionUsername = null
        Log.d(TAG, "Logs reset for logout - session username cleared")
    }

    /**
     * Cleanup - delegates to DLMSInit
     * ✅ FIXED: Also saves current logs before cleanup
     */
    fun cleanup(context: Context) {
        // Save current logs before cleanup
        saveLogToPreferences()
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
                    NotificationManager.showSuccess("Registration completed successfully")

                    withContext(Dispatchers.Main) {
                        mContext?.let { ctx ->
                            meterReadingViewModel?.reloadMeters(ctx)
                        }
                    }

                    val updatedMeter = meter.copy(activate = 1)
                    _currentMeter.value = updatedMeter
                } else {
                    appendLog("ERROR: Failed to get billing data")

                }

            } catch (e: Exception) {
                appendLog("ERROR: ${e.message}")
                NotificationManager.showError("Registration failed: ${e.message}")
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
     * Exports JSON with single billing period
     */
    fun readData(meter: Meter, rates: FloatArray) {
        // Check if savedBillingData exists and is valid
        val savedData = _savedBillingData.value
        if (savedData != null && savedData.isValid() && savedData.billing.SerialNumber == meter.serialNumber) {
            // Prompt user: print only or do read
            _showReadDataOptionsDialog.value = true
            return
        }

        // No saved data, proceed with read
        performReadData(meter, rates)
    }

    fun performReadData(meter: Meter, rates: FloatArray) = viewModelScope.launch {
        if (_registrationState.value.isRunning) {
            appendLog("Read Data already running")
            return@launch
        }

        if (!::dlmsDataAccess.isInitialized) {
            appendLog("ERROR: DLMS not initialized - call initializeDLMS first")
            return@launch
        }

        try {
            if (!dlmsInit.isReady() || meter.bluetoothId.isNullOrEmpty()) {
                appendLog("ERROR: Not ready")
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

            // Step 3: Get billing data (SINGLE READING)
            appendLog("Getting billing data...")
            if (!dlmsFunctions.performGetSingleBillingData()) {
                appendLog("ERROR: Failed to get billing data")
                finishOperation()
                return@launch
            }

            val billingData = dlmsFunctions.getLastBillingDataResult()
            closeSession()

            if (billingData != null && billingData.size >= 10) {
                appendLog("Success to read data")

                // Parse current reading
                val currentRecord = BillingRecord(
                    clock = billingData[0],
                    imp = billingData[1].toFloatOrNull() ?: 0f,
                    exp = billingData[2].toFloatOrNull() ?: 0f,
                    abs = billingData[3].toFloatOrNull() ?: 0f,
                    net = billingData[4].toFloatOrNull() ?: 0f,
                    maxImp = billingData[5].toFloatOrNull() ?: 0f,
                    maxExp = billingData[6].toFloatOrNull() ?: 0f,
                    minVolt = billingData[7].toFloatOrNull() ?: 0f,
                    alert = billingData.getOrNull(8) ?: ""
                )

                appendLog("Current reading: ${currentRecord.imp} kWh")

                val previousReading = loadPreviousReading(meter.serialNumber)

                val effectivePrevReading = if (previousReading == null) {
                    loadFixedDateFromCSV(meter.serialNumber)
                } else {
                    previousReading.PresReading
                }

                val effectivePeriodFrom = if (previousReading == null) {
                    loadFixedDateFromCSV(meter.serialNumber) // Get fixedDate as first PeriodFrom
                } else {
                    previousReading.PeriodTo
                }

                // Create Billing object with current and previous data
                val billing = Billing().apply {
                    Period = dateTimeToMonth(billingData[1])
                    Commercial = "LARGE"
                    SerialNumber = meter.serialNumber
                    Multiplier = 1.0f
                    PeriodFrom = effectivePeriodFrom
                    PeriodTo = currentRecord.clock
                    PrevReading = effectivePrevReading?.toString()?.toFloatOrNull() ?: 0f
                    PresReading = currentRecord.imp
                    MaxDemand = currentRecord.maxImp / 1000f
                    DueDate = formattedMonthDay(1, 0)
                    DiscoDate = formattedMonthDay(1, 1)
                    Discount = 10.0f
                    Interest = 10.0f
                    Reader = "Fuji Taro"
                    ReadDatetime = getCurrentDateTime()  // Use existing function
                    Version = "v1.00.2"
                }

                // Calculate charges
                calculateBillingData(billing, rates)

                appendLog("Total Use: %.3f kWh".format(billing.TotalUse))
                appendLog("Total Amount: %.2f".format(billing.TotalAmount))

                // Save billing data for 30 days (replaces pending data approach)
                persistBillingData(billing, rates)

                // Save current as previous for next time
                savePreviousReading(meter.serialNumber, billing)

                appendLog("✅ Read Data Complete - Data saved for 30 days")
                NotificationManager.showSuccess("Read Data Complete - Data saved for 30 days")

                // Reload meters to update UI with latest data
                withContext(Dispatchers.Main) {
                    mContext?.let { ctx ->
                        meterReadingViewModel?.reloadMeters(ctx)
                    }
                }

                readDataPrinting.setPendingBillingData(billing)
                readDataPrinting.setSavedRates(rates)
                mContext?.let { context ->
                    readDataPrinting.showPrintDialog(context)
                }


            } else {
                appendLog("ERROR: Insufficient billing data received")
            }

        } catch (e: Exception) {
            appendLog("ERROR: ${e.message}")
            Log.e(TAG, "Read Data error", e)
            NotificationManager.showError("Read data failed: ${e.message}")
        } finally {
            dlmsInit.bluetoothLeService?.close()
            appendLog("Connection closed")
            finishOperation()
        }
    }

    /**
     * Save billing data to JSON from saved data
     * Can be called anytime within 30 days
     */
    fun saveStoredBillingToJSON() {
        // Check if JSON saving is enabled
        if (mContext?.let { AppPreferences.isJsonSavingEnabled(it) } == false) {
            appendLog("JSON saving is disabled in settings")
            return
        }

        val savedData = _savedBillingData.value
        if (savedData != null && savedData.isValid()) {
            viewModelScope.launch {
                appendLog("Saving stored billing data to JSON...")
                val success = DLMSJSONWriter.saveSingleBillingToJSON(
                    serialNumber = savedData.billing.SerialNumber,
                    billing = savedData.billing
                )

                if (success) {
                    appendLog("✅ Success to save billing data to JSON")
                    appendLog("Data was ${30 - savedData.daysRemaining()} days old")
                    // Trigger share dialog if enabled
                    mContext?.let { ctx ->
                        if (AppPreferences.isAutoShareExportEnabled(ctx)) {
                            DLMSJSONWriter.shareJSON(ctx, savedData.billing.SerialNumber)
                        }
                    }
                } else {
                    appendLog("❌ Failed to save billing data to JSON")
                }
            }
        } else {
            appendLog("No valid billing data available to save")
        }
    }

    /**
     * Save pending billing data to JSON (for immediate dialog)
     * Called after user confirms via dialog
     */
    private fun saveReadDataToJSON() {
        // Check if JSON saving is enabled
        if (mContext?.let { AppPreferences.isJsonSavingEnabled(it) } == false) {
            appendLog("JSON saving is disabled in settings")
            return
        }
        val billing = readDataPrinting.pendingBillingData.value
        if (billing != null) {
            viewModelScope.launch {
                appendLog("Saving billing data to JSON...")
                val success = DLMSJSONWriter.saveSingleBillingToJSON(
                    serialNumber = billing.SerialNumber,
                    billing = billing
                )

                if (success) {
                    appendLog("✅ Success to save billing data to JSON")
                    // Trigger share dialog if enabled
                    mContext?.let { ctx ->
                        if (AppPreferences.isAutoShareExportEnabled(ctx)) {
                            DLMSJSONWriter.shareJSON(ctx, billing.SerialNumber)
                        }
                    }
                } else {
                    appendLog("❌ Failed to save billing data to JSON")
                }

                // Clear pending data after save
                clearPendingBillingData()
            }
        } else {
            appendLog("No pending billing data to save")
        }
    }

    /**
     * Clear pending billing data without saving (for dialog dismiss)
     */
    private fun clearPendingBillingData() {
        readDataPrinting.clearPendingBillingData()
    }

    /**
     * Load previous reading from user-specific SharedPreferences
     * ✅ FIXED: Now uses user-specific preferences
     */
    private fun loadPreviousReading(serialNumber: String?): Billing? {
        val context = mContext ?: return null
        val sessionManager = userSessionManager ?: return null
        val session = sessionManager.getSession() ?: return null

        val prefs = context.getSharedPreferences("MeterReadings_${session.username}", Context.MODE_PRIVATE)
        val json = prefs.getString("prev_${serialNumber}", null) ?: return null

        return try {
            Gson().fromJson(json, Billing::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading previous reading: ${e.message}")
            null
        }
    }

    /**
     * Save current reading as previous for next comparison
     * ✅ FIXED: Now uses user-specific preferences
     */
    @SuppressLint("UseKtx")
    private fun savePreviousReading(serialNumber: String?, billing: Billing) {
        val context = mContext ?: return
        val sessionManager = userSessionManager ?: return
        val session = sessionManager.getSession() ?: return

        val prefs = context.getSharedPreferences("MeterReadings_${session.username}", Context.MODE_PRIVATE)
        val json = Gson().toJson(billing)
        prefs.edit().putString("prev_${serialNumber}", json).apply()
    }

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

            // ===== UNIFIED BLOCK TRANSFER =====
            appendLog("Getting load profile data (may take multiple requests)...")

            val allLoadProfileData = dlmsDataAccess.performBlockTransfer(
                operationName = "Load Profile",
                initialRequest = { dlmsFunctions.performGetLoadProfile() },
                blockRequest = { dlmsFunctions.performGetLoadProfileBlock() },
                logCallback = { appendLog(it) }
            )

            if (allLoadProfileData == null) {
                appendLog("ERROR: Failed to get load profile data")
                finishOperation()
                return@launch
            }

            closeSession()

            // Save to CSV
            if (allLoadProfileData.size > 5) {
                val success = DLMSCSVWriter.saveToCSV(
                    context = mContext,
                    type = DLMSCSVWriter.CSVType.LOAD_PROFILE,
                    serialNumber = meter.serialNumber,
                    data = allLoadProfileData
                )
                if (success) {
                    appendLog("Success to get and save ${allLoadProfileData.size} load profile records to file")
                    appendLog("✅ Load Profile Complete")
                    NotificationManager.showSuccess("Load profile completed successfully")
                } else {
                    appendLog("ERROR: Failed to save load profile to CSV")
                }
            }

        } catch (e: Exception) {
            appendLog("ERROR: ${e.message}")
            Log.e(TAG, "Load Profile error", e)
            NotificationManager.showError("Load profile failed: ${e.message}")
        } finally {
            // ALWAYS close connection, success or failure
            dlmsInit.bluetoothLeService?.close()
            appendLog("Connection closed")
            finishOperation()
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


            // First request - starts block transfer
            appendLog("Requesting event records...")

            // Initial access to Power Quality event log
            if (!dlmsDataAccess.accessData(0, DLMS.IST_POWER_QUALITY, 2, false)) {
                appendLog("ERROR: Failed to access event records")
                finishOperation()
                return@launch
            }

            val allEventData = dlmsDataAccess.performBlockTransfer(
                operationName = "Event Log",
                initialRequest = { dlmsFunctions.performGetEventLog() },
                blockRequest = { dlmsFunctions.performGetEventLogBlock() },
                logCallback = { appendLog(it) }
            )

            closeSession()

            // Save to CSV
            if (allEventData != null) {
                if (allEventData.size > 3) {
                    val success = DLMSCSVWriter.saveToCSV(
                        context = mContext,
                        type = DLMSCSVWriter.CSVType.EVENT_LOG,
                        serialNumber = meter.serialNumber,
                        data = allEventData
                    )
                    if (success) {
                        appendLog("Success to get and save ${allEventData.size} event records to file")
                        appendLog("✅ Event Log Complete")
                        NotificationManager.showSuccess("Event log completed successfully")
                    } else {
                        appendLog("ERROR: Failed to save event log to CSV")
                    }
                }
            }

        } catch (e: Exception) {
            appendLog("ERROR: ${e.message}")
            Log.e(TAG, "Event Log error", e)
            NotificationManager.showError("Event log failed: ${e.message}")
        } finally {
            // ALWAYS close connection, success or failure
            dlmsInit.bluetoothLeService?.close()
            appendLog("Connection closed")
            finishOperation()
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

            // ===== UNIFIED BLOCK TRANSFER =====
            appendLog("Getting billing data (may take multiple requests)...")

            val allBillingData = dlmsDataAccess.performBlockTransfer(
                operationName = "Billing Data",
                initialRequest = { dlmsFunctions.performGetBillingData() },
                blockRequest = { dlmsFunctions.performGetBillingDataBlock() },
                logCallback = { appendLog(it) }
            )

            if (allBillingData == null) {
                appendLog("ERROR: Failed to get billing data")
                finishOperation()
                return@launch
            }

            closeSession()

            // Parse and save billing data
            if (allBillingData.size >= 10) {
                // Remove timestamp if present (first entry)
                if (allBillingData[0].contains("/")) {
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

                // Save to CSV - pass the original data with timestamp for filename
                val dataWithTimestamp = ArrayList<String>()
                dataWithTimestamp.add(records.firstOrNull()?.clock ?: "")
                dataWithTimestamp.addAll(allBillingData)

                val success = DLMSCSVWriter.saveToCSV(
                    context = mContext,
                    type = DLMSCSVWriter.CSVType.BILLING,
                    serialNumber = meter.serialNumber,
                    data = dataWithTimestamp,
                    additionalData = Pair(records, rates)
                )

                if (success) {
                    appendLog("Success to get and save ${records.size} billing records to file")
                    appendLog("✅ Billing Data Complete")
                    NotificationManager.showSuccess("Billing data completed successfully")
                } else {
                    appendLog("ERROR: Failed to save billing data to CSV")
                }
            } else {
                appendLog("ERROR: Insufficient billing data (received ${allBillingData.size} entries)")
            }

        } catch (e: Exception) {
            appendLog("ERROR: ${e.message}")
            Log.e(TAG, "Billing Data error", e)
            NotificationManager.showError("Billing data failed: ${e.message}")
        } finally {
            // ALWAYS close connection, success or failure
            dlmsInit.bluetoothLeService?.close()
            appendLog("Connection closed")
            finishOperation()
        }
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
                        NotificationManager.showSuccess("Set clock completed successfully")

                    } else {
                        appendLog("ERROR: Failed to set clock - ${receive[1]}")
                    }
                } else {
                    appendLog("✅ Clock set (response parsing incomplete)")
                    NotificationManager.showSuccess("Set clock completed successfully")
                }
            } else {
                appendLog("ERROR: Failed to set clock")
            }

        } catch (e: Exception) {
            appendLog("ERROR: ${e.message}")
            Log.e(TAG, "Set Clock error", e)
            NotificationManager.showError("Set clock failed: ${e.message}")
        } finally {
            // ALWAYS close connection, success or failure
            dlmsInit.bluetoothLeService?.close()
            appendLog("Connection closed")
            finishOperation()
        }
    }

    /**
     * Update meter CSV with billingPrintDate after successful print
     * Reuses existing meter data and calls exportMeterDataWithBillingDate
     */
    private fun updateMeterBillingPrintDate(serialNumber: String) {
        viewModelScope.launch(Dispatchers.IO) {
            // ✅ NEW: Track pending CSV update
            _pendingCsvUpdates.value++
            try {
                Log.d(TAG, "updateMeterBillingPrintDate called for: $serialNumber")
                val context = mContext ?: return@launch
                val sessionManager = SessionManager.getInstance(context)
                val yearMonth = getCurrentYearMonth()
                val filename = "${yearMonth}_meter.csv"
                val meterFile = UserFileManager.getMeterFile(context, sessionManager, filename)
                Log.d(TAG, "Meter file path: ${meterFile.absolutePath}, exists: ${meterFile.exists()}")

                if (!meterFile.exists()) {
                    appendLog("⚠ Warning: Meter CSV file not found")
                    return@launch
                }

                // Read existing meter data from CSV
                val lines = meterFile.readLines().toMutableList()
                val serialNoIndex = 2
                var meterRow: List<String>? = null

                // Find the meter row by serial number
                for (line in lines) {
                    if (!line.startsWith("UID,")) {  // Skip header
                        val columns = line.split(',')
                        // ✅ FIX: Clean the serial number by removing quotes and trimming
                        val csvSerialNo = columns.getOrNull(serialNoIndex)
                            ?.trim()
                            ?.removeSurrounding("\"")

                        if (csvSerialNo == serialNumber) {
                            meterRow = columns
                            break
                        }
                    }
                }

                if (meterRow != null && meterRow.size >= 12) {
                    // Format current date/time for billingPrintDate
                    val dateFormat = SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault())
                    val billingPrintDate = dateFormat.format(Date())

                    appendLog("Updating meter CSV for Serial: $serialNumber")
                    appendLog("Found meter with UID: ${meterRow[0]}")

                    // ✅ FIXED: Update CSV directly without using dlmsFunctions
                    // This works even when dlmsFunctions is not initialized (batch printing)
                    val updatedLine = buildString {
                        // Columns 0-11 from existing data
                        for (i in 0..11) {
                            if (i > 0) append(",")
                            append("\"${meterRow[i].trim().removeSurrounding("\"")}\"")
                        }
                        // Column 12: billingPrintDate (new value)
                        append(",\"$billingPrintDate\"")
                        // Column 13: lastCommunication (keep existing if present)
                        if (meterRow.size >= 14) {
                            append(",\"${meterRow[13].trim().removeSurrounding("\"")}\"")
                        }
                    }

                    // Find and replace the meter's line in the CSV
                    val updatedLines = lines.toMutableList()
                    for (i in 1 until updatedLines.size) {
                        val columns = updatedLines[i].split(',')
                        val csvSerialNo = columns.getOrNull(serialNoIndex)
                            ?.trim()
                            ?.removeSurrounding("\"")

                        if (csvSerialNo == serialNumber) {
                            updatedLines[i] = updatedLine
                            break
                        }
                    }

                    // Write updated CSV back to file
                    meterFile.writeText(updatedLines.joinToString("\n"))

                    appendLog("✅ Billing print date saved to meter CSV: $billingPrintDate")
                    Log.d(TAG, "CSV update complete for $serialNumber, will reload meters")

                    // ✅ FIXED: Skip auto-reload during batch processing
                    // Batch manager will reload once at the end to avoid redundant reloads
                    // Check if there are other pending updates before reloading
                    withContext(Dispatchers.Main) {
                        // Decrement pending counter first
                        _pendingCsvUpdates.value--

                        // Only reload if this was the last pending update
                        // This prevents multiple redundant reloads during batch processing
                        if (_pendingCsvUpdates.value == 0) {
                            mContext?.let { ctx ->
                                meterReadingViewModel?.reloadMeters(ctx)
                                Log.d(TAG, "Meters reloaded after all CSV updates completed")
                            }
                        } else {
                            Log.d(TAG, "CSV update complete for $serialNumber, ${_pendingCsvUpdates.value} updates still pending")
                        }
                    }
                } else {
                    if (meterRow == null) {
                        appendLog("⚠ ERROR: Meter not found in CSV for serial: $serialNumber")
                    } else {
                        appendLog("⚠ ERROR: Meter row has insufficient data (${meterRow.size} columns)")
                    }
                }
            } catch (e: Exception) {
                appendLog("ERROR: Failed to save billing print date: ${e.message}")
                Log.e(TAG, "updateMeterBillingPrintDate error", e)
            } finally {
                // ✅ NEW: Always decrement counter, even on error
                if (_pendingCsvUpdates.value > 0) {
                    _pendingCsvUpdates.value--
                }
            }
        }
    }

    /**
     * Trigger print from batch processing
     * This bypasses the dialog and directly initiates printing
     */
    fun triggerPrintFromBatch() {
        val savedData = _savedBillingData.value
        if (savedData != null && savedData.isValid()) {
            appendLog("Printing receipt for ${savedData.billing.SerialNumber}")
            readDataPrinting.setPendingBillingData(savedData.billing)
            readDataPrinting.setSavedRates(savedData.rates)
            // For batch processing, auto-confirm print
            mContext?.let { context ->
                readDataPrinting.confirmPrint(context)
            }
        } else {
            appendLog("ERROR: No valid billing data to print")
        }
    }

    fun proceedWithNewRead(meter: Meter, rates: FloatArray) {
        _showReadDataOptionsDialog.value = false
        performReadData(meter, rates)
    }

    fun printExistingData() {
        _showReadDataOptionsDialog.value = false
        val savedData = _savedBillingData.value
        if (savedData != null && savedData.isValid()) {
            readDataPrinting.setPendingBillingData(savedData.billing)
            readDataPrinting.setSavedRates(savedData.rates)
            mContext?.let { context ->
                readDataPrinting.showPrintDialog(context)
            }
        }
    }

    fun dismissReadDataDialog() {
        _showReadDataOptionsDialog.value = false
    }

    /**
     * Load fixedDate from CSV as baseline for first reading
     */
    private fun loadFixedDateFromCSV(serialNumber: String): String? {
        return try {
            val context = mContext ?: return null
            val sessionManager = SessionManager.getInstance(context)
            val yearMonth = getCurrentYearMonth()
            val filename = "${yearMonth}_meter.csv"
            val meterFile = UserFileManager.getMeterFile(context, sessionManager, filename)

            if (!meterFile.exists()) return null

            val lines = meterFile.readLines()
            val fixedDateIndex = 4 // Column 5 in CSV (0-indexed)
            val serialNoIndex = 2

            for (line in lines) {
                if (line.startsWith("UID,")) continue
                val columns = line.split(',')
                val csvSerialNo = columns.getOrNull(serialNoIndex)
                    ?.trim()?.removeSurrounding("\"")

                if (csvSerialNo == serialNumber) {
                    return columns.getOrNull(fixedDateIndex)
                        ?.trim()?.removeSurrounding("\"")
                }
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error loading fixedDate: ${e.message}")
            null
        }
    }

    override fun onCleared() {
        super.onCleared()
        mContext?.let { cleanup(it) }
    }
}