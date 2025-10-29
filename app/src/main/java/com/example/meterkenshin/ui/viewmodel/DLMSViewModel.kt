package com.example.meterkenshin.ui.viewmodel

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.example.meterkenshin.model.BillingRecord
import com.example.meterkenshin.dlms.DLMS
import com.example.meterkenshin.dlms.DLMSCSVWriter
import com.example.meterkenshin.dlms.DLMSDataAccess
import com.example.meterkenshin.dlms.DLMSFunctions
import com.example.meterkenshin.dlms.DLMSInit
import com.example.meterkenshin.dlms.DLMSJSONWriter
import com.example.meterkenshin.dlms.DLMSSessionManager
import com.example.meterkenshin.model.Billing
import com.example.meterkenshin.model.Meter
import com.example.meterkenshin.ui.component.createReceiptDataFromBilling
import com.example.meterkenshin.util.calculateBillingData
import com.example.meterkenshin.ui.component.printReceipt as sendReceiptToPrinter

data class RegistrationState(
    val isRunning: Boolean = false,
    val currentStage: Int = 0,
    val message: String = "",
    val isComplete: Boolean = false,
    val error: String? = null
)

@Suppress("ArrayInDataClass")
data class SavedBillingData(
    val billing: Billing,
    val timestamp: Long,
    val rates: FloatArray
) {
    // Check if data is still valid (within 30 days)
    fun isValid(): Boolean {
        val thirtyDaysInMillis = 30L * 24 * 60 * 60 * 1000
        return (System.currentTimeMillis() - timestamp) < thirtyDaysInMillis
    }

    fun daysRemaining(): Int {
        val thirtyDaysInMillis = 30L * 24 * 60 * 60 * 1000
        val elapsed = System.currentTimeMillis() - timestamp
        val remaining = thirtyDaysInMillis - elapsed
        return (remaining / (24 * 60 * 60 * 1000)).toInt()
    }
}

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

    private val _savedBillingData = MutableStateFlow<SavedBillingData?>(null)
    val savedBillingData: StateFlow<SavedBillingData?> = _savedBillingData.asStateFlow()

    private val _pendingBillingData = MutableStateFlow<Billing?>(null)
    val pendingBillingData: StateFlow<Billing?> = _pendingBillingData.asStateFlow()

    private val _showPrintDialog = MutableStateFlow(false)
    val showPrintDialog: StateFlow<Boolean> = _showPrintDialog.asStateFlow()

    private var printerViewModel: PrinterBluetoothViewModel? = null

    private val _showSaveDialog = MutableStateFlow(false)
    val showSaveDialog: StateFlow<Boolean> = _showSaveDialog.asStateFlow()

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
     * Set printer view model reference
     */
    fun setPrinterViewModel(viewModel: PrinterBluetoothViewModel) {
        this.printerViewModel = viewModel
    }

    /**
     * Print receipt with billing data
     * FIXED: Correctly uses sendReceiptToPrinter from ReceiptPrinterComponent
     */
    fun printReceipt(billing: Billing, rates: FloatArray? = null) {
        viewModelScope.launch {
            try {
                val printer = printerViewModel
                if (printer == null) {
                    appendLog("ERROR: Printer not configured")
                    return@launch
                }

                appendLog("Preparing receipt for printing...")

                // Calculate billing if rates provided
                if (rates != null) {
                    calculateBillingData(billing, rates)
                }

                // Create receipt data
                val receiptData = createReceiptDataFromBilling(billing)

                // Send to printer using the renamed import function
                appendLog("Sending receipt to printer...")
                sendReceiptToPrinter(receiptData, printer)

                appendLog("✅ Receipt sent to printer successfully")
            } catch (e: Exception) {
                appendLog("ERROR: Failed to print receipt - ${e.message}")
                Log.e(TAG, "Print receipt error", e)
            }
        }
    }

    /**
     * NEW: Print pending receipt (from read data)
     * Called when user confirms print dialog
     */
    fun printPendingReceipt() {
        val billing = _pendingBillingData.value
        if (billing != null) {
            val savedData = _savedBillingData.value
            val rates = savedData?.rates
            printReceipt(billing, rates)
        } else {
            appendLog("No pending billing data to print")
        }
    }

    /**
     * NEW: Show print dialog after read data completes
     */
    private fun showPrintDialog() {
        _showPrintDialog.value = true
    }

    /**
     * NEW: Hide print dialog
     */
    fun dismissPrintDialog() {
        _showPrintDialog.value = false
    }

    /**
     * NEW: Confirm print - prints receipt and shows save dialog next
     */
    fun confirmPrint() {
        printPendingReceipt()
        dismissPrintDialog()
        // Show save dialog after print
        _showSaveDialog.value = true
    }

    /**
     * NEW: Skip print - goes directly to save dialog
     */
    fun skipPrint() {
        dismissPrintDialog()
        // Show save dialog
        _showSaveDialog.value = true
    }
    /**
     * NEW: Show save dialog
     */
    fun showSaveDialog() {
        _showSaveDialog.value = true
    }

    /**
     * NEW: Dismiss save dialog
     */
    fun dismissSaveDialog() {
        _showSaveDialog.value = false
    }

    /**
     * NEW: Confirm save - saves to JSON and clears pending data
     */
    fun confirmSave() {
        saveReadDataToJSON()
        dismissSaveDialog()
    }

    /**
     * NEW: Skip save - clears pending data without saving
     */
    fun skipSave() {
        clearPendingBillingData()
        dismissSaveDialog()
    }

    /**
     * Initialize and load saved billing data from SharedPreferences
     * Call this in initializeDLMS()
     */
    @SuppressLint("UseKtx")
    private fun loadSavedBillingData() {
        try {
            val prefs = mContext?.getSharedPreferences("BillingDataStorage", Context.MODE_PRIVATE)
            val json = prefs?.getString("saved_billing_data", null)

            if (json != null) {
                val gson = Gson()
                val type = object : TypeToken<SavedBillingData>() {}.type
                val data = gson.fromJson<SavedBillingData>(json, type)

                // Only load if still valid
                if (data.isValid()) {
                    _savedBillingData.value = data
                    appendLog("Loaded billing data (${data.daysRemaining()} days remaining)")
                } else {
                    // Clear expired data
                    prefs.edit().remove("saved_billing_data").apply()
                    appendLog("Cleared expired billing data")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading saved billing data: ${e.message}")
        }
    }

    /**
     * Save billing data to SharedPreferences with timestamp
     */
    @SuppressLint("UseKtx")
    private fun persistBillingData(billing: Billing, rates: FloatArray) {
        try {
            val savedData = SavedBillingData(
                billing = billing,
                timestamp = System.currentTimeMillis(),
                rates = rates
            )

            val gson = Gson()
            val json = gson.toJson(savedData)

            val prefs = mContext?.getSharedPreferences("BillingDataStorage", Context.MODE_PRIVATE)
            prefs?.edit()?.putString("saved_billing_data", json)?.apply()

            _savedBillingData.value = savedData
            appendLog("Billing data saved for 30 days")
        } catch (e: Exception) {
            Log.e(TAG, "Error persisting billing data: ${e.message}")
        }
    }

    /**
     * Clear saved billing data manually
     */
    @SuppressLint("UseKtx")
    fun clearSavedBillingData() {
        val prefs = mContext?.getSharedPreferences("BillingDataStorage", Context.MODE_PRIVATE)
        prefs?.edit()?.remove("saved_billing_data")?.apply()
        _savedBillingData.value = null
        appendLog("Cleared saved billing data")
    }

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
     * Exports JSON with single billing period
     */
    fun readData(meter: Meter, rates: FloatArray) = viewModelScope.launch {
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

                if (previousReading != null) {
                    // Create Billing object with current and previous data
                    val billing = Billing().apply {
                        Period = DLMSJSONWriter.dateTimeToMonth(billingData[1])
                        Commercial = "LARGE"
                        SerialNumber = meter.serialNumber
                        Multiplier = 1.0f
                        PeriodFrom = previousReading.PeriodTo
                        PeriodTo = currentRecord.clock
                        PrevReading = previousReading.PresReading
                        PresReading = currentRecord.imp
                        MaxDemand = currentRecord.maxImp / 1000f
                        DueDate = DLMSJSONWriter.formattedMonthDay(1, 0)
                        DiscoDate = DLMSJSONWriter.formattedMonthDay(1, 1)
                        Discount = 10.0f
                        Interest = 10.0f
                        Reader = "Fuji Taro"
                        ReadDatetime = DLMSJSONWriter.getNowDate()
                        Version = "v1.00.2"
                    }

                    // Calculate charges
                    calculateBillingData(billing, rates)

                    appendLog("Total Use: %.3f kWh".format(billing.TotalUse))
                    appendLog("Total Amount: %.2f".format(billing.TotalAmount))

                    // Save billing data for 30 days (replaces pending data approach)
                    persistBillingData(billing, rates)

                    // Also set as pending to show immediate dialog
                    _pendingBillingData.value = billing

                    // Save current as previous for next time
                    savePreviousReading(meter.serialNumber, billing)

                    appendLog("✅ Read Data Complete - Data saved for 30 days")

                    // NEW: Show print dialog first
                    showPrintDialog()
                } else {
                    appendLog("No previous reading available - saving current as baseline")
                    val baseline = Billing().apply {
                        PeriodTo = currentRecord.clock
                        PresReading = currentRecord.imp
                    }
                    savePreviousReading(meter.serialNumber, baseline)
                    appendLog("✅ Read Data Complete - Baseline saved")
                }
            } else {
                appendLog("ERROR: Insufficient billing data received")
            }

        } catch (e: Exception) {
            appendLog("ERROR: ${e.message}")
            Log.e(TAG, "Read Data error", e)
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
        val savedData = _savedBillingData.value
        if (savedData != null && savedData.isValid()) {
            viewModelScope.launch {
                appendLog("Saving stored billing data to JSON...")
                val success = DLMSJSONWriter.saveSingleBillingToJSON(
                    context = mContext,
                    serialNumber = savedData.billing.SerialNumber,
                    billing = savedData.billing
                )

                if (success) {
                    appendLog("✅ Success to save billing data to JSON")
                    appendLog("Data was ${30 - savedData.daysRemaining()} days old")
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
    fun saveReadDataToJSON() {
        val billing = _pendingBillingData.value
        if (billing != null) {
            viewModelScope.launch {
                appendLog("Saving billing data to JSON...")
                val success = DLMSJSONWriter.saveSingleBillingToJSON(
                    context = mContext,
                    serialNumber = billing.SerialNumber,
                    billing = billing
                )

                if (success) {
                    appendLog("✅ Success to save billing data to JSON")
                } else {
                    appendLog("❌ Failed to save billing data to JSON")
                }

                // Clear pending data after save
                _pendingBillingData.value = null
            }
        } else {
            appendLog("No pending billing data to save")
        }
    }

    /**
     * Clear pending billing data without saving (for dialog dismiss)
     */
    fun clearPendingBillingData() {
        _pendingBillingData.value = null
    }

    /**
     * Load previous reading from SharedPreferences
     */
    private fun loadPreviousReading(serialNumber: String?): Billing? {
        val prefs = mContext?.getSharedPreferences("MeterReadings", Context.MODE_PRIVATE)
        val json = prefs?.getString("prev_${serialNumber}", null) ?: return null

        return try {
            Gson().fromJson(json, Billing::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading previous reading: ${e.message}")
            null
        }
    }

    /**
     * Save current reading as previous for next comparison
     */
    @SuppressLint("UseKtx")
    private fun savePreviousReading(serialNumber: String?, billing: Billing) {
        val prefs = mContext?.getSharedPreferences("MeterReadings", Context.MODE_PRIVATE)
        val json = Gson().toJson(billing)
        prefs?.edit()?.putString("prev_${serialNumber}", json)?.apply()
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
                } else {
                    appendLog("ERROR: Failed to save load profile to CSV")
                }
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
                    } else {
                        appendLog("ERROR: Failed to save event log to CSV")
                    }
                }
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