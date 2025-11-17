package com.example.meterkenshin.ui.screen

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import com.example.meterkenshin.BuildConfig
import com.example.meterkenshin.ui.component.card.AppSettingsCard
import com.example.meterkenshin.ui.manager.SessionManager
import com.example.meterkenshin.ui.manager.AppPreferences
import com.example.meterkenshin.ui.viewmodel.FileUploadViewModel

@Composable
fun SettingsScreen(
    sessionManager: SessionManager,
    fileUploadViewModel: FileUploadViewModel
) {
    val context = LocalContext.current
    val session = sessionManager.getSession()
    val scope = rememberCoroutineScope()
    var showHelpDialog by remember { mutableStateOf(false) }
    var showHardResetDialog by remember { mutableStateOf(false) }
    var showDeleteExportedDialog by remember { mutableStateOf(false) }
    var isResetting by remember { mutableStateOf(false) }

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
            var jsonEnabled by remember { mutableStateOf(AppPreferences.isJsonSavingEnabled(context)) }
            var printEnabled by remember { mutableStateOf(AppPreferences.isPrintingEnabled(context)) }

            AppSettingsCard(
                title = "JSON Saving",
                description = if (jsonEnabled)
                    "Enabled - Billing data will be saved as JSON"
                else "Disabled - No JSON files will be created",
                icon = Icons.Default.Save,
                isEnabled = jsonEnabled,
                enabledColor = Color(0xFF4CAF50),
                onToggle = {
                    jsonEnabled = it
                    AppPreferences.setJsonSavingEnabled(context, it)
                }
            )
            Spacer(modifier = Modifier.height(8.dp))
            AppSettingsCard(
                title = "Receipt Printing",
                description = if (printEnabled)
                    "Enabled - Print dialogs will be shown"
                else "Disabled - Printing will be skipped automatically",
                icon = Icons.Default.Print,
                isEnabled = printEnabled,
                enabledColor = Color(0xFF2196F3),
                onToggle = {
                    printEnabled = it
                    AppPreferences.setPrintingEnabled(context, it)
                }
            )
        }

// App Settings Section
        SettingsSection(title = "App Settings") {
            var notifFromTop by remember { mutableStateOf(AppPreferences.isNotificationFromTop(context)) }
            var dlmsConfirm by remember { mutableStateOf(AppPreferences.isDlmsConfirmEnabled(context)) }

            AppSettingsCard(
                title = "Notification Position",
                description = if (notifFromTop) "Show from top" else "Show from bottom",
                icon = if (notifFromTop) Icons.Default.VerticalAlignTop else Icons.Default.VerticalAlignBottom,
                isEnabled = notifFromTop,
                enabledColor = Color(0xFF2196F3),
                onToggle = {
                    notifFromTop = it
                    AppPreferences.setNotificationFromTop(context, it)
                }
            )
            Spacer(modifier = Modifier.height(8.dp))
            AppSettingsCard(
                title = "DLMS Confirm Dialog",
                description = if (dlmsConfirm)
                    "Enabled - Show confirmation before actions"
                else "Disabled - Execute actions directly",
                icon = Icons.Default.CheckCircle,
                isEnabled = dlmsConfirm,
                enabledColor = Color(0xFF4CAF50),
                onToggle = {
                    dlmsConfirm = it
                    AppPreferences.setDlmsConfirmEnabled(context, it)
                }
            )
        }

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 8.dp),
            thickness = DividerDefaults.Thickness,
            color = Color.Transparent
        )

        Box(modifier = Modifier.padding(horizontal = 16.dp)) {
            BluetoothPermissionCard(context = context)
        }

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 8.dp),
            thickness = DividerDefaults.Thickness,
            color = Color.Transparent
        )

        Box(modifier = Modifier.padding(horizontal = 16.dp)) {
            HardResetCard(
                onResetClick = { showHardResetDialog = true },
                isResetting = isResetting
            )
        }

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 16.dp),
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

    if (showHelpDialog) {
        HelpDialog(onDismiss = { showHelpDialog = false })
    }

    // Hard Reset Confirmation Dialog
    if (showHardResetDialog) {
        HardResetConfirmationDialog(
            onDismiss = { showHardResetDialog = false },
            onConfirm = {
                showHardResetDialog = false
                showDeleteExportedDialog = true
            }
        )
    }

    // Delete Exported Files Dialog
    if (showDeleteExportedDialog) {
        DeleteExportedFilesDialog(
            onDismiss = { showDeleteExportedDialog = false },
            onYes = {
                showDeleteExportedDialog = false
                isResetting = true
                scope.launch {
                    performHardReset(context, deleteExported = true)
                    fileUploadViewModel.checkExistingFiles(context)
                    isResetting = false
                }
            },
            onNo = {
                showDeleteExportedDialog = false
                isResetting = true
                scope.launch {
                    performHardReset(context, deleteExported = true)
                    fileUploadViewModel.checkExistingFiles(context)
                    isResetting = false
                }
            }
        )
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
                Text(
                    text = "Help & Documentation",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
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
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
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
                contentDescription = null
            )
        },
        title = { Text("Help & Support") },
        text = {
            Text("For assistance with MeterKenshin, please contact your system administrator or refer to the user manual.")
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("OK")
            }
        }
    )
}

@Composable
fun HardResetCard(
    onResetClick: () -> Unit,
    isResetting: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteForever,
                    contentDescription = "Hard Reset",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Hard Reset App",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        text = "Delete all app data and optionally exported files",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onResetClick,
                enabled = !isResetting,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                if (isResetting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = MaterialTheme.colorScheme.onError,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Resetting...")
                } else {
                    Icon(
                        imageVector = Icons.Default.RestartAlt,
                        contentDescription = "Reset",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Hard Reset")
                }
            }
        }
    }
}

@Composable
fun HardResetConfirmationDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = { Text("Hard Reset App?") },
        text = {
            Text("This will delete ALL app data including:\n\n• Meter CSV files\n• Printer CSV files\n• Rate CSV files\n• Billing JSON files\n• App settings\n\nThis action cannot be undone!")
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Continue")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun DeleteExportedFilesDialog(
    onDismiss: () -> Unit,
    onYes: () -> Unit,
    onNo: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Folder,
                contentDescription = null
            )
        },
        title = { Text("Delete Exported Files?") },
        text = {
            Text("Do you also want to delete exported files from the Download folder?\n\nThis includes all exported receipts and data files.")
        },
        confirmButton = {
            TextButton(
                onClick = onYes,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Yes, Delete All")
            }
        },
        dismissButton = {
            TextButton(onClick = onNo) {
                Text("No, Keep Exported")
            }
        }
    )
}

private suspend fun performHardReset(context: Context, deleteExported: Boolean) {
    withContext(Dispatchers.IO) {
        try {
            // Delete app_files folder
            val externalFilesDir = context.getExternalFilesDir(null)
            if (externalFilesDir != null) {
                val appFilesDir = File(externalFilesDir, "app_files")
                deleteDirectory(appFilesDir)
            }

            val internalAppFilesDir = File(context.filesDir, "app_files")
            deleteDirectory(internalAppFilesDir)

            // Delete billing JSON files
            externalFilesDir?.listFiles()?.forEach { file ->
                if (file.name.matches(Regex("\\d{6}_\\w+\\.json"))) {
                    file.delete()
                }
            }

            // Clear preferences
            AppPreferences.clearAll(context)

            // Delete exported files if requested
            if (deleteExported) {
                deleteExportedFiles()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

private fun deleteDirectory(directory: File) {
    if (directory.exists()) {
        directory.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                deleteDirectory(file)
            } else {
                file.delete()
            }
        }
        directory.delete()
    }
}

private fun deleteExportedFiles() {
    try {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)

        downloadsDir.listFiles()?.forEach { file ->
            if (file.name.contains("receipt", ignoreCase = true) ||
                file.name.contains("meter", ignoreCase = true) ||
                file.name.contains("export", ignoreCase = true)) {
                file.delete()
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}