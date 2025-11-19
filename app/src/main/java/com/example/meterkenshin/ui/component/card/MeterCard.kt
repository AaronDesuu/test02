package com.example.meterkenshin.ui.component.card

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
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.platform.LocalContext
import com.example.meterkenshin.model.Meter
import com.example.meterkenshin.utils.*
import com.example.meterkenshin.ui.manager.AppPreferences
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Universal Modern Meter Card Component with MeterReadingViewModel Integration
 */
@SuppressLint("DefaultLocale")
@Composable
fun MeterCard(
    meter: Meter,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showChevron: Boolean = true,
    isNearby: Boolean = false,
    inspectionStatus: InspectionStatus = getInspectionStatus(meter),
    signalStrength: Int? = null,
    showCheckbox: Boolean = false,
    isSelected: Boolean = false,
) {
    val context = LocalContext.current
    val isPrintingEnabled = AppPreferences.isPrintingEnabled(context)

    // Check if meter is not registered (activate = 0)
    val isNotRegistered = meter.activate == 0
    val isDisabled = isNotRegistered && showCheckbox

    // Determine connection status based on activate field from CSV (via MeterReadingViewModel)
    val connectionStatus = when {
        isNearby -> ConnectionStatus.ONLINE
        else -> ConnectionStatus.OFFLINE
    }

    Card(
        onClick = {
            if (!isDisabled) {
                onClick()
            }
        },
        modifier = modifier,
        enabled = !isDisabled,
        colors = CardDefaults.cardColors(
            containerColor = if (isDisabled) {
                MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.surface
            }
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
                    // CHANGE: Show checkbox OR electric icon
                    if (showCheckbox && !isNotRegistered) {
                        // Selection mode - show checkbox
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { onClick() },
                            modifier = Modifier.size(40.dp)
                        )
                    } else {
                        // Normal mode - show electric icon
                        Box {
                            Icon(
                                imageVector = Icons.Default.ElectricBolt,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(40.dp)
                            )
                        }
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
                            // Truncate to one decimal place without rounding
                            val truncatedValue = (it * 10).toInt() / 10.0
                            "$truncatedValue kWh"
                        } ?: "0.0 kWh"

                        // Last read date from CSV column 11 (readDate/lastMaintenanceDate)
                        meter.readDate?.let { lastDate ->
                            val formatter = SimpleDateFormat("dd MMM yyyy, HH:mm:ss", Locale.getDefault())
                            Text(
                                text = "Last Read: ${formatter.format(lastDate)}",
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
                        if (isNearby && signalStrength != null) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            signalStrength >= -70 -> Color(0xFF4CAF50) // Good/Excellent - Green
                                            else -> Color(0xFFFF9800) // Fair/Poor - Yellow/Orange
                                        }
                                    )
                            )

                        } else {
                            // Offline - show red dot
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFF44336))
                            )
                        }

                        Spacer(modifier = Modifier.width(4.dp))

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
                                text = "${getSignalQuality(rssi)} $rssi dBm",
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
                        String.format("%07.1f kW", it)
                    } ?: "00000.0 kW"
                }

                Text(
                    text = "Max $maxDemandText",
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
                            Color(0xFF9E9E9E)
                        } else if (isPrintingEnabled) {
                            when (inspectionStatus) {
                                InspectionStatus.INSPECTED_BILLING_PRINTED -> Color(0xFF4CAF50)
                                InspectionStatus.INSPECTED_BILLING_NOT_PRINTED -> Color(0xFFFF9800)
                                InspectionStatus.NOT_INSPECTED -> Color(0xFFF44336)
                            }
                        } else {
                            // 2-state mode when printing disabled
                            when (getSimplifiedInspectionStatus(meter)) {
                                SimplifiedInspectionStatus.INSPECTED -> Color(0xFF4CAF50)
                                SimplifiedInspectionStatus.NOT_INSPECTED -> Color(0xFFF44336)
                            }
                        },
                        shape = RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)
                    )
            ) {
                Text(
                    text = if (isNotRegistered) {
                        "Meter not Registered"
                    } else if (isPrintingEnabled) {
                        when (inspectionStatus) {
                            InspectionStatus.INSPECTED_BILLING_PRINTED -> "Inspected & Billing Printed"
                            InspectionStatus.INSPECTED_BILLING_NOT_PRINTED -> "Inspected, Billing not Printed"
                            InspectionStatus.NOT_INSPECTED -> "Not Inspected"
                        }
                    } else {
                        // 2-state mode when printing disabled
                        when (getSimplifiedInspectionStatus(meter)) {
                            SimplifiedInspectionStatus.INSPECTED -> "Inspected"
                            SimplifiedInspectionStatus.NOT_INSPECTED -> "Not Inspected"
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
    }
}