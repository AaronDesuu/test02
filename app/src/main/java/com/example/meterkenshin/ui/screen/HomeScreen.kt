package com.example.meterkenshin.ui.screen

import android.util.Log
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.ElectricMeter
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import com.example.meterkenshin.model.Permission
import com.example.meterkenshin.model.RequiredFile
import com.example.meterkenshin.model.UserRole
import com.example.meterkenshin.model.getPermissions
import com.example.meterkenshin.ui.component.BluetoothStatusComponent
import com.example.meterkenshin.ui.viewmodel.BluetoothViewModel
import com.example.meterkenshin.ui.viewmodel.FileUploadViewModel
import com.example.meterkenshin.ui.viewmodel.MeterReadingViewModel
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.ui.res.colorResource

import com.example.meterkenshin.bluetooth.BluetoothManager
import com.example.meterkenshin.model.Meter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Random

private const val TAG = "HomeScreen"

// Data class for real-time meter readings
data class MeterReading(
    val meterId: String,
    val reading: Float?,
    val timestamp: Date = Date(),
    val quality: ReadingQuality = ReadingQuality.GOOD
)

enum class ReadingQuality(val displayName: String) {
    EXCELLENT("Excellent"),
    GOOD("Good"),
    FAIR("Fair"),
    POOR("Poor")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    sessionManager: SessionManager,
    onLogout: () -> Unit,
    onNavigateToFileUpload: () -> Unit = {},
    onNavigateToReceiptTemplate: () -> Unit = {},
    onNavigateToMeterReading: () -> Unit = {},
    fileUploadViewModel: FileUploadViewModel = viewModel(),
    meterReadingViewModel: MeterReadingViewModel = viewModel(),
    bluetoothViewModel: BluetoothViewModel
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
    val bluetoothConnectionState by bluetoothViewModel.connectionState.collectAsState()
    val isBluetoothEnabled by bluetoothViewModel.isBluetoothEnabled.collectAsState()
    val connectedDevice by bluetoothViewModel.connectedDevice.collectAsState()
    val bluetoothStatusMessage by bluetoothViewModel.statusMessage.collectAsState()

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

    // Generate sample readings based on real meters
    val recentReadings = remember(meterUiState.allMeters) {
        recentReadings(meterUiState.allMeters)
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
            // Quick Actions
            item {
                QuickActionsSection(
                    userRole = session.role,
                    onNavigateToFileUpload = onNavigateToFileUpload,
                    onNavigateToReceiptTemplate = onNavigateToReceiptTemplate,
                    onNavigateToMeterReading = onNavigateToMeterReading,
                    isMeterDataAvailable = isMeterCsvUploaded && meterUiState.allMeters.isNotEmpty()
                )
            }

            // System Overview Statistics
            item {
                SystemOverviewSection(
                    overview = systemOverview,
                    isLoading = meterUiState.isLoading,
                    isMeterDataLoaded = isMeterCsvUploaded && meterUiState.allMeters.isNotEmpty(),
                    bluetoothConnectionState = bluetoothConnectionState,
                    isBluetoothEnabled = isBluetoothEnabled
                )
            }
            // Recent Readings
            item {
                RecentReadingsSection(
                    readings = recentReadings.take(5),
                    meters = meterUiState.allMeters,
                    onNavigateToMeterReading = onNavigateToMeterReading,
                    isLoading = meterUiState.isLoading,
                    isMeterDataLoaded = isMeterCsvUploaded && meterUiState.allMeters.isNotEmpty()
                )
            }
            // Bluetooth Status Card
            item {
                BluetoothStatusComponent(
                    connectionState = bluetoothConnectionState,
                    isBluetoothEnabled = isBluetoothEnabled,
                    connectedDevice = connectedDevice,
                    statusMessage = bluetoothStatusMessage,
                    bluetoothViewModel = bluetoothViewModel
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
private fun QuickActionsSection(
    userRole: UserRole,
    onNavigateToFileUpload: () -> Unit,
    onNavigateToReceiptTemplate: () -> Unit,
    onNavigateToMeterReading: () -> Unit,
    isMeterDataAvailable: Boolean
) {
    Column {
        Text(
            text = stringResource(R.string.quick_actions),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 4.dp)
        ) {
            val quickActions = getQuickActions(userRole)

            items(quickActions) { action ->
                QuickActionCard(
                    action = action,
                    onNavigateToFileUpload = onNavigateToFileUpload,
                    onNavigateToReceiptTemplate = onNavigateToReceiptTemplate,
                    onNavigateToMeterReading = onNavigateToMeterReading,
                    isMeterDataAvailable = isMeterDataAvailable
                )
            }
        }
    }
}

@Composable
private fun QuickActionCard(
    action: QuickAction,
    onNavigateToFileUpload: () -> Unit,
    onNavigateToReceiptTemplate: () -> Unit,
    onNavigateToMeterReading: () -> Unit,
    isMeterDataAvailable: Boolean
) {
    val isEnabled = when (action.title) {
        "Meter Reading" -> isMeterDataAvailable
        "Receipt Template" -> isMeterDataAvailable  // Enable only when meter data is available
        else -> true
    }

    Card(
        onClick = {
            when (action.title) {
                "Import Data" -> {
                    Log.d(TAG, "Navigating to File Upload")
                    onNavigateToFileUpload()
                }
                "Receipt Template" -> {
                    if (isMeterDataAvailable) {
                        Log.d(TAG, "Navigating to Receipt Template")
                        onNavigateToReceiptTemplate()
                    } else {
                        Log.d(TAG, "Receipt Template unavailable - no meter data")
                    }
                }
                "Meter Reading" -> {
                    if (isMeterDataAvailable) {
                        Log.d(TAG, "Navigating to Meter Reading")
                        onNavigateToMeterReading()
                    } else {
                        Log.d(TAG, "Meter Reading unavailable - no data")
                    }
                }
                "View All Readings" -> {
                    Log.d(TAG, "Navigating to Meter Reading")
                    onNavigateToMeterReading()
                }
                else -> {
                    Log.d(TAG, "Action '${action.title}' not implemented yet")
                }
            }
        },
        modifier = Modifier
            .width(120.dp)
            .height(100.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isEnabled) {
                MaterialTheme.colorScheme.surface
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            }
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = action.icon,
                contentDescription = null,
                tint = if (isEnabled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                },
                modifier = Modifier.size(32.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = action.title,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = if (isEnabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                }
            )

            if (!isEnabled && action.title == "Meter Reading") {
                Text(
                    text = "Upload CSV",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun SystemOverviewSection(
    overview: SystemOverview,
    isLoading: Boolean,
    isMeterDataLoaded: Boolean,
    bluetoothConnectionState: BluetoothManager.ConnectionState?,
    isBluetoothEnabled: Boolean
) {
    Column {
        Text(
            text = stringResource(R.string.overview_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        if (isLoading) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier.padding(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("Loading meter data...")
                }
            }
        } else {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(horizontal = 4.dp)
            ) {
                item {
                    StatCard(
                        title = stringResource(R.string.total_meters),
                        value = if (isMeterDataLoaded) overview.totalMeters.toString() else "0",
                        icon = Icons.Default.ElectricMeter,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                item {
                    StatCard(
                        title = stringResource(R.string.active_meters),
                        value = if (isMeterDataLoaded) overview.activeMeters.toString() else "0",
                        icon = Icons.Default.CheckCircle,
                        color = Color(0xFF4CAF50)
                    )
                }
                item {
                    BluetoothPrinterStatusCard(
                        connectionState = bluetoothConnectionState,
                        isBluetoothEnabled = isBluetoothEnabled
                    )
                }
                item {
                    StatCard(
                        title = "CSV Status",
                        value = if (isMeterDataLoaded) "✓" else "!",
                        icon = Icons.Default.Description,
                        color = if (isMeterDataLoaded) Color(0xFF4CAF50) else Color(0xFFFF9800)
                    )
                }
            }
        }
    }
}


@Composable
private fun BluetoothPrinterStatusCard(
    connectionState: BluetoothManager.ConnectionState?,
    isBluetoothEnabled: Boolean
) {
    // Determine status based on Bluetooth state
    val (statusText, statusIcon, statusColor) = when {
        !isBluetoothEnabled -> Triple(
            "Off",
            Icons.Default.BluetoothDisabled,
            colorResource(R.color.csv_error_foreground)
        )
        connectionState == BluetoothManager.ConnectionState.CONNECTED -> Triple(
            "ON",
            Icons.Default.CheckCircle,
            colorResource(R.color.csv_success_foreground)
        )
        connectionState == BluetoothManager.ConnectionState.CONNECTING -> Triple(
            "...",
            Icons.Default.Sync,
            colorResource(R.color.csv_warning_foreground)
        )
        connectionState == BluetoothManager.ConnectionState.ERROR -> Triple(
            "Error",
            Icons.Default.Error,
            colorResource(R.color.csv_error_foreground)
        )
        else -> Triple(
            "Off",
            Icons.Default.Warning,
            colorResource(R.color.csv_error_foreground)
        )
    }

    Card(
        modifier = Modifier
            .width(140.dp)
            .height(120.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = statusColor.copy(alpha = 0.08f)
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.5.dp,
            statusColor.copy(alpha = 0.2f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = statusIcon,
                contentDescription = null,
                tint = statusColor,
                modifier = Modifier.size(28.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = statusText,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = statusColor,
                textAlign = TextAlign.Center
            )

            Text(
                text = "Printer Status",
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
private fun StatCard(
    title: String,
    value: String,
    icon: ImageVector,
    color: Color
) {
    Card(
        modifier = Modifier
            .width(140.dp)
            .height(120.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.08f)
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.5.dp,
            color.copy(alpha = 0.2f)
        )
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
    readings: List<MeterReading>,
    meters: List<Meter>,
    onNavigateToMeterReading: () -> Unit,
    isLoading: Boolean,
    isMeterDataLoaded: Boolean
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.recent_readings),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            TextButton(
                onClick = onNavigateToMeterReading,
                enabled = isMeterDataLoaded
            ) {
                Text(stringResource(R.string.view_all_readings))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        when {
            isLoading -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(16.dp))
                        Text("Loading readings...")
                    }
                }
            }
            !isMeterDataLoaded -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.FileUpload,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No meter data available",
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "Upload meter.csv to see readings",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
            readings.isEmpty() -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Text(
                        text = stringResource(R.string.no_readings_available),
                        modifier = Modifier.padding(24.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center
                    )
                }
            }
            else -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
                    )
                ) {
                    Column {
                        readings.forEachIndexed { index, reading ->
                            val meter = meters.find { it.id == reading.meterId }
                            ReadingItem(
                                reading = reading,
                                meter = meter,
                                showDivider = index < readings.size - 1
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReadingItem(
    reading: MeterReading,
    meter: Meter?,
    showDivider: Boolean
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "S/N : ${meter?.serialNumber ?: reading.meterId}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Status : connected",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(reading.timestamp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = reading.reading?.let { String.format("%.2f kWh", it) } ?: "Not Read",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = when (reading.quality) {
                            ReadingQuality.EXCELLENT -> Color(0xFF4CAF50)
                            ReadingQuality.GOOD -> Color(0xFF8BC34A)
                            ReadingQuality.FAIR -> Color(0xFFFF9800)
                            ReadingQuality.POOR -> Color(0xFFF44336)
                        }.copy(alpha = 0.15f)
                    )
                ) {
                    Text(
                        text = reading.quality.displayName,
                        style = MaterialTheme.typography.labelSmall,
                        color = when (reading.quality) {
                            ReadingQuality.EXCELLENT -> Color(0xFF4CAF50)
                            ReadingQuality.GOOD -> Color(0xFF8BC34A)
                            ReadingQuality.FAIR -> Color(0xFFFF9800)
                            ReadingQuality.POOR -> Color(0xFFF44336)
                        },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
        }

        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )
        }
    }
}


// Data classes for quick actions
data class QuickAction(
    val title: String,
    val icon: ImageVector,
    val requiredPermission: Permission? = null
)

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

private fun recentReadings(meters: List<Meter>): List<MeterReading> {
    if (meters.isEmpty()) return emptyList()

    val random = Random()
    return meters.take(5).map { meter ->
        MeterReading(
            meterId = meter.serialNumber,
            reading = meter.impKWh?.toFloat(),
            timestamp = Date(System.currentTimeMillis() - random.nextLong() % (24 * 60 * 60 * 1000)),
            quality = ReadingQuality.entries[random.nextInt(ReadingQuality.entries.size)]
        )
    }
}

private fun getQuickActions(userRole: UserRole): List<QuickAction> {
    val userPermissions = userRole.getPermissions()

    return listOf(
        QuickAction(
            title = "Meter Reading",
            icon = Icons.Default.ElectricBolt,
            requiredPermission = Permission.READ_DASHBOARD
        ),
        QuickAction(
            title = "Import Data",
            icon = Icons.Default.FileUpload,
            requiredPermission = Permission.VIEW_REPORTS
        ),
        QuickAction(
            title = "Receipt Template",
            icon = Icons.Default.Receipt,
            requiredPermission = Permission.READ_DASHBOARD
        ),
        QuickAction(
            title = "Add Meter",
            icon = Icons.Default.Add,
            requiredPermission = Permission.MANAGE_USERS
        ),
        QuickAction(
            title = "Settings",
            icon = Icons.Default.Settings,
            requiredPermission = Permission.SYSTEM_SETTINGS
        ),
    ).filter { action ->
        action.requiredPermission == null || userPermissions.contains(action.requiredPermission)
    }
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