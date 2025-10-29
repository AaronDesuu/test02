package com.example.meterkenshin.model

@Suppress("ArrayInDataClass")
data class SavedBillingData(
    val billing: Billing,
    val timestamp: Long,
    val rates: FloatArray
) {
    // Check if data is still valid (within 30 days)
    fun isValid(): Boolean {
        val thirtyDaysInMillis = 30L * 24 * 60 * 60 * 1000
        return (System.currentTimeMillis() - timestamp) < thirtyDaysInMillis
    }

    fun daysRemaining(): Int {
        val thirtyDaysInMillis = 30L * 24 * 60 * 60 * 1000
        val elapsed = System.currentTimeMillis() - timestamp
        val remaining = thirtyDaysInMillis - elapsed
        return (remaining / (24 * 60 * 60 * 1000)).toInt()
    }
}