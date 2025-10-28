package com.example.meterkenshin.model

class Billing {
    var Period: String? = null
    var Commercial: String? = null
    var SerialID: String? = null
    var Multiplier: Float? = null
    var PeriodTo: String? = null
    var PresReading: Float? = null
    var PeriodFrom: String? = null
    var PrevReading: Float? = null
    var MaxDemand: Float? = null
    var TotalUse: Float? = null
    var GenTransCharges: Float? = null
    var DistributionCharges: Float? = null
    var SustainableCapex: Float? = null
    var OtherCharges: Float? = null
    var UniversalCharges: Float? = null
    var ValueAddedTax: Float? = null
    var TotalAmount: Float? = null
    var Discount: Float? = null
    var Interest: Float? = null
    var DueDate: String? = null
    var DiscoDate: String? = null
    var Reader: String? = null
    var ReadDatetime: String? = null
    var Version: String? = null
}

/**
 * Data class for billing record
 */
data class BillingRecord(
    val clock: String,      // Date/time
    val imp: Float,         // Import energy [kWh]
    val exp: Float,         // Export energy [kWh]
    val abs: Float,         // Absolute energy [kWh]
    val net: Float,         // Net energy [kWh]
    val maxImp: Float,      // Max import demand [W]
    val maxExp: Float,      // Max export demand [W]
    val minVolt: Float,     // Minimum voltage [V]
    val alert1: String,     // Alert status 1
    val alert2: String      // Alert status 2
)
