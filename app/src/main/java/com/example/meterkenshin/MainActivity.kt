// app/src/main/java/com/example/meterkenshin/MainActivity.kt
package com.example.meterkenshin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.meterkenshin.model.MeterData
import com.example.meterkenshin.ui.screen.FileUploadScreen
import com.example.meterkenshin.ui.screen.HomeScreen
import com.example.meterkenshin.ui.screen.MeterDetailScreen
import com.example.meterkenshin.ui.screen.MeterReadingScreen
import com.example.meterkenshin.ui.screen.ReceiptScreen
import com.example.meterkenshin.ui.theme.MeterKenshinTheme
import com.example.meterkenshin.ui.viewmodel.BluetoothViewModel
import com.example.meterkenshin.ui.viewmodel.FileUploadViewModel
import com.example.meterkenshin.ui.viewmodel.MeterReadingViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MeterKenshinTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MeterKenshinApp()
                }
            }
        }
    }
}

@Composable
fun MeterKenshinApp() {
    val context = LocalContext.current

    // ViewModels
    val fileUploadViewModel: FileUploadViewModel = viewModel()
    val meterReadingViewModel: MeterReadingViewModel = viewModel()
    val bluetoothViewModel: BluetoothViewModel = viewModel()

    // Initialize file check
    androidx.compose.runtime.LaunchedEffect(Unit) {
        fileUploadViewModel.checkExistingFiles(context)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { paddingValues ->
        AppNavigation(
            context = context,
            fileUploadViewModel = fileUploadViewModel,
            meterReadingViewModel = meterReadingViewModel,
            bluetoothViewModel = bluetoothViewModel,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        )
    }
}

@Composable
private fun AppNavigation(
    context: android.content.Context,
    fileUploadViewModel: FileUploadViewModel,
    meterReadingViewModel: MeterReadingViewModel,
    bluetoothViewModel: BluetoothViewModel,
    modifier: Modifier = Modifier
) {
    var currentScreen by remember { mutableStateOf("home") }
    var selectedMeter by remember { mutableStateOf<MeterData?>(null) }

    when (currentScreen) {
        "home" -> {
            HomeScreen(
                onNavigateToFileUpload = { currentScreen = "file_upload" },
                onNavigateToReceiptTemplate = { currentScreen = "receipt" },
                onNavigateToMeterReading = { currentScreen = "meter_reading" },
                fileUploadViewModel = fileUploadViewModel,
                meterReadingViewModel = meterReadingViewModel,
                bluetoothViewModel = bluetoothViewModel
            )
        }

        "file_upload" -> {
            FileUploadScreen(
                viewModel = fileUploadViewModel,
                onUploadComplete = {
                    // Refresh file data and meter data after upload
                    fileUploadViewModel.checkExistingFiles(context)
                    currentScreen = "home"
                },
                onBackPressed = { currentScreen = "home" }
            )
        }

        "meter_reading" -> {
            MeterReadingScreen(
                fileUploadViewModel = fileUploadViewModel,
                meterReadingViewModel = meterReadingViewModel,
                onBackPressed = { currentScreen = "home" },
                onNavigateToFileUpload = { currentScreen = "file_upload" },
                onNavigateToMeterDetail = { meter ->
                    selectedMeter = meter
                    currentScreen = "meter_detail"
                }
            )
        }

        "meter_detail" -> {
            selectedMeter?.let { meter ->
                MeterDetailScreen(
                    meter = meter,
                    onBackPressed = { currentScreen = "meter_reading" },
                    onRegistration = {
                        // TODO: Implement DLMS registration
                        // This will communicate with the meter to register parameters
                        handleMeterAction("Registration", meter)
                    },
                    onReadData = {
                        // TODO: Implement DLMS data reading
                        // This will read current meter values via DLMS protocol
                        handleMeterAction("Read Data", meter)
                    },
                    onLoadProfile = {
                        // TODO: Implement DLMS load profile retrieval
                        // This will retrieve historical load profile data
                        handleMeterAction("Load Profile", meter)
                    },
                    onEventLog = {
                        // TODO: Implement DLMS event log retrieval
                        // This will retrieve meter event history
                        handleMeterAction("Event Log", meter)
                    },
                    onBillingData = {
                        // TODO: Implement DLMS billing data retrieval
                        // This will retrieve billing and tariff data
                        handleMeterAction("Billing Data", meter)
                    },
                    onSetClock = {
                        // TODO: Implement DLMS clock synchronization
                        // This will synchronize meter clock with system time
                        handleMeterAction("Set Clock", meter)
                    }
                )
            }
        }

        "receipt" -> {
            ReceiptScreen(
                fileUploadViewModel = fileUploadViewModel,
                bluetoothViewModel = bluetoothViewModel,
                onBackPressed = { currentScreen = "home" }
            )
        }
    }
}

/**
 * Handle meter actions (placeholder for future DLMS implementation)
 */
private fun handleMeterAction(action: String, meter: MeterData) {
    // TODO: This is where you'll implement the actual DLMS communication
    // For now, this is just a placeholder that logs the action

    when (action) {
        "Registration" -> {
            // Implementation will:
            // 1. Connect to meter via Bluetooth using meter.bluetoothId
            // 2. Perform DLMS association
            // 3. Register meter parameters
            // 4. Update meter status in CSV if needed
            println("TODO: Implement meter registration for ${meter.serialNumber}")
        }

        "Read Data" -> {
            // Implementation will:
            // 1. Connect to meter via Bluetooth
            // 2. Read current energy values via DLMS
            // 3. Update the meter.csv with new readings
            // 4. Update UI with fresh data
            println("TODO: Implement meter data reading for ${meter.serialNumber}")
        }

        "Load Profile" -> {
            // Implementation will:
            // 1. Connect to meter via Bluetooth
            // 2. Retrieve load profile data via DLMS
            // 3. Save to CSV file with timestamp
            // 4. Show success/failure message
            println("TODO: Implement load profile retrieval for ${meter.serialNumber}")
        }

        "Event Log" -> {
            // Implementation will:
            // 1. Connect to meter via Bluetooth
            // 2. Retrieve event log via DLMS
            // 3. Save events to CSV file
            // 4. Display event summary
            println("TODO: Implement event log retrieval for ${meter.serialNumber}")
        }

        "Billing Data" -> {
            // Implementation will:
            // 1. Connect to meter via Bluetooth
            // 2. Retrieve billing data via DLMS
            // 3. Process tariff calculations
            // 4. Save billing records to CSV
            println("TODO: Implement billing data retrieval for ${meter.serialNumber}")
        }

        "Set Clock" -> {
            // Implementation will:
            // 1. Connect to meter via Bluetooth
            // 2. Get current system time
            // 3. Set meter clock via DLMS
            // 4. Verify clock synchronization
            println("TODO: Implement clock synchronization for ${meter.serialNumber}")
        }
    }

    // Note: Each action should also handle:
    // - Bluetooth connection management
    // - DLMS protocol communication
    // - Error handling and user feedback
    // - Progress indicators during operations
    // - Logging for debugging and audit trails
}