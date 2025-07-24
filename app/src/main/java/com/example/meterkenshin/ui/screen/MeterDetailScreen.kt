// app/src/main/java/com/example/meterkenshin/ui/screen/MeterDetailScreen.kt
package com.example.meterkenshin.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.meterkenshin.R
import com.example.meterkenshin.model.MeterData
import com.example.meterkenshin.model.MeterStatus

/**
 * Modern Meter Detail Screen with updated design and new data structure
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeterDetailScreen(
    meter: MeterData,
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
        // Modern Top App Bar
        CenterAlignedTopAppBar(
            title = {
                Text(
                    text = stringResource(R.string.meter_detail_title, meter.serialNumber),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            navigationIcon = {
                IconButton(onClick = onBackPressed) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back)
                    )
                }
            },
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
                titleContentColor = MaterialTheme.colorScheme.onSurface
            )
        )

        // Main content with modern design
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Meter Information Card
            MeterInfoCard(meter = meter)

            // Energy Data Card
            EnergyDataCard(meter = meter)

            // Technical Data Card
            TechnicalDataCard(meter = meter)

            // Action Buttons Section
            ActionButtonsSection(
                onRegistration = onRegistration,
                onReadData = onReadData,
                onLoadProfile = onLoadProfile,
                onEventLog = onEventLog,
                onBillingData = onBillingData,
                onSetClock = onSetClock
            )

            // Bottom spacing for navigation
            Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
}

/**
 * Meter information card
 */
@Composable
private fun MeterInfoCard(
    meter: MeterData,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.meter_information),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            // Status indicator
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(getStatusColor(meter.status))
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.status_format, meter.status.displayName),
                    style = MaterialTheme.typography.bodyLarge
                )
            }

            // Meter details
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                InfoRow(
                    label = stringResource(R.string.meter_uid),
                    value = meter.uid
                )
                InfoRow(
                    label = stringResource(R.string.serial_number),
                    value = meter.serialNumber
                )
                InfoRow(
                    label = stringResource(R.string.bluetooth_id),
                    value = meter.bluetoothId
                )
                if (!meter.fixedDate.isNullOrBlank()) {
                    InfoRow(
                        label = stringResource(R.string.fixed_date),
                        value = meter.fixedDate
                    )
                }
                if (!meter.readDate.isNullOrBlank()) {
                    InfoRow(
                        label = stringResource(R.string.last_read_date),
                        value = meter.readDate
                    )
                }
            }
        }
    }
}

/**
 * Energy data card
 */
@Composable
private fun EnergyDataCard(
    meter: MeterData,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Assessment,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.energy_data),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            // Energy readings
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                EnergyValueCard(
                    label = stringResource(R.string.import_energy),
                    value = meter.formattedImpKwh,
                    unit = "kWh",
                    color = MaterialTheme.colorScheme.primary,
                    hasData = meter.impKwh != null
                )
                EnergyValueCard(
                    label = stringResource(R.string.export_energy),
                    value = meter.formattedExpKwh,
                    unit = "kWh",
                    color = MaterialTheme.colorScheme.secondary,
                    hasData = meter.expKwh != null
                )
            }

            // Max demand readings
            if (meter.impMaxDemandKw != null || meter.expMaxDemandKw != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    EnergyValueCard(
                        label = stringResource(R.string.imp_max_demand),
                        value = meter.impMaxDemandKw?.let { String.format("%.3f", it) } ?: "------",
                        unit = "kW",
                        color = MaterialTheme.colorScheme.tertiary,
                        hasData = meter.impMaxDemandKw != null
                    )
                    EnergyValueCard(
                        label = stringResource(R.string.exp_max_demand),
                        value = meter.expMaxDemandKw?.let { String.format("%.3f", it) } ?: "------",
                        unit = "kW",
                        color = MaterialTheme.colorScheme.tertiary,
                        hasData = meter.expMaxDemandKw != null
                    )
                }
            }
        }
    }
}

/**
 * Technical data card
 */
@Composable
private fun TechnicalDataCard(
    meter: MeterData,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.technical_data),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (meter.minVoltV != null) {
                    InfoRow(
                        label = stringResource(R.string.minimum_voltage),
                        value = stringResource(R.string.voltage_format, meter.minVoltV)
                    )
                }

                InfoRow(
                    label = stringResource(R.string.connection_status),
                    value = if (meter.isActive)
                        stringResource(R.string.meter_online)
                    else
                        stringResource(R.string.meter_offline)
                )

                if (!meter.alert.isNullOrBlank()) {
                    InfoRow(
                        label = stringResource(R.string.alerts),
                        value = meter.alert,
                        valueColor = colorResource(R.color.warning_light)
                    )
                }
            }
        }
    }
}

/**
 * Energy value card component
 */
@Composable
private fun EnergyValueCard(
    label: String,
    value: String,
    unit: String,
    color: Color,
    hasData: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = if (hasData) color else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = unit,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Info row component
 */
@Composable
private fun InfoRow(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = valueColor,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.End
        )
    }
}

/**
 * Action buttons section
 */
@Composable
private fun ActionButtonsSection(
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
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.meter_actions),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            // Action buttons grid
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ActionButton(
                        text = stringResource(R.string.registration),
                        description = stringResource(R.string.registration_description),
                        icon = Icons.Default.Person,
                        onClick = onRegistration,
                        modifier = Modifier.weight(1f)
                    )
                    ActionButton(
                        text = stringResource(R.string.read_data),
                        description = stringResource(R.string.read_data_description),
                        icon = Icons.Default.Storage,
                        onClick = onReadData,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ActionButton(
                        text = stringResource(R.string.load_profile),
                        description = stringResource(R.string.load_profile_description),
                        icon = Icons.Default.Assessment,
                        onClick = onLoadProfile,
                        modifier = Modifier.weight(1f)
                    )
                    ActionButton(
                        text = stringResource(R.string.event_log),
                        description = stringResource(R.string.event_log_description),
                        icon = Icons.Default.Event,
                        onClick = onEventLog,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ActionButton(
                        text = stringResource(R.string.billing_data),
                        description = stringResource(R.string.billing_data_description),
                        icon = Icons.Default.Payment,
                        onClick = onBillingData,
                        modifier = Modifier.weight(1f)
                    )
                    ActionButton(
                        text = stringResource(R.string.set_clock),
                        description = stringResource(R.string.set_clock_description),
                        icon = Icons.Default.AccessTime,
                        onClick = onSetClock,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

/**
 * Action button component
 */
@Composable
private fun ActionButton(
    text: String,
    description: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(80.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Get color for meter status
 */
@Composable
private fun getStatusColor(status: MeterStatus): Color {
    return when (status) {
        MeterStatus.ACTIVE -> colorResource(R.color.success_light)
        MeterStatus.INACTIVE -> colorResource(R.color.outline_light)
        MeterStatus.NO_DATA -> colorResource(R.color.warning_light)
        MeterStatus.ERROR -> colorResource(R.color.error_light)
    }
}