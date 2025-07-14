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

// Mock data generators for demonstration
object MockDataGenerator {

    fun generateMeters(): List<Meter> {
        val locations = listOf(
            "Building A - Floor 1", "Building A - Floor 2", "Building B - Main",
            "Warehouse 1", "Warehouse 2", "Office Complex", "Factory Floor",
            "Parking Garage", "Cafeteria", "Server Room"
        )

        return (1..25).map { index ->
            Meter(
                id = "MTR${String.format("%03d", index)}",
                serialNumber = "SN${Random.nextLong(100000, 999999)}",
                location = locations.random(),
                type = MeterType.values().random(),
                status = when (Random.nextInt(100)) {
                    in 0..79 -> MeterStatus.ACTIVE
                    in 80..89 -> MeterStatus.OFFLINE
                    in 90..94 -> MeterStatus.MAINTENANCE
                    else -> MeterStatus.ERROR
                },
                installationDate = Date(System.currentTimeMillis() - Random.nextLong(0, 365L * 24 * 60 * 60 * 1000 * 3)), // Last 3 years
                lastMaintenanceDate = if (Random.nextBoolean()) Date(System.currentTimeMillis() - Random.nextLong(0, 90L * 24 * 60 * 60 * 1000)) else null,
                coordinates = Coordinates(
                    latitude = 35.6762 + Random.nextDouble(-0.1, 0.1), // Tokyo area
                    longitude = 139.6503 + Random.nextDouble(-0.1, 0.1)
                )
            )
        }
    }

    fun generateRecentReadings(meters: List<Meter>): List<MeterReading> {
        val readings = mutableListOf<MeterReading>()

        meters.take(10).forEach { meter ->
            repeat(Random.nextInt(1, 5)) { index ->
                readings.add(
                    MeterReading(
                        id = "RD${System.currentTimeMillis()}${index}",
                        meterId = meter.id,
                        reading = Random.nextDouble(1000.0, 5000.0),
                        timestamp = Date(System.currentTimeMillis() - Random.nextLong(0, 24L * 60 * 60 * 1000)), // Last 24 hours
                        readBy = listOf("system", "admin", "reader").random(),
                        readingType = ReadingType.values().random(),
                        quality = ReadingQuality.values().random(),
                        notes = if (Random.nextBoolean()) "Normal reading" else null
                    )
                )
            }
        }

        return readings.sortedByDescending { it.timestamp }
    }

    fun generateSystemOverview(meters: List<Meter>): SystemOverview {
        val activeCount = meters.count { it.status == MeterStatus.ACTIVE }
        val offlineCount = meters.count { it.status == MeterStatus.OFFLINE }
        val maintenanceCount = meters.count { it.status == MeterStatus.MAINTENANCE }

        return SystemOverview(
            totalMeters = meters.size,
            activeMeters = activeCount,
            offlineMeters = offlineCount,
            maintenanceMeters = maintenanceCount,
            todayReadings = Random.nextInt(50, 200),
            pendingExports = Random.nextInt(0, 10),
            lastSyncTime = Date(System.currentTimeMillis() - Random.nextLong(0, 60L * 60 * 1000)), // Last hour
            syncStatus = SyncStatus.values().random()
        )
    }

    fun generateConsumptionSummary(): ConsumptionSummary {
        val monthlyTotal = Random.nextDouble(10000.0, 50000.0)
        return ConsumptionSummary(
            totalConsumption = monthlyTotal,
            dailyAverage = monthlyTotal / 30,
            weeklyTotal = monthlyTotal / 4,
            monthlyTotal = monthlyTotal,
            peakUsage = Random.nextDouble(200.0, 500.0),
            peakUsageTime = Date(System.currentTimeMillis() - Random.nextLong(0, 7L * 24 * 60 * 60 * 1000)) // Last week
        )
    }

    fun generateAlerts(): List<Alert> {
        val alerts = mutableListOf<Alert>()

        repeat(Random.nextInt(2, 8)) { index ->
            alerts.add(
                Alert(
                    id = "ALT${System.currentTimeMillis()}${index}",
                    type = AlertType.values().random(),
                    title = when (AlertType.values().random()) {
                        AlertType.HIGH_CONSUMPTION -> "High consumption detected"
                        AlertType.METER_OFFLINE -> "Meter offline"
                        AlertType.READING_OVERDUE -> "Reading overdue"
                        AlertType.SYSTEM_MAINTENANCE -> "System maintenance scheduled"
                        AlertType.ERROR -> "System error occurred"
                        AlertType.WARNING -> "System warning"
                    },
                    message = "This is a sample alert message for demonstration purposes.",
                    timestamp = Date(System.currentTimeMillis() - Random.nextLong(0, 7L * 24 * 60 * 60 * 1000)), // Last week
                    severity = AlertSeverity.values().random(),
                    isRead = Random.nextBoolean(),
                    meterId = if (Random.nextBoolean()) "MTR${String.format("%03d", Random.nextInt(1, 26))}" else null
                )
            )
        }

        return alerts.sortedByDescending { it.timestamp }
    }
}