package com.example.meterkenshin.communication

import android.content.Context
import androidx.lifecycle.viewModelScope
import com.example.meterkenshin.R
import com.example.meterkenshin.ui.screen.Meter
import com.example.meterkenshin.ui.viewmodel.MeterReadingViewModel
import kotlinx.coroutines.launch

/**
 * Extension functions to integrate updated DLMSCommunicationManager
 * with existing MeterReadingViewModel and UI components
 */

/**
 * Initialize DLMS communication with automatic meter CSV parsing
 */
fun MeterReadingViewModel.initializeDLMSWithCsvParsing(
    context: Context,
    bluetoothAdapter: android.bluetooth.BluetoothAdapter?,
    csvFileName: String = "meter.csv"
) {
    val dlmsManager = DLMSCommunicationManager(context, bluetoothAdapter)

    // Parse meter CSV file automatically
    viewModelScope.launch {
        val parseResult = dlmsManager.parseMeterCsvFile(csvFileName)
        when (parseResult) {
            is ParseResult.Success -> {
                // Update UI state with parsed meters
                updateMetersFromDLMS(parseResult.meters)
            }
            is ParseResult.Error -> {
                // Handle parse error
                updateErrorState(parseResult.message)
            }
        }
    }

    // Observe DLMS communication state
    viewModelScope.launch {
        dlmsManager.communicationState.collect { state ->
            updateDLMSConnectionStatus(state)
        }
    }

    // Observe meter data updates from DLMS
    viewModelScope.launch {
        dlmsManager.meterDataUpdates.collect { dataUpdate ->
            dataUpdate?.let { update ->
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
            }
        }
    }

    // Store DLMS manager reference
    setDLMSManager(dlmsManager)
}

/**
 * Connect to meter using DLMS with error handling
 */
fun DLMSCommunicationManager.connectToMeterSafely(
    meter: Meter,
    context: Context,
    onSuccess: (Meter) -> Unit = {},
    onError: (String) -> Unit = {}
) {
    connectToMeter(
        meter = meter,
        bluetoothAddress = meter.bluetoothId,
        onSuccess = {
            onSuccess(meter)
        },
        onError = { error ->
            val errorMsg = context.getString(R.string.dlms_error) + ": $error"
            onError(errorMsg)
        }
    )
}

/**
 * Execute DLMS operation with result handling
 */
fun DLMSCommunicationManager.executeOperation(
    operation: DLMSOperation,
    meter: Meter? = null,
    onResult: (List<String>) -> Unit = {},
    onError: (String) -> Unit = {}
) {
    try {
        when (operation) {
            DLMSOperation.READ_INSTANTANEOUS -> {
                readInstantaneousValues(meter)
            }
            DLMSOperation.READ_LOAD_PROFILE -> {
                readLoadProfile(meter)
            }
            DLMSOperation.READ_BILLING_DATA -> {
                readBillingData(meter)
            }
            DLMSOperation.SET_CLOCK -> {
                setMeterClock(meter)
            }
            DLMSOperation.READ_ALL_METERS -> {
                readAllMeters()
            }
        }
    } catch (e: Exception) {
        onError("DLMS operation failed: ${e.message}")
    }
}

/**
 * Get meter connection quality based on RSSI
 */
fun DLMSMeterData.getConnectionQuality(): ConnectionQuality {
    return when {
        rssi >= -50 -> ConnectionQuality.EXCELLENT
        rssi >= -60 -> ConnectionQuality.GOOD
        rssi >= -70 -> ConnectionQuality.FAIR
        else -> ConnectionQuality.POOR
    }
}

/**
 * Format meter reading for display
 */
fun DLMSMeterData.formatForDisplay(context: Context): List<Pair<String, String>> {
    val displayData = mutableListOf<Pair<String, String>>()

    impKwh?.let {
        displayData.add(
            context.getString(R.string.dlms_active_energy) to
                    "${String.format("%.3f", it)} ${context.getString(R.string.unit_kwh)}"
        )
    }

    expKwh?.let {
        displayData.add(
            "Export Energy" to
                    "${String.format("%.3f", it)} ${context.getString(R.string.unit_kwh)}"
        )
    }

    voltage?.let {
        displayData.add(
            context.getString(R.string.dlms_voltage) to
                    "${String.format("%.2f", it)} ${context.getString(R.string.unit_voltage)}"
        )
    }

    maxDemand?.let {
        displayData.add(
            context.getString(R.string.dlms_max_demand) to
                    "${String.format("%.3f", it)} ${context.getString(R.string.unit_power)}"
        )
    }

    current?.let {
        displayData.add(
            context.getString(R.string.dlms_current) to
                    "${String.format("%.2f", it)} ${context.getString(R.string.unit_current)}"
        )
    }

    powerFactor?.let {
        displayData.add(
            context.getString(R.string.dlms_power_factor) to
                    String.format("%.3f", it)
        )
    }

    frequency?.let {
        displayData.add(
            context.getString(R.string.dlms_frequency) to
                    "${String.format("%.2f", it)} ${context.getString(R.string.unit_frequency)}"
        )
    }

    temperature?.let {
        displayData.add(
            context.getString(R.string.dlms_temperature) to
                    "${String.format("%.1f", it)} ${context.getString(R.string.unit_temperature)}"
        )
    }

    batteryVoltage?.let {
        displayData.add(
            context.getString(R.string.dlms_battery_voltage) to
                    "${String.format("%.2f", it)} ${context.getString(R.string.unit_voltage)}"
        )
    }

    displayData.add(
        context.getString(R.string.dlms_signal_strength) to
                "$rssi ${context.getString(R.string.unit_dbm)}"
    )

    return displayData
}

/**
 * Check if meter is active and ready for DLMS communication
 */
fun Meter.isActiveForDLMS(): Boolean {
    return activate == 1 && bluetoothId.isNotEmpty()
}

/**
 * Get DLMS rank from meter activate status
 */
fun Meter.getDLMSRank(): Int {
    return when (activate) {
        1 -> DLMSConstants.RANK_READER
        2 -> DLMSConstants.RANK_POWER
        3 -> DLMSConstants.RANK_SUPER
        4 -> DLMSConstants.RANK_ADMIN
        else -> DLMSConstants.RANK_PUBLIC
    }
}

/**
 * Validate meter for DLMS communication
 */
fun Meter.validateForDLMS(): DLMSValidationResult {
    val errors = mutableListOf<String>()

    if (uid.isEmpty()) {
        errors.add("UID is required")
    }

    if (serialNo.isEmpty()) {
        errors.add("Serial number is required")
    }

    if (bluetoothId.isEmpty()) {
        errors.add("Bluetooth ID is required")
    } else if (!android.bluetooth.BluetoothAdapter.checkBluetoothAddress(bluetoothId)) {
        errors.add("Invalid Bluetooth address format")
    }

    if (activate !in 0..4) {
        errors.add("Invalid activation rank")
    }

    return if (errors.isEmpty()) {
        DLMSValidationResult.Valid
    } else {
        DLMSValidationResult.Invalid(errors)
    }
}

/**
 * Convert ParseResult to UI state update
 */
fun ParseResult.toUiUpdate(): MeterLoadResult {
    return when (this) {
        is ParseResult.Success -> MeterLoadResult.Success(meters)
        is ParseResult.Error -> MeterLoadResult.Error(message)
    }
}

/**
 * Batch operation progress formatting
 */
fun BatchOperationProgress.getProgressMessage(context: Context): String {
    return when {
        isCompleted -> context.getString(R.string.batch_operation_completed)
        errorMessage != null -> context.getString(R.string.batch_operation_failed, errorMessage)
        isRunning -> context.getString(R.string.batch_reading_progress, currentMeter, totalMeters)
        else -> ""
    }
}

/**
 * DLMS operations enum
 */
enum class DLMSOperation {
    READ_INSTANTANEOUS,
    READ_LOAD_PROFILE,
    READ_BILLING_DATA,
    SET_CLOCK,
    READ_ALL_METERS
}

/**
 * DLMS validation result
 */
sealed class DLMSValidationResult {
    object Valid : DLMSValidationResult()
    data class Invalid(val errors: List<String>) : DLMSValidationResult()
}

/**
 * Connection quality enum (already defined in MeterReadingScreen)
 */
enum class ConnectionQuality {
    EXCELLENT, GOOD, FAIR, POOR;

    fun getDisplayText(context: Context): String {
        return when (this) {
            EXCELLENT -> "Excellent"
            GOOD -> "Good"
            FAIR -> "Fair"
            POOR -> "Poor"
        }
    }

    fun getColor(): androidx.compose.ui.graphics.Color {
        return when (this) {
            EXCELLENT -> androidx.compose.ui.graphics.Color.Green
            GOOD -> androidx.compose.ui.graphics.Color(0xFF8BC34A)
            FAIR -> androidx.compose.ui.graphics.Color(0xFFFF9800)
            POOR -> androidx.compose.ui.graphics.Color.Red
        }
    }
}

/**
 * Meter load result (for compatibility with existing code)
 */
sealed class MeterLoadResult {
    data class Success(val meters: List<Meter>) : MeterLoadResult()
    data class Error(val message: String) : MeterLoadResult()
}

/**
 * Helper functions for MeterReadingViewModel integration
 */

// Extension function placeholders that should be implemented in MeterReadingViewModel
private fun MeterReadingViewModel.updateMetersFromDLMS(meters: List<Meter>) {
    // Update the UI state with parsed meters from DLMS
    // This should update _uiState.value with the new meters list
}

private fun MeterReadingViewModel.updateErrorState(message: String) {
    // Update the UI state with error message
    // This should update _uiState.value with error state
}

private fun MeterReadingViewModel.updateDLMSConnectionStatus(state: DLMSCommunicationState) {
    // Update UI with DLMS connection status
    // This should update connection indicators in the UI
}

private fun MeterReadingViewModel.updateMeterWithDLMSData(
    uid: String,
    isConnected: Boolean,
    rssi: Int,
    lastReading: Float?,
    readingTimestamp: java.util.Date
) {
    // Update specific meter's connection status and readings
    // This should update the meter status in _meterStatuses
}

private fun MeterReadingViewModel.updateMeterReadings(
    uid: String,
    impKwh: Float?,
    expKwh: Float?,
    maxDemand: Float?,
    voltage: Float?,
    readingDate: java.util.Date
) {
    // Update meter readings data
    // This should update the actual meter readings in the UI
}

private fun MeterReadingViewModel.setDLMSManager(dlmsManager: DLMSCommunicationManager) {
    // Store reference to DLMS manager for later use
    // This should store the dlmsManager instance in the ViewModel
}