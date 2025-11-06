package com.example.meterkenshin.ui.component

import android.annotation.SuppressLint
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.RequiresApi
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.meterkenshin.R
import com.example.meterkenshin.data.RequiredFile
import com.example.meterkenshin.model.Meter
import com.example.meterkenshin.ui.viewmodel.BatchPrintManager
import com.example.meterkenshin.ui.viewmodel.BatchPrintMode
import com.example.meterkenshin.ui.viewmodel.BatchProcessingManager
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
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@SuppressLint("MissingPermission")
@Composable
fun MeterListComponent(
    fileUploadViewModel: FileUploadViewModel = viewModel(),
    meterReadingViewModel: MeterReadingViewModel = viewModel(),
    onMeterClick: (Meter) -> Unit = {},
    showSearch: Boolean = true,
    showStatistics: Boolean = true,
    maxItemsToShow: Int? = null, // null = show all, number = limit items
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier,
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
    val currentStep by batchProcessor.currentStep.collectAsState()
    val currentStepDescription by batchProcessor.currentStepDescription.collectAsState()
    val errorCount by batchProcessor.errorCount.collectAsState()
    val selectionMode by meterReadingViewModel.selectionMode.collectAsState()
    val selectedMeters by meterReadingViewModel.selectedMeters.collectAsState()
    val showPrinterErrorDialog by batchProcessor.showPrinterErrorDialog.collectAsState()
    val printerErrorMessage by batchProcessor.printerErrorMessage.collectAsState()

    // Batch print states
    val isPrinting by batchPrintManager.isProcessing.collectAsState()
    val printProcessedCount by batchPrintManager.processedCount.collectAsState()
    val printTotalCount by batchPrintManager.totalCount.collectAsState()
    val printCurrentMeterSerial by batchPrintManager.currentMeterSerial.collectAsState()
    val printCurrentStepDescription by batchPrintManager.currentStepDescription.collectAsState()
    val printErrorCount by batchPrintManager.errorCount.collectAsState()
    val showPrintPrinterErrorDialog by batchPrintManager.showPrinterErrorDialog.collectAsState()
    val printPrinterErrorMessage by batchPrintManager.printerErrorMessage.collectAsState()

    val printerViewModel: PrinterBluetoothViewModel = viewModel()
    val printerPaperStatus by printerViewModel.paperStatus.collectAsState()
    val printerCoverStatus by printerViewModel.coverStatus.collectAsState()

    // Add after the existing batch processor states
    val showRetryDialog by batchProcessor.showRetryDialog.collectAsState()
    val retryDialogMeter by batchProcessor.retryDialogMeter.collectAsState()

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
        if (isMeterCsvUploaded) {
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
                                        val message = if (success) {
                                            "All ${selectedMeterList.size} meters processed!"
                                        } else {
                                            "${selectedMeterList.size - failedMeters.size}/${selectedMeterList.size} completed"
                                        }
                                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
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
                                        Toast.makeText(
                                            context,
                                            "Refreshing BLE connection...",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        meterReadingViewModel.startBLEScanning()
                                    }) {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = "Refresh BLE scan"
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
                                        Toast.makeText(
                                            context,
                                            "No uninspected online meters found",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    } else {
                                        Toast.makeText(
                                            context,
                                            "Starting batch reading for ${notInspectedOnlineMeters.size} online meters",
                                            Toast.LENGTH_SHORT
                                        ).show()

                                        // Start batch processing with NOT_INSPECTED online registered meters only
                                        batchProcessor.processBatch(
                                            meters = notInspectedOnlineMeters,
                                            rates = rates,
                                            onComplete = { success, failedMeters ->
                                                val message = if (success) {
                                                    "All ${notInspectedOnlineMeters.size} meters processed!"
                                                } else {
                                                    "Processed with ${failedMeters.size} failures"
                                                }
                                                Toast.makeText(context, message, Toast.LENGTH_LONG)
                                                    .show()
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
                                    Toast.makeText(
                                        context,
                                        "Select meters to process",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            )
                        }
                    }
                }


                if (isProcessing) {
                    AlertDialog(
                        onDismissRequest = { /* Prevent dismiss */ },
                        title = {
                            // ⭐ Title with X button on the right (only during user action)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Batch Processing",
                                    style = MaterialTheme.typography.headlineSmall
                                )

                                // ⭐ X button on top-right corner (only show during awaiting user action)
                                if (awaitingUserAction) {
                                    IconButton(
                                        onClick = {
                                            batchProcessor.cancel()
                                            Toast.makeText(
                                                context,
                                                "Batch processing cancelled",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Cancel batch processing",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        },
                        text = {
                            Column(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                // ⭐ Meter Progress Bar
                                LinearProgressIndicator(
                                    progress = {
                                        if (totalCount > 0) processedCount.toFloat() / totalCount else 0f
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 8.dp)
                                )

                                // ⭐ Meter counter centered with errors on the right
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 16.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Centered meter count
                                    Text(
                                        text = "Meters: $processedCount / $totalCount",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )

                                    // ⭐ Error indicator on the right of meter count
                                    if (errorCount > 0) {
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Warning,
                                                contentDescription = "Errors",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "$errorCount",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.error,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }

                                // ⭐ Step Progress Section with current meter
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 16.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier.padding(16.dp)
                                    ) {
                                        // Step counter and current meter
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Step $currentStep of 7",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.primary
                                            )

                                            // Show current meter being processed
                                            if (currentMeterSerial != null) {
                                                Text(
                                                    text = "Meter: $currentMeterSerial",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))

                                        // Step progress bar
                                        LinearProgressIndicator(
                                            progress = { currentStep.toFloat() / 7f },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(bottom = 8.dp)
                                        )

                                        // Step description
                                        Text(
                                            text = currentStepDescription,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }

                                // ⭐ User Action Section (checkboxes)
                                if (awaitingUserAction && currentMeterSerial != null) {
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.primaryContainer
                                        )
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(16.dp)
                                        ) {
                                            // Title (X button now at top)
                                            Text(
                                                text = "Options for: $currentMeterSerial",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(bottom = 12.dp)
                                            )

                                            // Checkbox: Print Receipt
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable { batchProcessor.setPrintOption(!shouldPrint) }
                                                    .padding(vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Checkbox(
                                                    checked = shouldPrint,
                                                    onCheckedChange = {
                                                        batchProcessor.setPrintOption(
                                                            it
                                                        )
                                                    }
                                                )
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Column {
                                                    Text(
                                                        text = "Print Receipt",
                                                        style = MaterialTheme.typography.bodyLarge,
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                    Text(
                                                        text = "Print physical receipt",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }

                                            // Checkbox: Save JSON
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable { batchProcessor.setSaveJsonOption(!shouldSaveJson) }
                                                    .padding(vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Checkbox(
                                                    checked = shouldSaveJson,
                                                    onCheckedChange = {
                                                        batchProcessor.setSaveJsonOption(
                                                            it
                                                        )
                                                    }
                                                )
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Column {
                                                    Text(
                                                        text = "Save JSON",
                                                        style = MaterialTheme.typography.bodyLarge,
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                    Text(
                                                        text = "Save billing data to file",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }

                                            // Warning if nothing selected
                                            if (!shouldPrint && !shouldSaveJson) {
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Text(
                                                    text = "⚠️ No action selected - meter will be skipped",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.error
                                                )
                                            }

                                            Spacer(modifier = Modifier.height(12.dp))

                                            // Continue button
                                            Button(
                                                onClick = { batchProcessor.confirmUserAction() },
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(if (shouldPrint || shouldSaveJson) "Continue" else "Skip")
                                            }
                                        }
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            // Only show Cancel button when NOT awaiting user action
                            if (!awaitingUserAction) {
                                TextButton(onClick = { batchProcessor.cancel() }) {
                                    Text("Cancel")
                                }
                            }
                        }
                    )
                    if (showPrinterErrorDialog) {
                        PrinterStatusErrorDialog(
                            errorMessage = printerErrorMessage,
                            paperStatus = printerPaperStatus,
                            coverStatus = printerCoverStatus,
                            printerViewModel = printerViewModel,
                            onRetry = {
                                scope.launch {
                                    currentMeterSerial?.let { serial ->
                                        val meter =
                                            uiState.allMeters.find { it.serialNumber == serial }
                                        meter?.let {
                                            batchProcessor.retryPrinting(it)
                                        }
                                    }
                                }
                            },
                            onCancel = {
                                batchProcessor.dismissPrinterError()
                            }
                        )
                    }
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
                                val message = if (success) {
                                    "Batch printing completed successfully!"
                                } else {
                                    "Batch printing completed with ${failed.size} failures"
                                }
                                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                            }
                        },
                        onDismiss = {
                            showBatchPrintDialog = false
                        }
                    )
                }

                // 2. Batch Print Progress Dialog - HIDE when printer error dialog shows
                if (isPrinting && !showPrintPrinterErrorDialog) {
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
                            Toast.makeText(
                                context,
                                "Batch printing cancelled",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        onClose = {
                            batchPrintManager.reset()
                        }
                    )
                }

// 3. Batch Print Printer Error Dialog - Shows with PRIORITY over progress dialog
                if (showPrintPrinterErrorDialog) {
                    PrinterStatusErrorDialog(
                        errorMessage = printPrinterErrorMessage,
                        paperStatus = printerPaperStatus,
                        coverStatus = printerCoverStatus,
                        printerViewModel = printerViewModel,
                        onRetry = {
                            scope.launch {
                                printCurrentMeterSerial?.let { serial ->
                                    val meter = uiState.allMeters.find { it.serialNumber == serial }
                                    meter?.let { batchPrintManager.retryPrinting(it) }
                                }
                            }
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

/**
 * Filter and Sort Control Row
 */
@Composable
fun FilterSortControlRow(
    meterReadingViewModel: MeterReadingViewModel,
    modifier: Modifier = Modifier
) {
    val sortConfig by meterReadingViewModel.sortConfig.collectAsState()
    var showSortMenu by remember { mutableStateOf(false) }
    var showFilterMenu by remember { mutableStateOf(false) }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Filter Button
        OutlinedButton(
            onClick = { showFilterMenu = true },
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = Icons.Default.FilterList,
                contentDescription = "Filter",
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text("Filter")
        }

        // Sort Dropdown Button
        Box(modifier = Modifier.weight(1.5f)) {
            OutlinedButton(
                onClick = { showSortMenu = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Sort,
                    contentDescription = "Sort",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = when (sortConfig.field) {
                        SortField.SERIAL_NUMBER -> "S/N"
                        SortField.LOCATION -> "Location"
                        SortField.LAST_MAINTENANCE_DATE -> "Due Date"
                    },
                    maxLines = 1
                )
            }

            DropdownMenu(
                expanded = showSortMenu,
                onDismissRequest = { showSortMenu = false }
            ) {
                SortField.entries.forEach { field ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                when (field) {
                                    SortField.SERIAL_NUMBER -> "Serial Number"
                                    SortField.LOCATION -> "Location"
                                    SortField.LAST_MAINTENANCE_DATE -> "Last Maintenance"
                                }
                            )
                        },
                        onClick = {
                            meterReadingViewModel.setSortConfig(field, sortConfig.order)
                            showSortMenu = false
                        },
                        leadingIcon = {
                            if (sortConfig.field == field) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    )
                }
            }
        }

        // Ascending/Descending Toggle Button
        OutlinedButton(
            onClick = {
                val newOrder = if (sortConfig.order == SortOrder.ASCENDING)
                    SortOrder.DESCENDING else SortOrder.ASCENDING
                meterReadingViewModel.setSortConfig(sortConfig.field, newOrder)
            },
            modifier = Modifier.weight(0.5f)
        ) {
            Icon(
                imageVector = if (sortConfig.order == SortOrder.ASCENDING)
                    Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                contentDescription = if (sortConfig.order == SortOrder.ASCENDING) "Ascending" else "Descending",
                modifier = Modifier.size(18.dp)
            )
        }
    }

    // Filter Dialog (placeholder for now)
    if (showFilterMenu) {
        AlertDialog(
            onDismissRequest = { showFilterMenu = false },
            title = { Text("Filter Options") },
            text = { Text("Filter functionality coming soon!") },
            confirmButton = {
                TextButton(onClick = { showFilterMenu = false }) {
                    Text("Close")
                }
            }
        )
    }
}