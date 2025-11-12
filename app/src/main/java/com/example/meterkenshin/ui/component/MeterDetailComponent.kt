package com.example.meterkenshin.ui.component

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.meterkenshin.R
import com.example.meterkenshin.data.MeterSpecifications
import com.example.meterkenshin.model.Meter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


/**
 * Meter specifications card with enhanced CSV data support using MeterModel
 * Now uses MeterSpecifications data class for easy management
 */
@SuppressLint("DefaultLocale")
@Composable
fun MeterSpecificationsCard(
    meter: Meter,
    modifier: Modifier = Modifier
) {
    // Get the appropriate specifications for this meter
    val specs = MeterSpecifications.getSpecificationForMeter(meter.type.name)

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.meter_info),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Basic meter info

            SpecificationRow(
                label = "Meter Type",
                value = meter.type.displayName.ifBlank { "-" }
            )
            // Bluetooth ID
            SpecificationRow(
                label = "Bluetooth ID",
                value = meter.bluetoothId ?: "-"
            )
            SpecificationRow(
                label = "Phase Wire",
                value = specs.phaseWire
            )
            SpecificationRow(
                label = "Protocol",
                value = specs.protocol
            )
            SpecificationRow(
                label = "Communication",
                value = specs.communication
            )
            SpecificationRow(
                label = "V/A Rating",
                value = specs.voltageAmperageRating
            )
            SpecificationRow(
                label = "Frequency",
                value = specs.frequency
            )
            SpecificationRow(
                label = "Pulse Constant",
                value = specs.pulseConstant
            )
            SpecificationRow(
                label = "BLE Version",
                value = specs.bleVersion
            )
        }
    }
}

/**
 * Updated MeterStatusCard with BLE connection and RSSI info
 */
@Composable
fun MeterStatusCard(
    meter: Meter,
    rssi: Int = -200,
    isNearby: Boolean = false,
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier
) {
    // Connection status based on RSSI
    val connectionStatus = when {
        !isNearby -> stringResource(R.string.ble_status_disconnected)
        rssi >= -70 -> stringResource(R.string.ble_status_connected)
        rssi >= -85 -> "${stringResource(R.string.ble_status_connected)} (Fair)"
        else -> "${stringResource(R.string.ble_status_connected)} (Poor)"
    }

    val signalColor = when {
        !isNearby -> Color.Gray
        rssi >= -70 -> Color(0xFF4CAF50) // Green - Excellent
        rssi >= -85 -> Color(0xFFFFC107) // Yellow - Fair
        else -> Color(0xFFF44336) // Red - Poor
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            // Serial Number
            SpecificationRow(
                label = stringResource(R.string.serial_id).replace(": %1\$s", ""),
                value = meter.serialNumber.ifBlank { "-" }
            )

            // UID
            SpecificationRow(
                label = stringResource(R.string.uid).replace(": %1\$s", ""),
                value = meter.uid.toString().ifBlank { "-" }
            )

            // BLE Connection Status
            SpecificationRow(
                label = stringResource(R.string.status),
                value = connectionStatus,
                valueColor = signalColor
            )

            // Signal Strength (only show when nearby)
            if (isNearby && rssi > -200) {
                SpecificationRow(
                    label = stringResource(R.string.signal_strength).replace(": %1\$s dBm", ""),
                    value = "$rssi dBm",
                    valueColor = signalColor
                )
            }



            // Location
            SpecificationRow(
                label = "Location",
                value = meter.location.ifBlank { "-" }
            )

            // Last Communication
            val lastCommText = meter.lastCommunication?.let { lastComm ->
                val now = Date()
                val diffMillis = now.time - lastComm.time
                val diffMinutes = diffMillis / (1000 * 60)
                val diffHours = diffMinutes / 60
                val diffDays = diffHours / 24

                when {
                    diffMinutes < 1 -> "Just now"
                    diffMinutes < 60 -> "$diffMinutes min${if (diffMinutes > 1L) "s" else ""} ago"
                    diffHours < 24 -> "$diffHours hour${if (diffHours > 1L) "s" else ""} ago"
                    diffDays < 7 -> "$diffDays day${if (diffDays > 1L) "s" else ""} ago"
                    else -> {
                        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(lastComm)
                    }
                }
            } ?: "Never"

            SpecificationRow(
                label = "Last Communication",
                value = lastCommText
            )
        }
    }
}

/**
 * Modern specification row component with null-safe display
 */
@Composable
private fun SpecificationRow(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    isHighlighted: Boolean = false,
    isSensitive: Boolean = false,
    valueColor: Color? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )

        Text(
            text = if (isSensitive && value != "-") "••••••••••••••••" else value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.Medium,
            color = valueColor ?: if (isHighlighted) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f)
        )
    }
}
