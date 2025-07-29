package com.example.meterkenshin.model

import java.util.Date
import kotlin.random.Random

data class Meter(
    val id: String,
    val serialNumber: String,
    val location: String,
    val type: MeterType,
    val status: MeterStatus,
    val installationDate: Date,
    val lastMaintenanceDate: Date?,
    val coordinates: Coordinates? = null
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
    val notes: String? = null
)

enum class ReadingType(val displayName: String) {
    MANUAL("Manual"),
    AUTOMATIC("Automatic"),
    ESTIMATED("Estimated"),
    CORRECTED("Corrected")
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
    val peakUsageTime: Date
)

data class SystemOverview(
    val totalMeters: Int,
    val activeMeters: Int,
    val offlineMeters: Int,
    val maintenanceMeters: Int,
    val todayReadings: Int,
    val pendingExports: Int,
    val lastSyncTime: Date?,
    val syncStatus: SyncStatus
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
    val meterId: String? = null
)

enum class AlertType(val displayName: String) {
    HIGH_CONSUMPTION("High Consumption"),
    METER_OFFLINE("Meter Offline"),
    READING_OVERDUE("Reading Overdue"),
    SYSTEM_MAINTENANCE("System Maintenance"),
    ERROR("System Error"),
    WARNING("Warning")
}

enum class AlertSeverity(val displayName: String, val colorHex: String) {
    LOW("Low", "#4CAF50"),
    MEDIUM("Medium", "#FF9800"),
    HIGH("High", "#F44336"),
    CRITICAL("Critical", "#D32F2F")
}

