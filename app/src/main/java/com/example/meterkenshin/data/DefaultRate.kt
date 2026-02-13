package com.example.meterkenshin.data


/**
 * Provides the default rates for billing calculations.
 * These values are used as a fallback when a rate.csv file is not available.
 *
 * @return A [FloatArray] containing the 23 default rate values.
 */
internal fun getDefaultRates(): FloatArray {
    return floatArrayOf(
        2.5f, 150.0f, 0.1f,           // Gen/Trans charges (0-2)
        50.0f, 100.0f, 50.0f,         // Distribution charges (3-5)
        0.05f, 0.03f,                 // Sustainable CAPEX (6-7)
        0.02f, 0.01f,                 // Other charges (8-9)
        0.001f, 0.12f, 0.0025f,       // Universal charges (10-15)
        0.04f, 0.1f, 0.25f,
        0.0f, 0.3f, 0.3f, 0.012f,    // VAT per kWh (16-20): CAPEX, Trans, Gen, SysLoss, Universal
        0.0f, 0.12f, 0.12f           // VAT multipliers (21-22): Distribution, Other
    )
}