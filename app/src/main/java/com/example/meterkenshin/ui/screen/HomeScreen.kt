package com.example.meterkenshin.ui.screen

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.meterkenshin.R
import com.example.meterkenshin.manager.SessionManager
import com.example.meterkenshin.model.*
import com.example.meterkenshin.ui.screen.FileUploadScreen
import java.text.SimpleDateFormat
import java.util.*
import androidx.core.graphics.toColorInt

private const val TAG = "HomeScreen"

// Update the HomeScreen composable function signature to include the new navigation parameter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    sessionManager: SessionManager,
    onLogout: () -> Unit,
    onNavigateToFileUpload: () -> Unit = {},
    onNavigateToReceiptTemplate: () -> Unit = {} // Add this parameter
) {
    val session = sessionManager.getSession()

    if (session == null) {
        onLogout()
        return
    }

    // Mock data - in a real app, this would come from a repository/ViewModel
    val meters = remember { MockDataGenerator.generateMeters() }
    val recentReadings = remember { MockDataGenerator.generateRecentReadings(meters) }
    val systemOverview = remember { MockDataGenerator.generateSystemOverview(meters) }
    val consumptionSummary = remember { MockDataGenerator.generateConsumptionSummary() }
    val alerts = remember { MockDataGenerator.generateAlerts() }

    var showLogoutDialog by remember { mutableStateOf(false) }
    var selectedTimeRange by remember { mutableStateOf("Today") }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Enhanced Top App Bar
        CenterAlignedTopAppBar(
            title = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.home_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            actions = {
                // logout button
                IconButton(onClick = { showLogoutDialog = true }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                        contentDescription = stringResource(R.string.logout_button)
                    )
                }
            }
        )

        // Main Content
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Welcome Card
            item {
                WelcomeCard(session = session, systemOverview = systemOverview)
            }

            // Quick Actions - Updated to pass navigation parameters
            item {
                QuickActionsSection(
                    userRole = session.role,
                    onNavigateToFileUpload = onNavigateToFileUpload,
                    onNavigateToReceiptTemplate = onNavigateToReceiptTemplate // Pass the navigation function
                )
            }

            // System Overview Statistics
            item {
                SystemOverviewSection(overview = systemOverview)
            }

            // Recent Readings
            item {
                RecentReadingsSection(
                    readings = recentReadings.take(5),
                    meters = meters
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
private fun WelcomeCard(
    session: UserSession,
    systemOverview: SystemOverview
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (session.role) {
                UserRole.ROOT -> MaterialTheme.colorScheme.errorContainer
                UserRole.ADMIN -> MaterialTheme.colorScheme.tertiaryContainer
                UserRole.READER -> MaterialTheme.colorScheme.secondaryContainer
            }
        ),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Role Icon
                Card(
                    modifier = Modifier.size(56.dp),
                    shape = CircleShape,
                    colors = CardDefaults.cardColors(
                        containerColor = when (session.role) {
                            UserRole.ROOT -> MaterialTheme.colorScheme.error
                            UserRole.ADMIN -> MaterialTheme.colorScheme.tertiary
                            UserRole.READER -> MaterialTheme.colorScheme.secondary
                        }
                    )
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when (session.role) {
                                UserRole.ROOT -> Icons.Default.Security
                                UserRole.ADMIN -> Icons.Default.AdminPanelSettings
                                UserRole.READER -> Icons.Default.Visibility
                            },
                            contentDescription = null,
                            tint = when (session.role) {
                                UserRole.ROOT -> MaterialTheme.colorScheme.onError
                                UserRole.ADMIN -> MaterialTheme.colorScheme.onTertiary
                                UserRole.READER -> MaterialTheme.colorScheme.onSecondary
                            },
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.welcome_message, session.username),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = when (session.role) {
                            UserRole.ROOT -> MaterialTheme.colorScheme.onErrorContainer
                            UserRole.ADMIN -> MaterialTheme.colorScheme.onTertiaryContainer
                            UserRole.READER -> MaterialTheme.colorScheme.onSecondaryContainer
                        }
                    )

                    Text(
                        text = stringResource(R.string.user_role, session.role.displayName),
                        style = MaterialTheme.typography.bodyLarge,
                        color = when (session.role) {
                            UserRole.ROOT -> MaterialTheme.colorScheme.onErrorContainer
                            UserRole.ADMIN -> MaterialTheme.colorScheme.onTertiaryContainer
                            UserRole.READER -> MaterialTheme.colorScheme.onSecondaryContainer
                        }.copy(alpha = 0.8f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = stringResource(R.string.session_expires, session.getDaysUntilExpiry()),
                style = MaterialTheme.typography.bodyMedium,
                color = when (session.role) {
                    UserRole.ROOT -> MaterialTheme.colorScheme.onErrorContainer
                    UserRole.ADMIN -> MaterialTheme.colorScheme.onTertiaryContainer
                    UserRole.READER -> MaterialTheme.colorScheme.onSecondaryContainer
                }.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun QuickActionsSection(
    userRole: UserRole,
    onNavigateToFileUpload: () -> Unit,
    onNavigateToReceiptTemplate: () -> Unit = {} // Add this parameter
) {
    Log.d(TAG, "QuickActionsSection composing with navigation callbacks")
    Log.d(TAG, "FileUpload callback: $onNavigateToFileUpload")
    Log.d(TAG, "ReceiptTemplate callback: $onNavigateToReceiptTemplate")

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
            Log.d(TAG, "Quick actions: ${quickActions.map { it.title }}")

            items(quickActions) { action ->
                QuickActionCard(
                    action = action,
                    onNavigateToFileUpload = onNavigateToFileUpload,
                    onNavigateToReceiptTemplate = onNavigateToReceiptTemplate // Pass the new navigation function
                )
            }
        }
    }
}

@Composable
private fun QuickActionCard(
    action: QuickAction,
    onNavigateToFileUpload: () -> Unit,
    onNavigateToReceiptTemplate: () -> Unit = {} // Add this parameter
) {
    Card(
        onClick = {
            // Handle navigation based on action title
            when (action.title) {
                "Import Data" -> {
                    Log.d(TAG, "Navigating to File Upload")
                    onNavigateToFileUpload()
                }
                "Receipt Template" -> {
                    Log.d(TAG, "Navigating to Receipt Template")
                    onNavigateToReceiptTemplate()
                }
                else -> {
                    Log.d(TAG, "Action '${action.title}' not implemented yet")
                    // Handle other actions as needed
                }
            }
        },
        modifier = Modifier
            .width(120.dp)
            .height(100.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
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
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = action.title,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SystemOverviewSection(overview: SystemOverview) {
    Column {
        Text(
            text = stringResource(R.string.overview_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 4.dp)
        ) {
            item {
                StatCard(
                    title = stringResource(R.string.total_meters),
                    value = overview.totalMeters.toString(),
                    icon = Icons.Default.ElectricMeter,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            item {
                StatCard(
                    title = stringResource(R.string.active_meters),
                    value = overview.activeMeters.toString(),
                    icon = Icons.Default.CheckCircle,
                    color = Color(0xFF4CAF50)
                )
            }
            item {
                StatCard(
                    title = stringResource(R.string.offline_meters),
                    value = overview.offlineMeters.toString(),
                    icon = Icons.Default.ErrorOutline,
                    color = Color(0xFFF44336)
                )
            }
            item {
                StatCard(
                    title = stringResource(R.string.today_readings),
                    value = overview.todayReadings.toString(),
                    icon = Icons.Default.Timeline,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
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
    meters: List<Meter>
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

            TextButton(onClick = { /* TODO: Navigate to all readings */ }) {
                Text(stringResource(R.string.view_all_readings))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (readings.isEmpty()) {
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
        } else {
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
                    text = stringResource(R.string.meter_id, reading.meterId),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = meter?.location ?: "Unknown Location",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(
                        R.string.reading_timestamp,
                        SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(reading.timestamp)
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = stringResource(R.string.reading_value, reading.reading),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                // Reading quality indicator
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
    val requiredPermission: Permission?
)

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
            icon = Icons.Default.Edit,
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