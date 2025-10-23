package com.example.meterkenshin.ui.screen

import android.annotation.SuppressLint
import android.os.Build
import android.util.Log
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
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.meterkenshin.R
import com.example.meterkenshin.model.Meter
import java.text.SimpleDateFormat
import java.util.Locale
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.meterkenshin.data.MeterSpecifications
import com.example.meterkenshin.ui.component.DLMSFunctionsCard
import com.example.meterkenshin.ui.component.DLMSLogCard
import com.example.meterkenshin.ui.viewmodel.DLMSRegistrationViewModel
import com.example.meterkenshin.ui.viewmodel.MeterReadingViewModel

/**
 * Modern Meter Detail Screen with updated design and theme consistency
 */
@SuppressLint("MissingPermission")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeterDetailScreen(
    meter: Meter,
    meterReadingViewModel: MeterReadingViewModel = viewModel(),
    registrationViewModel: DLMSRegistrationViewModel = viewModel(key = "meter_${meter.uid}"),
    onReadData: () -> Unit = {},
    onLoadProfile: () -> Unit = {},
    onEventLog: () -> Unit = {},
    onBillingData: () -> Unit = {},
    onSetClock: () -> Unit = {},
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Track DLMS initialization state
    var isDlmsInitialized by remember { mutableStateOf(false) }

    // Collect states
    val discoveredDevices by meterReadingViewModel.discoveredDevices.collectAsState()
    val registrationState by registrationViewModel.registrationState.collectAsState()
    val dlmsLog by registrationViewModel.dlmsLog.collectAsState()

    // FIX: Initialize DLMS on screen load and WAIT for completion
    LaunchedEffect(Unit) {
        try {
            Log.i("MeterDetailScreen", "Initializing DLMS...")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registrationViewModel.initializeDLMS(context, meter)
            }
            isDlmsInitialized = true
            Log.i("MeterDetailScreen", "DLMS initialization complete")
        } catch (e: Exception) {
            Log.e("MeterDetailScreen", "Failed to initialize DLMS", e)
        }
    }

    // Get RSSI for this meter
    val rssi = meter.bluetoothId?.let {
        discoveredDevices[it.uppercase()]
    } ?: -200
    val isNearby = meterReadingViewModel.isMeterNearby(meter.bluetoothId)

    // Pause scanning when this screen is displayed
    LaunchedEffect(Unit) {
        try {
            meterReadingViewModel.pauseScanning()
            Log.i("MeterDetailScreen", "BLE scanning paused")
        } catch (e: Exception) {
            Log.e("MeterDetailScreen", "Error pausing BLE scan", e)
        }
    }

    // Resume scanning when leaving
    DisposableEffect(Unit) {
        onDispose {
            try {
                registrationViewModel.cleanup(context)
                meterReadingViewModel.resumeScanning()
                Log.i("MeterDetailScreen", "Cleanup complete")
            } catch (e: Exception) {
                Log.e("MeterDetailScreen", "Error during cleanup", e)
            }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Connection status card
            MeterStatusCard(
                meter = meter,
                rssi = rssi,
                isNearby = isNearby
            )

            // 2. DLMS function buttons - NOW IN SEPARATE FILE
            DLMSFunctionsCard(
                onRegistration = {
                    // FIX: Only allow if DLMS is initialized
                    if (isDlmsInitialized) {
                        registrationViewModel.startRegistration(meter)
                    } else {
                        Log.w("MeterDetailScreen", "DLMS not yet initialized")
                    }
                },
                onReadData = {
                    if (isDlmsInitialized) {
                        registrationViewModel.appendLog("Read Data clicked")
                        onReadData()
                    }
                },
                onLoadProfile = {
                    if (isDlmsInitialized) {
                        registrationViewModel.appendLog("Load Profile clicked")
                        onLoadProfile()
                    }
                },
                onEventLog = {
                    if (isDlmsInitialized) {
                        registrationViewModel.appendLog("Event Log clicked")
                        onEventLog()
                    }
                },
                onBillingData = {
                    if (isDlmsInitialized) {
                        registrationViewModel.appendLog("Billing Data clicked")
                        onBillingData()
                    }
                },
                onSetClock = {
                    if (isDlmsInitialized) {
                        registrationViewModel.appendLog("Set Clock clicked")
                        onSetClock()
                    }
                },
                // Disable buttons until initialized
                isProcessing = registrationState.isRunning || !isDlmsInitialized
            )

            // 3. DLMS Log output - NOW IN SEPARATE FILE
            DLMSLogCard(
                logText = dlmsLog,
                onClearLog = { registrationViewModel.clearLog() },
                isProcessing = registrationState.isRunning
            )

            // 4. Meter specifications card
            MeterSpecificationsCard(meter = meter)

            Spacer(modifier = Modifier.height(16.dp))
            Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
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
                    value = meter.readDate?.let { dateFormat.format(it) } ?: "-"
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

            // Serial Number
            SpecificationRow(
                label = stringResource(R.string.uid).replace(": %1\$s", ""),
                value = meter.uid.toString().ifBlank { "-" }
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

