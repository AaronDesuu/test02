package com.example.meterkenshin.ui.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.meterkenshin.communication.DLMSCommunicationManager
import com.example.meterkenshin.communication.DLMSCommunicationState
import com.example.meterkenshin.communication.DLMSMeterData
import com.example.meterkenshin.communication.AutoReadingState
import com.example.meterkenshin.ui.screen.Meter
import com.example.meterkenshin.ui.screen.MeterConnectionStatus
import com.example.meterkenshin.ui.screen.ConnectionQuality
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.text.SimpleDateFormat
import java.util.*

/**
 * Enhanced ViewModel for Meter Reading with DLMS integration and Automatic Reading
 * Integrates with project01-style automatic meter reading functionality
 */
class MeterReadingViewModel : ViewModel() {

    companion object {
        private const val TAG = "MeterReadingViewModel"
        private const val APP_FILES_FOLDER = "app_files"
    }

    // UI State
    private val _uiState = MutableStateFlow(MeterReadingUiState())
    val uiState: StateFlow<MeterReadingUiState> = _uiState.asStateFlow()

    // Search query
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // DLMS Communication Manager
    private var dlmsManager: DLMSCommunicationManager? = null

    // Selected meter for operations
    private val _selectedMeter = MutableStateFlow<Meter?>(null)
    val selectedMeter: StateFlow<Meter?> = _selectedMeter.asStateFlow()

    // Meter connection statuses
    private val _meterStatuses = MutableStateFlow<Map<String, MeterConnectionStatus>>(emptyMap())
    val meterStatuses: StateFlow<Map<String, MeterConnectionStatus>> = _meterStatuses.asStateFlow()

    // Automatic reading state
    private val _autoReadingProgress = MutableStateFlow(AutoReadingProgress())
    val autoReadingProgress: StateFlow<AutoReadingProgress> = _autoReadingProgress.asStateFlow()

    // Date formatter for display
    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    init {
        // Initialize search functionality
        viewModelScope.launch {
            searchQuery.collect { query ->
                filterMeters(query)
            }
        }
    }

    /**
     * Initialize DLMS manager and observe real data updates
     */
    fun initializeDLMS(context: Context, bluetoothAdapter: android.bluetooth.BluetoothAdapter?) {
        dlmsManager = DLMSCommunicationManager(context, bluetoothAdapter)

        // Observe DLMS communication state
        viewModelScope.launch {
            dlmsManager?.communicationState?.collect { state ->
                updateMeterConnectionStatus(state)
            }
        }

        // Observe real meter data updates from DLMS
        viewModelScope.launch {
            dlmsManager?.meterDataUpdates?.collect { dataUpdate ->
                dataUpdate?.let { update ->
                    // Update meter with real DLMS data
                    updateMeterWithDLMSData(
                        uid = update.meterUid,
                        isConnected = update.isConnected,
                        rssi = update.rssi,
                        lastReading = update.impKwh,
                        readingTimestamp = dateFormatter.format(update.readingDate)
                    )

                    // Update meter readings if available
                    if (update.impKwh != null || update.expKwh != null) {
                        updateMeterReadings(
                            uid = update.meterUid,
                            impKwh = update.impKwh,
                            expKwh = update.expKwh,
                            maxDemand = update.maxDemand,
                            voltage = update.voltage,
                            readingDate = dateFormatter.format(update.readingDate)
                        )
                    }

                    Log.d(TAG, "Updated meter ${update.meterUid} with real DLMS data: RSSI=${update.rssi}, Connected=${update.isConnected}")
                }
            }
        }

        // Observe automatic reading state
        viewModelScope.launch {
            dlmsManager?.autoReadingState?.collect { autoState ->
                updateAutoReadingProgress(autoState)
            }
        }

        Log.i(TAG, "DLMS Communication Manager initialized")
    }

    /**
     * Load meter data from CSV file (keeping original method name for compatibility)
     */
    fun loadMeters(context: Context, fileName: String) {
        loadMeterData(context, fileName)
    }

    /**
     * Load meter data from CSV file and start automatic reading
     */
    fun loadMeterData(context: Context, fileName: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

            try {
                // First parse the CSV using the DLMS manager (this will start automatic reading)
                dlmsManager?.parseMeterCsvFile(fileName)

                // Then load using the existing method for UI state
                val result = loadMeterDataFromFile(context, fileName)

                when (result) {
                    is MeterLoadResult.Success -> {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            allMeters = result.meters,
                            filteredMeters = filterMetersByQuery(result.meters, _searchQuery.value),
                            errorMessage = null
                        )

                        // Initialize meter statuses
                        initializeMeterStatuses(result.meters)

                        Log.i(TAG, "Successfully loaded ${result.meters.size} meters")

                        // Log active meters for automatic reading
                        val activeMeters = result.meters.filter { it.activate == 1 }
                        Log.i(TAG, "Found ${activeMeters.size} active meters for automatic reading")

                        if (activeMeters.isNotEmpty()) {
                            Log.i(TAG, "Automatic meter reading will start shortly...")
                        }
                    }

                    is MeterLoadResult.Error -> {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            allMeters = emptyList(),
                            filteredMeters = emptyList(),
                            errorMessage = result.message
                        )
                        Log.e(TAG, "Failed to load meters: ${result.message}")
                    }
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Unexpected error: ${e.message}"
                )
                Log.e(TAG, "Unexpected error loading meters", e)
            }
        }
    }

    /**
     * Update search query and filter meters
     */
    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    /**
     * Manually connect to a specific meter using DLMS
     */
    fun connectToMeter(meter: Meter, bluetoothAddress: String? = null) {
        viewModelScope.launch {
            _selectedMeter.value = meter

            // Update meter status to connecting
            updateMeterStatus(meter.uid) { status ->
                status.copy(
                    isConnected = false,
                    rssi = -100,
                    connectionQuality = ConnectionQuality.POOR
                )
            }

            // Use DLMS manager to connect
            dlmsManager?.connectToMeter(
                meter = meter,
                bluetoothAddress = bluetoothAddress ?: meter.bluetoothId,
                onSuccess = {
                    Log.i(TAG, "Successfully connected to meter ${meter.uid}")
                    updateMeterStatus(meter.uid) { status ->
                        status.copy(
                            isConnected = true,
                            dlmsSession = true
                        )
                    }
                },
                onError = { error ->
                    Log.e(TAG, "Failed to connect to meter ${meter.uid}: $error")
                    updateMeterStatus(meter.uid) { status ->
                        status.copy(
                            isConnected = false,
                            dlmsSession = false
                        )
                    }
                }
            )
        }
    }

    /**
     * Disconnect from current meter
     */
    fun disconnectFromMeter() {
        viewModelScope.launch {
            dlmsManager?.disconnect()
            _selectedMeter.value = null
        }
    }

    /**
     * Start automatic reading of all active meters
     */
    fun startAutomaticReading() {
        dlmsManager?.startAutomaticReading()
        Log.i(TAG, "Started automatic meter reading")

        _autoReadingProgress.value = _autoReadingProgress.value.copy(
            isManuallyStarted = true
        )
    }

    /**
     * Stop automatic reading
     */
    fun stopAutomaticReading() {
        dlmsManager?.stopAutomaticReading()
        Log.i(TAG, "Stopped automatic meter reading")

        _autoReadingProgress.value = _autoReadingProgress.value.copy(
            isManuallyStarted = false
        )
    }

    /**
     * Check if automatic reading is currently active
     */
    fun isAutomaticReadingActive(): Boolean {
        return _autoReadingProgress.value.isActive
    }

    /**
     * Get automatic reading statistics
     */
    fun getAutomaticReadingStats(): AutoReadingStats {
        val progress = _autoReadingProgress.value
        return AutoReadingStats(
            totalMeters = progress.totalMeters,
            completedReadings = progress.completedReadings,
            successfulReadings = progress.successfulReadings,
            failedReadings = progress.failedReadings,
            currentMeter = progress.currentMeterUid,
            isActive = progress.isActive
        )
    }

    /**
     * Execute DLMS read instantaneous data
     */
    fun readInstantaneousData(meter: Meter? = null) {
        val targetMeter = meter ?: _selectedMeter.value
        targetMeter?.let {
            dlmsManager?.readInstantaneousValues(it)
        }
    }

    /**
     * Execute DLMS read load profile
     */
    fun readLoadProfile(meter: Meter? = null) {
        val targetMeter = meter ?: _selectedMeter.value
        targetMeter?.let {
            dlmsManager?.readLoadProfile(it)
        }
    }

    /**
     * Execute DLMS read billing data
     */
    fun readBillingData(meter: Meter? = null) {
        val targetMeter = meter ?: _selectedMeter.value
        targetMeter?.let {
            dlmsManager?.readBillingData(it)
        }
    }

    /**
     * Execute DLMS set clock
     */
    fun setMeterClock(meter: Meter? = null) {
        val targetMeter = meter ?: _selectedMeter.value
        targetMeter?.let {
            dlmsManager?.setMeterClock(it)
        }
    }

    /**
     * Read all active meters manually (one-time batch operation)
     */
    fun readAllMeters() {
        dlmsManager?.readAllMeters()
    }

    /**
     * Get meters by connection status
     */
    fun getMetersByConnectionStatus(connected: Boolean): List<Meter> {
        val meters = _uiState.value.allMeters
        val statuses = _meterStatuses.value

        return meters.filter { meter ->
            statuses[meter.uid]?.isConnected == connected
        }
    }

    /**
     * Get active meters (activate = 1)
     */
    fun getActiveMeters(): List<Meter> {
        return _uiState.value.allMeters.filter { it.activate == 1 }
    }

    /**
     * Get connected meters
     */
    fun getConnectedMeters(): List<Meter> {
        return getMetersByConnectionStatus(true)
    }

    /**
     * Filter meters based on search query
     */
    private fun filterMeters(query: String) {
        val filteredMeters = filterMetersByQuery(_uiState.value.allMeters, query)
        _uiState.value = _uiState.value.copy(filteredMeters = filteredMeters)
    }

    /**
     * Filter meters by query string (updated for new Meter structure)
     */
    private fun filterMetersByQuery(meters: List<Meter>, query: String): List<Meter> {
        if (query.isBlank()) return meters

        val lowerQuery = query.lowercase().trim()
        return meters.filter { meter ->
            meter.uid.lowercase().contains(lowerQuery) ||
                    meter.serialNo.lowercase().contains(lowerQuery) ||
                    meter.bluetoothId.lowercase().contains(lowerQuery) ||
                    meter.activate.toString().contains(lowerQuery)
        }
    }

    /**
     * Update automatic reading progress in UI state
     */
    private fun updateAutoReadingProgress(autoState: AutoReadingState) {
        _autoReadingProgress.value = _autoReadingProgress.value.copy(
            isActive = autoState.isActive,
            totalMeters = autoState.totalMeters,
            currentMeterIndex = autoState.currentMeterIndex,
            completedReadings = autoState.completedReadings,
            successfulReadings = autoState.successfulReadings,
            failedReadings = autoState.failedReadings,
            currentMeterUid = autoState.currentMeterUid,
            lastOperation = autoState.lastOperation,
            lastError = autoState.lastError,
            startTime = autoState.startTime,
            endTime = autoState.endTime
        )

        // Log progress updates
        if (autoState.isActive) {
            Log.d(TAG, "Auto reading progress: ${autoState.completedReadings}/${autoState.totalMeters} " +
                    "(Success: ${autoState.successfulReadings}, Failed: ${autoState.failedReadings})")

            if (autoState.currentMeterUid.isNotEmpty()) {
                Log.d(TAG, "Currently reading: ${autoState.currentMeterUid}")
            }
        }
    }

    /**
     * Load meter data from CSV file based on actual meter.csv structure
     */
    private suspend fun loadMeterDataFromFile(
        context: Context,
        fileName: String
    ): MeterLoadResult = withContext(Dispatchers.IO) {
        try {
            val externalFilesDir = context.getExternalFilesDir(null)
            val appFilesDir = File(externalFilesDir, APP_FILES_FOLDER)
            val meterFile = File(appFilesDir, fileName)

            if (!meterFile.exists()) {
                return@withContext MeterLoadResult.Error("Meter file not found: ${meterFile.absolutePath}")
            }

            Log.d(TAG, "Loading meter data from: ${meterFile.absolutePath}")

            val meters = mutableListOf<Meter>()
            BufferedReader(FileReader(meterFile)).use { reader ->
                var isFirstLine = true
                var lineNumber = 0

                reader.forEachLine { line ->
                    lineNumber++

                    if (isFirstLine) {
                        // Skip header line - expected format: UID,Activate,Serial NO.,Bluetooth ID,Fixed date,Imp [kWh],Exp [kWh],ImpMaxDemand [kW],ExpMaxDemand [kW],MinVolt [V],Alert,Read date
                        isFirstLine = false
                        Log.d(TAG, "CSV Header: $line")
                        return@forEachLine
                    }

                    if (line.trim().isEmpty()) {
                        // Skip empty lines
                        return@forEachLine
                    }

                    try {
                        // Parse CSV line based on actual format: UID,Activate,Serial NO.,Bluetooth ID,Fixed date,Imp [kWh],Exp [kWh],ImpMaxDemand [kW],ExpMaxDemand [kW],MinVolt [V],Alert,Read date
                        val fields = line.split(",").map { it.trim().removeSurrounding("\"") }

                        if (fields.size >= 4) { // At minimum we need UID, Activate, Serial NO., Bluetooth ID
                            val meter = Meter(
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
                            meters.add(meter)
                            Log.d(TAG, "Parsed meter: UID=${meter.uid}, Serial=${meter.serialNo}, BT=${meter.bluetoothId}, Active=${meter.activate}")
                        } else {
                            Log.w(TAG, "Invalid line $lineNumber: insufficient fields (${fields.size}/12) - $line")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing line $lineNumber: $line", e)
                    }
                }
            }

            if (meters.isEmpty()) {
                MeterLoadResult.Error("No valid meter data found in CSV file")
            } else {
                Log.i(TAG, "Successfully loaded ${meters.size} meters from CSV")
                Log.i(TAG, "Active meters: ${meters.count { it.activate == 1 }}")
                MeterLoadResult.Success(meters)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error loading meter data", e)
            MeterLoadResult.Error("Failed to load meter data: ${e.message}")
        }
    }

    /**
     * Initialize meter connection statuses (empty - will be populated by real DLMS data)
     */
    private fun initializeMeterStatuses(meters: List<Meter>) {
        // Initialize empty status map - real data will come from DLMS
        val statuses = meters.associate { meter ->
            meter.uid to MeterConnectionStatus(
                account = meter.uid,
                isConnected = false,
                rssi = -100,
                connectionQuality = ConnectionQuality.POOR,
                dlmsSession = false
            )
        }
        _meterStatuses.value = statuses
        Log.d(TAG, "Initialized meter statuses for ${meters.size} meters")
    }

    /**
     * Update meter connection status based on DLMS communication state
     */
    private fun updateMeterConnectionStatus(dlmsState: DLMSCommunicationState) {
        dlmsState.connectedMeter?.let { meter ->
            updateMeterStatus(meter.uid) { currentStatus ->
                currentStatus.copy(
                    isConnected = dlmsState.isConnected
                )
            }
        }
    }

    /**
     * Update meter with DLMS data
     */
    fun updateMeterWithDLMSData(
        uid: String,
        isConnected: Boolean,
        rssi: Int,
        lastReading: Float?,
        readingTimestamp: String
    ) {
        updateMeterStatus(uid) { currentStatus ->
            currentStatus.copy(
                isConnected = isConnected,
                rssi = rssi,
                lastReading = lastReading,
                lastReadingDate = readingTimestamp,
                connectionQuality = getConnectionQuality(rssi),
                dlmsSession = isConnected
            )
        }

        Log.d(TAG, "Updated meter $uid: Connected=$isConnected, RSSI=$rssi, Reading=$lastReading")
    }

    /**
     * Update meter readings
     */
    fun updateMeterReadings(
        uid: String,
        impKwh: Float?,
        expKwh: Float?,
        maxDemand: Float?,
        voltage: Float?,
        readingDate: String
    ) {
        // Find and update the meter in the allMeters list
        val currentMeters = _uiState.value.allMeters.toMutableList()
        val meterIndex = currentMeters.indexOfFirst { it.uid == uid }

        if (meterIndex != -1) {
            val originalMeter = currentMeters[meterIndex]
            val updatedMeter = originalMeter.copy(
                impKwh = impKwh ?: originalMeter.impKwh,
                expKwh = expKwh ?: originalMeter.expKwh,
                impMaxDemand = maxDemand ?: originalMeter.impMaxDemand,
                minVolt = voltage ?: originalMeter.minVolt,
                readDate = readingDate
            )

            currentMeters[meterIndex] = updatedMeter

            _uiState.value = _uiState.value.copy(
                allMeters = currentMeters,
                filteredMeters = filterMetersByQuery(currentMeters, _searchQuery.value)
            )

            Log.d(TAG, "Updated readings for meter $uid: ImpKwh=$impKwh, ExpKwh=$expKwh, MaxDemand=$maxDemand, Voltage=$voltage")
        }
    }

    /**
     * Update specific meter status (using UID as account identifier)
     */
    private fun updateMeterStatus(
        uid: String,
        update: (MeterConnectionStatus) -> MeterConnectionStatus
    ) {
        val currentStatuses = _meterStatuses.value.toMutableMap()
        val currentStatus = currentStatuses[uid] ?: MeterConnectionStatus(
            account = uid,
            isConnected = false
        )
        currentStatuses[uid] = update(currentStatus)
        _meterStatuses.value = currentStatuses
    }

    /**
     * Get meter by UID
     */
    fun getMeterByUID(uid: String): Meter? {
        return _uiState.value.allMeters.find { it.uid == uid }
    }

    /**
     * Get connection quality based on RSSI
     */
    private fun getConnectionQuality(rssi: Int): ConnectionQuality {
        return when {
            rssi >= -50 -> ConnectionQuality.EXCELLENT
            rssi >= -60 -> ConnectionQuality.GOOD
            rssi >= -70 -> ConnectionQuality.FAIR
            else -> ConnectionQuality.POOR
        }
    }

    /**
     * Get DLMS manager instance for external access
     */
    fun getDLMSManager(): DLMSCommunicationManager? = dlmsManager

    override fun onCleared() {
        super.onCleared()
        // Clean up DLMS resources
        dlmsManager?.let { manager ->
            viewModelScope.launch {
                manager.stopAutomaticReading()
                manager.disconnect()
            }
        }
        Log.d(TAG, "MeterReadingViewModel cleared")
    }
}

/**
 * UI State for Meter Reading Screen
 */
data class MeterReadingUiState(
    val isLoading: Boolean = false,
    val allMeters: List<Meter> = emptyList(),
    val filteredMeters: List<Meter> = emptyList(),
    val errorMessage: String? = null,
    val selectedMeter: Meter? = null
)

/**
 * Automatic Reading Progress State
 */
data class AutoReadingProgress(
    val isActive: Boolean = false,
    val isManuallyStarted: Boolean = false,
    val totalMeters: Int = 0,
    val currentMeterIndex: Int = 0,
    val completedReadings: Int = 0,
    val successfulReadings: Int = 0,
    val failedReadings: Int = 0,
    val currentMeterUid: String = "",
    val lastOperation: String = "",
    val lastError: String? = null,
    val startTime: Long? = null,
    val endTime: Long? = null
) {
    val progressPercentage: Float
        get() = if (totalMeters > 0) (completedReadings.toFloat() / totalMeters) else 0f

    val successRate: Float
        get() = if (completedReadings > 0) (successfulReadings.toFloat() / completedReadings) else 0f
}

/**
 * Automatic Reading Statistics
 */
data class AutoReadingStats(
    val totalMeters: Int,
    val completedReadings: Int,
    val successfulReadings: Int,
    val failedReadings: Int,
    val currentMeter: String,
    val isActive: Boolean
)

/**
 * Meter load result
 */
sealed class MeterLoadResult {
    data class Success(val meters: List<Meter>) : MeterLoadResult()
    data class Error(val message: String) : MeterLoadResult()
}