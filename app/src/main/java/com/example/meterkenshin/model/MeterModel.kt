package com.example.meterkenshin.model

import java.util.Date
import kotlin.random.Random

data class Meter(
    val id: String, // Merged with uid from CSV
    val serialNumber: String, // Merged with serialNo from CSV
    val location: String,
    val type: MeterType,
    val status: MeterStatus,
    val installationDate: Date,
    val lastMaintenanceDate: Date?, // Merged with readDate from CSV
    val coordinates: Coordinates? = null,
    // New fields from meter.csv
    val activate: Int? = null,
    val bluetoothId: String? = null,
    val fixedDate: Date? = null,
    val impKWh: Double? = null,  // Import [kWh]
    val expKWh: Double? = null,  // Export [kWh]
    val impMaxDemandKW: Double? = null,  // Import Max Demand [kW]
    val expMaxDemandKW: Double? = null,  // Export Max Demand [kW]
    val minVoltV: Double? = null,  // Min Voltage [V]
    val alert: Double? = null
)

enum class MeterType(val displayName: String) {
    SINGLE_PHASE("Single Phase"),
    THREE_PHASE("Three Phase"),
    SMART_METER("Smart Meter"),
    INDUSTRIAL("Industrial")
}

enum class MeterStatus(val displayName: String, val colorHex: String) {
    ACTIVE("Active", "#4CAF50"),
    OFFLINE("Offline", "#F44336"),
    MAINTENANCE("Maintenance", "#FF9800"),
    ERROR("Error", "#E91E63")
}

data class Coordinates(
    val latitude: Double,
    val longitude: Double
)

data class MeterReading(
    val id: String,
    val meterId: String,
    val reading: Double, // in kWh
    val timestamp: Date,
    val readBy: String,
    val readingType: ReadingType,
    val quality: ReadingQuality = ReadingQuality.GOOD,
    val notes: String? = null,
    // Additional fields from meter.csv for enhanced readings
    val importReading: Double? = null,  // From Imp [kWh]
    val exportReading: Double? = null,  // From Exp [kWh]
    val maxDemandImport: Double? = null,  // From ImpMaxDemand [kW]
    val maxDemandExport: Double? = null,  // From ExpMaxDemand [kW]
    val minVoltage: Double? = null,  // From MinVolt [V]
    val alertLevel: Double? = null,  // From Alert
    val bluetoothId: String? = null  // For Bluetooth connectivity
)

enum class ReadingType(val displayName: String) {
    MANUAL("Manual"),
    AUTOMATIC("Automatic"),
    ESTIMATED("Estimated"),
    CORRECTED("Corrected"),
    BLUETOOTH("Bluetooth"),  // New type for Bluetooth readings
    REMOTE("Remote")  // New type for remote readings
}

enum class ReadingQuality(val displayName: String) {
    EXCELLENT("Excellent"),
    GOOD("Good"),
    FAIR("Fair"),
    POOR("Poor")
}

data class ConsumptionSummary(
    val totalConsumption: Double,
    val dailyAverage: Double,
    val weeklyTotal: Double,
    val monthlyTotal: Double,
    val peakUsage: Double,
    val peakUsageTime: Date,
    // Enhanced consumption data from CSV
    val totalImport: Double? = null,  // Total import consumption
    val totalExport: Double? = null,  // Total export (if applicable)
    val maxDemandPeak: Double? = null,  // Peak demand recorded
    val voltageStats: VoltageStats? = null  // Voltage statistics
)

data class VoltageStats(
    val minVoltage: Double,
    val maxVoltage: Double,
    val averageVoltage: Double,
    val voltageAlerts: Int
)

data class SystemOverview(
    val totalMeters: Int,
    val activeMeters: Int,
    val offlineMeters: Int,
    val maintenanceMeters: Int,
    val todayReadings: Int,
    val pendingExports: Int,
    val lastSyncTime: Date?,
    val syncStatus: SyncStatus,
    // Enhanced system overview with CSV data
    val bluetoothConnectedMeters: Int = 0,  // Meters with Bluetooth connectivity
    val totalImportEnergy: Double? = null,  // Total imported energy
    val totalExportEnergy: Double? = null,  // Total exported energy
    val averageVoltage: Double? = null,  // System average voltage
    val activeAlerts: Int = 0  // Number of active alerts
)

enum class SyncStatus(val displayName: String, val colorHex: String) {
    SYNCED("Synced", "#4CAF50"),
    SYNCING("Syncing", "#2196F3"),
    FAILED("Failed", "#F44336"),
    PENDING("Pending", "#FF9800")
}

data class Alert(
    val id: String,
    val type: AlertType,
    val title: String,
    val message: String,
    val timestamp: Date,
    val severity: AlertSeverity,
    val isRead: Boolean = false,
    val meterId: String? = null,
    // Enhanced alert data
    val alertValue: Double? = null,  // Value that triggered the alert
    val thresholdValue: Double? = null,  // Threshold that was exceeded
    val voltageLevel: Double? = null,  // Voltage level if voltage-related alert
    val bluetoothId: String? = null  // Associated Bluetooth ID
)

enum class AlertType(val displayName: String) {
    HIGH_CONSUMPTION("High Consumption"),
    METER_OFFLINE("Meter Offline"),
    READING_OVERDUE("Reading Overdue"),
    SYSTEM_MAINTENANCE("System Maintenance"),
    ERROR("System Error"),
    WARNING("Warning"),
    // New alert types based on CSV data
    LOW_VOLTAGE("Low Voltage"),
    HIGH_VOLTAGE("High Voltage"),
    BLUETOOTH_DISCONNECTED("Bluetooth Disconnected"),
    MAX_DEMAND_EXCEEDED("Max Demand Exceeded"),
    EXPORT_ANOMALY("Export Anomaly")
}

enum class AlertSeverity(val displayName: String, val colorHex: String) {
    LOW("Low", "#4CAF50"),
    MEDIUM("Medium", "#FF9800"),
    HIGH("High", "#F44336"),
    CRITICAL("Critical", "#D32F2F")
}

// New data class for CSV meter data parsing
data class MeterCsvData(
    val uid: Int, // Will be merged with id
    val activate: Int,
    val serialNo: Int, // Will be merged with serialNumber
    val bluetoothId: String,
    val fixedDate: Date?,
    val impKWh: Double?,
    val expKWh: Double?,
    val impMaxDemandKW: Double?,
    val expMaxDemandKW: Double?,
    val minVoltV: Double?,
    val alert: Double?,
    val readDate: Date? // Will be merged with lastMaintenanceDate
)

// Helper function to convert CSV data to Meter object
fun MeterCsvData.toMeter(
    location: String = "Unknown",
    type: MeterType = MeterType.SMART_METER,
    coordinates: Coordinates? = null
): Meter {
    val status = when (activate) {
        1 -> MeterStatus.ACTIVE
        0 -> MeterStatus.OFFLINE
        else -> MeterStatus.ERROR
    }

    return Meter(
        id = uid.toString(), // Merged: uid -> id
        serialNumber = serialNo.toString(), // Merged: serialNo -> serialNumber
        location = location,
        type = type,
        status = status,
        installationDate = fixedDate ?: Date(),
        lastMaintenanceDate = readDate, // Merged: readDate -> lastMaintenanceDate
        coordinates = coordinates,
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
}