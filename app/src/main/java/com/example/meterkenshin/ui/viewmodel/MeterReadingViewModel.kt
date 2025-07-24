// app/src/main/java/com/example/meterkenshin/ui/viewmodel/MeterReadingViewModel.kt
package com.example.meterkenshin.ui.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.meterkenshin.model.MeterData
import com.example.meterkenshin.model.MeterLoadResult
import com.example.meterkenshin.model.MeterStatistics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.FileReader

class MeterReadingViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(MeterReadingUiState())
    val uiState: StateFlow<MeterReadingUiState> = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    companion object {
        private const val TAG = "MeterReadingViewModel"
        private const val APP_FILES_FOLDER = "app_files"
        private const val METER_CSV_FILENAME = "meter.csv"
    }

    init {
        // Search query changes trigger filtering
        viewModelScope.launch {
            searchQuery.collect { query ->
                filterMeters(query)
            }
        }
    }

    /**
     * Load meters from CSV file
     */
    fun loadMeters(context: Context) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

            val result = loadMeterDataFromFile(context, METER_CSV_FILENAME)

            when (result) {
                is MeterLoadResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        allMeters = result.meters,
                        filteredMeters = result.meters,
                        errorMessage = null
                    )
                    Log.i(TAG, "Successfully loaded ${result.meters.size} meters")
                }
                is MeterLoadResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = result.message
                    )
                    Log.e(TAG, "Failed to load meters: ${result.message}")
                }
                is MeterLoadResult.Loading -> {
                    // Already handled above
                }
            }
        }
    }

    /**
     * Update search query
     */
    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    /**
     * Filter meters based on search query
     */
    private fun filterMeters(query: String) {
        val allMeters = _uiState.value.allMeters
        if (query.isBlank()) {
            _uiState.value = _uiState.value.copy(filteredMeters = allMeters)
            return
        }

        val filteredMeters = allMeters.filter { meter ->
            val lowerQuery = query.lowercase()
            meter.uid.lowercase().contains(lowerQuery) ||
                    meter.serialNumber.lowercase().contains(lowerQuery) ||
                    meter.bluetoothId.lowercase().contains(lowerQuery) ||
                    meter.displayName.lowercase().contains(lowerQuery)
        }

        _uiState.value = _uiState.value.copy(filteredMeters = filteredMeters)
    }

    /**
     * Load meter data from CSV file with new structure
     */
    private suspend fun loadMeterDataFromFile(
        context: Context,
        fileName: String
    ): MeterLoadResult {
        return withContext(Dispatchers.IO) {
            try {
                val externalFilesDir = context.getExternalFilesDir(null)
                val appFilesDir = File(externalFilesDir, APP_FILES_FOLDER)
                val meterFile = File(appFilesDir, fileName)

                if (!meterFile.exists()) {
                    return@withContext MeterLoadResult.Error("Meter file not found: ${meterFile.absolutePath}")
                }

                Log.d(TAG, "Loading meter data from: ${meterFile.absolutePath}")

                val meters = mutableListOf<MeterData>()
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

                            if (fields.size >= 4) { // Minimum required fields: UID, Activate, Serial NO., Bluetooth ID
                                val meter = MeterData(
                                    uid = fields[0],
                                    activate = fields[1].toIntOrNull() ?: 0,
                                    serialNumber = fields[2],
                                    bluetoothId = fields[3],
                                    fixedDate = fields.getOrNull(4)?.takeIf { it.isNotBlank() },
                                    impKwh = fields.getOrNull(5)?.toFloatOrNull(),
                                    expKwh = fields.getOrNull(6)?.toFloatOrNull(),
                                    impMaxDemandKw = fields.getOrNull(7)?.toFloatOrNull(),
                                    expMaxDemandKw = fields.getOrNull(8)?.toFloatOrNull(),
                                    minVoltV = fields.getOrNull(9)?.toFloatOrNull(),
                                    alert = fields.getOrNull(10)?.takeIf { it.isNotBlank() },
                                    readDate = fields.getOrNull(11)?.takeIf { it.isNotBlank() }
                                )
                                meters.add(meter)
                                Log.d(TAG, "Parsed meter: UID=${meter.uid}, Active=${meter.isActive}, S/N=${meter.serialNumber}")
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
    }

    /**
     * Get meter by UID
     */
    fun getMeterByUid(uid: String): MeterData? {
        return _uiState.value.allMeters.find { it.uid == uid }
    }

    /**
     * Get active meters only
     */
    fun getActiveMeters(): List<MeterData> {
        return _uiState.value.allMeters.filter { it.isActive }
    }

    /**
     * Get meter statistics
     */
    fun getMeterStatistics(): MeterStatistics {
        val meters = _uiState.value.allMeters
        val activeCount = meters.count { it.isActive }
        val inactiveCount = meters.count { !it.isActive }
        val noDataCount = meters.count { it.isActive && !it.hasReadingData }
        val totalImport = meters.mapNotNull { it.impKwh }.sum()
        val totalExport = meters.mapNotNull { it.expKwh }.sum()

        return MeterStatistics(
            totalCount = meters.size,
            activeCount = activeCount,
            inactiveCount = inactiveCount,
            noDataCount = noDataCount,
            totalImportEnergy = totalImport,
            totalExportEnergy = totalExport
        )
    }

    /**
     * Get meters by status
     */
    fun getMetersByStatus(status: com.example.meterkenshin.model.MeterStatus): List<MeterData> {
        return _uiState.value.allMeters.filter { it.status == status }
    }
}

/**
 * UI State for Meter Reading screen
 */
data class MeterReadingUiState(
    val isLoading: Boolean = false,
    val allMeters: List<MeterData> = emptyList(),
    val filteredMeters: List<MeterData> = emptyList(),
    val errorMessage: String? = null
)