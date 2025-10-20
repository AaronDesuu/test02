package com.example.meterkenshin.model

import java.util.Date

data class Meter(
    // Existing variable
    val serialNumber: String,
    val fixedDate: Date? = null,
    val impKWh: Double? = null,
    val expKWh: Double? = null,
    val impMaxDemandKW: Double? = null,
    val expMaxDemandKW: Double? = null,
    val minVoltV: Double? = null,
    val alert: Double? = null,
    val readDate: Date?,

    // New variable
    val location: String,
    val type: MeterType,
    val status: MeterStatus,
    val installationDate: Date?,
    val bluetoothId: String? = null,
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


