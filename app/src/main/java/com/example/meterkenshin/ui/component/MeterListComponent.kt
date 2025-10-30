package com.example.meterkenshin.ui.component

import android.annotation.SuppressLint
import android.os.Build
import android.widget.Toast
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.meterkenshin.data.RequiredFile
import com.example.meterkenshin.model.Meter
import com.example.meterkenshin.ui.viewmodel.BatchProcessingManager
import com.example.meterkenshin.ui.viewmodel.DLMSViewModel
import com.example.meterkenshin.ui.viewmodel.FileUploadViewModel
import com.example.meterkenshin.ui.viewmodel.MeterReadingViewModel
import com.example.meterkenshin.ui.viewmodel.SortField
import com.example.meterkenshin.ui.viewmodel.SortOrder
import com.example.meterkenshin.utils.loadMeterRates
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
    val batchProcessor = remember(dlmsViewModel, scope, context) {
        BatchProcessingManager(dlmsViewModel, scope, context)
    }

    val showPrintDialog by dlmsViewModel.showPrintDialog.collectAsState()
    val showSaveDialog by dlmsViewModel.showSaveDialog.collectAsState()
    val pendingBillingData by dlmsViewModel.pendingBillingData.collectAsState()

    // Collect batch processing states
    val isProcessing by batchProcessor.isProcessing.collectAsState()
    val batchProgress by batchProcessor.currentProgress.collectAsState()
    val processedCount by batchProcessor.processedCount.collectAsState()
    val totalCount by batchProcessor.totalCount.collectAsState()

    // Other existing states
    val selectionMode by meterReadingViewModel.selectionMode.collectAsState()
    val selectedMeters by meterReadingViewModel.selectedMeters.collectAsState()
    val rates = remember(fileUploadViewModel) {
        loadMeterRates(context, fileUploadViewModel)
    }
    // Load meters when CSV is available
    LaunchedEffect(isMeterCsvUploaded, Unit) {
        if (isMeterCsvUploaded) {
            val fileToLoad = if (File(
                    context.getExternalFilesDir(null),
                    "app_files/$currentMeterFile"
                ).exists()) {
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
                        // Selection toolbar - replaces search bar
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Left side - Selection count
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "${selectedMeters.size} selected",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                // Right side - Action buttons
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TextButton(onClick = { meterReadingViewModel.selectAllMeters() }) {
                                        Text("Select All")
                                    }
                                    TextButton(onClick = { meterReadingViewModel.clearSelection() }) {
                                        Text("Cancel")
                                    }
                                    Button(
                                        onClick = {
                                            val selected = uiState.filteredMeters.filter {
                                                selectedMeters.contains(it.uid)
                                            }
                                            if (selected.isNotEmpty()) {
                                                batchProcessor.processBatch(
                                                    meters = selected,
                                                    rates = rates,
                                                    onComplete = { success, failedMeters ->
                                                        meterReadingViewModel.clearSelection()
                                                        val message = if (success) {
                                                            "All ${selected.size} meters processed!"
                                                        } else {
                                                            "${selected.size - failedMeters.size}/${selected.size} completed"
                                                        }
                                                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                                                    }
                                                )
                                            }
                                        },
                                        enabled = selectedMeters.isNotEmpty() && !isProcessing
                                    ) {
                                        Text("Process")
                                    }
                                }
                            }
                        }
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
                                    // TODO
                                },
                                onBatchPrinting = {
                                    // TODO
                                },
                                onSelectAndPrint = {
                                    // Enter selection mode
                                    meterReadingViewModel.toggleSelectionMode()
                                    Toast.makeText(context, "Select meters to process", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }
                }

                // Show progress dialog during batch processing (place AFTER showStatistics section)
                if (isProcessing) {
                    AlertDialog(
                        onDismissRequest = { /* Prevent dismiss */ },
                        title = { Text("Batch Processing") },
                        text = {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = batchProgress,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(bottom = 16.dp)
                                )
                                LinearProgressIndicator(
                                    progress = {
                                        if (totalCount > 0) processedCount.toFloat() / totalCount else 0f
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Text(
                                    text = "$processedCount / $totalCount",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { batchProcessor.cancel() }) {
                                Text("Cancel")
                            }
                        }
                    )
                }

                // Show print dialog during batch processing
                if (isProcessing && showPrintDialog && pendingBillingData != null) {
                    PrintReceiptDialog(
                        serialNumber = pendingBillingData?.SerialNumber,
                        onConfirmPrint = {
                            dlmsViewModel.confirmPrint()
                        },
                        onSkipPrint = {
                            dlmsViewModel.skipPrint()
                        }
                    )
                }

                // Show save dialog during batch processing
                if (isProcessing && showSaveDialog && pendingBillingData != null) {
                    SaveJSONDialog(
                        onConfirm = {
                            dlmsViewModel.confirmSave()
                        },
                        onDismiss = {
                            dlmsViewModel.skipSave()
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
                            val signalStrength = discoveredDevices[meter.bluetoothId?.uppercase() ?: ""]

                            ModernMeterCard(
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
                            val signalStrength = meterReadingViewModel.getMeterSignalStrength(meter.bluetoothId ?: "")

                            ModernMeterCard(
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
                    text = when(sortConfig.field) {
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
                            Text(when(field) {
                                SortField.SERIAL_NUMBER -> "Serial Number"
                                SortField.LOCATION -> "Location"
                                SortField.LAST_MAINTENANCE_DATE -> "Last Maintenance"
                            })
                        },
                        onClick = {
                            meterReadingViewModel.setSortConfig(field, sortConfig.order)
                            showSortMenu = false
                        },
                        leadingIcon = {
                            if (sortConfig.field == field) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
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