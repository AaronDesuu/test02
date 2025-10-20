package com.example.meterkenshin.model

import java.util.Date

data class Meter(
    val id: String,
    val serialNumber: String,
    val location: String,
    val type: MeterType,
    val status: MeterStatus,
    val installationDate: Date?,
    val lastMaintenanceDate: Date?,
    val coordinates: Coordinates?,
    val activate: Int = 0,
    val bluetoothId: String? = null,
    val fixedDate: Date? = null,
    val impKWh: Double? = null,
    val expKWh: Double? = null,
    val impMaxDemandKW: Double? = null,
    val expMaxDemandKW: Double? = null,
    val minVoltV: Double? = null,
    val alert: Double? = null
)

enum class MeterType(val displayName: String) {
    Type01("FT5"),
}

enum class MeterStatus() {  // Currently used in MeterTestViewmodel
    ACTIVE(),
    OFFLINE(),
    MAINTENANCE(),
    ERROR()
}

data class Coordinates(     // Currently used in MeterTestViewmodel
    val latitude: Double,
    val longitude: Double
)

