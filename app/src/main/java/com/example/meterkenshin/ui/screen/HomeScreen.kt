package com.example.meterkenshin.ui.screen


import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ElectricMeter
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.meterkenshin.R
import com.example.meterkenshin.manager.SessionManager
import com.example.meterkenshin.model.Meter
import com.example.meterkenshin.model.RequiredFile
import com.example.meterkenshin.printer.BluetoothPrinterManager
import com.example.meterkenshin.ui.component.BluetoothStatusComponent
import com.example.meterkenshin.ui.viewmodel.PrinterBluetoothViewModel
import com.example.meterkenshin.ui.viewmodel.FileUploadViewModel
import com.example.meterkenshin.ui.viewmodel.MeterReadingViewModel
import com.example.meterkenshin.ui.component.HomeMeterList

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    sessionManager: SessionManager,
    onLogout: () -> Unit,
    onNavigateToMeterReading: () -> Unit = {},
    fileUploadViewModel: FileUploadViewModel = viewModel(),
    meterReadingViewModel: MeterReadingViewModel = viewModel(),
    onNavigateToMeterDetail: (Meter) -> Unit = {},
    printerBluetoothViewModel: PrinterBluetoothViewModel
) {
    val context = LocalContext.current
    val session = sessionManager.getSession()

    if (session == null) {
        onLogout()
        return
    }

    // Observe real data from ViewModels
    val uploadState by fileUploadViewModel.uploadState.collectAsState()
    val meterUiState by meterReadingViewModel.uiState.collectAsState()
    val bluetoothConnectionState by printerBluetoothViewModel.connectionState.collectAsState()
    val isBluetoothEnabled by printerBluetoothViewModel.isBluetoothEnabled.collectAsState()
    val connectedDevice by printerBluetoothViewModel.connectedDevice.collectAsState()
    val bluetoothStatusMessage by printerBluetoothViewModel.statusMessage.collectAsState()
    val paperStatus by printerBluetoothViewModel.paperStatus.collectAsState()
    val coverStatus by printerBluetoothViewModel.coverStatus.collectAsState()


    // Start/stop monitoring based on connection
    LaunchedEffect(bluetoothConnectionState) {
        if (bluetoothConnectionState == BluetoothPrinterManager.ConnectionState.CONNECTED) {
            printerBluetoothViewModel.startStatusMonitoring()
        } else {
            printerBluetoothViewModel.stopStatusMonitoring()
        }
    }

    // Check if meter.csv is uploaded
    val meterCsvFile = uploadState.requiredFiles.find { it.type == RequiredFile.FileType.METER }
    val isMeterCsvUploaded = meterCsvFile?.isUploaded == true

    // Load meter data when CSV is uploaded
    LaunchedEffect(isMeterCsvUploaded) {
        if (isMeterCsvUploaded) {
            meterReadingViewModel.loadMeters(context, meterCsvFile.fileName)
        }
    }

    // Calculate real system overview from actual meter data
    val systemOverview = remember(meterUiState.allMeters) {
        calculateSystemOverview(meterUiState.allMeters)
    }


    var showLogoutDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Main Content
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // System Overview Statistics
            item {
                SystemOverviewSection(
                    overview = systemOverview,
                    isLoading = meterUiState.isLoading,
                    isMeterDataLoaded = isMeterCsvUploaded && meterUiState.allMeters.isNotEmpty()
                )
            }

            // Printer Configuration (Bluetooth Status)
            item {
                BluetoothStatusComponent(
                    connectionState = bluetoothConnectionState,
                    isBluetoothEnabled = isBluetoothEnabled,
                    connectedDevice = connectedDevice,
                    statusMessage = bluetoothStatusMessage,
                    paperStatus = paperStatus,
                    coverStatus = coverStatus,
                    printerBluetoothViewModel = printerBluetoothViewModel
                )
            }

            // Recent Readings
            item {
                RecentReadingsSection(
                    onNavigateToMeterReading = onNavigateToMeterReading,
                    onNavigateToMeterDetail = onNavigateToMeterDetail,
                    fileUploadViewModel = fileUploadViewModel,
                    meterReadingViewModel = meterReadingViewModel
                )
            }

            // Add some bottom padding
            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // Logout Confirmation Dialog
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Logout") },
            text = { Text("Are you sure you want to logout?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutDialog = false
                        sessionManager.logout()
                        onLogout()
                    }
                ) {
                    Text("Logout")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}


@Composable
private fun SystemOverviewSection(
    overview: SystemOverview,
    isLoading: Boolean,
    isMeterDataLoaded: Boolean
) {
    Column {
        Text(
            text = stringResource(R.string.overview_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        if (isLoading) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(horizontal = 4.dp)
            ) {
                // Total Meters
                item {
                    OverviewCard(
                        title = stringResource(R.string.total_meters),
                        value = overview.totalMeters.toString(),
                        icon = Icons.Default.ElectricMeter,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.width(140.dp)
                    )
                }

                // Online Meters
                item {
                    OverviewCard(
                        title = "Online Meters",
                        value = overview.activeMeters.toString(),
                        icon = Icons.Default.CheckCircle,
                        color = Color(0xFF4CAF50),
                        modifier = Modifier.width(140.dp)
                    )
                }

                // CSV Status
                item {
                    OverviewCard(
                        title = "CSV Status",
                        value = if (isMeterDataLoaded) "Loaded" else "No Data",
                        icon = Icons.Default.Description,
                        color = if (isMeterDataLoaded) Color(0xFF4CAF50) else Color(0xFFFF9800),
                        modifier = Modifier.width(140.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun OverviewCard(
    title: String,
    value: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.height(120.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.08f)
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.2f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(28.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )

            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RecentReadingsSection(
    onNavigateToMeterReading: () -> Unit,
    onNavigateToMeterDetail: (Meter) -> Unit,
    fileUploadViewModel: FileUploadViewModel = viewModel(),
    meterReadingViewModel: MeterReadingViewModel = viewModel()
) {
    HomeMeterList(
        fileUploadViewModel = fileUploadViewModel,
        meterReadingViewModel = meterReadingViewModel,
        onMeterClick = onNavigateToMeterDetail,
        onViewAllClick = onNavigateToMeterReading
    )
}


// Helper functions for real data processing
private fun calculateSystemOverview(meters: List<Meter>): SystemOverview {
    val rankDistribution = meters.groupBy { it.id }.mapValues { it.value.size }

    return SystemOverview(
        totalMeters = meters.size,
        activeMeters = meters.size, // Assume all loaded meters are active
        offlineMeters = 0, // No offline detection yet
        todayReadings = (meters.size * 0.8).toInt(), // Simulate 80% reading completion
        totalConsumption = meters.size * 150.5f, // Simulate consumption
        pendingExports = if (meters.isNotEmpty()) 2 else 0,
        lastSyncSuccess = meters.isNotEmpty(),
        rankDistribution = rankDistribution
    )
}

// Update SystemOverview data class to include rank distribution
data class SystemOverview(
    val totalMeters: Int,
    val activeMeters: Int,
    val offlineMeters: Int,
    val todayReadings: Int,
    val totalConsumption: Float,
    val pendingExports: Int,
    val lastSyncSuccess: Boolean,
    val rankDistribution: Map<String, Int> = emptyMap()
)