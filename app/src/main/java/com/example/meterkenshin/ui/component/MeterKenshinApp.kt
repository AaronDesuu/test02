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
import com.example.meterkenshin.ui.viewmodel.BluetoothViewModel
import com.example.meterkenshin.ui.viewmodel.FileUploadViewModel
import com.example.meterkenshin.ui.viewmodel.MeterReadingViewModel

@Composable
fun MeterKenshinApp(
    sessionManager: SessionManager,
    fileUploadViewModel: FileUploadViewModel,
    meterReadingViewModel: MeterReadingViewModel,
    bluetoothViewModel: BluetoothViewModel
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
            else -> AppScreen.HOME
        }
    }

    // Handle Android system back button
    BackHandler(enabled = isLoggedIn && currentScreen != "home") {
        when (currentScreen) {
            "meter_detail" -> currentScreen = "meter_reading"
            "file_upload", "receipt", "meter_reading" -> currentScreen = "home"
            else -> currentScreen = "home"
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
        bluetoothViewModel = bluetoothViewModel,
        currentScreen = currentAppScreen,
        onNavigateToScreen = { screen ->
            // Handle navigation from drawer
            when (screen) {
                AppScreen.HOME -> currentScreen = "home"
                AppScreen.METER_READING -> currentScreen = "meter_reading"
                AppScreen.IMPORT_DATA -> currentScreen = "file_upload"
                AppScreen.RECEIPT_TEMPLATE -> currentScreen = "receipt"
                AppScreen.UNKNOWN -> { /* Settings not implemented yet */ }
                else -> { /* Handle other screens */ }
            }
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
                    onNavigateToFileUpload = { currentScreen = "file_upload" },
                    onNavigateToReceiptTemplate = { currentScreen = "receipt" },
                    onNavigateToMeterReading = { currentScreen = "meter_reading" },
                    fileUploadViewModel = fileUploadViewModel,
                    meterReadingViewModel = meterReadingViewModel,
                    bluetoothViewModel = bluetoothViewModel
                )
            }
            currentScreen == "file_upload" -> {
                FileUploadScreen(
                    viewModel = fileUploadViewModel,
                    onUploadComplete = {
                        // Refresh file data after upload
                        fileUploadViewModel.checkExistingFiles(context)
                        currentScreen = "home"
                    },
                    onBackPressed = { currentScreen = "home" }
                )
            }
            currentScreen == "receipt" -> {
                ReceiptScreen(
                    fileUploadViewModel = fileUploadViewModel,
                    bluetoothViewModel = bluetoothViewModel,
                    onBackPressed = { currentScreen = "home" },
                    onNavigateToFileUpload = { currentScreen = "file_upload" }
                )
            }
            currentScreen == "meter_reading" -> {
                MeterReadingScreen(
                    fileUploadViewModel = fileUploadViewModel,
                    meterReadingViewModel = meterReadingViewModel,
                    onBackPressed = { currentScreen = "home" },
//                    onNavigateToFileUpload = { currentScreen = "file_upload" },
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
                        onBackPressed = { currentScreen = "meter_reading" }
                    )
                }
            }
        }
    }
}