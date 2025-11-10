package com.example.meterkenshin.ui.viewmodel

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.meterkenshin.data.BillingDataRepository
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
import com.example.meterkenshin.ui.manager.AppPreferences
import com.example.meterkenshin.model.Billing
import com.example.meterkenshin.model.BillingRecord
import com.example.meterkenshin.model.Meter
import com.example.meterkenshin.ui.manager.NotificationManager
import com.example.meterkenshin.utils.calculateBillingData
import com.example.meterkenshin.utils.getCurrentYearMonth
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

    private val _dlmsLog = MutableStateFlow("")
    val dlmsLog: StateFlow<String> = _dlmsLog.asStateFlow()

    private val _savedBillingData = MutableStateFlow<SavedBillingData?>(null)
    val savedBillingData: StateFlow<SavedBillingData?> = _savedBillingData.asStateFlow()

    private var billingRepository: BillingDataRepository? = null

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

    private val _currentMeter = MutableStateFlow<Meter?>(null)
    val currentMeter: StateFlow<Meter?> = _currentMeter.asStateFlow()

    private var meterReadingViewModel: MeterReadingViewModel? = null

    // Add near other StateFlows
    private val _showReadDataOptionsDialog = MutableStateFlow(false)
    val showReadDataOptionsDialog: StateFlow<Boolean> = _showReadDataOptionsDialog.asStateFlow()

    fun setPrinterViewModel(viewModel: PrinterBluetoothViewModel) {
        readDataPrinting.setPrinterViewModel(viewModel)
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
        billingRepository = BillingDataRepository(context)
        readDataPrinting.setContext(context)

        readDataPrinting.setOnPrintSuccessCallback { serialNumber ->
            updateMeterBillingPrintDate(serialNumber)
        }

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
        val data = billingRepository?.loadBillingData()
        if (data != null) {
            _savedBillingData.value = data
            appendLog("Loaded billing data (${data.daysRemaining()} days remaining)")
        }
    }

    /**
     * Save billing data to SharedPreferences with timestamp
     */
    private fun persistBillingData(billing: Billing, rates: FloatArray) {
        val savedData = billingRepository?.saveBillingData(billing, rates)
        _savedBillingData.value = savedData
        appendLog("Billing data saved for 30 days")
    }

    /**
     * Clear saved billing data manually and reset meter to Not Inspected
     */
    fun clearSavedBillingData() {
        billingRepository?.clear()
        _savedBillingData.value = null

        // Clear meter readDate to return to Not Inspected state
        meter?.let { currentMeter ->
            clearMeterReadDate(currentMeter.serialNumber)
        }

        appendLog("Cleared saved billing data")
    }

    /**
     * Clear meter's readDate and billingPrintDate to reset to Not Inspected state
     */
    private fun clearMeterReadDate(serialNumber: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val externalFilesDir = mContext?.getExternalFilesDir(null) ?: return@launch
                val csvDir = File(externalFilesDir, "app_files")
                val yearMonth = getCurrentYearMonth()
                val filename = "${yearMonth}_meter.csv"
                val meterFile = File(csvDir, filename)

                if (!meterFile.exists()) {
                    appendLog("⚠ Warning: Meter CSV file not found")
                    return@launch
                }

                val lines = meterFile.readLines().toMutableList()
                val serialNoIndex = 2
                val readDateIndex = 11
                val billingPrintDateIndex = 12

                for (i in 1 until lines.size) {
                    val columns = lines[i].split(',').toMutableList()
                    val csvSerialNo = columns.getOrNull(serialNoIndex)
                        ?.trim()
                        ?.removeSurrounding("\"")

                    if (csvSerialNo == serialNumber) {
                        // Clear readDate and billingPrintDate
                        if (columns.size > readDateIndex) {
                            columns[readDateIndex] = columns[4] // Set readDate = fixedDate
                        }
                        if (columns.size > billingPrintDateIndex) {
                            columns[billingPrintDateIndex] = "" // Clear billingPrintDate
                        }

                        lines[i] = columns.joinToString(",")
                        appendLog("✅ Reset meter Read Data: $serialNumber")
                        break
                    }
                }

                meterFile.writeText(lines.joinToString("\n"))

                // Reload meters to update UI
                withContext(Dispatchers.Main) {
                    mContext?.let { ctx ->
                        meterReadingViewModel?.reloadMeters(ctx)
                    }
                }

            } catch (e: Exception) {
                appendLog("ERROR: Failed to reset meter state: ${e.message}")
                Log.e(TAG, "clearMeterReadDate error", e)
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
                    Period = DLMSJSONWriter.dateTimeToMonth(billingData[1])
                    Commercial = "LARGE"
                    SerialNumber = meter.serialNumber
                    Multiplier = 1.0f
                    PeriodFrom = effectivePeriodFrom
                    PeriodTo = currentRecord.clock
                    PrevReading = effectivePrevReading?.toString()?.toFloatOrNull() ?: 0f
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

                // Save current as previous for next time
                savePreviousReading(meter.serialNumber, billing)

                appendLog("✅ Read Data Complete - Data saved for 30 days")
                NotificationManager.showSuccess("Read Data Complete - Data saved for 30 days")

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
            try {
                val externalFilesDir = mContext?.getExternalFilesDir(null) ?: return@launch
                val csvDir = File(externalFilesDir, "app_files")
                val yearMonth = getCurrentYearMonth()
                val filename = "${yearMonth}_meter.csv"
                val meterFile = File(csvDir, filename)

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

                    // ✅ FIX: Use CURRENT meter values from CSV, not from billing
                    // Just update the billingPrintDate, keep all other values the same
                    dlmsFunctions.exportMeterDataWithBillingDate(
                        uid = meterRow[0].trim().removeSurrounding("\""),
                        activate = meterRow[1].trim().removeSurrounding("\""),
                        serialNo = meterRow[2].trim().removeSurrounding("\""),
                        bluetoothId = meterRow[3].trim().removeSurrounding("\""),
                        fixedDate = meterRow[4].trim().removeSurrounding("\""),
                        imp = meterRow[5].trim().removeSurrounding("\""),
                        exp = meterRow[6].trim().removeSurrounding("\""),
                        impMaxDemand = meterRow[7].trim().removeSurrounding("\""),
                        expMaxDemand = meterRow[8].trim().removeSurrounding("\""),
                        minVolt = meterRow[9].trim().removeSurrounding("\""),
                        alert = meterRow[10].trim().removeSurrounding("\""),
                        readDate = meterRow[11].trim().removeSurrounding("\""),
                        billingPrintDate = billingPrintDate  // Only this is new
                    )

                    appendLog("✅ Billing print date saved to meter CSV: $billingPrintDate")

                    // Reload meters to update UI
                    withContext(Dispatchers.Main) {
                        mContext?.let { ctx ->
                            meterReadingViewModel?.reloadMeters(ctx)
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
            val externalFilesDir = mContext?.getExternalFilesDir(null) ?: return null
            val csvDir = File(externalFilesDir, "app_files")
            val yearMonth = getCurrentYearMonth()
            val filename = "${yearMonth}_meter.csv"
            val meterFile = File(csvDir, filename)

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