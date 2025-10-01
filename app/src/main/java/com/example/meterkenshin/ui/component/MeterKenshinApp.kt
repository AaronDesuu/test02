package com.example.meterkenshin.ui.component

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.example.meterkenshin.manager.SessionManager
import com.example.meterkenshin.ui.screen.FileUploadScreen
import com.example.meterkenshin.ui.screen.HomeScreen
import com.example.meterkenshin.ui.screen.LoginScreen
import com.example.meterkenshin.model.Meter
import com.example.meterkenshin.ui.screen.MeterDetailScreen
import com.example.meterkenshin.ui.screen.MeterReadingScreen
import com.example.meterkenshin.ui.screen.ReceiptScreen
import com.example.meterkenshin.ui.screen.MeterCardTestScreen // Add this import
import com.example.meterkenshin.ui.screen.SettingsScreen
import com.example.meterkenshin.ui.viewmodel.PrinterBluetoothViewModel
import com.example.meterkenshin.ui.viewmodel.FileUploadViewModel
import com.example.meterkenshin.ui.viewmodel.MeterReadingViewModel

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
            "meter_card_test" -> AppScreen.UNKNOWN // Use UNKNOWN for test screen since we don't need a specific enum
            else -> AppScreen.HOME
        }
    }

    // Handle Android system back button
    BackHandler(enabled = isLoggedIn && currentScreen != "home") {
        currentScreen = when (currentScreen) {
            "meter_detail" -> "meter_reading"
            "meter_card_test" -> "home" // Add this line for test screen
            "file_upload", "receipt", "meter_reading" -> "home"
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
                AppScreen.SETTINGS -> currentScreen = "settings"  // Add this line
                else -> { }
            }
        },
        onNavigateToTest = { // Add this parameter - this is what was missing!
            currentScreen = "meter_card_test"
        },
        onLogout = {
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
                    }
                )
            }
            currentScreen == "home" -> {
                HomeScreen(
                    sessionManager = sessionManager,
                    onLogout = {
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
                        meter = meter
                    )
                }
            }
            currentScreen == "settings" -> {
                SettingsScreen(
                    sessionManager = sessionManager
                )
            }

            // Add this new case for the test screen - this was missing!
            currentScreen == "meter_card_test" -> {
                MeterCardTestScreen(
                )
            }
        }
    }
}