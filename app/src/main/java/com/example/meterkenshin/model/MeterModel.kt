package com.example.meterkenshin.model

import java.util.Date

data class Meter(
    // Existing variable
    val uid: Int,
    val activate: Int,
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
    val status: MeterStatus,
    val location: String,
    val type: MeterType,
    val installationDate: Date?,
    val bluetoothId: String? = null,
    val billingPrintDate: Date? = null,
    val lastCommunication: Date? = null,

    // DLMS credentials (add these)
    val account: String = "Admin",
    val key: String = "30303030303030303030303030303030",
    val logical: String = "61",
    val rank: Int = 1,
    )

enum class MeterType(val displayName: String) {
    Type01("FT5"),
}

enum class MeterStatus() {  // Currently used in MeterTestViewmodel
    ACTIVE(),
    OFFLINE(),
    ERROR()
}


