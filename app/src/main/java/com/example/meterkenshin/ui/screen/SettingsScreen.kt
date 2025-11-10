package com.example.meterkenshin.ui.screen

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.meterkenshin.BuildConfig
import com.example.meterkenshin.manager.SessionManager
import com.example.meterkenshin.ui.manager.AppPreferences

@Composable
fun SettingsScreen(
    sessionManager: SessionManager
) {
    val context = LocalContext.current
    val session = sessionManager.getSession()
    var showHelpDialog by remember { mutableStateOf(false) }

    // No Scaffold or TopAppBar here - it's handled by AppWithDrawer in NavigationDrawer.kt
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // User Profile Section
        SettingsSection(title = "User Profile") {
            session?.let {
                UserProfileCard(
                    username = it.username,
                    role = it.role.displayName,
                    loginTime = it.loginTime.toString()
                )
            }
        }

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 8.dp),
            thickness = DividerDefaults.Thickness,
            color = DividerDefaults.color
        )

        // Data Management Section
        SettingsSection(title = "Data Management") {
            JsonSavingToggleCard(context = context)
            PrintingToggleCard(context = context)
        }

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 8.dp),
            thickness = DividerDefaults.Thickness,
            color = DividerDefaults.color
        )

        // Bluetooth Permissions Section
        SettingsSection(title = "Bluetooth Permissions") {
            BluetoothPermissionCard(context = context)
        }

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 8.dp),
            thickness = DividerDefaults.Thickness,
            color = DividerDefaults.color
        )

        // App Version Section
        SettingsSection(title = "About") {
            AppVersionCard()
        }

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 8.dp),
            thickness = DividerDefaults.Thickness,
            color = DividerDefaults.color
        )

        // Help & Support Section
        SettingsSection(title = "Help & Support") {
            HelpSupportCard(
                onHelpClick = { showHelpDialog = true }
            )
        }
    }

    // Help Dialog
    if (showHelpDialog) {
        HelpDialog(onDismiss = { showHelpDialog = false })
    }
}

@Composable
fun SettingsSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color.Black,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        content()
    }
}

@Composable
fun UserProfileCard(
    username: String,
    role: String,
    loginTime: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            SettingsItem(
                icon = Icons.Default.Person,
                title = "Username",
                value = username
            )
            Spacer(modifier = Modifier.height(12.dp))
            SettingsItem(
                icon = Icons.Default.AdminPanelSettings,
                title = "Role",
                value = role
            )
            Spacer(modifier = Modifier.height(12.dp))
            SettingsItem(
                icon = Icons.Default.AccessTime,
                title = "Last Login",
                value = loginTime
            )
        }
    }
}

@Composable
fun BluetoothPermissionCard(context: Context) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Bluetooth,
                    contentDescription = "Bluetooth",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Bluetooth Settings",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Manage Bluetooth permissions",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Open App Settings")
            }
        }
    }
}

@Composable
fun AppVersionCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            SettingsItem(
                icon = Icons.Default.Info,
                title = "App Name",
                value = "MeterKenshin"
            )
            Spacer(modifier = Modifier.height(12.dp))
            SettingsItem(
                icon = Icons.Default.Code,
                title = "Version",
                value = BuildConfig.VERSION_NAME
            )
            Spacer(modifier = Modifier.height(12.dp))
            SettingsItem(
                icon = Icons.Default.Numbers,
                title = "Build Number",
                value = BuildConfig.VERSION_CODE.toString()
            )
        }
    }
}

@Composable
fun HelpSupportCard(onHelpClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onHelpClick)
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Help,
                    contentDescription = "Help",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Help Center",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "View app documentation and FAQs",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "Open",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                thickness = DividerDefaults.Thickness,
                color = DividerDefaults.color
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = { /* TODO: Open support email */ })
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Email,
                    contentDescription = "Contact Support",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Contact Support",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "support@meterapp.com",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "Open",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun SettingsItem(
    icon: ImageVector,
    title: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun HelpDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Help,
                contentDescription = "Help",
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text("Help & Support")
        },
        text = {
            Column {
                Text(
                    text = "Getting Started",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = "1. Login with your credentials\n" +
                            "2. Import required CSV files\n" +
                            "3. Connect to Bluetooth printer\n" +
                            "4. Start meter reading process\n" +
                            "5. Upload completed readings",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Text(
                    text = "Troubleshooting",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = "• Ensure Bluetooth is enabled\n" +
                            "• Grant all required permissions\n" +
                            "• Check file format matches template\n" +
                            "• Restart app if issues persist",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Got It")
            }
        }
    )
}

@Composable
fun JsonSavingToggleCard(context: Context) {
    var isEnabled by remember {
        mutableStateOf(AppPreferences.isJsonSavingEnabled(context))
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    isEnabled = !isEnabled
                    AppPreferences.setJsonSavingEnabled(context, isEnabled)
                }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Save,
                contentDescription = null,
                tint = if (isEnabled) Color(0xFF4CAF50) else Color.Gray
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "JSON Saving",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = if (isEnabled) "Enabled - Billing data will be saved as JSON"
                    else "Disabled - No JSON files will be created",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Switch(
                checked = isEnabled,
                onCheckedChange = {
                    isEnabled = it
                    AppPreferences.setJsonSavingEnabled(context, it)
                }
            )
        }
    }
}

@Composable
fun PrintingToggleCard(context: Context) {
    var isEnabled by remember {
        mutableStateOf(AppPreferences.isPrintingEnabled(context))
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    isEnabled = !isEnabled
                    AppPreferences.setPrintingEnabled(context, isEnabled)
                }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Print,
                contentDescription = null,
                tint = if (isEnabled) Color(0xFF2196F3) else Color.Gray
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Receipt Printing",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = if (isEnabled) "Enabled - Print dialogs will be shown"
                    else "Disabled - Printing will be skipped automatically",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Switch(
                checked = isEnabled,
                onCheckedChange = {
                    isEnabled = it
                    AppPreferences.setPrintingEnabled(context, it)
                }
            )
        }
    }
}