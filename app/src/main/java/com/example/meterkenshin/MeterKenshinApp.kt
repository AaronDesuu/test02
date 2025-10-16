package com.example.meterkenshin

import android.annotation.SuppressLint
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import android.util.Log
import com.example.meterkenshin.manager.SessionManager
import com.example.meterkenshin.ui.screen.FileUploadScreen
import com.example.meterkenshin.ui.screen.HomeScreen
import com.example.meterkenshin.ui.screen.LoginScreen
import com.example.meterkenshin.model.Meter
import com.example.meterkenshin.ui.component.AppScreen
import com.example.meterkenshin.ui.component.AppWithDrawer
import com.example.meterkenshin.ui.screen.MeterDetailScreen
import com.example.meterkenshin.ui.screen.MeterReadingScreen
import com.example.meterkenshin.ui.screen.ReceiptScreen
import com.example.meterkenshin.ui.screen.MeterCardTestScreen
import com.example.meterkenshin.ui.screen.SettingsScreen
import com.example.meterkenshin.ui.viewmodel.DLMSViewModel
import com.example.meterkenshin.ui.viewmodel.PrinterBluetoothViewModel
import com.example.meterkenshin.ui.viewmodel.FileUploadViewModel
import com.example.meterkenshin.ui.viewmodel.MeterReadingViewModel
import kotlinx.coroutines.delay

@SuppressLint("MissingPermission")
@Composable
fun MeterKenshinApp(
    sessionManager: SessionManager,
    fileUploadViewModel: FileUploadViewModel,
    meterReadingViewModel: MeterReadingViewModel,
    printerBluetoothViewModel: PrinterBluetoothViewModel
) {
    val context = LocalContext.current

    // Add DLMS ViewModel
    val dlmsViewModel = remember { DLMSViewModel(context) }

    // Use the original session checking logic
    var isLoggedIn by remember { mutableStateOf(sessionManager.isLoggedIn()) }
    var currentScreen by remember { mutableStateOf("home") }
    var selectedMeter by remember { mutableStateOf<Meter?>(null) }

    // Track current screen for drawer title
    val currentAppScreen = remember(currentScreen) {
        when (currentScreen) {
            "login" -> AppScreen.LOGIN
            "home" -> AppScreen.HOME
            "meter_reading" -> AppScreen.METER_READING
            "file_upload" -> AppScreen.IMPORT_DATA
            "receipt" -> AppScreen.RECEIPT_TEMPLATE
            "meter_detail" -> AppScreen.METER_DETAIL
            "settings" -> AppScreen.SETTINGS
            "meter_card_test" -> AppScreen.UNKNOWN
            else -> AppScreen.HOME
        }
    }

    // Handle Android system back button
    BackHandler(enabled = isLoggedIn && currentScreen != "home") {
        currentScreen = when (currentScreen) {
            "meter_detail" -> "meter_reading"
            "meter_card_test" -> "home"
            "file_upload", "receipt", "meter_reading", "settings" -> "home"
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

    // ✅ NEW: Start BLE scanning automatically when logged in
    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn) {
            try {
                // Wait a bit for initialization
                delay(1000)

                Log.i("MeterKenshinApp", "User logged in - starting automatic BLE scan")
                meterReadingViewModel.startBLEScanning()
            } catch (e: Exception) {
                Log.e("MeterKenshinApp", "Error starting BLE scan after login", e)
            }
        }
    }

    // Wrap everything with AppWithDrawer
    AppWithDrawer(
        sessionManager = sessionManager,
        fileUploadViewModel = fileUploadViewModel,
        meterReadingViewModel = meterReadingViewModel,
        printerBluetoothViewModel = printerBluetoothViewModel,
        currentScreen = currentAppScreen,
        onNavigateToScreen = { screen ->
            when (screen) {
                AppScreen.HOME -> currentScreen = "home"
                AppScreen.METER_READING -> currentScreen = "meter_reading"
                AppScreen.IMPORT_DATA -> currentScreen = "file_upload"
                AppScreen.RECEIPT_TEMPLATE -> currentScreen = "receipt"
                AppScreen.SETTINGS -> currentScreen = "settings"
                else -> {}
            }
        },
        onNavigateToTest = {
            currentScreen = "meter_card_test"
        },
        onLogout = {
            // ✅ NEW: Stop BLE scanning on logout
            try {
                meterReadingViewModel.stopBLEScanning()
                Log.i("MeterKenshinApp", "BLE scanning stopped on logout")
            } catch (e: Exception) {
                Log.e("MeterKenshinApp", "Error stopping BLE scan on logout", e)
            }

            // Handle logout from drawer
            sessionManager.logout()
            isLoggedIn = false
            currentScreen = "login"
        }
    ) {
        when {
            !isLoggedIn || currentScreen == "login" -> {
                LoginScreen(
                    sessionManager = sessionManager,
                    onLoginSuccess = {
                        isLoggedIn = true
                        currentScreen = "home"
                        // Initialize file checking after successful login (preserve original)
                        fileUploadViewModel.checkExistingFiles(context)

                        // ✅ NEW: Start BLE scanning immediately after successful login
                        try {
                            Log.i("MeterKenshinApp", "Login successful - starting BLE scan")
                            meterReadingViewModel.startBLEScanning()
                        } catch (e: Exception) {
                            Log.e("MeterKenshinApp", "Error starting BLE scan after login", e)
                        }
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

                        sessionManager.logout()
                        isLoggedIn = false
                        currentScreen = "login"
                    },
                    onNavigateToMeterReading = { currentScreen = "meter_reading" },
                    fileUploadViewModel = fileUploadViewModel,
                    meterReadingViewModel = meterReadingViewModel,
                    printerBluetoothViewModel = printerBluetoothViewModel
                )
            }

            currentScreen == "file_upload" -> {
                FileUploadScreen(
                    viewModel = fileUploadViewModel
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
                        onRegistration = {
                            // Start registration sequence
                            dlmsViewModel.performRegistration(
                                bleService = null, // TODO: pass BLE service
                                bluetoothId = meter.bluetoothId ?: ""
                            )
                        },
                        onReadData = { /* TODO */ },
                        onLoadProfile = { /* TODO */ },
                        onEventLog = { /* TODO */ },
                        onBillingData = { /* TODO */ },
                        onSetClock = { /* TODO */ }
                    )
                }
            }

            currentScreen == "settings" -> {
                SettingsScreen(
                    sessionManager = sessionManager
                )
            }

            currentScreen == "meter_card_test" -> {
                MeterCardTestScreen()
            }
        }
    }
}