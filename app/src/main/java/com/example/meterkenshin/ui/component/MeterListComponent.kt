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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.NetworkCell
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
 * Reusable Meter List Component with MeterReadingViewModel Integration
 * This component can be used across different screens (MeterReadingScreen, HomeScreen, etc.)
 * Handles all meter list functionality including CSV loading, search, and display
 * Now includes print button next to search bar
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
    dlmsMaxDemandProvider: ((Meter) -> Double?)? = null, // Function to get DLMS data for each meter
    useScrolling: Boolean = true // Parameter to control scrolling behavior
) {
    val context = LocalContext.current
    val uiState by meterReadingViewModel.uiState.collectAsState()
    val searchQuery by meterReadingViewModel.searchQuery.collectAsState()
    val uploadState by fileUploadViewModel.uploadState.collectAsState()

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
                // Search bar with print button (optional)
                if (showSearch) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Search field taking most of the space
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
                            modifier = Modifier
                                .weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )

                        // Print Actions Dropdown
                        PrintActionsDropdown(
                            onBatchReading = {
                                // TODO: Implement batch reading functionality
                                // This will batch read all BLE Meters on the list that are not yet inspected
                                // After batch read, automatically initiate batch printing receipt of batch read meters
                            },
                            onBatchPrinting = {
                                // TODO: Implement batch printing functionality
                                // This will batch print all inspected BLE meters on the list that are not yet printed
                            },
                            onSelectAndPrint = {
                                // TODO: Implement select and print functionality
                                // This will allow selection of meter(s) from the list that are not yet printed
                                // Then print the receipts of all selected meter(s)
                            }
                        )
                    }
                }

                // Statistics row (optional)
                if (showStatistics) {
                    MeterStatisticsRow(
                        totalMeters = uiState.allMeters.size,
                        filteredMeters = uiState.filteredMeters.size,
                        onlineMeters = uiState.allMeters.count { it.status.displayName == "Active" },
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }

                // Meters list - conditional scrolling
                val metersToShow = if (maxItemsToShow != null) {
                    uiState.filteredMeters.take(maxItemsToShow)
                } else {
                    uiState.filteredMeters
                }

                if (useScrolling) {
                    // Use LazyColumn for full-screen scrollable lists
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(metersToShow) { meter ->
                            ModernMeterCard(
                                meter = meter,
                                onClick = { onMeterClick(meter) },
                                modifier = Modifier.fillMaxWidth(),
                                reading = null,
                                showChevron = true,
                                customContent = null,
                                dlmsMaxDemand = dlmsMaxDemandProvider?.invoke(meter),
                                meterReadingViewModel = meterReadingViewModel,
                                inspectionStatus = getInspectionStatus(meter)
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
                            ModernMeterCard(
                                meter = meter,
                                onClick = { onMeterClick(meter) },
                                modifier = Modifier.fillMaxWidth(),
                                reading = null,
                                showChevron = true,
                                customContent = null,
                                dlmsMaxDemand = dlmsMaxDemandProvider?.invoke(meter),
                                meterReadingViewModel = meterReadingViewModel,
                                inspectionStatus = getInspectionStatus(meter)
                            )
                        }

                        // Show "View All" item if limited and more items exist
                        if (maxItemsToShow != null && uiState.filteredMeters.size > maxItemsToShow) {
                            ViewAllMetersCard(
                                remainingCount = uiState.filteredMeters.size - maxItemsToShow,
                                onClick = { /* Navigate to full meter list */ }
                            )
                        }

                        // Add bottom padding
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}

/**
 * Statistics row showing meter counts
 */
@Composable
private fun MeterStatisticsRow(
    totalMeters: Int,
    filteredMeters: Int,
    onlineMeters: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        StatisticCard(
            label = "Total",
            value = totalMeters.toString(),
            icon = Icons.Default.Cable,
            color = MaterialTheme.colorScheme.primary
        )
        StatisticCard(
            label = "Showing",
            value = filteredMeters.toString(),
            icon = Icons.Default.Search,
            color = MaterialTheme.colorScheme.secondary
        )
        StatisticCard(
            label = "Online",
            value = onlineMeters.toString(),
            icon = Icons.Default.NetworkCell,
            color = Color(0xFF4CAF50)
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
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.1f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
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
 * Loading card for when meters are being loaded
 */
@Composable
private fun LoadingCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = "Loading meters...",
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

/**
 * Error card for when meter loading fails
 */
@Composable
private fun ErrorCard(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Error,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Error loading meters",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            TextButton(onClick = onRetry) {
                Text("Retry")
            }
        }
    }
}

/**
 * Empty meters card for when no meters are found
 */
@Composable
private fun EmptyMetersCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Cable,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "No meters found",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Check your meter CSV file or search criteria",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
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
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
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
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Meter file required",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Please upload a meter CSV file to view meters",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f),
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * View all meters card for when there are more items to show
 */
@Composable
private fun ViewAllMetersCard(
    remainingCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "View $remainingCount more meters",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}