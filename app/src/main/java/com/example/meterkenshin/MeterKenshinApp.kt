package com.example.meterkenshin

import android.annotation.SuppressLint
import android.os.Build
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.example.meterkenshin.ui.manager.AppPreferences
import com.example.meterkenshin.ui.manager.NotificationManager
import com.example.meterkenshin.ui.manager.SessionManager
import com.example.meterkenshin.model.Meter
import com.example.meterkenshin.ui.AppScreen
import com.example.meterkenshin.ui.AppWithDrawer
import com.example.meterkenshin.ui.NotificationHost
import com.example.meterkenshin.ui.screen.FileUploadScreen
import com.example.meterkenshin.ui.screen.HomeScreen
import com.example.meterkenshin.ui.screen.LoginScreen
import com.example.meterkenshin.ui.screen.MeterDetailScreen
import com.example.meterkenshin.ui.screen.MeterReadingScreen
import com.example.meterkenshin.ui.screen.ReceiptScreen
import com.example.meterkenshin.ui.screen.SettingsScreen
import com.example.meterkenshin.ui.viewmodel.FileUploadViewModel
import com.example.meterkenshin.ui.viewmodel.MeterReadingViewModel
import com.example.meterkenshin.ui.viewmodel.PrinterBluetoothViewModel
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.meterkenshin.model.UserRole
import com.example.meterkenshin.ui.screen.ExportScreen

@OptIn(ExperimentalMaterial3Api::class)
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@SuppressLint("MissingPermission")
@Composable
fun MeterKenshinApp(
    sessionManager: SessionManager,
    fileUploadViewModel: FileUploadViewModel,
    meterReadingViewModel: MeterReadingViewModel,
    printerBluetoothViewModel: PrinterBluetoothViewModel
) {
    val context = LocalContext.current

    // Use the original session checking logic
    var isLoggedIn by remember { mutableStateOf(sessionManager.isLoggedIn()) }
    var currentScreen by remember { mutableStateOf("home") }
    var selectedMeter by remember { mutableStateOf<Meter?>(null) }
    var showAddMeterSheet by remember { mutableStateOf(false) }
    val isAdmin = remember { sessionManager.getSession()?.role == UserRole.ADMIN }

    // Track current screen for drawer title
    val currentAppScreen = remember(currentScreen) {
        when (currentScreen) {
            "login" -> AppScreen.LOGIN
            "home" -> AppScreen.HOME
            "meter_reading" -> AppScreen.METER_READING
            "file_upload" -> AppScreen.IMPORT_DATA
            "export_data" -> AppScreen.EXPORT_DATA
            "receipt" -> AppScreen.RECEIPT_TEMPLATE
            "meter_detail" -> AppScreen.METER_DETAIL
            "settings" -> AppScreen.SETTINGS
            else -> AppScreen.HOME
        }
    }

    // Handle Android system back button
    BackHandler(enabled = isLoggedIn && currentScreen != "home") {
        currentScreen = when (currentScreen) {
            "meter_detail" -> "meter_reading"
            "file_upload", "receipt", "meter_reading", "settings", "export_data" -> "home"
            else -> "home"
        }
    }

    // Check login state immediately on app start (preserve original logic)
    LaunchedEffect(Unit) {
        isLoggedIn = sessionManager.isLoggedIn()
        if (isLoggedIn) {
            currentScreen = "home"
            // Initialize file checking after login check (preserve original behavior)
            fileUploadViewModel.checkExistingFiles(context)
        } else {
            currentScreen = "login"
        }
    }

    // Start BLE scanning reactively when meters are loaded
    // Previous approach used a fixed 1-second delay which caused a race condition:
    // scanning started before meters were loaded, failed silently, and never retried
    val meterUiState by meterReadingViewModel.uiState.collectAsState()
    val metersLoaded = isLoggedIn && meterUiState.allMeters.isNotEmpty()
    val uploadState by fileUploadViewModel.uploadState.collectAsState()
    val allFilesUploaded = uploadState.allFilesUploaded

    LaunchedEffect(metersLoaded) {
        if (metersLoaded) {
            try {
                Log.i("MeterKenshinApp", "Meters loaded (${meterUiState.allMeters.size}) - starting BLE scan")
                meterReadingViewModel.startBLEScanning()
            } catch (e: Exception) {
                Log.e("MeterKenshinApp", "Error starting BLE scan", e)
            }
        }
    }

    // Show scanning notification only when all required files are uploaded
    LaunchedEffect(allFilesUploaded) {
        if (allFilesUploaded && metersLoaded) {
            NotificationManager.showInfo("Scanning for nearby meters...")
        }
    }

    // Wrap everything with AppWithDrawer
    NotificationHost {
        AppWithDrawer(
            sessionManager = sessionManager,
            currentScreen = currentAppScreen,
            onNavigateToScreen = { screen ->
                when (screen) {
                    AppScreen.HOME -> currentScreen = "home"
                    AppScreen.METER_READING -> currentScreen = "meter_reading"
                    AppScreen.IMPORT_DATA -> currentScreen = "file_upload"
                    AppScreen.EXPORT_DATA -> currentScreen = "export_data"
                    AppScreen.RECEIPT_TEMPLATE -> currentScreen = "receipt"
                    AppScreen.SETTINGS -> currentScreen = "settings"
                    else -> {}
                }
            },
            onLogout = {
                // ✅ NEW: Stop BLE scanning on logout
                try {
                    meterReadingViewModel.stopBLEScanning()
                    Log.i("MeterKenshinApp", "BLE scanning stopped on logout")
                } catch (e: Exception) {
                    Log.e("MeterKenshinApp", "Error stopping BLE scan on logout", e)
                }

                // ✅ FIXED: Clear meter data on logout to prevent cross-user contamination
                try {
                    meterReadingViewModel.clearMeters()
                    Log.i("MeterKenshinApp", "Meter data cleared on logout")
                } catch (e: Exception) {
                    Log.e("MeterKenshinApp", "Error clearing meters on logout", e)
                }

                // ✅ FIXED: Clear AppPreferences cache on logout
                try {
                    AppPreferences.clearCache()
                    Log.i("MeterKenshinApp", "AppPreferences cache cleared on logout")
                } catch (e: Exception) {
                    Log.e("MeterKenshinApp", "Error clearing AppPreferences cache", e)
                }

                // Handle logout from drawer
                sessionManager.logout()
                isLoggedIn = false
                currentScreen = "login"

                // ✅ FIXED: Re-check files after logout to update state for next user
                try {
                    fileUploadViewModel.checkExistingFiles(context)
                    Log.i("MeterKenshinApp", "File state refreshed after logout")
                } catch (e: Exception) {
                    Log.e("MeterKenshinApp", "Error checking files after logout", e)
                }
            },
            topBarActions = {
                if (isAdmin && currentScreen == "meter_reading") {
                    IconButton(onClick = { showAddMeterSheet = true }) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add Meter"
                        )
                    }
                }
            }
        ) {
            // Add Meter Bottom Sheet
            if (showAddMeterSheet) {
                AddMeterBottomSheet(
                    meterReadingViewModel = meterReadingViewModel,
                    onDismiss = { showAddMeterSheet = false }
                )
            }

            when {
                !isLoggedIn || currentScreen == "login" -> {
                    LoginScreen(
                        sessionManager = sessionManager,
                        onLoginSuccess = {
                            isLoggedIn = true
                            currentScreen = "home"
                            // Initialize file checking after successful login (preserve original)
                            fileUploadViewModel.checkExistingFiles(context)
                            // BLE scanning starts automatically via LaunchedEffect when meters are loaded
                        }
                    )
                }

                currentScreen == "home" -> {
                    HomeScreen(
                        sessionManager = sessionManager,
                        onLogout = {
                            // ✅ NEW: Stop BLE scanning on logout from HomeScreen
                            try {
                                meterReadingViewModel.stopBLEScanning()
                                Log.i(
                                    "MeterKenshinApp",
                                    "BLE scanning stopped on logout from HomeScreen"
                                )
                            } catch (e: Exception) {
                                Log.e("MeterKenshinApp", "Error stopping BLE scan", e)
                            }

                            // ✅ FIXED: Clear meter data on logout to prevent cross-user contamination
                            try {
                                meterReadingViewModel.clearMeters()
                                Log.i("MeterKenshinApp", "Meter data cleared on logout from HomeScreen")
                            } catch (e: Exception) {
                                Log.e("MeterKenshinApp", "Error clearing meters on logout", e)
                            }

                            // ✅ FIXED: Clear AppPreferences cache on logout
                            try {
                                AppPreferences.clearCache()
                                Log.i("MeterKenshinApp", "AppPreferences cache cleared on logout from HomeScreen")
                            } catch (e: Exception) {
                                Log.e("MeterKenshinApp", "Error clearing AppPreferences cache", e)
                            }

                            sessionManager.logout()
                            isLoggedIn = false
                            currentScreen = "login"

                            // ✅ FIXED: Re-check files after logout to update state for next user
                            try {
                                fileUploadViewModel.checkExistingFiles(context)
                                Log.i("MeterKenshinApp", "File state refreshed after logout from HomeScreen")
                            } catch (e: Exception) {
                                Log.e("MeterKenshinApp", "Error checking files after logout", e)
                            }
                        },
                        onNavigateToMeterReading = { currentScreen = "meter_reading" },
                        onNavigateToMeterDetail = { meter ->
                            selectedMeter = meter
                            currentScreen = "meter_detail"
                        },
                        fileUploadViewModel = fileUploadViewModel,
                        meterReadingViewModel = meterReadingViewModel,
                        printerBluetoothViewModel = printerBluetoothViewModel
                    )
                }

                currentScreen == "file_upload" -> {
                    FileUploadScreen(
                        viewModel = fileUploadViewModel,
                        meterReadingViewModel = meterReadingViewModel
                    )
                }

                currentScreen == "receipt" -> {
                    ReceiptScreen(
                        fileUploadViewModel = fileUploadViewModel,
                        printerBluetoothViewModel = printerBluetoothViewModel,
                        onNavigateToFileUpload = { currentScreen = "file_upload" }
                    )
                }

                currentScreen == "meter_reading" -> {
                    MeterReadingScreen(
                        fileUploadViewModel = fileUploadViewModel,
                        meterReadingViewModel = meterReadingViewModel,
                        onNavigateToMeterDetail = { meter ->
                            selectedMeter = meter
                            currentScreen = "meter_detail"
                        }
                    )
                }

                currentScreen == "meter_detail" -> {
                    selectedMeter?.let { meter ->
                        MeterDetailScreen(
                            meter = meter,
                            sessionManager = sessionManager
                        )
                    }
                }

                currentScreen == "settings" -> {
                    SettingsScreen(
                        sessionManager = sessionManager,
                        fileUploadViewModel = fileUploadViewModel,
                        meterReadingViewModel = meterReadingViewModel,
                        onLogout = {
                            try {
                                meterReadingViewModel.stopBLEScanning()
                            } catch (_: Exception) {}
                            AppPreferences.clearCache()
                            sessionManager.logout()
                            isLoggedIn = false
                            currentScreen = "login"
                        },
                        onNavigateToHome = { currentScreen = "home" }
                    )
                }

                currentScreen == "export_data" -> {
                    ExportScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddMeterBottomSheet(
    meterReadingViewModel: MeterReadingViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val uiState by meterReadingViewModel.uiState.collectAsState()
    val existingSerialNumbers = remember(uiState.allMeters) {
        uiState.allMeters.map { it.serialNumber }.toSet()
    }

    var serialNumber by remember { mutableStateOf("") }
    var bluetoothId by remember { mutableStateOf("") }

    val serialTrimmed = serialNumber.trim()
    val bluetoothTrimmed = bluetoothId.trim().uppercase()
    val isDuplicateSerial = serialTrimmed in existingSerialNumbers
    val isValidMac = bluetoothTrimmed.matches(
        Regex("^([0-9A-F]{2}:){5}[0-9A-F]{2}$")
    )
    val isValid = serialTrimmed.isNotEmpty() && bluetoothTrimmed.isNotEmpty() &&
            !isDuplicateSerial && isValidMac

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = androidx.compose.ui.Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Add New Meter",
                style = MaterialTheme.typography.titleLarge
            )

            Text(
                text = "Add a new meter to the system",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = serialNumber,
                onValueChange = { serialNumber = it },
                label = { Text("Serial Number") },
                singleLine = true,
                isError = isDuplicateSerial,
                supportingText = if (isDuplicateSerial) {
                    { Text("Serial number already exists", color = MaterialTheme.colorScheme.error) }
                } else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = androidx.compose.ui.Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = bluetoothId,
                onValueChange = { bluetoothId = it },
                label = { Text("Bluetooth MAC Address") },
                placeholder = { Text("AA:BB:CC:DD:EE:FF") },
                singleLine = true,
                isError = bluetoothTrimmed.isNotEmpty() && !isValidMac,
                supportingText = if (bluetoothTrimmed.isNotEmpty() && !isValidMac) {
                    { Text("Format: AA:BB:CC:DD:EE:FF", color = MaterialTheme.colorScheme.error) }
                } else null,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters
                ),
                modifier = androidx.compose.ui.Modifier.fillMaxWidth()
            )

            Spacer(modifier = androidx.compose.ui.Modifier.height(8.dp))

            Button(
                onClick = {
                    meterReadingViewModel.addMeterToCSV(context, serialTrimmed, bluetoothTrimmed)
                    NotificationManager.showSuccess("Meter $serialTrimmed added")
                    onDismiss()
                },
                enabled = isValid,
                modifier = androidx.compose.ui.Modifier.fillMaxWidth()
            ) {
                Text("Add Meter")
            }

            Spacer(modifier = androidx.compose.ui.Modifier.height(16.dp))
        }
    }
}