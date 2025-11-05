package com.example.meterkenshin.utils

import androidx.compose.ui.graphics.Color
import com.example.meterkenshin.model.Meter

/**
 * Inspection status enum for the three states
 * These states are determined by actual meter data
 */
enum class InspectionStatus(
    val color: Color
) {
    INSPECTED_BILLING_PRINTED(Color(0xFF4CAF50)), // Green
    INSPECTED_BILLING_NOT_PRINTED(Color(0xFFFF9800)), // Yellow
    NOT_INSPECTED(Color(0xFFF44336)) // Red
}

/**
 * Helper function to determine inspection status based on meter data
 * Uses the meter's properties to determine the actual inspection and billing status
 *
 * EXTRACTED from MeterCardComponent to be universally usable (non-Composable)
 */
fun getInspectionStatus(meter: Meter): InspectionStatus {
    // Check if meter has been read
    if (meter.readDate == null || meter.impKWh == null) {
        return InspectionStatus.NOT_INSPECTED
    }

    // Meter has been inspected (has readDate and readings)
    // Now check billing print status
    if (meter.billingPrintDate != null) {
        // Calculate the difference in days
        val timeDiffMillis = meter.billingPrintDate.time - meter.readDate.time
        val daysDiff = timeDiffMillis / (1000 * 60 * 60 * 24)

        // Check if billing was printed within 30 days AFTER the read date
        if (daysDiff in 0..30) {
            return InspectionStatus.INSPECTED_BILLING_PRINTED
        }
    }

    // Meter has readings but billing not printed (or printed outside 30-day window)
    return InspectionStatus.INSPECTED_BILLING_NOT_PRINTED
}

/**
 * Connection status enum based on online/offline status
 */
enum class ConnectionStatus(
    val displayName: String,
    val color: Color
) {
    ONLINE_EXCELLENT("Online", Color(0xFF4CAF50)), // Green for online
    ONLINE_FAIR("Online", Color(0xFFFF9800)), // Yellow/Orange for fair signal
    OFFLINE("Offline", Color(0xFFF44336)) // Red for offline
}

/**
 * Get signal color based on RSSI
 */
fun getSignalColor(rssi: Int): Color {
    return when {
        rssi >= -50 -> Color(0xFF4CAF50) // Excellent: Best
        rssi >= -70 -> Color(0xFF8BC34A) // Good: Light Green
        rssi >= -85 -> Color(0xFFFFC107) // Fair: Orange
        else -> Color(0xFFF44336) // Poor: Red
    }
}

/**
 * Get signal quality description based on RSSI
 */
fun getSignalQuality(rssi: Int): String {
    return when {
        rssi >= -50 -> "Best"
        rssi >= -70 -> "Good"
        rssi >= -85 -> "Fair"
        else -> "Poor"
    }
}