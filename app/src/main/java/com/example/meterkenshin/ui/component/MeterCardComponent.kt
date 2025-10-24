package com.example.meterkenshin.ui.component

import android.annotation.SuppressLint
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.meterkenshin.model.Meter
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Universal Modern Meter Card Component with MeterReadingViewModel Integration
 * This component works with MeterReadingViewModel and can be reused across screens
 * Updated to match the PNG design with three status states at bottom
 * MODIFIED: Added "Meter not Registered" status for activate=0 with pale/transparent card
 */
@SuppressLint("DefaultLocale")
@Composable
fun ModernMeterCard(
    meter: Meter,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showChevron: Boolean = true,
    customContent: (@Composable () -> Unit)? = null,
    isNearby: Boolean = false,
    inspectionStatus: InspectionStatus = getInspectionStatus(meter, isNearby),
    signalStrength: Int? = null,
) {
    // Check if meter is not registered (activate = 0)
    val isNotRegistered = meter.activate == 0

    // Determine connection status based on activate field from CSV (via MeterReadingViewModel)
    val connectionStatus = when {
        isNearby -> ConnectionStatus.ONLINE_EXCELLENT
        else -> ConnectionStatus.OFFLINE
    }

    Card(
        onClick = onClick,
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isNearby) 4.dp else 2.dp
        ),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
        )
    ) {
        Column {
            // Main content area
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left side - Meter icon and ID
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    // Meter icon with status indicator
                    Box {
                        Icon(
                            imageVector = Icons.Default.ElectricBolt,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(40.dp)
                        )

                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // Meter information
                    Column {
                        // Serial Number from CSV column 2
                        Text(
                            text = meter.serialNumber,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(4.dp))


                        Text(
                            text = meter.location,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Normal
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        // Energy consumption from CSV Imp [kWh] column 5
                        val usageText = meter.impKWh?.let {
                            "${String.format("%.1f", it)}kWh"
                        } ?: "0kWh"

                        // Last read date from CSV column 11 (readDate/lastMaintenanceDate)
                        meter.readDate?.let { lastDate ->
                            val formatter = SimpleDateFormat("dd MMM yyyy, HH:mm:ss", Locale.getDefault())
                            Text(
                                text = "Last Read\n ${formatter.format(lastDate)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } ?: run {
                            Text(
                                text = "No readings",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "usage: $usageText",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Right side - Status and chevron
                Column(
                    horizontalAlignment = Alignment.End
                ) {
                    // Connection status indicator
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(connectionStatus.color)
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Text(
                            text = connectionStatus.displayName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Signal strength if available
                    signalStrength?.let { rssi ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Cable,
                                contentDescription = null,
                                tint = getSignalColor(rssi),
                                modifier = Modifier.size(12.dp)
                            )

                            Spacer(modifier = Modifier.width(4.dp))

                            Text(
                                text = "${getSignalQuality(rssi)} ($rssi dBm)",
                                style = MaterialTheme.typography.bodySmall,
                                color = getSignalColor(rssi),
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    // Chevron icon
                    if (showChevron) {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "View details",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Max Demand display area (right aligned above status bar)
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.End
            ) {
                // Max demand text from impMaxDemandKW or impKWh
                val maxDemandText = meter.impMaxDemandKW?.let {
                    String.format("%07.1f kW", it)
                } ?: run {
                    // Use impKWh as max demand if impMaxDemandKW is not available
                    meter.impKWh?.let {
                        String.format("%07.1f kWh", it)
                    } ?: "0000000.0 kWh"
                }

                Text(
                    text = maxDemandText,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(end = 16.dp, bottom = 8.dp)
                )
            }

            // Status bar with inspection status - Shows "Meter not Registered" when activate=0
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp)
                    .background(
                        color = if (isNotRegistered) {
                            Color(0xFF9E9E9E) // Gray color for not registered
                        } else {
                            when (inspectionStatus) {
                                InspectionStatus.INSPECTED_BILLING_PRINTED -> Color(0xFF4CAF50) // Green
                                InspectionStatus.INSPECTED_BILLING_NOT_PRINTED -> Color(0xFFFF9800) // Yellow/Orange
                                InspectionStatus.NOT_INSPECTED -> Color(0xFFF44336) // Red
                            }
                        },
                        shape = RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)
                    )
            ) {
                Text(
                    text = if (isNotRegistered) {
                        "Meter not Registered"
                    } else {
                        when (inspectionStatus) {
                            InspectionStatus.INSPECTED_BILLING_PRINTED -> "Inspected & Billing Printed"
                            InspectionStatus.INSPECTED_BILLING_NOT_PRINTED -> "Inspected, Billing not Printed"
                            InspectionStatus.NOT_INSPECTED -> "Not Inspected"
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 8.dp)
                )
            }
        }

        // Custom content if provided
        customContent?.invoke()
    }
}

/**
 * Inspection status enum for the three states shown in PNG
 * These states are now determined by actual meter data
 */
enum class InspectionStatus(
    val color: Color
) {
    INSPECTED_BILLING_PRINTED(Color(0xFF4CAF50)), // Green
    INSPECTED_BILLING_NOT_PRINTED(Color(0xFFFF9800)), // Yellow
    NOT_INSPECTED(Color(0xFFF44336)) // Red
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
 * Helper function to determine inspection status based on meter data
 * Uses the meter's properties to determine the actual inspection and billing status
 */
@Composable
fun getInspectionStatus(meter: Meter, isNearby: Boolean): InspectionStatus {
    // Use meter data to determine inspection status
    return when {
        meter.readDate == null && (meter.impKWh == null || meter.impKWh == 0.0) -> {
            InspectionStatus.NOT_INSPECTED
        }

        // If meter has readings
        meter.readDate != null -> {
            InspectionStatus.INSPECTED_BILLING_NOT_PRINTED
        }

        // Default case - not inspected
        else -> InspectionStatus.NOT_INSPECTED
    }
}

fun getSignalColor(rssi: Int): Color {
    return when {
        rssi >= -50 -> Color(0xFF4CAF50) // Excellent: Green
        rssi >= -70 -> Color(0xFF8BC34A) // Good: Light Green
        rssi >= -85 -> Color(0xFFFFC107) // Fair: Orange
        else -> Color(0xFFF44336) // Poor: Red
    }
}

fun getSignalQuality(rssi: Int): String {
    return when {
        rssi >= -50 -> "Excellent"
        rssi >= -70 -> "Good"
        rssi >= -85 -> "Fair"
        else -> "Poor"
    }
}