package com.example.meterkenshin.ui.screen

import android.annotation.SuppressLint
import android.os.Build
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.meterkenshin.model.Meter
import com.example.meterkenshin.ui.component.DLMSFunctionsCard
import com.example.meterkenshin.ui.component.DLMSLogCard
import com.example.meterkenshin.ui.component.MeterSpecificationsCard
import com.example.meterkenshin.ui.component.MeterStatusCard
import com.example.meterkenshin.ui.component.NotificationHost
import com.example.meterkenshin.ui.component.PrintReceiptDialog
import com.example.meterkenshin.ui.component.PrinterStatusErrorDialog
import com.example.meterkenshin.ui.component.SaveJSONDialog
import com.example.meterkenshin.ui.component.SavedBillingDataCard
import com.example.meterkenshin.ui.viewmodel.DLMSViewModel
import com.example.meterkenshin.ui.viewmodel.FileUploadViewModel
import com.example.meterkenshin.ui.viewmodel.MeterReadingViewModel
import com.example.meterkenshin.ui.viewmodel.PrinterBluetoothViewModel
import com.example.meterkenshin.utils.loadMeterRates

/**
 * Modern Meter Detail Screen with updated design and theme consistency
 */
@SuppressLint("MissingPermission")
@Composable
fun MeterDetailScreen(
    modifier: Modifier = Modifier,
    meter: Meter,
    meterReadingViewModel: MeterReadingViewModel = viewModel(),
    registrationViewModel: DLMSViewModel = viewModel(key = "meter_${meter.uid}"),
    fileUploadViewModel: FileUploadViewModel = viewModel(),
    printerViewModel: PrinterBluetoothViewModel = viewModel(),  // ADDED: Printer view model
) {
    val context = LocalContext.current

    // Track DLMS initialization state
    var isDlmsInitialized by remember { mutableStateOf(false) }

    // Collect printer error dialog state
    val showPrinterErrorDialog by registrationViewModel.showPrinterErrorDialog.collectAsState()
    val printerErrorMessage by registrationViewModel.printerErrorMessage.collectAsState()

    // Track dialog state
    var showSaveJSONDialog by remember { mutableStateOf(false) }

    // Collect states
    val discoveredDevices by meterReadingViewModel.discoveredDevices.collectAsState()
    val registrationState by registrationViewModel.registrationState.collectAsState()
    val dlmsLog by registrationViewModel.dlmsLog.collectAsState()
    val pendingBillingData by registrationViewModel.pendingBillingData.collectAsState()
    val savedBillingData by registrationViewModel.savedBillingData.collectAsState()

    // Observe the current meter from the ViewModel
    val updatedMeter by registrationViewModel.currentMeter.collectAsState()

    // Use updatedMeter if available, otherwise use the passed meter
    val activeMeter = updatedMeter ?: meter

    // NEW: Collect dialog states
    val showPrintDialog by registrationViewModel.showPrintDialog.collectAsState()
    val showSaveDialog by registrationViewModel.showSaveDialog.collectAsState()

    // Initialize DLMS and set printer reference
    LaunchedEffect(Unit) {
        try {
            Log.i("MeterDetailScreen", "Initializing DLMS...")

            // ADDED: Set printer view model reference
            registrationViewModel.setPrinterViewModel(printerViewModel)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registrationViewModel.initializeDLMS(context, meter)
            }
            isDlmsInitialized = true
            Log.i("MeterDetailScreen", "DLMS initialization complete")
        } catch (e: Exception) {
            Log.e("MeterDetailScreen", "Failed to initialize DLMS", e)
        }
    }


    // Get RSSI for this meter
    val rssi = meter.bluetoothId?.let {
        discoveredDevices[it.uppercase()]
    } ?: -200
    val isNearby = meterReadingViewModel.isMeterNearby(meter.bluetoothId)

    // Pause scanning when this screen is displayed
    LaunchedEffect(Unit) {
        try {
            meterReadingViewModel.pauseScanning()
            Log.i("MeterDetailScreen", "BLE scanning paused")
        } catch (e: Exception) {
            Log.e("MeterDetailScreen", "Error pausing BLE scan", e)
        }
    }

    // Show dialog when billing data is ready
    LaunchedEffect(pendingBillingData) {
        if (pendingBillingData != null) {
            showSaveJSONDialog = true
        }
    }

    // Resume scanning when leaving
    DisposableEffect(Unit) {
        onDispose {
            try {
                registrationViewModel.cleanup(context)
                meterReadingViewModel.resumeScanning()
                Log.i("MeterDetailScreen", "Cleanup complete")
            } catch (e: Exception) {
                Log.e("MeterDetailScreen", "Error during cleanup", e)
            }
        }
    }
    NotificationHost(modifier = modifier) {
        Column(modifier = modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
                    .padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. Connection status card
                MeterStatusCard(
                    meter = meter,
                    rssi = rssi,
                    isNearby = isNearby
                )

                // 2. DLMS function buttons - NOW IN SEPARATE FILE
                DLMSFunctionsCard(
                    meterActivate = activeMeter.activate,  // This will update when CSV is written
                    onRegistration = {
                        if (meter.activate == 0) {
                            registrationViewModel.registration(meter)
                        } else {
                            Log.w("MeterDetailScreen", "DLMS not yet initialized")
                        }
                    },
                    onReadData = {
                        if (isDlmsInitialized && activeMeter.activate == 1) {  // Changed
                            val rates = loadMeterRates(context, fileUploadViewModel)
                            registrationViewModel.readData(meter, rates)
                        }
                    },
                    onLoadProfile = {
                        if (isDlmsInitialized && activeMeter.activate == 1) {  // Changed
                            registrationViewModel.loadProfile(meter)
                        }
                    },
                    onEventLog = {
                        if (isDlmsInitialized && activeMeter.activate == 1) {  // Changed
                            registrationViewModel.eventLog(meter)
                        }
                    },
                    onBillingData = {
                        if (isDlmsInitialized && activeMeter.activate == 1) {  // Changed
                            val rates = loadMeterRates(context, fileUploadViewModel)
                            registrationViewModel.billingData(meter, rates)
                        }
                    },
                    onSetClock = {
                        if (isDlmsInitialized) {
                            registrationViewModel.setClock(meter)
                        }
                    },
                    // Disable buttons until initialized
                    isProcessing = registrationState.isRunning || !isDlmsInitialized
                )

                // 3. DLMS Log output - NOW IN SEPARATE FILE
                DLMSLogCard(
                    logText = dlmsLog,
                    onClearLog = { registrationViewModel.clearLog() },
                    isProcessing = registrationState.isRunning
                )


                // Saved Billing Data Card (NEW - shows if data available)
                savedBillingData?.let { saved ->
                    if (saved.isValid()) {
                        SavedBillingDataCard(
                            billing = saved.billing,
                            daysRemaining = saved.daysRemaining(),
                            printerViewModel = printerViewModel,
                            onPrintReceipt = {
                                // NEW: Print receipt from saved data
                                registrationViewModel.printReceipt(saved.billing, saved.rates)
                            },
                            onSaveJSON = {
                                registrationViewModel.saveStoredBillingToJSON()
                            },
                            onClearData = {
                                registrationViewModel.clearSavedBillingData()
                            }
                        )
                    }
                }

                // 4. Meter specifications card
                MeterSpecificationsCard(meter = meter)

                Spacer(modifier = Modifier.height(16.dp))
                Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
            }

            // Print Receipt Dialog - Shows FIRST after read data completes
            if (showPrintDialog && pendingBillingData != null) {
                PrintReceiptDialog(
                    serialNumber = pendingBillingData?.SerialNumber,
                    onConfirmPrint = {
                        // Check printer status and print
                        registrationViewModel.confirmPrint()
                    },
                    onSkipPrint = {
                        // Skip print, move to save dialog
                        registrationViewModel.skipPrint()
                    }
                )
            }

            if (showPrinterErrorDialog) {
                PrinterStatusErrorDialog(
                    errorMessage = printerErrorMessage,
                    paperStatus = registrationViewModel.readDataPrinting.printerPaperStatus.collectAsState().value,
                    coverStatus = registrationViewModel.readDataPrinting.printerCoverStatus.collectAsState().value,
                    printerViewModel = printerViewModel,
                    onRetry = { registrationViewModel.readDataPrinting.retryPrint() },
                    onCancel = { registrationViewModel.readDataPrinting.cancelPrintFromError() }
                )
            }

            // Save JSON Dialog - Shows SECOND after print dialog
            if (showSaveDialog && pendingBillingData != null) {
                SaveJSONDialog(
                    onConfirm = {
                        // Save to JSON
                        registrationViewModel.confirmSave()
                    },
                    onDismiss = {
                        // Skip save, clear pending data
                        registrationViewModel.skipSave()
                    }
                )
            }
        }
    }
}