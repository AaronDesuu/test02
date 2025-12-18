package com.example.meterkenshin.ui.component

import android.annotation.SuppressLint
import android.os.Build
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.meterkenshin.R
import com.example.meterkenshin.data.RequiredFile
import com.example.meterkenshin.model.Meter
import com.example.meterkenshin.ui.component.FilterSortControlRow
import com.example.meterkenshin.ui.component.card.MeterCard
import com.example.meterkenshin.ui.component.card.SelectionModeCard
import com.example.meterkenshin.ui.component.dialog.BatchPrintOptionsDialog
import com.example.meterkenshin.ui.component.dialog.BatchPrintProgressDialog
import com.example.meterkenshin.ui.component.dialog.BatchProcessingDialog
import com.example.meterkenshin.ui.component.dialog.PrinterStatusErrorDialog
import com.example.meterkenshin.ui.manager.AppPreferences
import com.example.meterkenshin.ui.manager.BatchPrintManager
import com.example.meterkenshin.ui.manager.BatchPrintMode
import com.example.meterkenshin.ui.manager.BatchProcessingManager
import com.example.meterkenshin.ui.manager.NotificationManager
import com.example.meterkenshin.ui.viewmodel.DLMSViewModel
import com.example.meterkenshin.ui.viewmodel.FileUploadViewModel
import com.example.meterkenshin.ui.viewmodel.MeterReadingViewModel
import com.example.meterkenshin.ui.viewmodel.PrinterBluetoothViewModel
import com.example.meterkenshin.ui.viewmodel.SortField
import com.example.meterkenshin.ui.viewmodel.SortOrder
import com.example.meterkenshin.utils.InspectionStatus
import com.example.meterkenshin.utils.getInspectionStatus
import com.example.meterkenshin.utils.loadMeterRates
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


/**
 * Reusable Meter List Component with Automatic BLE Scanning
 * Handles all meter list functionality including CSV loading, search, and display
 * BLE scanning starts automatically when user is logged in
 */
@Suppress("KotlinConstantConditions")
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@SuppressLint("MissingPermission")
@Composable
fun MeterListComponent(
    modifier: Modifier = Modifier,
    fileUploadViewModel: FileUploadViewModel = viewModel(),
    meterReadingViewModel: MeterReadingViewModel = viewModel(),
    onMeterClick: (Meter) -> Unit = {},
    showSearch: Boolean = true,
    showStatistics: Boolean = true,
    maxItemsToShow: Int? = null, // null = show all, number = limit items
    customHeader: (@Composable () -> Unit)? = null,
    customEmptyState: (@Composable () -> Unit)? = null,
    useScrolling: Boolean = true
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uiState by meterReadingViewModel.uiState.collectAsState()
    val searchQuery by meterReadingViewModel.searchQuery.collectAsState()
    val uploadState by fileUploadViewModel.uploadState.collectAsState()

    // BLE Scanning states
    val nearbyMeterCount by meterReadingViewModel.nearbyMeterCount.collectAsState()

    // Check if meter.csv is uploaded
    val meterCsvFile = uploadState.requiredFiles.find { it.type == RequiredFile.FileType.METER }
    val isMeterCsvUploaded = meterCsvFile?.isUploaded == true

    val currentYearMonth = SimpleDateFormat("yyyyMM", Locale.getDefault()).format(Date())
    val currentMeterFile = "${currentYearMonth}_meter.csv"
    val fallbackFile = "meter.csv"

    val dlmsViewModel: DLMSViewModel = viewModel()
    val batchProcessor = remember(dlmsViewModel, meterReadingViewModel, scope, context) {
        BatchProcessingManager(
            dlmsViewModel = dlmsViewModel,
            meterReadingViewModel = meterReadingViewModel,
            scope = scope,
            context = context
        )
    }
    val batchPrintManager = remember(dlmsViewModel, meterReadingViewModel, scope, context) {
        BatchPrintManager(
            dlmsViewModel = dlmsViewModel,
            meterReadingViewModel = meterReadingViewModel,
            scope = scope,
            context = context
        )
    }

    // Collect batch processing states
    val isProcessing by batchProcessor.isProcessing.collectAsState()
    val processedCount by batchProcessor.processedCount.collectAsState()
    val totalCount by batchProcessor.totalCount.collectAsState()
    val awaitingUserAction by batchProcessor.awaitingUserAction.collectAsState()
    val currentMeterSerial by batchProcessor.currentMeterSerial.collectAsState()
    val shouldPrint by batchProcessor.shouldPrint.collectAsState()
    val shouldSaveJson by batchProcessor.shouldSaveJson.collectAsState()
    val currentStepDescription by batchProcessor.currentStepDescription.collectAsState()
    val errorCount by batchProcessor.errorCount.collectAsState()
    val selectionMode by meterReadingViewModel.selectionMode.collectAsState()
    val selectedMeters by meterReadingViewModel.selectedMeters.collectAsState()
    val showProcessingPrinterError by batchProcessor.showPrinterErrorDialog.collectAsState() // RENAME THIS
    val processingPrinterErrorMessage by batchProcessor.printerErrorMessage.collectAsState() // RENAME THIS

    // Batch print states
    val isPrinting by batchPrintManager.isProcessing.collectAsState()
    val printProcessedCount by batchPrintManager.processedCount.collectAsState()
    val printTotalCount by batchPrintManager.totalCount.collectAsState()
    val printCurrentMeterSerial by batchPrintManager.currentMeterSerial.collectAsState()
    val printCurrentStepDescription by batchPrintManager.currentStepDescription.collectAsState()
    val printErrorCount by batchPrintManager.errorCount.collectAsState()
    val showPrinterErrorDialog by batchPrintManager.showPrinterErrorDialog.collectAsState()
    val printerErrorMessage by batchPrintManager.printerErrorMessage.collectAsState()

    val printerViewModel: PrinterBluetoothViewModel = viewModel()
    val printerPaperStatus by printerViewModel.paperStatus.collectAsState()
    val printerCoverStatus by printerViewModel.coverStatus.collectAsState()


    // Add after the existing batch processor states
    val showRetryDialog by batchProcessor.showRetryDialog.collectAsState()
    val retryDialogMeter by batchProcessor.retryDialogMeter.collectAsState()
    val showUseExistingDialog by batchProcessor.showUseExistingDialog.collectAsState()
    val useExistingDialogMeter by batchProcessor.useExistingDialogMeter.collectAsState()

    // Set printer reference in DLMS ViewModel
    LaunchedEffect(Unit) {
        dlmsViewModel.setPrinterViewModel(printerViewModel)
        batchProcessor.setPrinterViewModel(printerViewModel)
        Log.d("MeterListComponent", "Printer ViewModel configured for batch processing")
    }

    LaunchedEffect(Unit) {
        dlmsViewModel.setPrinterViewModel(printerViewModel)
        batchProcessor.setPrinterViewModel(printerViewModel)
        batchPrintManager.setPrinterViewModel(printerViewModel) // ADD THIS
        Log.d("MeterListComponent", "Printer ViewModel configured for batch operations")
    }
    var showBatchPrintDialog by remember { mutableStateOf(false) }
    var batchPrintMode by remember { mutableStateOf(BatchPrintMode.ALL) }

    BackHandler(enabled = selectionMode) {
        meterReadingViewModel.clearSelection()
    }

    // Other existing states
    val rates = remember(fileUploadViewModel) {
        loadMeterRates(context, fileUploadViewModel)
    }
    // Load meters when CSV is available
    LaunchedEffect(isMeterCsvUploaded, Unit) {
        if (isMeterCsvUploaded && uiState.allMeters.isEmpty()) {
            val fileToLoad = if (File(
                    context.getExternalFilesDir(null),
                    "app_files/$currentMeterFile"
                ).exists()
            ) {
                currentMeterFile
            } else {
                fallbackFile
            }

            meterReadingViewModel.loadMeters(context, fileToLoad)
        }
    }

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // Custom header if provided
        customHeader?.invoke()

        when {
            // Check if meter file is uploaded
            !isMeterCsvUploaded -> {
                if (customEmptyState != null) {
                    customEmptyState()
                } else {
                    MeterFileNotUploadedCard()
                }
            }

            // Loading state
            uiState.isLoading -> {
                LoadingCard()
            }

            // Error state
            uiState.errorMessage != null -> {
                ErrorCard(
                    message = uiState.errorMessage!!,
                    onRetry = { meterReadingViewModel.loadMeters(context, "meter.csv") }
                )
            }

            // Empty state
            uiState.allMeters.isEmpty() -> {
                if (customEmptyState != null) {
                    customEmptyState()
                } else {
                    EmptyMetersCard()
                }
            }

            // Success state with meters
            else -> {
                // Search bar (without scan button - scanning is automatic)
                if (showSearch) {
                    // When in selection mode, replace search bar with selection toolbar
                    if (selectionMode) {
                        SelectionModeCard(
                            selectedCount = selectedMeters.size,
                            filteredMeters = uiState.filteredMeters,
                            selectedMeters = selectedMeters,
                            onSelectAll = { meterReadingViewModel.selectAllMeters() },
                            onCancel = { meterReadingViewModel.clearSelection() },
                            onBatchRead = { selectedMeterList ->
                                batchProcessor.processBatch(
                                    meters = selectedMeterList,
                                    rates = rates,
                                    onComplete = { success, failedMeters ->
                                        meterReadingViewModel.clearSelection()
                                        if (success) {
                                            NotificationManager.showSuccess("All ${selectedMeterList.size} meters processed!")
                                        } else {
                                            NotificationManager.showWarning("${selectedMeterList.size - failedMeters.size}/${selectedMeterList.size} completed")
                                        }
                                    }
                                )
                            },
                            isProcessing = isProcessing
                        )
                    } else {
                        // Normal mode - search bar with print dropdown
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Search field
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = meterReadingViewModel::updateSearchQuery,
                                label = { Text("Search meters...") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = "Search"
                                    )
                                },
                                trailingIcon = {
                                    IconButton(onClick = {
                                        // Refresh both BLE scanning and meter list
                                        meterReadingViewModel.startBLEScanning()
                                        meterReadingViewModel.reloadMeters(context, forceReload = true)
                                        NotificationManager.showInfo("Refreshing meters and BLE")
                                    }) {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = "Refresh meters and BLE scan"
                                        )
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            )

                            // Print Actions Dropdown

                            PrintActionsDropdown(
                                onBatchReading = {

                                    val notInspectedOnlineMeters =
                                        uiState.allMeters.filter { meter ->
                                            // Must be registered (activate == 1)
                                            meter.activate == 1 &&
                                                    // Must be NOT_INSPECTED (using same logic as MeterCardComponent)
                                                    getInspectionStatus(meter) == InspectionStatus.NOT_INSPECTED &&
                                                    // Must be online (nearby via BLE)
                                                    meterReadingViewModel.isMeterNearby(meter.bluetoothId)
                                        }

                                    if (notInspectedOnlineMeters.isEmpty()) {
                                        NotificationManager.showWarning("No uninspected online meters found")
                                    } else {
                                        NotificationManager.showInfo("Starting batch reading for ${notInspectedOnlineMeters.size} online meters")

                                        // Start batch processing with NOT_INSPECTED online registered meters only
                                        batchProcessor.processBatch(
                                            meters = notInspectedOnlineMeters,
                                            rates = rates,
                                            onComplete = { success, failedMeters ->
                                                if (success) {
                                                    NotificationManager.showSuccess("All ${notInspectedOnlineMeters.size} meters processed!")
                                                } else {
                                                    NotificationManager.showWarning("Processed with ${failedMeters.size} failures")
                                                }
                                            }
                                        )
                                    }
                                },
                                onBatchPrinting = {
                                    showBatchPrintDialog = true // ADD THIS
                                },
                                onSelectAndPrint = {
                                    // Enter selection mode
                                    meterReadingViewModel.toggleSelectionMode()
                                    NotificationManager.showInfo("Select meters to process")
                                },
                                isPrintingEnabled = AppPreferences.isPrintingEnabled(context),
                            )
                        }
                    }
                }

                // Use extracted BatchProcessingDialog component
                if (isProcessing) {
                    BatchProcessingDialog(
                        context = context,
                        isProcessing = isProcessing,
                        processedCount = processedCount,
                        totalCount = totalCount,
                        currentStepDescription = currentStepDescription,
                        currentMeterSerial = currentMeterSerial,
                        errorCount = errorCount,
                        awaitingUserAction = awaitingUserAction,
                        showUseExistingDialog = showUseExistingDialog,
                        useExistingDialogMeter = useExistingDialogMeter,
                        shouldPrint = shouldPrint,
                        shouldSaveJson = shouldSaveJson,
                        // NEW: Printer error handling
                        showPrinterErrorDialog = showProcessingPrinterError,
                        printerErrorMessage = processingPrinterErrorMessage,
                        printerViewModel = printerViewModel,
                        onUseExistingClicked = { batchProcessor.onUseExistingClicked() },
                        onReadNewClicked = { batchProcessor.onReadNewClicked() },
                        onPrintOptionChanged = { batchProcessor.setPrintOption(it) },
                        onSaveJsonOptionChanged = { batchProcessor.setSaveJsonOption(it) },
                        onConfirmUserAction = { batchProcessor.confirmUserAction() },
                        onCancel = {
                            batchProcessor.cancel()
                            NotificationManager.showInfo("Batch processing cancelled")
                        },
                        // NEW: Printer error callbacks
                        dismissPrinterError = {
                            batchProcessor.dismissPrinterError()
                        },
                        retryPrinting = {
                            scope.launch {
                                currentMeterSerial?.let { serial ->
                                    val meter = uiState.allMeters.find { it.serialNumber == serial }
                                    meter?.let { batchProcessor.retryPrinting(it) }
                                }
                            }
                        }
                    )
                }

                // Retry/Skip Dialog for failed read operations
                if (showRetryDialog && retryDialogMeter != null) {
                    AlertDialog(
                        onDismissRequest = { /* Prevent dismiss */ },
                        title = {
                            Text(stringResource(R.string.retry_connection))
                        },
                        text = {
                            Column {
                                Text(stringResource(R.string.data_read_failed))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Meter: $retryDialogMeter",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        },
                        confirmButton = {
                            Button(onClick = { batchProcessor.onRetryClicked() }) {
                                Text(stringResource(R.string.retry))
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { batchProcessor.onSkipClicked() }) {
                                Text(stringResource(R.string.skip))
                            }
                        }
                    )
                }


                // 1. Batch Print Options Dialog
                if (showBatchPrintDialog) {
                    BatchPrintOptionsDialog(
                        selectedMode = batchPrintMode,
                        onModeSelected = { mode ->
                            batchPrintMode = mode
                        },
                        onConfirm = {
                            showBatchPrintDialog = false

                            // Start batch printing with selected mode
                            batchPrintManager.processBatchPrint(
                                meters = uiState.allMeters,
                                printMode = batchPrintMode
                            ) { success, failed ->
                                if (success) {
                                    NotificationManager.showSuccess("Batch printing completed successfully!")
                                } else {
                                    NotificationManager.showWarning("Batch printing completed with ${failed.size} failures")
                                }
                            }
                        },
                        onDismiss = {
                            showBatchPrintDialog = false
                        }
                    )
                }

                // 2. Batch Print Progress Dialog - HIDE when printer error dialog shows
                if (isPrinting && !showPrinterErrorDialog) {
                    val waitingForConfirm by batchPrintManager.waitingForConfirmation.collectAsState()

                    BatchPrintProgressDialog(
                        processedCount = printProcessedCount,
                        totalCount = printTotalCount,
                        currentStepDescription = printCurrentStepDescription,
                        currentMeterSerial = printCurrentMeterSerial,
                        errorCount = printErrorCount,
                        isProcessing = isPrinting,
                        showConfirmButton = waitingForConfirm,
                        onConfirm = { batchPrintManager.confirmPrint() },
                        onCancel = {
                            batchPrintManager.cancel()
                            NotificationManager.showInfo("Batch printing cancelled")
                        },
                        onClose = {
                            batchPrintManager.reset()
                        }
                    )
                }

// 3. Batch Print Printer Error Dialog - Shows with PRIORITY over progress dialog
                if (showPrinterErrorDialog) {
                    PrinterStatusErrorDialog(
                        errorMessage = printerErrorMessage,
                        paperStatus = printerPaperStatus,
                        coverStatus = printerCoverStatus,
                        printerViewModel = printerViewModel,
                        onRetry = {
                            batchPrintManager.retryPrinting()
                        },
                        onCancel = {
                            batchPrintManager.dismissPrinterError()
                        }
                    )
                }

                // Statistics
                if (showStatistics) {
                    MeterStatisticsRow(
                        totalMeters = uiState.allMeters.size,
                        showingMeters = uiState.filteredMeters.size,
                        nearbyMeters = nearbyMeterCount,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 4.dp)
                    )
                }

                FilterSortControlRow(
                    meterReadingViewModel = meterReadingViewModel,
                    modifier = Modifier.padding(bottom = 2.dp)
                )

                // Meter list
                val metersToShow = if (maxItemsToShow != null) {
                    uiState.filteredMeters.take(maxItemsToShow)
                } else {
                    uiState.filteredMeters
                }

                if (useScrolling) {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(metersToShow) { meter ->
                            // Check if meter is nearby
                            val isNearby = meterReadingViewModel.isMeterNearby(meter.bluetoothId)
                            val discoveredDevices by meterReadingViewModel.discoveredDevices.collectAsState()
                            val signalStrength =
                                discoveredDevices[meter.bluetoothId?.uppercase() ?: ""]

                            MeterCard(
                                meter = meter,
                                onClick = {
                                    if (selectionMode) {
                                        meterReadingViewModel.toggleMeterSelection(meter.uid)
                                    } else {
                                        onMeterClick(meter)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                isNearby = isNearby,
                                inspectionStatus = getInspectionStatus(meter),
                                signalStrength = signalStrength,
                                showCheckbox = selectionMode,
                                isSelected = selectedMeters.contains(meter.uid)
                            )
                        }

                        // Show "View All" item if limited and more items exist
                        if (maxItemsToShow != null && uiState.filteredMeters.size > maxItemsToShow) {
                            item {
                                ViewAllMetersCard(
                                    remainingCount = uiState.filteredMeters.size - maxItemsToShow,
                                    onClick = { /* Navigate to full meter list */ }
                                )
                            }
                        }

                        // Add bottom padding
                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                } else {
                    // Use Column for non-scrollable lists (embedded in other scrollable containers)
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        metersToShow.forEach { meter ->
                            val isNearby = meterReadingViewModel.isMeterNearby(meter.bluetoothId)
                            val signalStrength = meterReadingViewModel.getMeterSignalStrength(
                                meter.bluetoothId ?: ""
                            )

                            MeterCard(
                                meter = meter,
                                onClick = { onMeterClick(meter) },
                                modifier = Modifier.fillMaxWidth(),
                                isNearby = isNearby,
                                inspectionStatus = getInspectionStatus(meter),
                                signalStrength = signalStrength
                            )
                        }

                        // Show "View All" item if limited and more items exist
                        if (maxItemsToShow != null && uiState.filteredMeters.size > maxItemsToShow) {
                            ViewAllMetersCard(
                                remainingCount = uiState.filteredMeters.size - maxItemsToShow,
                                onClick = { /* Navigate to full list */ }
                            )
                        }
                    }
                }
            }
        }
    }
}