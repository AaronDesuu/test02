package com.example.meterkenshin.ui.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.meterkenshin.model.Meter
import com.example.meterkenshin.model.MeterType
import com.example.meterkenshin.model.MeterStatus
//import com.example.meterkenshin.ui.screen.MeterLoadResult
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
import java.util.Date
import java.util.Locale

/**
 * ViewModel for Meter Reading functionality using MeterModel data class
 * Handles meter data loading, search, and filtering operations
 */
class MeterReadingViewModel : ViewModel() {

    companion object {
        private const val TAG = "MeterReadingViewModel"
        private const val APP_FILES_FOLDER = "app_files"
    }

    // State management
    private val _uiState = MutableStateFlow(MeterReadingUiState())
    val uiState: StateFlow<MeterReadingUiState> = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    /**
     * Load meters from CSV file and convert to MeterModel objects
     */
    fun loadMeters(context: Context, fileName: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

            val result = withContext(Dispatchers.IO) {
                loadMeterDataFromFile(context, fileName)
            }

            when (result) {
                is MeterLoadResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        allMeters = result.meters,
                        filteredMeters = result.meters,
                        errorMessage = null
                    )
                }

                is MeterLoadResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = result.message
                    )
                }
            }
        }
    }

    /**
     * Update search query and filter meters
     */
    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        filterMeters(query)
    }

    /**
     * Filter meters based on search query
     */
    private fun filterMeters(query: String) {
        val filteredMeters = if (query.isBlank()) {
            _uiState.value.allMeters
        } else {
            filterMetersByQuery(_uiState.value.allMeters, query)
        }

        _uiState.value = _uiState.value.copy(filteredMeters = filteredMeters)
    }

    /**
     * Filter meters by search query
     */
    private fun filterMetersByQuery(meters: List<Meter>, query: String): List<Meter> {
        val lowerQuery = query.lowercase()
        return meters.filter { meter ->
            meter.id.lowercase().contains(lowerQuery) ||
                    meter.serialNumber.lowercase().contains(lowerQuery) ||
                    meter.location.lowercase().contains(lowerQuery) ||
                    meter.type.displayName.lowercase().contains(lowerQuery) ||
                    meter.status.displayName.lowercase().contains(lowerQuery) ||
                    meter.bluetoothId?.lowercase()?.contains(lowerQuery) == true
        }
    }

    /**
     * Load meter data from CSV file and parse to MeterModel objects
     */
    private suspend fun loadMeterDataFromFile(
        context: Context,
        fileName: String
    ): MeterLoadResult {
        return try {
            val externalFilesDir = context.getExternalFilesDir(null)
            val appFilesDir = File(externalFilesDir, APP_FILES_FOLDER)
            val meterFile = File(appFilesDir, fileName)

            if (!meterFile.exists()) {
                return MeterLoadResult.Error("Meter file not found: ${meterFile.absolutePath}")
            }

            Log.d(TAG, "Loading meter data from: ${meterFile.absolutePath}")

            val meters = mutableListOf<Meter>()
            val reader = BufferedReader(FileReader(meterFile))

            reader.use { bufferedReader ->
                var isFirstLine = true
                var lineNumber = 0

                bufferedReader.forEachLine { line ->
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
                        // Parse CSV line based on meter.csv format
                        val fields = line.split(",").map { it.trim().removeSurrounding("\"") }

                        if (fields.size >= 12) {
                            val meter = parseCsvLineToMeter(fields, lineNumber)
                            meter?.let {
                                meters.add(it)
                                Log.d(TAG, "Parsed meter: $it")
                            }
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
     * Parse CSV line to Meter object based on the updated MeterModel
     */
    private fun parseCsvLineToMeter(fields: List<String>, lineNumber: Int): Meter? {
        return try {
            // CSV format: UID,Activate,Serial NO.,Bluetooth ID,Fixed date,Imp [kWh],Exp [kWh],ImpMaxDemand [kW],ExpMaxDemand [kW],MinVolt [V],Alert,Read date
            val uid = fields[0].toIntOrNull()
            val activate = fields[1].toIntOrNull()
            val serialNo = fields[2].toIntOrNull()
            val bluetoothId = fields[3].takeIf { it.isNotBlank() }
            val fixedDate = parseDate(fields[4])
            val impKWh = fields[5].toDoubleOrNull()
            val expKWh = fields[6].toDoubleOrNull()
            val impMaxDemandKW = fields[7].toDoubleOrNull()
            val expMaxDemandKW = fields[8].toDoubleOrNull()
            val minVoltV = fields[9].toDoubleOrNull()
            val alert = fields[10].toDoubleOrNull()
            val readDate = parseDate(fields[11])

            // Determine meter status based on activate field
            val status = when (activate) {
                1 -> MeterStatus.ACTIVE
                0 -> MeterStatus.OFFLINE
                else -> MeterStatus.ERROR
            }

            // Determine meter type based on available data or default to SMART_METER
            val type = when {
                bluetoothId != null -> MeterType.SMART_METER
                impKWh != null && expKWh != null -> MeterType.THREE_PHASE
                else -> MeterType.SINGLE_PHASE
            }

            // Create Meter object with merged fields
            Meter(
                id = uid?.toString() ?: "unknown_$lineNumber", // uid -> id
                serialNumber = serialNo?.toString() ?: "unknown", // serialNo -> serialNumber
                location = "Unknown Location", // Default location since not in CSV
                type = type,
                status = status,
                installationDate = fixedDate ?: Date(),
                lastMaintenanceDate = readDate, // readDate -> lastMaintenanceDate
                coordinates = null, // Not available in CSV
                activate = activate,
                bluetoothId = bluetoothId,
                fixedDate = fixedDate,
                impKWh = impKWh,
                expKWh = expKWh,
                impMaxDemandKW = impMaxDemandKW,
                expMaxDemandKW = expMaxDemandKW,
                minVoltV = minVoltV,
                alert = alert
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error creating Meter object from line $lineNumber", e)
            null
        }
    }

    /**
     * Parse date string to Date object
     */
    private fun parseDate(dateString: String): Date? {
        if (dateString.isBlank()) return null

        return try {
            // Try different date formats
            val formats = listOf(
                "yyyy-MM-dd",
                "dd/MM/yyyy",
                "MM/dd/yyyy",
                "yyyy-MM-dd HH:mm:ss",
                "dd/MM/yyyy HH:mm:ss"
            )

            for (format in formats) {
                try {
                    val dateFormat = SimpleDateFormat(format, Locale.getDefault())
                    return dateFormat.parse(dateString)
                } catch (e: Exception) {
                    // Try next format
                    continue
                }
            }

            // If no format works, return null
            null
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse date: $dateString", e)
            null
        }
    }

    /**
     * Get meter by ID
     */
    fun getMeterById(id: String): Meter? {
        return _uiState.value.allMeters.find { it.id == id }
    }

    /**
     * Get meters by status
     */
    fun getMetersByStatus(status: MeterStatus): List<Meter> {
        return _uiState.value.allMeters.filter { it.status == status }
    }

    /**
     * Get meters by type
     */
    fun getMetersByType(type: MeterType): List<Meter> {
        return _uiState.value.allMeters.filter { it.type == type }
    }

    /**
     * Get unique meter types from all meters
     */
    fun getUniqueMeterTypes(): List<MeterType> {
        return _uiState.value.allMeters.map { it.type }.distinct()
    }

    /**
     * Get unique meter statuses from all meters
     */
    fun getUniqueMeterStatuses(): List<MeterStatus> {
        return _uiState.value.allMeters.map { it.status }.distinct()
    }

    /**
     * Get meter statistics
     */
    fun getMeterStatistics(): MeterStatistics {
        val meters = _uiState.value.allMeters
        val statusCounts = meters.groupBy { it.status }.mapValues { it.value.size }
        val typeCounts = meters.groupBy { it.type }.mapValues { it.value.size }
        val bluetoothEnabled = meters.count { it.bluetoothId != null }
        val energyReadings = meters.count { it.impKWh != null || it.expKWh != null }

        return MeterStatistics(
            totalCount = meters.size,
            statusDistribution = statusCounts,
            typeDistribution = typeCounts,
            bluetoothEnabledCount = bluetoothEnabled,
            energyReadingsCount = energyReadings
        )
    }

    /**
     * Get meters with Bluetooth capability
     */
    fun getBluetoothEnabledMeters(): List<Meter> {
        return _uiState.value.allMeters.filter { it.bluetoothId != null }
    }

    /**
     * Get meters with energy readings
     */
    fun getMetersWithEnergyReadings(): List<Meter> {
        return _uiState.value.allMeters.filter { it.impKWh != null || it.expKWh != null }
    }

    /**
     * Get meters with alerts
     */
    fun getMetersWithAlerts(): List<Meter> {
        return _uiState.value.allMeters.filter { it.alert != null && it.alert!! > 0 }
    }
}

/**
 * UI State for Meter Reading screen using MeterModel
 */
data class MeterReadingUiState(
    val isLoading: Boolean = false,
    val allMeters: List<Meter> = emptyList(),
    val filteredMeters: List<Meter> = emptyList(),
    val errorMessage: String? = null
)

/**
 * Enhanced meter statistics data class
 */
data class MeterStatistics(
    val totalCount: Int,
    val statusDistribution: Map<MeterStatus, Int>,
    val typeDistribution: Map<MeterType, Int>,
    val bluetoothEnabledCount: Int,
    val energyReadingsCount: Int
)

/**
 * Sealed class for meter loading results
 */
sealed class MeterLoadResult {
    data class Success(val meters: List<Meter>) : MeterLoadResult()
    data class Error(val message: String) : MeterLoadResult()
}