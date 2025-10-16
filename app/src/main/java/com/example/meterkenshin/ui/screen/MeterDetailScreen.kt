package com.example.meterkenshin.ui.screen

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.meterkenshin.R
import com.example.meterkenshin.model.Meter
import java.text.SimpleDateFormat
import java.util.Locale
import androidx.core.graphics.toColorInt
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.meterkenshin.data.MeterSpecifications
import com.example.meterkenshin.ui.viewmodel.MeterReadingViewModel

/**
 * Modern Meter Detail Screen with updated design and theme consistency
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeterDetailScreen(
    meter: Meter,
    meterReadingViewModel: MeterReadingViewModel = viewModel(),
    onBack: () -> Unit = {},
    onRegistration: () -> Unit = {},
    onReadData: () -> Unit = {},
    onLoadProfile: () -> Unit = {},
    onEventLog: () -> Unit = {},
    onBillingData: () -> Unit = {},
    onSetClock: () -> Unit = {}
) {
    // Collect BLE states
    val discoveredDevices by meterReadingViewModel.discoveredDevices.collectAsState()

    // Get RSSI for this meter
    val rssi = meter.bluetoothId?.let {
        discoveredDevices[it.uppercase()]
    } ?: -200

    val isNearby = meterReadingViewModel.isMeterNearby(meter.bluetoothId)

    // Connection status based on RSSI
    val connectionStatus = when {
        !isNearby -> stringResource(R.string.ble_status_disconnected)
        rssi >= -70 -> stringResource(R.string.ble_status_connected)
        rssi >= -85 -> "${stringResource(R.string.ble_status_connected)} (Fair)"
        else -> "${stringResource(R.string.ble_status_connected)} (Poor)"
    }

    val signalColor = when {
        !isNearby -> Color.Gray
        rssi >= -70 -> Color(0xFF4CAF50) // Green
        rssi >= -85 -> Color(0xFFFFC107) // Yellow
        else -> Color(0xFFF44336) // Red
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            MeterStatusCard(
                meter = meter,
                rssi = rssi,
                isNearby = isNearby
            )

            // DLMS Functions Card
            DLMSFunctionsCard(
                onRegistration = onRegistration,
                onReadData = onReadData,
                onLoadProfile = onLoadProfile,
                onEventLog = onEventLog,
                onBillingData = onBillingData,
                onSetClock = onSetClock
            )

            // Meter Specifications Card
            MeterSpecificationsCard(meter = meter)

            Spacer(modifier = Modifier.height(16.dp))
            Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
}

/**
 * DLMS functions card with correct parameters
 */
@Composable
private fun DLMSFunctionsCard(
    onRegistration: () -> Unit,
    onReadData: () -> Unit,
    onLoadProfile: () -> Unit,
    onEventLog: () -> Unit,
    onBillingData: () -> Unit,
    onSetClock: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.dlms_functions),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            FunctionButton(
                text = stringResource(R.string.dlms_registration),
                icon = Icons.Default.Person,
                onClick = onRegistration,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))

            FunctionButton(
                text = stringResource(R.string.dlms_read_data),
                icon = Icons.Default.Assessment,
                onClick = onReadData,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))

            FunctionButton(
                text = stringResource(R.string.dlms_load_profile),
                icon = Icons.Default.Storage,
                onClick = onLoadProfile,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))

            FunctionButton(
                text = stringResource(R.string.dlms_event_log),
                icon = Icons.Default.Event,
                onClick = onEventLog,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))

            FunctionButton(
                text = stringResource(R.string.dlms_billing_data),
                icon = Icons.Default.Payment,
                onClick = onBillingData,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))

            FunctionButton(
                text = stringResource(R.string.dlms_set_clock),
                icon = Icons.Default.AccessTime,
                onClick = onSetClock,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}


/**
 * Function button component
 */
@Composable
private fun FunctionButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    isActive: Boolean = true,
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(56.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isActive) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            contentColor = if (isActive) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        ),
        shape = RoundedCornerShape(16.dp),
        enabled = true
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * Meter specifications card with enhanced CSV data support using MeterModel
 * Now uses MeterSpecifications data class for easy management
 */
@SuppressLint("DefaultLocale")
@Composable
private fun MeterSpecificationsCard(
    meter: Meter,
    modifier: Modifier = Modifier
) {
    // Get the appropriate specifications for this meter
    val specs = MeterSpecifications.getSpecificationForMeter(meter.type.name)

    // Date formatter for last reading
    val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())

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

            // Enhanced data from CSV
            if (meter.impKWh != null || meter.expKWh != null ||
                meter.impMaxDemandKW != null || meter.expMaxDemandKW != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Energy Readings",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                SpecificationRow(
                    label = "Import Energy (kWh)",
                    value = meter.impKWh?.let { String.format("%.2f", it) } ?: "-"
                )
                SpecificationRow(
                    label = "Export Energy (kWh)",
                    value = meter.expKWh?.let { String.format("%.2f", it) } ?: "-"
                )
                SpecificationRow(
                    label = "Import Max Demand (kW)",
                    value = meter.impMaxDemandKW?.let { String.format("%.2f", it) } ?: "-"
                )
                SpecificationRow(
                    label = "Export Max Demand (kW)",
                    value = meter.expMaxDemandKW?.let { String.format("%.2f", it) } ?: "-"
                )
                SpecificationRow(
                    label = "Last Reading Date",
                    value = meter.lastMaintenanceDate?.let { dateFormat.format(it) } ?: "-"
                )
            }
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
private fun MeterStatusCard(
    meter: Meter,
    rssi: Int = -200,
    isNearby: Boolean = false,
    modifier: Modifier = Modifier
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Cable,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.meter_stats),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }

            // Serial Number
            SpecificationRow(
                label = stringResource(R.string.serial_id).replace(": %1\$s", ""),
                value = meter.serialNumber.ifBlank { "-" }
            )

            // BLE Connection Status
            SpecificationRow(
                label = stringResource(R.string.status_connected),
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

            // Last Communication (placeholder - can be updated with actual data)
            SpecificationRow(
                label = "Last Communication",
                value = if (isNearby) "Just now" else "-"
            )
        }
    }
}

/**
 * Modern specification row component with null-safe display
 */
@Composable
private fun SpecificationRow(
    label: String,
    value: String,
    isHighlighted: Boolean = false,
    isSensitive: Boolean = false,
    valueColor: Color? = null,
    modifier: Modifier = Modifier
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

