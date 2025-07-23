package com.example.meterkenshin.ui.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
 * ViewModel for Meter Reading functionality
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
     * Load meters from CSV file
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
                        filteredMeters = filterMeters(result.meters, _searchQuery.value),
                        errorMessage = null
                    )
                    Log.d(TAG, "Successfully loaded ${result.meters.size} meters")
                }
                is MeterLoadResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = result.message
                    )
                    Log.e(TAG, "Failed to load meters: ${result.message}")
                }
            }
        }
    }

    /**
     * Update search query and filter meters
     */
    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        val currentState = _uiState.value
        _uiState.value = currentState.copy(
            filteredMeters = filterMeters(currentState.allMeters, query)
        )
    }

    /**
     * Clear search query
     */
    fun clearSearch() {
        updateSearchQuery("")
    }

    /**
     * Refresh meter data
     */
    fun refreshMeters(context: Context, fileName: String) {
        loadMeters(context, fileName)
    }

    /**
     * Clear error message
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    /**
     * Filter meters based on search query
     */
    private fun filterMeters(meters: List<Meter>, query: String): List<Meter> {
        if (query.isBlank()) return meters

        val lowerQuery = query.lowercase()
        return meters.filter { meter ->
            meter.account.lowercase().contains(lowerQuery) ||
                    meter.key.lowercase().contains(lowerQuery) ||
                    meter.logical.lowercase().contains(lowerQuery) ||
                    meter.rank.lowercase().contains(lowerQuery)
        }
    }

    /**
     * Load meter data from CSV file based on project01 parsing logic
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
                        // Skip header line - expected format: Account,Key,Logical,Rank
                        isFirstLine = false
                        Log.d(TAG, "CSV Header: $line")
                        return@forEachLine
                    }

                    if (line.trim().isEmpty()) {
                        // Skip empty lines
                        return@forEachLine
                    }

                    try {
                        // Parse CSV line based on project01 format: Account,Key,Logical,Rank
                        val fields = line.split(",").map { it.trim().removeSurrounding("\"") }

                        if (fields.size >= 4) {
                            val meter = Meter(
                                account = fields[0],
                                key = fields[1],
                                logical = fields[2],
                                rank = fields[3]
                            )
                            meters.add(meter)
                            Log.d(TAG, "Parsed meter: $meter")
                        } else {
                            Log.w(TAG, "Invalid line $lineNumber: insufficient fields (${fields.size}/4) - $line")
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
     * Get meter by account ID
     */
    fun getMeterByAccount(account: String): Meter? {
        return _uiState.value.allMeters.find { it.account == account }
    }

    /**
     * Get meters by rank
     */
    fun getMetersByRank(rank: String): List<Meter> {
        return _uiState.value.allMeters.filter { it.rank == rank }
    }

    /**
     * Get unique ranks from all meters
     */
    fun getUniqueRanks(): List<String> {
        return _uiState.value.allMeters.map { it.rank }.distinct().sorted()
    }

    /**
     * Get meter statistics
     */
    fun getMeterStatistics(): MeterStatistics {
        val meters = _uiState.value.allMeters
        val rankCounts = meters.groupBy { it.rank }.mapValues { it.value.size }

        return MeterStatistics(
            totalCount = meters.size,
            uniqueRanks = rankCounts.size,
            rankDistribution = rankCounts
        )
    }
}

/**
 * UI State for Meter Reading screen
 */
data class MeterReadingUiState(
    val isLoading: Boolean = false,
    val allMeters: List<Meter> = emptyList(),
    val filteredMeters: List<Meter> = emptyList(),
    val errorMessage: String? = null
)

/**
 * Meter statistics data class
 */
data class MeterStatistics(
    val totalCount: Int,
    val uniqueRanks: Int,
    val rankDistribution: Map<String, Int>
)