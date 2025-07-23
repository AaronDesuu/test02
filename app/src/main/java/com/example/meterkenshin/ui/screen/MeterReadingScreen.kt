package com.example.meterkenshin.ui.screen

import android.content.Context
import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.NetworkCell
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.meterkenshin.R
import com.example.meterkenshin.model.RequiredFile
import com.example.meterkenshin.ui.viewmodel.FileUploadViewModel
import com.example.meterkenshin.ui.viewmodel.MeterReadingViewModel
import com.example.meterkenshin.ui.viewmodel.BluetoothViewModel
import com.example.meterkenshin.DLMS.DLMS
import java.text.SimpleDateFormat
import java.util.*
import kotlin.random.Random

/**
 * Data class representing a meter from the CSV file
 * Updated to match actual meter.csv structure:
 * UID,Activate,Serial NO.,Bluetooth ID,Fixed date,Imp [kWh],Exp [kWh],ImpMaxDemand [kW],ExpMaxDemand [kW],MinVolt [V],Alert,Read date
 */
data class Meter(
    val uid: String,                    // UID
    val activate: Int,                  // Activate (0/1)
    val serialNo: String,               // Serial NO.
    val bluetoothId: String,            // Bluetooth ID (MAC address)
    val fixedDate: String? = null,      // Fixed date
    val impKwh: Float? = null,          // Imp [kWh]
    val expKwh: Float? = null,          // Exp [kWh]
    val impMaxDemand: Float? = null,    // ImpMaxDemand [kW]
    val expMaxDemand: Float? = null,    // ExpMaxDemand [kW]
    val minVolt: Float? = null,         // MinVolt [V]
    val alert: Float? = null,           // Alert
    val readDate: String? = null        // Read date
)

/**
 * Data class for DLMS meter communication status
 */
data class MeterConnectionStatus(
    val account: String,
    val isConnected: Boolean = false,
    val rssi: Int = -100,
    val lastReading: Float? = null,
    val lastReadingDate: String? = null,
    val connectionQuality: ConnectionQuality = ConnectionQuality.POOR,
    val dlmsSession: Boolean = false
)

enum class ConnectionQuality {
    EXCELLENT, GOOD, FAIR, POOR
}

/**
 * Enhanced Meter Reading Screen with DLMS Communication
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeterReadingScreen(
    fileUploadViewModel: FileUploadViewModel = viewModel(),
    meterReadingViewModel: MeterReadingViewModel = viewModel(),
    bluetoothViewModel: BluetoothViewModel = viewModel(),
    onBackPressed: () -> Unit = {},
    onNavigateToFileUpload: () -> Unit = {},
    onNavigateToMeterDetail: (Meter) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Observe upload state from FileUploadViewModel
    val uploadState by fileUploadViewModel.uploadState.collectAsState()

    // Observe meter reading state from MeterReadingViewModel
    val meterUiState by meterReadingViewModel.uiState.collectAsState()
    val searchQuery by meterReadingViewModel.searchQuery.collectAsState()

    // Observe Bluetooth state
    val bluetoothState by bluetoothViewModel.connectionState.collectAsState()
    val isBluetoothEnabled by bluetoothViewModel.isBluetoothEnabled.collectAsState()

    // Check if meter.csv is uploaded
    val meterCsvFile = uploadState.requiredFiles.find { it.type == RequiredFile.FileType.METER }

    // Load meter data when CSV file is available
    LaunchedEffect(meterCsvFile?.isUploaded) {
        if (meterCsvFile?.isUploaded == true && meterCsvFile.fileName != null) {
            meterReadingViewModel.loadMeterData(context, meterCsvFile.fileName)
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.meter_reading_title),
                        style = MaterialTheme.typography.headlineSmall,
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
                actions = {
                    // Bluetooth status indicator
                    Icon(
                        imageVector = Icons.Default.Bluetooth,
                        contentDescription = null,
                        tint = if (isBluetoothEnabled) {
                            colorResource(R.color.bluetooth_connected_foreground)
                        } else {
                            colorResource(R.color.bluetooth_disabled_foreground)
                        },
                        modifier = Modifier
                            .size(24.dp)
                            .padding(end = 8.dp)
                    )

                    // Refresh action
                    IconButton(
                        onClick = {
                            if (meterCsvFile?.isUploaded == true && meterCsvFile.fileName != null) {
                                meterReadingViewModel.loadMeterData(context, meterCsvFile.fileName)
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.refresh)
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        when {
            // Show upload prompt if meter.csv not uploaded
            meterCsvFile?.isUploaded != true -> {
                MeterFileNotUploadedContent(
                    onNavigateToFileUpload = onNavigateToFileUpload,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                )
            }

            // Show loading state
            meterUiState.isLoading -> {
                LoadingMetersContent(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                )
            }

            // Show error state
            meterUiState.errorMessage != null -> {
                ErrorLoadingMetersContent(
                    errorMessage = meterUiState.errorMessage,
                    onRetry = {
                        if (meterCsvFile?.fileName != null) {
                            meterReadingViewModel.loadMeterData(context, meterCsvFile.fileName)
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                )
            }

            // Show empty state
            meterUiState.filteredMeters.isEmpty() && meterUiState.allMeters.isEmpty() -> {
                EmptyMetersContent(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                )
            }

            // Show meter list
            else -> {
                MeterListContent(
                    meters = meterUiState.filteredMeters,
                    searchQuery = searchQuery,
                    onSearchQueryChange = meterReadingViewModel::updateSearchQuery,
                    onMeterClick = onNavigateToMeterDetail,
                    bluetoothViewModel = bluetoothViewModel,
                    meterStatuses = meterReadingViewModel.meterStatuses.collectAsState().value,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                )
            }
        }
    }
}

/**
 * Main content showing the list of meters with DLMS communication features
 */
@Composable
private fun MeterListContent(
    meters: List<Meter>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onMeterClick: (Meter) -> Unit,
    bluetoothViewModel: BluetoothViewModel,
    meterStatuses: Map<String, MeterConnectionStatus> = emptyMap(),
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(16.dp)
    ) {
        // Search field
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            label = { Text(stringResource(R.string.search_meters)) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null
                )
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Meters count
        Text(
            text = stringResource(R.string.meters_found, meters.size, meters.size),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Meter list
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(meters) { meter ->
                DLMSMeterCard(
                    meter = meter,
                    onClick = { onMeterClick(meter) },
                    bluetoothViewModel = bluetoothViewModel,
                    meterStatus = meterStatuses[meter.uid]
                )
            }
        }
    }
}

/**
 * Enhanced meter card with DLMS communication status and real data display
 */
@Composable
private fun DLMSMeterCard(
    meter: Meter,
    onClick: () -> Unit,
    bluetoothViewModel: BluetoothViewModel,
    meterStatus: MeterConnectionStatus? = null,
    modifier: Modifier = Modifier
) {
    // Use real connection status from DLMS or default to inactive
    val connectionStatus = meterStatus ?: MeterConnectionStatus(
        account = meter.uid,
        isConnected = false,
        rssi = -100,
        connectionQuality = ConnectionQuality.POOR,
        dlmsSession = false
    )

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
            // Left side - UID in circle with connection status
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            meter.activate == 0 -> colorResource(R.color.bluetooth_disabled_background)
                            connectionStatus.connectionQuality == ConnectionQuality.EXCELLENT -> colorResource(R.color.bluetooth_connected_background)
                            connectionStatus.connectionQuality == ConnectionQuality.GOOD -> colorResource(R.color.bluetooth_connecting_background)
                            connectionStatus.connectionQuality == ConnectionQuality.FAIR -> colorResource(R.color.bluetooth_connecting_background)
                            else -> colorResource(R.color.bluetooth_disconnected_background)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = meter.uid,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        meter.activate == 0 -> colorResource(R.color.bluetooth_disabled_foreground)
                        connectionStatus.connectionQuality == ConnectionQuality.EXCELLENT -> colorResource(R.color.bluetooth_connected_foreground)
                        connectionStatus.connectionQuality == ConnectionQuality.GOOD -> colorResource(R.color.bluetooth_connecting_foreground)
                        connectionStatus.connectionQuality == ConnectionQuality.FAIR -> colorResource(R.color.bluetooth_connecting_foreground)
                        else -> colorResource(R.color.bluetooth_disconnected_foreground)
                    }
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Main content
            Column(modifier = Modifier.weight(1f)) {
                // Title row with meter info
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Serial: ${meter.serialNo}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    // Real RSSI from DLMS
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (connectionStatus.isConnected) Icons.Default.Wifi else Icons.Default.NetworkCell,
                            contentDescription = null,
                            tint = when {
                                meter.activate == 0 -> colorResource(R.color.bluetooth_disabled_foreground)
                                connectionStatus.connectionQuality == ConnectionQuality.EXCELLENT -> colorResource(R.color.bluetooth_connected_foreground)
                                connectionStatus.connectionQuality == ConnectionQuality.GOOD -> colorResource(R.color.bluetooth_connecting_foreground)
                                connectionStatus.connectionQuality == ConnectionQuality.FAIR -> colorResource(R.color.bluetooth_connecting_foreground)
                                else -> colorResource(R.color.bluetooth_disconnected_foreground)
                            },
                            modifier = Modifier.size(16.dp)
                        )

                        Spacer(modifier = Modifier.width(4.dp))

                        Text(
                            text = "${connectionStatus.rssi}dBm",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Meter details
                Row(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "UID: ${meter.uid}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Text(
                            text = "Status: ${if (meter.activate == 1) "Active" else "Inactive"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (meter.activate == 1) {
                                colorResource(R.color.bluetooth_connected_foreground)
                            } else {
                                colorResource(R.color.bluetooth_disabled_foreground)
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Real DLMS data display
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = if (connectionStatus.lastReading != null) {
                                "Imp [kWh]: ${String.format("%.3f", connectionStatus.lastReading)}"
                            } else if (meter.impKwh != null) {
                                "Imp [kWh]: ${String.format("%.3f", meter.impKwh)}"
                            } else {
                                "Imp [kWh]: ------"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (connectionStatus.lastReading != null || meter.impKwh != null) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            fontWeight = if (connectionStatus.lastReading != null || meter.impKwh != null) FontWeight.Medium else FontWeight.Normal
                        )

                        Text(
                            text = if (connectionStatus.lastReadingDate != null) {
                                "Read date: ${connectionStatus.lastReadingDate}"
                            } else if (meter.readDate != null) {
                                "Read date: ${meter.readDate}"
                            } else {
                                "Read date: Not recorded"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Real connection status
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        meter.activate == 0 -> colorResource(R.color.bluetooth_disabled_foreground)
                                        connectionStatus.dlmsSession -> colorResource(R.color.bluetooth_connected_foreground)
                                        connectionStatus.isConnected -> colorResource(R.color.bluetooth_connecting_foreground)
                                        else -> colorResource(R.color.bluetooth_disconnected_foreground)
                                    }
                                )
                        )

                        Spacer(modifier = Modifier.width(4.dp))

                        Text(
                            text = when {
                                meter.activate == 0 -> "Inactive"
                                connectionStatus.dlmsSession -> "DLMS Connected"
                                connectionStatus.isConnected -> "BLE Connected"
                                else -> "Offline"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Chevron
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}


/**
 * Content shown when meters are loading
 */
@Composable
private fun LoadingMetersContent(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp),
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.loading_meters),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/**
 * Content shown when there's an error loading meters
 */
@Composable
private fun ErrorLoadingMetersContent(
    errorMessage: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.padding(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Error,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(48.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = stringResource(R.string.error_loading_meters),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                Card(
                    onClick = onRetry,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.retry),
                        color = MaterialTheme.colorScheme.onError,
                        modifier = Modifier.padding(vertical = 4.dp, horizontal = 16.dp)
                    )
                }
            }
        }
    }
}

/**
 * Content shown when no meters are found in the CSV
 */
@Composable
private fun EmptyMetersContent(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Cable,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(80.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = stringResource(R.string.no_meters_found),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = stringResource(R.string.no_meters_found_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Content shown when meter.csv is not uploaded
 */
@Composable
private fun MeterFileNotUploadedContent(
    onNavigateToFileUpload: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.padding(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            shape = RoundedCornerShape(20.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Cable,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(64.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = stringResource(R.string.meter_file_not_uploaded_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = stringResource(R.string.meter_file_not_uploaded_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                Card(
                    onClick = onNavigateToFileUpload,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.upload_meter_file),
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                    )
                }
            }
        }
    }
}

/**
 * Sealed class for meter loading results
 */
sealed class MeterLoadResult {
    data class Success(val meters: List<Meter>) : MeterLoadResult()
    data class Error(val message: String) : MeterLoadResult()
}