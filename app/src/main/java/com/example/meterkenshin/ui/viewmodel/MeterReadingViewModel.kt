package com.example.meterkenshin.ui.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.meterkenshin.communication.DLMSCommunicationManager
import com.example.meterkenshin.ui.screen.Meter
import com.example.meterkenshin.ui.screen.MeterLoadResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.FileReader

/**
 * Enhanced ViewModel for Meter Reading with DLMS integration
 */
class MeterReadingViewModel : ViewModel() {

    companion object {
        private const val TAG = "MeterReadingViewModel"
        private const val APP_FILES_FOLDER = "MeterKenshin"
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
                        readingTimestamp = update.readingDate
                    )

                    // Update meter readings if available
                    if (update.impKwh != null || update.expKwh != null) {
                        updateMeterReadings(
                            uid = update.meterUid,
                            impKwh = update.impKwh,
                            expKwh = update.expKwh,
                            maxDemand = update.maxDemand,
                            voltage = update.voltage,
                            readingDate = update.readingDate
                        )
                    }

                    Log.d(TAG, "Updated meter ${update.meterUid} with real DLMS data: RSSI=${update.rssi}, Connected=${update.isConnected}")
                }
            }
        }
    }

    /**
     * Load meter data from CSV file (keeping original method name)
     */
    fun loadMeters(context: Context, fileName: String) {
        loadMeterData(context, fileName)
    }

    /**
     * Load meter data from CSV file
     */
    fun loadMeterData(context: Context, fileName: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

            try {
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
     * Connect to a specific meter using DLMS
     */
    fun connectToMeter(meter: Meter, bluetoothAddress: String? = null) {
        viewModelScope.launch {
            _selectedMeter.value = meter

            // Update meter status to connecting
            updateMeterStatus(meter.uid) { status ->
                status.copy(
                    isConnecting = true,
                    lastError = null
                )
            }

            // Use DLMS manager to connect (use bluetoothId from meter)
            dlmsManager?.connectToMeter(meter, bluetoothAddress ?: meter.bluetoothId)
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
     * Execute DLMS function on connected meter
     */
    fun executeDLMSFunction(functionId: String, meter: Meter) {
        viewModelScope.launch {
            try {
                updateMeterStatus(meter.uid) { status ->
                    status.copy(isExecutingOperation = true)
                }

                when (functionId) {
                    "registration" -> {
                        // Handle registration/authentication
                        dlmsManager?.executeActionRequest(0, 1, "registration")
                    }
                    "read_data" -> {
                        // Read instantaneous data
                        dlmsManager?.readInstantaneousData()
                    }
                    "load_profile" -> {
                        // Read load profile data
                        dlmsManager?.readLoadProfile()
                    }
                    "event_log" -> {
                        // Read event log
                        dlmsManager?.readEventLog()
                    }
                    "billing_data" -> {
                        // Read billing data
                        dlmsManager?.readBillingData()
                    }
                    "set_clock" -> {
                        // Set meter clock
                        dlmsManager?.setMeterClock()
                    }
                }

                // Simulate successful operation
                updateMeterStatus(meter.uid) { status ->
                    status.copy(
                        isExecutingOperation = false,
                        lastOperationTime = System.currentTimeMillis(),
                        lastOperation = functionId
                    )
                }

                Log.i(TAG, "DLMS function '$functionId' executed for meter ${meter.uid}")

            } catch (e: Exception) {
                updateMeterStatus(meter.uid) { status ->
                    status.copy(
                        isExecutingOperation = false,
                        lastError = "Operation failed: ${e.message}"
                    )
                }
                Log.e(TAG, "DLMS function execution failed", e)
            }
        }
    }

    /**
     * Update meter status with real DLMS data
     */
    fun updateMeterWithDLMSData(
        uid: String,
        isConnected: Boolean,
        rssi: Int,
        lastReading: Float? = null,
        readingTimestamp: String? = null,
        connectionQuality: ConnectionQuality? = null
    ) {
        updateMeterStatus(uid) { currentStatus ->
            currentStatus.copy(
                isConnected = isConnected,
                rssi = rssi,
                lastReading = lastReading,
                lastReadingDate = readingTimestamp,
                connectionQuality = connectionQuality ?: calculateConnectionQuality(rssi),
                dlmsSession = isConnected,
                connectionTime = if (isConnected) System.currentTimeMillis() else null,
                lastError = null
            )
        }
    }

    /**
     * Calculate connection quality based on RSSI
     */
    private fun calculateConnectionQuality(rssi: Int): ConnectionQuality {
        return when {
            rssi > -50 -> ConnectionQuality.EXCELLENT
            rssi > -65 -> ConnectionQuality.GOOD
            rssi > -80 -> ConnectionQuality.FAIR
            else -> ConnectionQuality.POOR
        }
    }

    /**
     * Update meter readings from DLMS response
     */
    fun updateMeterReadings(
        uid: String,
        impKwh: Float?,
        expKwh: Float?,
        maxDemand: Float?,
        voltage: Float?,
        readingDate: String?
    ) {
        // Update the meter data in the list
        val currentState = _uiState.value
        val updatedMeters = currentState.allMeters.map { meter ->
            if (meter.uid == uid) {
                meter.copy(
                    impKwh = impKwh ?: meter.impKwh,
                    expKwh = expKwh ?: meter.expKwh,
                    impMaxDemand = maxDemand ?: meter.impMaxDemand,
                    minVolt = voltage ?: meter.minVolt,
                    readDate = readingDate ?: meter.readDate
                )
            } else {
                meter
            }
        }

        _uiState.value = currentState.copy(
            allMeters = updatedMeters,
            filteredMeters = filterMetersByQuery(updatedMeters, _searchQuery.value)
        )

        // Update connection status with new reading
        updateMeterStatus(uid) { status ->
            status.copy(
                lastReading = impKwh,
                lastReadingDate = readingDate,
                lastOperationTime = System.currentTimeMillis()
            )
        }
    }

    /**
     * Handle DLMS connection lost
     */
    fun onDLMSConnectionLost(uid: String, reason: String) {
        updateMeterStatus(uid) { status ->
            status.copy(
                isConnected = false,
                dlmsSession = false,
                lastError = reason,
                connectionTime = null
            )
        }
    }

    /**
     * Get meter statistics
     */
    fun getMeterStatistics(): MeterStatistics {
        val meters = _uiState.value.allMeters
        val statuses = _meterStatuses.value

        val connectedCount = statuses.values.count { it.isConnected }
        val rankCounts = meters.groupBy { it.rank }.mapValues { it.value.size }

        return MeterStatistics(
            totalCount = meters.size,
            connectedCount = connectedCount,
            uniqueRanks = rankCounts.size,
            rankDistribution = rankCounts
        )
    }

    /**
     * Get meters by connection status
     */
    fun getMetersByConnectionStatus(connected: Boolean): List<Meter> {
        val meters = _uiState.value.allMeters
        val statuses = _meterStatuses.value

        return meters.filter { meter ->
            statuses[meter.account]?.isConnected == connected
        }
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
                            Log.d(TAG, "Parsed meter: UID=${meter.uid}, Serial=${meter.serialNo}, BT=${meter.bluetoothId}")
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
    }

    /**
     * Update meter connection status based on DLMS communication state
     */
    private fun updateMeterConnectionStatus(dlmsState: com.example.meterkenshin.communication.DLMSCommunicationState) {
        dlmsState.connectedMeter?.let { meter ->
            updateMeterStatus(meter.account) { currentStatus ->
                currentStatus.copy(
                    isConnected = dlmsState.isConnected,
                    isConnecting = dlmsState.isConnecting,
                    connectionTime = dlmsState.connectionTime,
                    lastError = dlmsState.lastError,
                    isExecutingOperation = dlmsState.isExecutingOperation
                )
            }
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
     * Get meters by activation status
     */
    fun getMetersByActivation(isActive: Boolean): List<Meter> {
        val activationValue = if (isActive) 1 else 0
        return _uiState.value.allMeters.filter { it.activate == activationValue }
    }

    /**
     * Get unique serial numbers from all meters
     */
    fun getUniqueSerials(): List<String> {
        return _uiState.value.allMeters.map { it.serialNo }.distinct().sorted()
    }

    override fun onCleared() {
        super.onCleared()
        // Clean up DLMS manager
        dlmsManager?.disconnect()
    }
}

/**
 * Enhanced UI State for Meter Reading screen with DLMS integration
 */
data class MeterReadingUiState(
    val isLoading: Boolean = false,
    val allMeters: List<Meter> = emptyList(),
    val filteredMeters: List<Meter> = emptyList(),
    val errorMessage: String? = null
)

/**
 * Enhanced meter connection status with DLMS features
 */
data class MeterConnectionStatus(
    val account: String,
    val isConnected: Boolean = false,
    val isConnecting: Boolean = false,
    val isExecutingOperation: Boolean = false,
    val rssi: Int = -100,
    val lastReading: Float? = null,
    val lastReadingDate: String? = null,
    val connectionQuality: ConnectionQuality = ConnectionQuality.POOR,
    val dlmsSession: Boolean = false,
    val connectionTime: Long? = null,
    val lastError: String? = null,
    val lastOperation: String? = null,
    val lastOperationTime: Long? = null
)

/**
 * Connection quality enumeration
 */
enum class ConnectionQuality {
    EXCELLENT, GOOD, FAIR, POOR
}

/**
 * Enhanced meter statistics with connection info
 */
data class MeterStatistics(
    val totalCount: Int,
    val connectedCount: Int,
    val uniqueRanks: Int,
    val rankDistribution: Map<String, Int>
)
