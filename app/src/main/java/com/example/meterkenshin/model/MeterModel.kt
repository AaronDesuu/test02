// app/src/main/java/com/example/meterkenshin/model/MeterData.kt
package com.example.meterkenshin.model

import java.util.Date

/**
 * Data class representing a meter from the meter.csv file
 * Based on CSV structure: UID,Activate,Serial NO.,Bluetooth ID,Fixed date,Imp [kWh],Exp [kWh],ImpMaxDemand [kW],ExpMaxDemand [kW],MinVolt [V],Alert,Read date
 */
data class MeterData(
    val uid: String,                    // UID - meter ID
    val activate: Int,                  // Activate - 0 or 1
    val serialNumber: String,           // Serial NO. - device serial number
    val bluetoothId: String,            // Bluetooth ID - MAC address
    val fixedDate: String? = null,      // Fixed date - installation/fixed date
    val impKwh: Float? = null,          // Imp [kWh] - import energy
    val expKwh: Float? = null,          // Exp [kWh] - export energy
    val impMaxDemandKw: Float? = null,  // ImpMaxDemand [kW] - import max demand
    val expMaxDemandKw: Float? = null,  // ExpMaxDemand [kW] - export max demand
    val minVoltV: Float? = null,        // MinVolt [V] - minimum voltage
    val alert: String? = null,          // Alert - alert information
    val readDate: String? = null        // Read date - last reading date
) {
    /**
     * Check if meter is active (activate = 1)
     */
    val isActive: Boolean
        get() = activate == 1

    /**
     * Check if meter has reading data
     */
    val hasReadingData: Boolean
        get() = impKwh != null && impKwh > 0

    /**
     * Get status based on activation and reading data
     */
    val status: MeterStatus
        get() = when {
            !isActive -> MeterStatus.INACTIVE
            hasReadingData -> MeterStatus.ACTIVE
            else -> MeterStatus.NO_DATA
        }

    /**
     * Get formatted import energy value
     */
    val formattedImpKwh: String
        get() = if (impKwh != null) String.format("%.3f", impKwh) else "------"

    /**
     * Get formatted export energy value
     */
    val formattedExpKwh: String
        get() = if (expKwh != null) String.format("%.3f", expKwh) else "------"

    /**
     * Get display name for UI
     */
    val displayName: String
        get() = "Meter $uid"

    /**
     * Get short description for list items
     */
    val description: String
        get() = "S/N: $serialNumber"
}

/**
 * Enum representing meter status
 */
enum class MeterStatus(val displayName: String, val colorName: String) {
    ACTIVE("Active", "success_light"),
    INACTIVE("Inactive", "outline_light"),
    NO_DATA("No Data", "warning_light"),
    ERROR("Error", "error_light")
}

/**
 * Data class for meter statistics
 */
data class MeterStatistics(
    val totalCount: Int,
    val activeCount: Int,
    val inactiveCount: Int,
    val noDataCount: Int,
    val totalImportEnergy: Float,
    val totalExportEnergy: Float
)

/**
 * Result wrapper for meter loading operations
 */
sealed class MeterLoadResult {
    data class Success(val meters: List<MeterData>) : MeterLoadResult()
    data class Error(val message: String) : MeterLoadResult()
    object Loading : MeterLoadResult()
}