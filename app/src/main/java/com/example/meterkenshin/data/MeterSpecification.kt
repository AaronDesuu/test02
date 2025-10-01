package com.example.meterkenshin.data

/**
 * Data class to hold meter specification constants
 * This makes it easy to manage and update meter specifications
 */
data class MeterSpecification(
    val phaseWire: String,
    val protocol: String,
    val communication: String,
    val voltageAmperageRating: String,
    val frequency: String,
    val pulseConstant: String,
    val bleVersion: String,

)

/**
 * Object to provide default meter specifications
 * You can easily modify these values or create different specification profiles
 */
object MeterSpecifications {

    /**
     * Default meter specifications
     */
    val DEFAULT = MeterSpecification(
        phaseWire = "Single-phase and 2-wire",
        protocol = "IEC 62056-21",
        communication = "DLMS/COSEM",
        voltageAmperageRating = "240V/100A",
        frequency = "50Hz",
        pulseConstant = "1600imp/kWh",
        bleVersion = "5.1"
    )

    /**
     * Alternative specification profile for high-capacity meters
     */
    val VER1 = MeterSpecification(
        phaseWire = "Single-phase and 2-wire",
        protocol = "IEC 62056-21",
        communication = "DLMS/COSEM",
        voltageAmperageRating = "240V/100A",
        frequency = "50Hz",
        pulseConstant = "1600imp/kWh",
        bleVersion = "5.1"
    )

    /**
     * Alternative specification profile for low-voltage meters
     */
    val VER2 = MeterSpecification(
        phaseWire = "Single-phase and 2-wire",
        protocol = "IEC 62056-21",
        communication = "DLMS/COSEM",
        voltageAmperageRating = "240V/100A",
        frequency = "50Hz",
        pulseConstant = "1600imp/kWh",
        bleVersion = "5.1"
    )

    /**
     * Get specification by meter type or use default
     * You can extend this to return different specs based on meter properties
     */
    fun getSpecificationForMeter(meterType: String?): MeterSpecification {
        return when (meterType?.uppercase()) {
            "VER1" -> VER1
            "VER2" -> VER2
            else -> DEFAULT
        }
    }
}