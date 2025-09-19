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
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Universal Modern Meter Card Component
 * This component is shared between HomeScreen and MeterReadingScreen
 * Any design updates here will automatically apply to both screens
 */
@Composable
fun ModernMeterCard(
    meter: Meter,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    reading: MeterReading? = null,
    showChevron: Boolean = true,
    customContent: (@Composable () -> Unit)? = null
) {
    val isOnline = remember(meter) { meter.status.displayName == "Active" }
    val hasBluetoothId = remember(meter) { !meter.bluetoothId.isNullOrEmpty() }

    Card(
        onClick = onClick,
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
        )
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left side - Meter Icon with Status
            MeterIconSection(
                isOnline = isOnline,
                hasBluetoothId = hasBluetoothId
            )

            Spacer(modifier = Modifier.width(16.dp))

            // Middle section - Meter Details
            MeterDetailsSection(
                meter = meter,
                reading = reading,
                modifier = Modifier.weight(1f)
            )

            // Right side - Additional Info and Navigation
            MeterActionSection(
                meter = meter,
                reading = reading,
                showChevron = showChevron,
                customContent = customContent
            )
        }
    }
}

@Composable
private fun MeterIconSection(
    isOnline: Boolean,
    hasBluetoothId: Boolean
) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(
                when {
                    isOnline && hasBluetoothId -> MaterialTheme.colorScheme.primaryContainer
                    isOnline -> MaterialTheme.colorScheme.secondaryContainer
                    else -> MaterialTheme.colorScheme.errorContainer
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.ElectricBolt,
            contentDescription = null,
            tint = when {
                isOnline && hasBluetoothId -> MaterialTheme.colorScheme.onPrimaryContainer
                isOnline -> MaterialTheme.colorScheme.onSecondaryContainer
                else -> MaterialTheme.colorScheme.onErrorContainer
            },
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
private fun MeterDetailsSection(
    meter: Meter,
    reading: MeterReading?,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        // Serial Number (Primary identifier)
        Text(
            text = "S/N: ${meter.serialNumber}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Location
        Text(
            text = meter.location.ifBlank { "Unknown Location" },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Status and Connection Info Row
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Status indicator dot
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(
                        Color(meter.status.colorHex.toColorInt())
                    )
            )
            Spacer(modifier = Modifier.width(8.dp))

            // Status text with connection type
            val statusText = buildString {
                append(meter.status.displayName)
                if (!meter.bluetoothId.isNullOrEmpty()) {
                    append(" • BT")
                }
            }

            Text(
                text = statusText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Last Reading/Maintenance Info
        Spacer(modifier = Modifier.height(4.dp))
        val lastReadText = when {
            reading != null -> {
                "Last Read: ${SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(reading.timestamp)}"
            }
            meter.lastMaintenanceDate != null -> {
                "Last Maintenance: ${SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(meter.lastMaintenanceDate)}"
            }
            else -> "No recent activity"
        }

        Text(
            text = lastReadText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1
        )
    }
}

@Composable
private fun MeterActionSection(
    meter: Meter,
    reading: MeterReading?,
    showChevron: Boolean,
    customContent: (@Composable () -> Unit)?
) {
    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Custom content or default reading/signal info
        if (customContent != null) {
            customContent()
        } else {
            // Show reading value if available
            if (reading?.reading != null) {
                Text(
                    text = String.format("%.2f kWh", reading.reading),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.End
                )
            } else if (meter.impKWh != null) {
                // Show import energy from meter data
                Text(
                    text = String.format("%.2f kWh", meter.impKWh),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.End
                )
            } else {
                // Show signal strength or connection status
                val signalText = when {
                    meter.minVoltV != null -> "${meter.minVoltV}V"
                    meter.bluetoothId != null -> "BT Ready"
                    else -> "No Data"
                }

                Text(
                    text = signalText,
                    style = MaterialTheme.typography.bodySmall,
                    color = when {
                        meter.minVoltV != null && meter.minVoltV > 220 -> Color(0xFF4CAF50)
                        meter.minVoltV != null && meter.minVoltV > 200 -> Color(0xFFFF9800)
                        meter.minVoltV != null -> Color(0xFFF44336)
                        meter.bluetoothId != null -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    textAlign = TextAlign.End
                )
            }
        }

    }
}

/**
 * Compact version of the meter card for smaller spaces
 */
@Composable
fun CompactMeterCard(
    meter: Meter,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    reading: MeterReading? = null
) {
    val isOnline = remember(meter) { meter.status.displayName == "Active" }

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
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Status indicator
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(Color(meter.status.colorHex.toColorInt()))
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Serial number
            Text(
                text = meter.serialNumber,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Reading or status
            if (reading?.reading != null) {
                Text(
                    text = String.format("%.1f kWh", reading.reading),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                Text(
                    text = meter.status.displayName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
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