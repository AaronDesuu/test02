package com.example.meterkenshin.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import com.example.meterkenshin.R
import com.example.meterkenshin.model.Meter
import com.example.meterkenshin.model.MeterReading
import com.example.meterkenshin.ui.viewmodel.MeterReadingViewModel
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Universal Modern Meter Card Component with MeterReadingViewModel Integration
 * This component works with MeterReadingViewModel and can be reused across screens
 * Updated to match the PNG design with three status states at bottom
 */
@Composable
fun ModernMeterCard(
    meter: Meter,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    reading: MeterReading? = null,
    showChevron: Boolean = true,
    customContent: (@Composable () -> Unit)? = null,
    dlmsMaxDemand: Double? = null, // Will be obtained via DLMS later
    meterReadingViewModel: MeterReadingViewModel? = null, // For future DLMS integration
    inspectionStatus: InspectionStatus = InspectionStatus.NOT_INSPECTED // New status for bottom bar
) {
    // Determine connection status based on activate field from CSV (via MeterReadingViewModel)
    val connectionStatus = remember(meter.activate) {
        when (meter.activate) {
            1 -> ConnectionStatus.ONLINE_EXCELLENT
            0 -> ConnectionStatus.OFFLINE
            else -> ConnectionStatus.OFFLINE
        }
    }

    Card(
        onClick = onClick,
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
        )
    ) {
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

                    // Last read date from CSV column 11 (readDate/lastMaintenanceDate)
                    meter.lastMaintenanceDate?.let { lastDate ->
                        val formatter = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                        Text(
                            text = "Last read: ${formatter.format(lastDate)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } ?: run {
                        Text(
                            text = "Last read: Unknown",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    // Location
                    Text(
                        text = meter.location,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    // Show Import kWh from CSV column 5 (Imp [kWh])
                    val usageText = meter.impKWh?.let {
                        "${String.format("%.0f", it)}kWh"
                    } ?: "0kWh"

                    Text(
                        text = "usage: " + usageText,
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
                        color = connectionStatus.color,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Show chevron if enabled
                if (showChevron) {
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = stringResource(R.string.view_details),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Bottom section with max demand and status bar
        Column {
            // Max demand display (bottom right, above status bar)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                // Max demand from CSV (impMaxDemandKW) or formatted usage as max demand
                val maxDemandText = meter.impMaxDemandKW?.let {
                    String.format("%07.1f kWh", it)
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

            // Status bar with three states
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp)
                    .background(
                        color = when (inspectionStatus) {
                            InspectionStatus.INSPECTED_BILLING_PRINTED -> Color(0xFF4CAF50) // Green
                            InspectionStatus.INSPECTED_BILLING_NOT_PRINTED -> Color(0xFFFF9800) // Yellow/Orange
                            InspectionStatus.NOT_INSPECTED -> Color(0xFFF44336) // Red
                        },
                        shape = RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)
                    )
            ) {
                Text(
                    text = when (inspectionStatus) {
                        InspectionStatus.INSPECTED_BILLING_PRINTED -> "Inspected & Billing Printed"
                        InspectionStatus.INSPECTED_BILLING_NOT_PRINTED -> "Inspected, Billing not Printed"
                        InspectionStatus.NOT_INSPECTED -> "Not Inspected"
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
 * These states will be determined by DLMS communication later
 */
enum class InspectionStatus(
    val displayName: String,
    val color: Color
) {
    INSPECTED_BILLING_PRINTED("Inspected & Billing Printed", Color(0xFF4CAF50)), // Green
    INSPECTED_BILLING_NOT_PRINTED("Inspected, Billing not Printed", Color(0xFFFF9800)), // Yellow
    NOT_INSPECTED("Not Inspected", Color(0xFFF44336)) // Red
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
 * Helper function to determine inspection status based on DLMS data
 * Currently returns NOT_INSPECTED, will be updated when DLMS is implemented
 */
@Composable
fun getInspectionStatus(meter: Meter, dlmsData: Any? = null): InspectionStatus {
    // TODO: Implement DLMS-based inspection status logic
    // For now, return NOT_INSPECTED as default
    return InspectionStatus.NOT_INSPECTED
}

/**
 * Statistics card for meter overview
 */
@Composable
fun MeterStatCard(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.1f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Helper function to get connection status text for status bar
 * Note: Inspection and billing states will be implemented later via DLMS
 */
@Composable
fun getConnectionStatusText(meter: Meter): String {
    return when (meter.activate) {
        1 -> "Online"
        0 -> "Offline"
        else -> "Unknown"
    }
}

/**
 * Helper function to get status bar color
 * Note: Additional inspection/billing status colors will be added later via DLMS
 */
@Composable
fun getStatusBarColor(meter: Meter): Color {
    return when (meter.activate) {
        1 -> Color(0xFF4CAF50) // Green for active/online
        0 -> Color(0xFFF44336) // Red for inactive/offline
        else -> Color(0xFF757575) // Gray for unknown
    }
}