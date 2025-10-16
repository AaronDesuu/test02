package com.example.meterkenshin.ui.component

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.meterkenshin.model.Meter
import com.example.meterkenshin.model.RequiredFile
import com.example.meterkenshin.ui.viewmodel.FileUploadViewModel
import com.example.meterkenshin.ui.viewmodel.MeterReadingViewModel

/**
 * Reusable Meter List Component with Automatic BLE Scanning
 * Handles all meter list functionality including CSV loading, search, and display
 * BLE scanning starts automatically when user is logged in
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    dlmsMaxDemandProvider: ((Meter) -> Double?)? = null,
    useScrolling: Boolean = true
) {
    val context = LocalContext.current
    val uiState by meterReadingViewModel.uiState.collectAsState()
    val searchQuery by meterReadingViewModel.searchQuery.collectAsState()
    val uploadState by fileUploadViewModel.uploadState.collectAsState()

    // BLE Scanning states
    val nearbyMeterCount by meterReadingViewModel.nearbyMeterCount.collectAsState()

    // Check if meter.csv is uploaded
    val meterCsvFile = uploadState.requiredFiles.find { it.type == RequiredFile.FileType.METER }
    val isMeterCsvUploaded = meterCsvFile?.isUploaded == true

    // Load meters when CSV is available
    LaunchedEffect(isMeterCsvUploaded) {
        if (isMeterCsvUploaded) {
            meterReadingViewModel.loadMeters(context, "meter.csv")
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
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                                    contentDescription = null
                                )
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )

                        // Print Actions Dropdown
                        PrintActionsDropdown(
                            onBatchReading = {
                                // TODO: Implement batch reading functionality
                            },
                            onBatchPrinting = {
                                // TODO: Implement batch printing functionality
                            },
                            onSelectAndPrint = {
                                // TODO: Implement select and print functionality
                            }
                        )
                    }
                }

                // Statistics
                if (showStatistics) {
                    MeterStatisticsRow(
                        totalMeters = uiState.allMeters.size,
                        showingMeters = uiState.filteredMeters.size,
                        nearbyMeters = nearbyMeterCount,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                    )
                }

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
                                onClick = { onMeterClick(meter) },
                                modifier = Modifier.fillMaxWidth(),
                                dlmsMaxDemand = dlmsMaxDemandProvider?.invoke(meter),
                                isNearby = isNearby,
                                inspectionStatus = getInspectionStatus(meter, null, isNearby),
                                signalStrength = signalStrength
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
                                dlmsMaxDemand = dlmsMaxDemandProvider?.invoke(meter),
                                isNearby = isNearby,
                                inspectionStatus = getInspectionStatus(meter, null, isNearby),
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
 * Meter Statistics Row with Nearby Count
 */
@Composable
fun MeterStatisticsRow(
    totalMeters: Int,
    showingMeters: Int,
    nearbyMeters: Int = 0,
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(), // Ensure Row fills the width
        horizontalArrangement = Arrangement.SpaceEvenly // Use SpaceEvenly
    ) {
        StatisticCard(
            label = "Total",
            value = totalMeters.toString(),
            icon = Icons.Default.Cable,
            color = MaterialTheme.colorScheme.primary
            // modifier = Modifier.weight(1f) // <-- REMOVE THIS
        )
        StatisticCard(
            label = "Showing",
            value = showingMeters.toString(),
            icon = Icons.Default.Search,
            color = MaterialTheme.colorScheme.secondary
            // modifier = Modifier.weight(1f) // <-- REMOVE THIS
        )
        StatisticCard(
            label = "Active",
            value = nearbyMeters.toString(),
            icon = Icons.Default.CheckCircle,
            color = Color(0xFF4CAF50)
            // modifier = Modifier.weight(1f) // <-- REMOVE THIS
        )
    }
}


/**
 * Individual statistic card
 */
@Composable
private fun StatisticCard(
    label: String,
    value: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier, // <-- REMOVE .fillMaxWidth()
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.1f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(12.dp), // <-- REMOVE .fillMaxWidth()
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}


/**
 * Card shown when meter file is not uploaded
 */
@Composable
private fun MeterFileNotUploadedCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Error,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Meter File Required",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Please upload a meter.csv file to view meter data.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Loading indicator card
 */
@Composable
private fun LoadingCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Loading meters...",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

/**
 * Error card with retry button
 */
@Composable
private fun ErrorCard(
    message: String,
    onRetry: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Error,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Error Loading Meters",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(modifier = Modifier.height(16.dp))
            TextButton(onClick = onRetry) {
                Text("Retry")
            }
        }
    }
}

/**
 * Empty meters card
 */
@Composable
private fun EmptyMetersCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.ElectricBolt,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No Meters Found",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "The meter CSV file contains no valid meter data.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * View All Meters Card
 */
@Composable
private fun ViewAllMetersCard(
    remainingCount: Int,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
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
            Column {
                Text(
                    text = "View All Meters",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "+$remainingCount more meters",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }
            Icon(
                imageVector = Icons.Default.Cable,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}