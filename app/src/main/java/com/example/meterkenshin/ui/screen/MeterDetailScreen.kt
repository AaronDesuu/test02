package com.example.meterkenshin.ui.screen

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

/**
 * Modern Meter Detail Screen with updated design and theme consistency
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeterDetailScreen(
    meter: Meter,
    onBackPressed: () -> Unit = {},
    onRegistration: () -> Unit = {},
    onReadData: () -> Unit = {},
    onLoadProfile: () -> Unit = {},
    onEventLog: () -> Unit = {},
    onBillingData: () -> Unit = {},
    onSetClock: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // Main content with modern design
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Connection status card
            MeterStatusCard(meter = meter)

            // DLMS function buttons with modern design
            DLMSFunctionsCard(
                onRegistration = onRegistration,
                onReadData = onReadData,
                onLoadProfile = onLoadProfile,
                onEventLog = onEventLog,
                onBillingData = onBillingData,
                onSetClock = onSetClock
            )

            // Meter specifications card
            MeterSpecificationsCard(meter = meter)

            // Bottom padding for system bars
            Spacer(modifier = Modifier.height(16.dp))
            Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
}

/**
 * DLMS functions in a modern card design
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
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
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
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Function buttons in single column (6 rows)
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
    modifier: Modifier = Modifier
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
 */
@Composable
private fun MeterSpecificationsCard(
    meter: Meter,
    modifier: Modifier = Modifier
) {
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
            SpecificationRow(
                label = "Location",
                value = meter.location.ifBlank { "-" }
            )
            SpecificationRow(
                label = "Meter Type",
                value = meter.type.displayName.ifBlank { "-" }
            )
            SpecificationRow(
                label = "Status",
                value = meter.status.displayName,
                valueColor = Color(meter.status.colorHex.toColorInt())
            )

            // Enhanced data from CSV
            if (meter.impKWh != null || meter.expKWh != null || meter.impMaxDemandKW != null || meter.expMaxDemandKW != null) {
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
            }

            // System information
            if (meter.bluetoothId != null || meter.minVoltV != null || meter.alert != null || meter.activate != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "System Information",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                SpecificationRow(
                    label = "Bluetooth ID",
                    value = meter.bluetoothId ?: "-"
                )
                SpecificationRow(
                    label = "Min Voltage (V)",
                    value = meter.minVoltV?.let { String.format("%.1f", it) } ?: "-"
                )
                SpecificationRow(
                    label = "Alert Level",
                    value = meter.alert?.let { String.format("%.2f", it) } ?: "-"
                )
                SpecificationRow(
                    label = "Activation Status",
                    value = when (meter.activate) {
                        1 -> "Active"
                        0 -> "Inactive"
                        else -> "-"
                    }
                )
            }

            // Dates
            if (meter.fixedDate != null || meter.lastMaintenanceDate != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Dates",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                SpecificationRow(
                    label = "Installation Date",
                    value = meter.fixedDate?.let { dateFormat.format(it) } ?: "-"
                )
                SpecificationRow(
                    label = "Last Reading Date",
                    value = meter.lastMaintenanceDate?.let { dateFormat.format(it) } ?: "-"
                )
            }

            // Technical Specifications (static data)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Technical Specifications",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            SpecificationRow(label = "Protocol", value = stringResource(R.string.protocol_iec))
            SpecificationRow(label = "Communication", value = stringResource(R.string.communication_dlms))
            SpecificationRow(label = "V/A Rating", value = "240V/100A")
            SpecificationRow(label = "Frequency", value = "50Hz")
        }
    }
}

/**
 * Meter status card
 */
@Composable
private fun MeterStatusCard(
    meter: Meter,
    modifier: Modifier = Modifier
) {
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
            SpecificationRow(
                label = "Serial Number",
                value = meter.serialNumber.ifBlank { "-" }
            )
            SpecificationRow(
                label = stringResource(R.string.status_connected),
                value = stringResource(R.string.status_connected),
                valueColor = Color(0xFF4CAF50)
            )
            SpecificationRow(label = "Signal Strength", value = "-")
            SpecificationRow(label = "Last Communication", value = "-")
            SpecificationRow(label = "Last Billing Read", value = "-")
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

/**
 * Helper composable for detail rows
 */
@Composable
private fun DetailRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value.ifBlank { "-" },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f)
        )
    }
}