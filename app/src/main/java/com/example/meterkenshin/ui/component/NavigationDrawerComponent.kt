package com.example.meterkenshin.ui.component

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.meterkenshin.R
import com.example.meterkenshin.manager.SessionManager
import com.example.meterkenshin.model.UserRole
import com.example.meterkenshin.model.UserSession
import com.example.meterkenshin.ui.viewmodel.PrinterBluetoothViewModel
import com.example.meterkenshin.ui.viewmodel.FileUploadViewModel
import com.example.meterkenshin.ui.viewmodel.MeterReadingViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class DrawerNavigationItem(
    val title: String,
    val icon: ImageVector,
    val action: () -> Unit,
    val screen: AppScreen
)

// Add this enum for tracking current screen
enum class AppScreen(val titleRes: Int) {
    LOGIN(R.string.login_title),
    HOME(R.string.home_title),
    METER_READING(R.string.meter_reading_title),
    IMPORT_DATA(R.string.file_upload_title),
    RECEIPT_TEMPLATE(R.string.receipt_template_title),
    METER_DETAIL(R.string.meter_detail_title),
    SETTINGS(R.string.settings_title),
    UNKNOWN(R.string.test),

}

@Suppress("unused")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppWithDrawer(
    sessionManager: SessionManager,
    fileUploadViewModel: FileUploadViewModel,
    meterReadingViewModel: MeterReadingViewModel,
    printerBluetoothViewModel: PrinterBluetoothViewModel,
    currentScreen: AppScreen = AppScreen.HOME,
    onNavigateToScreen: (AppScreen) -> Unit = {},
    onNavigateToTest: () -> Unit = {}, // Add this parameter for test navigation
    onLogout: () -> Unit = {},
    content: @Composable () -> Unit
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val session = sessionManager.getSession()

    // Only show drawer if user is logged in
    if (session == null) {
        // No drawer for login screen
        content()
    } else {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                DrawerContentWithTest(
                    session = session,
                    currentScreen = currentScreen,
                    onNavigationItemClick = onNavigateToScreen,
                    onTestClick = onNavigateToTest, // Pass the test callback
                    onCloseDrawer = {
                        scope.launch {
                            drawerState.close()
                        }
                    },
                    onLogout = onLogout
                )
            }
        ) {
            Scaffold(
                topBar = {
                    CenterAlignedTopAppBar(
                        title = {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = stringResource(currentScreen.titleRes),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = colorResource(R.color.black)
                                )
                                Text(
                                    text = remember {
                                        SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(
                                            Date()
                                        )
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colorResource(R.color.on_surface_light)
                                )
                            }
                        },
                        navigationIcon = {
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        if (drawerState.isClosed) {
                                            drawerState.open()
                                        } else {
                                            drawerState.close()
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Menu,
                                    contentDescription = stringResource(R.string.open_drawer),
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        },
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            titleContentColor = MaterialTheme.colorScheme.onSurface,
                            navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                        )
                    )
                }
            ) { paddingValues ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    content()
                }
            }
        }
    }
}

@Composable
private fun DrawerContentWithTest(
    session: UserSession,
    currentScreen: AppScreen,
    onNavigationItemClick: (AppScreen) -> Unit,
    onTestClick: () -> Unit, // Custom callback for test navigation
    onCloseDrawer: () -> Unit,
    onLogout: () -> Unit
) {
    ModalDrawerSheet(
        modifier = Modifier.width(300.dp),
        drawerContainerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // User Profile Section
            UserProfileSection(session = session)

            Spacer(modifier = Modifier.height(16.dp))

            // Main Navigation Items
            val navigationItems = listOf(
                DrawerNavigationItem(
                    title = stringResource(R.string.nav_home),
                    icon = Icons.Default.Home,
                    action = {
                        onNavigationItemClick(AppScreen.HOME)
                        onCloseDrawer()
                    },
                    screen = AppScreen.HOME
                ),
                DrawerNavigationItem(
                    title = stringResource(R.string.nav_meter_reading),
                    icon = Icons.Default.Speed,
                    action = {
                        onNavigationItemClick(AppScreen.METER_READING)
                        onCloseDrawer()
                    },
                    screen = AppScreen.METER_READING
                ),
                DrawerNavigationItem(
                    title = stringResource(R.string.nav_import_data),
                    icon = Icons.Default.Upload,
                    action = {
                        onNavigationItemClick(AppScreen.IMPORT_DATA)
                        onCloseDrawer()
                    },
                    screen = AppScreen.IMPORT_DATA
                ),
                DrawerNavigationItem(
                    title = stringResource(R.string.nav_receipt_template),
                    icon = Icons.Default.Receipt,
                    action = {
                        onNavigationItemClick(AppScreen.RECEIPT_TEMPLATE)
                        onCloseDrawer()
                    },
                    screen = AppScreen.RECEIPT_TEMPLATE
                ),
                DrawerNavigationItem(
                    title = stringResource(R.string.nav_settings),
                    icon = Icons.Default.Settings,
                    action = {
                        onNavigationItemClick(AppScreen.SETTINGS)
                        onCloseDrawer()
                    },
                    screen = AppScreen.SETTINGS
                )
            )

            // Render main navigation items
            navigationItems.forEach { item ->
                val isSelected = currentScreen == item.screen
                NavigationDrawerItem(
                    label = {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    },
                    icon = {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.title
                        )
                    },
                    selected = isSelected,
                    onClick = item.action,
                    modifier = Modifier.padding(horizontal = 12.dp),
                    colors = NavigationDrawerItemDefaults.colors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        unselectedContainerColor = MaterialTheme.colorScheme.surface,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }

            // OPTION 2: Always show debug section
            Spacer(modifier = Modifier.height(16.dp))

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Debug & Testing",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp)
            )

            NavigationDrawerItem(
                label = {
                    Text(
                        text = "ModernMeterCard Test",
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                icon = {
                    Icon(
                        imageVector = Icons.Default.Science,
                        contentDescription = "ModernMeterCard Test",
                        tint = MaterialTheme.colorScheme.tertiary
                    )
                },
                selected = false,
                onClick = {
                    onTestClick()
                    onCloseDrawer()
                },
                modifier = Modifier.padding(horizontal = 12.dp),
                colors = NavigationDrawerItemDefaults.colors(
                    unselectedContainerColor = MaterialTheme.colorScheme.surface,
                    unselectedIconColor = MaterialTheme.colorScheme.tertiary,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )

            Spacer(modifier = Modifier.weight(1f))

            LogoutSection(
                onLogout = {
                    onCloseDrawer()
                    onLogout()
                }
            )
        }
    }
}

@Composable
private fun UserProfileSection(session: UserSession) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // User Avatar
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier.fillMaxSize(),
                    shape = CircleShape,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    border = BorderStroke(2.dp, MaterialTheme.colorScheme.onPrimaryContainer)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when (session.role) {
                                UserRole.ADMIN -> Icons.Default.AdminPanelSettings
                                UserRole.READER -> Icons.Default.Security
                                UserRole.ROOT -> TODO()
                            },
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // User Info
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = session.username,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = session.role.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
private fun LogoutSection(
    onLogout: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        OutlinedButton(
            onClick = onLogout,
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.logout_button),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
        }
    }
}