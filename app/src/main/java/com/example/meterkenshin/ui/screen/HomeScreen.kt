// app/src/main/java/com/example/meterkenshin/ui/screen/HomeScreen.kt
package com.example.meterkenshin.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.example.meterkenshin.manager.SessionManager
import com.example.meterkenshin.model.MeterData
import com.example.meterkenshin.model.Permission
import com.example.meterkenshin.model.RequiredFile
import com.example.meterkenshin.model.UserRole
import com.example.meterkenshin.model.UserSession
import com.example.meterkenshin.model.getPermissions
import com.example.meterkenshin.ui.viewmodel.FileUploadViewModel
import com.example.meterkenshin.ui.viewmodel.MeterReadingViewModel
import com.example.meterkenshin.ui.component.BluetoothStatusComponent
import com.example.meterkenshin.ui.viewmodel.BluetoothViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

private const val TAG = "HomeScreen"

// Data classes for real-time meter readings
data class MeterReading(
    val meterId: String,
    val reading: Float,
    val timestamp: Date = Date(),
    val quality: ReadingQuality = ReadingQuality.GOOD
)

enum class ReadingQuality(val displayName: String) {
    GOOD("Good"),
    FAIR("Fair"),
    POOR("Poor")
}

// Data class for quick actions
data class QuickAction(
    val title: String,
    val icon: ImageVector,
    val requiredPermission: Permission? = null
)

/**
 * Home Screen with dashboard overview and navigation
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToFileUpload: () -> Unit = {},
    onNavigateToReceiptTemplate: () -> Unit = {},
    onNavigateToMeterReading: () -> Unit = {},
    fileUploadViewModel: FileUploadViewModel = viewModel(),
    meterReadingViewModel: MeterReadingViewModel = viewModel(),
    bluetoothViewModel: BluetoothViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    // Use simple mock session data - replace with your actual session management
    val mockUserSession = SimpleUserSession(
        username = "Admin User",
        userRole = "Administrator",
        expiresAt = System.currentTimeMillis() + (7 * 24 * 60 * 60 * 1000L) // 7 days
    )

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // Top App Bar
        CenterAlignedTopAppBar(
            title = {
                Text(
                    text = stringResource(R.string.home_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            actions = {
                IconButton(
                    onClick = {
                        // Handle logout - implement according to your session management
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Logout,
                        contentDescription = stringResource(R.string.logout_button),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            },
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
                titleContentColor = MaterialTheme.colorScheme.onSurface
            )
        )

        // Main content
        HomeContent(
            userSession = mockUserSession,
            onNavigateToFileUpload = onNavigateToFileUpload,
            onNavigateToReceiptTemplate = onNavigateToReceiptTemplate,
            onNavigateToMeterReading = onNavigateToMeterReading,
            fileUploadViewModel = fileUploadViewModel,
            meterReadingViewModel = meterReadingViewModel,
            bluetoothViewModel = bluetoothViewModel,
            modifier = Modifier.fillMaxSize()
        )
    }
}

// Simple user session data class to replace the complex one
data class SimpleUserSession(
    val username: String,
    val userRole: String,
    val expiresAt: Long
)

/**
 * Main home content with dashboard sections
 */
@Composable
private fun HomeContent(
    userSession: SimpleUserSession,
    onNavigateToFileUpload: () -> Unit,
    onNavigateToReceiptTemplate: () -> Unit,
    onNavigateToMeterReading: () -> Unit,
    fileUploadViewModel: FileUploadViewModel,
    meterReadingViewModel: MeterReadingViewModel,
    bluetoothViewModel: BluetoothViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uploadState by fileUploadViewModel.uploadState.collectAsState()
    val meterUiState by meterReadingViewModel.uiState.collectAsState()

    // Check if meter CSV is uploaded and load meters
    val meterCsvFile = uploadState.requiredFiles.find { it.type == RequiredFile.FileType.METER }
    val isMeterCsvUploaded = meterCsvFile?.isUploaded == true

    LaunchedEffect(isMeterCsvUploaded) {
        if (isMeterCsvUploaded && meterUiState.allMeters.isEmpty()) {
            meterReadingViewModel.loadMeters(context)
        }
    }

    // Calculate overview with real data
    val systemOverview = remember(meterUiState.allMeters) {
        calculateSystemOverview(meterUiState.allMeters)
    }

    // Generate sample readings from real meter data
    val recentReadings = remember(meterUiState.allMeters) {
        generateSampleReadings(meterUiState.allMeters)
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Welcome Header
        item {
            WelcomeHeader(userSession = userSession)
        }

        // System Overview
        item {
            SystemOverviewCard(
                overview = systemOverview,
                isLoading = meterUiState.isLoading,
                onViewDetails = onNavigateToMeterReading,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // File Upload Status
        item {
            FileUploadStatusCard(
                uploadState = uploadState,
                onNavigateToUpload = onNavigateToFileUpload,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Recent Readings
        if (recentReadings.isNotEmpty()) {
            item {
                RecentReadingsCard(
                    readings = recentReadings,
                    onViewAll = onNavigateToMeterReading,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Quick Actions
        item {
            QuickActionsCard(
                actions = getQuickActions(userSession.userRole),
                onNavigateToFileUpload = onNavigateToFileUpload,
                onNavigateToReceiptTemplate = onNavigateToReceiptTemplate,
                onNavigateToMeterReading = onNavigateToMeterReading,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * Welcome header section - simplified without session dependencies
 */
@Composable
private fun WelcomeHeader(
    userSession: SimpleUserSession,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Welcome, ${userSession.username}!",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )

            Text(
                text = "Role: ${userSession.userRole}",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
            )

            val daysRemaining = ((userSession.expiresAt - System.currentTimeMillis()) / (24 * 60 * 60 * 1000)).toInt()
            if (daysRemaining > 0) {
                Text(
                    text = "Session expires in $daysRemaining days",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }
        }
    }
}

/**
 * System overview card with real meter statistics
 */
@Composable
private fun SystemOverviewCard(
    overview: SystemOverview,
    isLoading: Boolean,
    onViewDetails: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.overview_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                TextButton(onClick = onViewDetails) {
                    Text(text = "View All")
                }
            }

            if (isLoading) {
                // Loading state
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            } else {
                // First row of metrics
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    OverviewMetric(
                        title = stringResource(R.string.total_meters),
                        value = overview.totalMeters.toString(),
                        color = MaterialTheme.colorScheme.primary
                    )
                    OverviewMetric(
                        title = stringResource(R.string.active_meters),
                        value = overview.activeMeters.toString(),
                        color = colorResource(R.color.success_light)
                    )
                    OverviewMetric(
                        title = stringResource(R.string.offline_meters),
                        value = overview.offlineMeters.toString(),
                        color = colorResource(R.color.outline_light)
                    )
                }

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                // Second row of metrics
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    OverviewMetric(
                        title = stringResource(R.string.today_readings),
                        value = overview.todayReadings.toString(),
                        color = MaterialTheme.colorScheme.tertiary
                    )
                    OverviewMetric(
                        title = stringResource(R.string.total_consumption),
                        value = String.format("%.1f kWh", overview.totalConsumption),
                        color = MaterialTheme.colorScheme.secondary
                    )
                    OverviewMetric(
                        title = stringResource(R.string.sync_status),
                        value = if (overview.lastSyncSuccess)
                            stringResource(R.string.synced)
                        else
                            stringResource(R.string.sync_pending),
                        color = if (overview.lastSyncSuccess)
                            colorResource(R.color.success_light)
                        else
                            colorResource(R.color.warning_light)
                    )
                }
            }
        }
    }
}

/**
 * Individual overview metric
 */
@Composable
private fun OverviewMetric(
    title: String,
    value: String,
    color: Color,
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
            color = color
        )
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * File upload status card
 */
@Composable
private fun FileUploadStatusCard(
    uploadState: com.example.meterkenshin.model.FileUploadState,
    onNavigateToUpload: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.file_upload_status),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                TextButton(onClick = onNavigateToUpload) {
                    Text(text = "Manage Files")
                }
            }

            // File status indicators
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                uploadState.requiredFiles.forEach { file ->
                    FileStatusItem(file = file)
                }
            }

            // Upload progress summary
            if (uploadState.allFilesUploaded) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = colorResource(R.color.success_light),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "All files uploaded successfully",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colorResource(R.color.success_light),
                        fontWeight = FontWeight.Medium
                    )
                }
            } else {
                Button(
                    onClick = onNavigateToUpload,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.FileUpload,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Upload Files")
                }
            }
        }
    }
}

/**
 * Individual file status item
 */
@Composable
private fun FileStatusItem(
    file: RequiredFile,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Status icon
        val (icon, color) = when (file.status) {
            com.example.meterkenshin.model.FileUploadState.FileStatus.UPLOADED ->
                Icons.Default.CheckCircle to colorResource(R.color.success_light)
            com.example.meterkenshin.model.FileUploadState.FileStatus.ERROR ->
                Icons.Default.Error to colorResource(R.color.error_light)
            com.example.meterkenshin.model.FileUploadState.FileStatus.UPLOADING ->
                Icons.Default.Sync to colorResource(R.color.warning_light)
            else ->
                Icons.Default.Warning to colorResource(R.color.outline_light)
        }

        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(16.dp)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = file.displayName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = file.status.name.lowercase().capitalize(),
                style = MaterialTheme.typography.bodySmall,
                color = color
            )
        }
    }
}

/**
 * Recent readings card
 */
@Composable
private fun RecentReadingsCard(
    readings: List<MeterReading>,
    onViewAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.recent_readings),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                TextButton(onClick = onViewAll) {
                    Text(text = "View All")
                }
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                readings.forEach { reading ->
                    ReadingItem(reading = reading)
                }
            }
        }
    }
}

/**
 * Individual reading item
 */
@Composable
private fun ReadingItem(
    reading: MeterReading,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = "Meter ${reading.meterId}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(reading.timestamp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Text(
            text = String.format("%.3f kWh", reading.reading),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary
        )

        // Quality indicator
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(getQualityColor(reading.quality))
        )
    }
}

/**
 * Quick actions card - simplified without permission system
 */
@Composable
private fun QuickActionsCard(
    actions: List<QuickAction>,
    onNavigateToFileUpload: () -> Unit,
    onNavigateToReceiptTemplate: () -> Unit,
    onNavigateToMeterReading: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Quick Actions",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(actions) { action ->
                    QuickActionItem(
                        action = action,
                        onClick = {
                            when (action.title) {
                                "Meter Reading" -> onNavigateToMeterReading()
                                "Import Data" -> onNavigateToFileUpload()
                                "Receipt Template" -> onNavigateToReceiptTemplate()
                                else -> { /* Handle other actions */ }
                            }
                        }
                    )
                }
            }
        }
    }
}

/**
 * Individual quick action item
 */
@Composable
private fun QuickActionItem(
    action: QuickAction,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .clickable { onClick() }
            .width(120.dp)
            .height(100.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
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
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = action.title,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Helper functions for real data processing with new MeterData structure
 */
private fun calculateSystemOverview(meters: List<MeterData>): SystemOverview {
    val activeMeters = meters.count { it.isActive }
    val inactiveMeters = meters.count { !it.isActive }
    val metersWithData = meters.count { it.hasReadingData }
    val totalConsumption = meters.mapNotNull { it.impKwh }.sum()

    return SystemOverview(
        totalMeters = meters.size,
        activeMeters = activeMeters,
        offlineMeters = inactiveMeters,
        todayReadings = metersWithData,
        totalConsumption = totalConsumption,
        pendingExports = if (meters.isNotEmpty()) 2 else 0,
        lastSyncSuccess = meters.isNotEmpty(),
        rankDistribution = emptyMap()
    )
}

private fun generateSampleReadings(meters: List<MeterData>): List<MeterReading> {
    if (meters.isEmpty()) return emptyList()

    return meters.filter { it.hasReadingData }.take(5).map { meter ->
        MeterReading(
            meterId = meter.uid,
            reading = meter.impKwh ?: 0f,
            timestamp = Date(),
            quality = ReadingQuality.entries[Random.nextInt(ReadingQuality.entries.size)]
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

/**
 * Get color for reading quality
 */
@Composable
private fun getQualityColor(quality: ReadingQuality): Color {
    return when (quality) {
        ReadingQuality.GOOD -> colorResource(R.color.success_light)
        ReadingQuality.FAIR -> colorResource(R.color.warning_light)
        ReadingQuality.POOR -> colorResource(R.color.error_light)
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