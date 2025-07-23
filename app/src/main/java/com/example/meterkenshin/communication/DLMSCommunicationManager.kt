package com.example.meterkenshin.communication

import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.meterkenshin.DLMS.DLMS
import com.example.meterkenshin.R
import com.example.meterkenshin.ui.screen.Meter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.util.*
import kotlin.random.Random

/**
 * Enhanced DLMS Communication Manager with Automatic Meter Reading
 * Based on project01's automatic scanning and reading functionality
 */
class DLMSCommunicationManager(
    private val context: Context,
    private val bluetoothAdapter: BluetoothAdapter?
) : ViewModel() {

    companion object {
        private const val TAG = "DLMSCommManager"
        private const val APP_FILES_FOLDER = "app_files"
        private const val METER_CSV_FILENAME = "meter.csv"

        // Timer constants based on project01
        private const val SCAN_TICK = 2000L  // 2 seconds scan interval
        private const val TICK = 200L        // 200ms tick interval
        private const val AUTO_READ_INTERVAL = 30000L // 30 seconds between meter readings
        private const val CONNECTION_TIMEOUT = 60000L // 60 seconds connection timeout
    }

    // DLMS instance from existing Java implementation
    private var dlmsInstance: DLMS? = null

    // Parsed meter table from CSV
    private val _meterTable = MutableStateFlow<List<Meter>>(emptyList())
    val meterTable: StateFlow<List<Meter>> = _meterTable.asStateFlow()

    // Communication state
    private val _communicationState = MutableStateFlow(DLMSCommunicationState())
    val communicationState: StateFlow<DLMSCommunicationState> = _communicationState.asStateFlow()

    // Session state
    private val _sessionState = MutableStateFlow(DLMSSessionState())
    val sessionState: StateFlow<DLMSSessionState> = _sessionState.asStateFlow()

    // DLMS operation results
    private val _operationResults = MutableStateFlow<List<String>>(emptyList())
    val operationResults: StateFlow<List<String>> = _operationResults.asStateFlow()

    // Meter data updates from DLMS readings
    private val _meterDataUpdates = MutableStateFlow<DLMSMeterData?>(null)
    val meterDataUpdates: StateFlow<DLMSMeterData?> = _meterDataUpdates.asStateFlow()

    // Automatic reading state
    private val _autoReadingState = MutableStateFlow(AutoReadingState())
    val autoReadingState: StateFlow<AutoReadingState> = _autoReadingState.asStateFlow()

    // Batch operation progress
    private val _batchProgress = MutableStateFlow(BatchOperationProgress())
    val batchProgress: StateFlow<BatchOperationProgress> = _batchProgress.asStateFlow()

    // Timer jobs for automatic reading
    private var scanJob: Job? = null
    private var autoReadJob: Job? = null
    private var connectionTimeoutJob: Job? = null

    // Current reading queue
    private val pendingMeterQueue = mutableListOf<Meter>()
    private var currentMeterIndex = 0
    private var isAutoReadingActive = false

    init {
        initializeDLMS()
    }

    /**
     * Initialize DLMS instance
     */
    private fun initializeDLMS() {
        try {
            dlmsInstance = DLMS(context)
            updateCommunicationState {
                it.copy(isInitialized = true)
            }
            Log.d(TAG, "DLMS instance initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize DLMS", e)
            updateCommunicationState {
                it.copy(
                    isInitialized = false,
                    lastError = context.getString(R.string.dlms_error) + ": ${e.message}"
                )
            }
        }
    }

    /**
     * Parse meter.csv file and start automatic reading if enabled
     */
    fun parseMeterCsvFile(fileName: String = METER_CSV_FILENAME): ParseResult {
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    parseMeterCsvFromFile(fileName)
                }

                when (result) {
                    is ParseResult.Success -> {
                        _meterTable.value = result.meters
                        Log.i(TAG, "Successfully parsed ${result.meters.size} meters from CSV")

                        // Initialize the meter queue with activated meters
                        initializeMeterQueue()

                        // Start automatic reading if there are active meters
                        val activeMeters = result.meters.filter { it.activate == 1 }
                        if (activeMeters.isNotEmpty()) {
                            Log.i(TAG, "Found ${activeMeters.size} active meters, starting automatic reading")
                            startAutomaticReading()
                        }
                    }
                    is ParseResult.Error -> {
                        Log.e(TAG, "Failed to parse meter CSV: ${result.message}")
                        updateCommunicationState {
                            it.copy(lastError = result.message)
                        }
                    }
                }
            } catch (e: Exception) {
                val errorMsg = context.getString(R.string.csv_parsing_error) + ": ${e.message}"
                Log.e(TAG, errorMsg, e)
            }
        }

        return ParseResult.Success(emptyList()) // Placeholder for immediate return
    }

    /**
     * Initialize the meter reading queue with activated meters
     */
    private fun initializeMeterQueue() {
        val activeMeters = _meterTable.value.filter { it.activate == 1 }
        pendingMeterQueue.clear()
        pendingMeterQueue.addAll(activeMeters)
        currentMeterIndex = 0

        updateAutoReadingState {
            it.copy(
                totalMeters = activeMeters.size,
                queuedMeters = activeMeters.size,
                currentMeterIndex = 0
            )
        }

        Log.d(TAG, "Initialized meter queue with ${activeMeters.size} active meters")
    }

    /**
     * Start automatic meter reading process (based on project01 methodology)
     */
    fun startAutomaticReading() {
        if (isAutoReadingActive) {
            Log.d(TAG, "Automatic reading already active")
            return
        }

        val activeMeters = _meterTable.value.filter { it.activate == 1 }
        if (activeMeters.isEmpty()) {
            Log.w(TAG, "No active meters found for automatic reading")
            return
        }

        isAutoReadingActive = true
        updateAutoReadingState {
            it.copy(
                isActive = true,
                startTime = System.currentTimeMillis(),
                totalMeters = activeMeters.size,
                completedReadings = 0
            )
        }

        Log.i(TAG, "Starting automatic reading for ${activeMeters.size} meters")

        // Start the automatic reading loop
        autoReadJob = viewModelScope.launch {
            try {
                while (isAutoReadingActive && pendingMeterQueue.isNotEmpty()) {
                    val meter = getNextMeterFromQueue()
                    if (meter != null) {
                        performAutomaticMeterReading(meter)
                        delay(AUTO_READ_INTERVAL) // Wait between meter readings
                    } else {
                        // All meters read, restart the cycle
                        initializeMeterQueue()
                        delay(SCAN_TICK) // Scan interval before restarting
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in automatic reading loop", e)
                stopAutomaticReading()
            }
        }
    }

    /**
     * Stop automatic meter reading
     */
    fun stopAutomaticReading() {
        isAutoReadingActive = false
        autoReadJob?.cancel()
        scanJob?.cancel()
        connectionTimeoutJob?.cancel()

        updateAutoReadingState {
            it.copy(
                isActive = false,
                endTime = System.currentTimeMillis()
            )
        }

        // Disconnect if currently connected
        if (_communicationState.value.isConnected) {
            disconnect()
        }

        Log.i(TAG, "Stopped automatic meter reading")
    }

    /**
     * Get next meter from the reading queue
     */
    private fun getNextMeterFromQueue(): Meter? {
        return if (currentMeterIndex < pendingMeterQueue.size) {
            val meter = pendingMeterQueue[currentMeterIndex]
            currentMeterIndex++

            updateAutoReadingState { state ->
                state.copy(
                    currentMeterIndex = currentMeterIndex,
                    queuedMeters = pendingMeterQueue.size - currentMeterIndex,
                    currentMeterUid = meter.uid
                )
            }

            meter
        } else {
            null
        }
    }

    /**
     * Perform automatic reading of a specific meter (based on project01 MSG_READER)
     */
    private suspend fun performAutomaticMeterReading(meter: Meter) {
        Log.d(TAG, "Starting automatic reading for meter: ${meter.uid}")

        updateAutoReadingState { state ->
            state.copy(
                currentMeterUid = meter.uid,
                lastOperation = "Connecting to ${meter.uid}"
            )
        }

        try {
            // Step 1: Connect to meter
            val connected = connectToMeterWithTimeout(meter)
            if (!connected) {
                Log.w(TAG, "Failed to connect to meter ${meter.uid}")
                recordFailedReading(meter, "Connection failed")
                return
            }

            // Step 2: Authenticate and establish session
            val authenticated = authenticateWithMeter(meter)
            if (!authenticated) {
                Log.w(TAG, "Failed to authenticate with meter ${meter.uid}")
                recordFailedReading(meter, "Authentication failed")
                disconnect()
                return
            }

            // Step 3: Read meter data (following project01 MSG_READER pattern)
            val readingSuccess = performMeterDataReading(meter)
            if (readingSuccess) {
                recordSuccessfulReading(meter)
                Log.i(TAG, "Successfully read data from meter ${meter.uid}")
            } else {
                recordFailedReading(meter, "Data reading failed")
                Log.w(TAG, "Failed to read data from meter ${meter.uid}")
            }

            // Step 4: Disconnect
            disconnect()
            delay(500) // Small delay between operations

        } catch (e: Exception) {
            Log.e(TAG, "Error during automatic reading of meter ${meter.uid}", e)
            recordFailedReading(meter, e.message ?: "Unknown error")
            disconnect()
        }
    }

    /**
     * Connect to meter with timeout (based on project01 Connection method)
     */
    private suspend fun connectToMeterWithTimeout(meter: Meter): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                var connected = false

                // Start connection timeout
                connectionTimeoutJob = viewModelScope.launch {
                    delay(CONNECTION_TIMEOUT)
                    if (!connected) {
                        Log.w(TAG, "Connection timeout for meter ${meter.uid}")
                    }
                }

                // Simulate connection process (replace with actual DLMS connection)
                updateCommunicationState {
                    it.copy(
                        isConnecting = true,
                        connectedMeter = meter
                    )
                }

                // Simulate connection delay and process
                delay(1000 + kotlin.random.Random.nextInt(2000)) // 1-3 seconds connection time
                // For simulation, assume 80% success rate
                connected = Random.nextFloat() < 0.8f

                if (connected) {
                    updateCommunicationState {
                        it.copy(
                            isConnected = true,
                            isConnecting = false,
                            connectionTime = System.currentTimeMillis()
                        )
                    }

                    // Emit connection success
                    _meterDataUpdates.value = DLMSMeterData(
                        meterUid = meter.uid,
                        isConnected = true,
                        rssi = -45 - Random.nextInt(30),
                        readingDate = Date()
                    )
                } else {
                    updateCommunicationState {
                        it.copy(
                            isConnected = false,
                            isConnecting = false,
                            lastError = "Connection failed to ${meter.uid}"
                        )
                    }
                }

                connectionTimeoutJob?.cancel()
                connected

            } catch (e: Exception) {
                Log.e(TAG, "Connection error for meter ${meter.uid}", e)
                false
            }
        }
    }

    /**
     * Authenticate with meter (based on project01 authentication process)
     */
    private suspend fun authenticateWithMeter(meter: Meter): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                updateAutoReadingState { state ->
                    state.copy(lastOperation = "Authenticating with ${meter.uid}")
                }

                // Simulate authentication process
                delay(500)

                updateSessionState {
                    it.copy(
                        isSessionEstablished = true,
                        meterAccount = meter.uid,
                        meterLogical = meter.serialNo,
                        authenticationLevel = "Level ${meter.activate}"
                    )
                }

                // Simulate authentication success (90% rate for connected meters)
                Random.nextFloat() < 0.9f

            } catch (e: Exception) {
                Log.e(TAG, "Authentication error for meter ${meter.uid}", e)
                false
            }
        }
    }

    /**
     * Perform actual meter data reading (based on project01 MSG_READER stages)
     */
    private suspend fun performMeterDataReading(meter: Meter): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                updateAutoReadingState { state ->
                    state.copy(lastOperation = "Reading data from ${meter.uid}")
                }

                // Stage 1: Read instantaneous values (like project01 mSubStage 1)
                delay(300)
                val instantaneousData = readInstantaneousValues(meter)

                // Stage 2: Read load profile data (like project01 mSubStage 3)
                delay(300)
                val profileData = readLoadProfileData(meter)

                // Stage 3: Read billing data (like project01 mSubStage 5)
                delay(300)
                val billingData = readBillingData(meter)

                // Combine all readings and update meter data
                if (instantaneousData != null || profileData != null || billingData != null) {
                    val meterData = DLMSMeterData(
                        meterUid = meter.uid,
                        isConnected = true,
                        rssi = -45 - Random.nextInt(30),
                        impKwh = instantaneousData?.get("impKwh") ?: (meter.impKwh ?: 0f) + Random.nextFloat() * 10f,
                        expKwh = instantaneousData?.get("expKwh") ?: (meter.expKwh ?: 0f) + Random.nextFloat() * 5f,
                        maxDemand = instantaneousData?.get("maxDemand") ?: Random.nextFloat() * 50f,
                        voltage = instantaneousData?.get("voltage") ?: 220f + Random.nextFloat() * 20f,
                        current = instantaneousData?.get("current") ?: Random.nextFloat() * 100f,
                        powerFactor = instantaneousData?.get("powerFactor") ?: 0.85f + Random.nextFloat() * 0.15f,
                        frequency = instantaneousData?.get("frequency") ?: 50f + Random.nextFloat() * 2f,
                        temperature = instantaneousData?.get("temperature") ?: 25f + Random.nextFloat() * 15f,
                        batteryVoltage = instantaneousData?.get("batteryVoltage") ?: 3.6f + Random.nextFloat() * 0.8f,
                        readingDate = Date()
                    )

                    _meterDataUpdates.value = meterData
                    return@withContext true
                }

                false

            } catch (e: Exception) {
                Log.e(TAG, "Data reading error for meter ${meter.uid}", e)
                false
            }
        }
    }

    /**
     * Read instantaneous values from meter (PRIVATE - used internally)
     */
    private suspend fun readInstantaneousValues(meter: Meter): Map<String, Float>? {
        return try {
            delay(200) // Simulate DLMS communication time

            // Simulate reading instantaneous values
            mapOf(
                "impKwh" to ((meter.impKwh ?: 1000f) + Random.nextFloat() * 10f),
                "expKwh" to ((meter.expKwh ?: 100f) + Random.nextFloat() * 5f),
                "maxDemand" to (Random.nextFloat() * 50f),
                "voltage" to (220f + Random.nextFloat() * 20f),
                "current" to (Random.nextFloat() * 100f),
                "powerFactor" to (0.85f + Random.nextFloat() * 0.15f),
                "frequency" to (50f + Random.nextFloat() * 2f),
                "temperature" to (25f + Random.nextFloat() * 15f),
                "batteryVoltage" to (3.6f + Random.nextFloat() * 0.8f)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error reading instantaneous values", e)
            null
        }
    }

    /**
     * Read load profile data from meter
     */
    private suspend fun readLoadProfileData(meter: Meter): Map<String, Any>? {
        return try {
            delay(300) // Simulate DLMS communication time

            // Simulate load profile data
            mapOf(
                "profileDate" to Date(),
                "intervalData" to "Load profile data for ${meter.uid}",
                "status" to "Normal"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error reading load profile data", e)
            null
        }
    }

    /**
     * Read billing data from meter
     */
    private suspend fun readBillingData(meter: Meter): Map<String, Any>? {
        return try {
            delay(200) // Simulate DLMS communication time

            // Simulate billing data
            mapOf(
                "billingDate" to Date(),
                "billingPeriod" to "Monthly",
                "totalConsumption" to ((meter.impKwh ?: 1000f) + Random.nextFloat() * 100f)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error reading billing data", e)
            null
        }
    }

    /**
     * Record successful meter reading
     */
    private fun recordSuccessfulReading(meter: Meter) {
        updateAutoReadingState { state ->
            state.copy(
                completedReadings = state.completedReadings + 1,
                successfulReadings = state.successfulReadings + 1,
                lastSuccessfulReading = meter.uid,
                lastOperation = "Successfully read ${meter.uid}"
            )
        }
    }

    /**
     * Record failed meter reading
     */
    private fun recordFailedReading(meter: Meter, error: String) {
        updateAutoReadingState { state ->
            state.copy(
                completedReadings = state.completedReadings + 1,
                failedReadings = state.failedReadings + 1,
                lastFailedReading = meter.uid,
                lastError = error,
                lastOperation = "Failed to read ${meter.uid}: $error"
            )
        }
    }

    /**
     * Parse meter CSV file from storage
     */
    private suspend fun parseMeterCsvFromFile(fileName: String): ParseResult = withContext(Dispatchers.IO) {
        try {
            val externalFilesDir = context.getExternalFilesDir(null)
            val appFilesDir = File(externalFilesDir, APP_FILES_FOLDER)
            val meterFile = File(appFilesDir, fileName)

            if (!meterFile.exists()) {
                return@withContext ParseResult.Error(
                    "Meter CSV file not found: ${meterFile.absolutePath}"
                )
            }

            Log.d(TAG, "Parsing meter CSV from: ${meterFile.absolutePath}")

            val meters = mutableListOf<Meter>()
            BufferedReader(FileReader(meterFile)).use { reader ->
                var isFirstLine = true
                var lineNumber = 0

                reader.forEachLine { line ->
                    lineNumber++

                    if (isFirstLine) {
                        isFirstLine = false
                        Log.d(TAG, "CSV Header: $line")
                        return@forEachLine
                    }

                    if (line.trim().isEmpty()) {
                        return@forEachLine
                    }

                    try {
                        val meter = parseMeterCsvLine(line, lineNumber)
                        meter?.let { meters.add(it) }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to parse line $lineNumber: $line", e)
                    }
                }
            }

            if (meters.isEmpty()) {
                ParseResult.Error(context.getString(R.string.no_meters_found))
            } else {
                ParseResult.Success(meters)
            }

        } catch (e: Exception) {
            ParseResult.Error("Failed to read meter CSV file: ${e.message}")
        }
    }

    /**
     * Parse individual CSV line into Meter object
     */
    private fun parseMeterCsvLine(line: String, lineNumber: Int): Meter? {
        try {
            val fields = line.split(",").map { it.trim().removeSurrounding("\"") }

            if (fields.size < 4) {
                Log.w(TAG, "Line $lineNumber: insufficient fields (${fields.size})")
                return null
            }

            return Meter(
                uid = fields.getOrNull(0) ?: "",
                activate = fields.getOrNull(1)?.toIntOrNull() ?: 0,
                serialNo = fields.getOrNull(2) ?: "",
                bluetoothId = fields.getOrNull(3) ?: "",
                fixedDate = fields.getOrNull(4)?.takeIf { it.isNotEmpty() },
                impKwh = fields.getOrNull(5)?.takeIf { it.isNotEmpty() }?.toFloatOrNull(),
                expKwh = fields.getOrNull(6)?.takeIf { it.isNotEmpty() }?.toFloatOrNull(),
                impMaxDemand = fields.getOrNull(7)?.takeIf { it.isNotEmpty() }?.toFloatOrNull(),
                expMaxDemand = fields.getOrNull(8)?.takeIf { it.isNotEmpty() }?.toFloatOrNull(),
                minVolt = fields.getOrNull(9)?.takeIf { it.isNotEmpty() }?.toFloatOrNull(),
                alert = fields.getOrNull(10)?.takeIf { it.isNotEmpty() }?.toFloatOrNull(),
                readDate = fields.getOrNull(11)?.takeIf { it.isNotEmpty() }
            )

        } catch (e: Exception) {
            Log.e(TAG, "Error parsing meter line $lineNumber: $line", e)
            return null
        }
    }

    /**
     * Disconnect from current meter
     */
    fun disconnect() {
        viewModelScope.launch {
            try {
                updateCommunicationState { it.copy(isDisconnecting = true) }

                dlmsInstance?.let { dlms ->
                    // Disconnect using existing DLMS implementation
                    delay(300) // Simulate disconnection time
                }

                updateCommunicationState {
                    it.copy(
                        isConnected = false,
                        isDisconnecting = false,
                        connectedMeter = null,
                        connectionTime = null
                    )
                }

                updateSessionState {
                    DLMSSessionState() // Reset session state
                }

                _meterDataUpdates.value = _communicationState.value.connectedMeter?.let { meter ->
                    DLMSMeterData(
                        meterUid = meter.uid,
                        isConnected = false,
                        rssi = -100,
                        readingDate = Date()
                    )
                }

            } catch (e: Exception) {
                Log.e(TAG, "Disconnection failed", e)
                updateCommunicationState {
                    it.copy(
                        isDisconnecting = false,
                        lastError = "Disconnection failed: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * Get meter by UID from parsed table
     */
    fun getMeterByUid(uid: String): Meter? {
        return _meterTable.value.find { it.uid == uid }
    }

    /**
     * Get active meters from parsed table
     */
    fun getActiveMeters(): List<Meter> {
        return _meterTable.value.filter { it.activate == 1 }
    }

    /**
     * Helper function to update communication state
     */
    private fun updateCommunicationState(update: (DLMSCommunicationState) -> DLMSCommunicationState) {
        _communicationState.value = update(_communicationState.value)
    }

    /**
     * Helper function to update session state
     */
    private fun updateSessionState(update: (DLMSSessionState) -> DLMSSessionState) {
        _sessionState.value = update(_sessionState.value)
    }

    /**
     * Helper function to update auto reading state
     */
    private fun updateAutoReadingState(update: (AutoReadingState) -> AutoReadingState) {
        _autoReadingState.value = update(_autoReadingState.value)
    }

    override fun onCleared() {
        super.onCleared()
        // Clean up automatic reading
        stopAutomaticReading()

        // Clean up DLMS resources
        if (_communicationState.value.isConnected) {
            disconnect()
        }
    }
}

/**
 * Parse result for meter CSV parsing
 */
sealed class ParseResult {
    data class Success(val meters: List<Meter>) : ParseResult()
    data class Error(val message: String) : ParseResult()
}

/**
 * DLMS Communication State
 */
data class DLMSCommunicationState(
    val isInitialized: Boolean = false,
    val isConnected: Boolean = false,
    val isConnecting: Boolean = false,
    val isDisconnecting: Boolean = false,
    val isExecutingOperation: Boolean = false,
    val connectedMeter: Meter? = null,
    val connectionTime: Long? = null,
    val lastError: String? = null
)

/**
 * DLMS Session State
 */
data class DLMSSessionState(
    val isSessionEstablished: Boolean = false,
    val meterAccount: String? = null,
    val meterLogical: String? = null,
    val connectionStep: String = "",
    val stepProgress: Float = 0f,
    val authenticationLevel: String? = null
)

/**
 * DLMS Meter Data Update
 */
data class DLMSMeterData(
    val meterUid: String,
    val isConnected: Boolean = false,
    val rssi: Int = -100,
    val impKwh: Float? = null,
    val expKwh: Float? = null,
    val maxDemand: Float? = null,
    val voltage: Float? = null,
    val current: Float? = null,
    val powerFactor: Float? = null,
    val frequency: Float? = null,
    val temperature: Float? = null,
    val batteryVoltage: Float? = null,
    val readingDate: Date = Date()
)

/**
 * Automatic Reading State
 */
data class AutoReadingState(
    val isActive: Boolean = false,
    val startTime: Long? = null,
    val endTime: Long? = null,
    val totalMeters: Int = 0,
    val currentMeterIndex: Int = 0,
    val queuedMeters: Int = 0,
    val completedReadings: Int = 0,
    val successfulReadings: Int = 0,
    val failedReadings: Int = 0,
    val currentMeterUid: String = "",
    val lastSuccessfulReading: String? = null,
    val lastFailedReading: String? = null,
    val lastOperation: String = "",
    val lastError: String? = null
)

/**
 * Batch Operation Progress
 */
data class BatchOperationProgress(
    val isRunning: Boolean = false,
    val isCompleted: Boolean = false,
    val operation: String = "",
    val totalMeters: Int = 0,
    val currentMeter: Int = 0,
    val currentMeterUid: String = "",
    val progress: Float = 0f,
    val errorMessage: String? = null
)