package com.example.meterkenshin.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.example.meterkenshin.ui.viewmodel.MeterReadingViewModel
import com.example.meterkenshin.ui.viewmodel.SortField
import com.example.meterkenshin.ui.viewmodel.SortOrder

/**
 * Filter and Sort Control Row Component
 *
 * Provides filtering and sorting controls for meter lists:
 * - Location filter (centered dropdown)
 * - Status filters (Not Inspected, Inspected, Billing states, Online/Offline)
 * - Sort options (Serial Number, Location, Last Maintenance)
 * - Sort order toggle (Ascending/Descending)
 */
@Composable
fun FilterSortControlRow(
    meterReadingViewModel: MeterReadingViewModel,
    modifier: Modifier = Modifier
) {
    val sortConfig by meterReadingViewModel.sortConfig.collectAsState()
    val uiState by meterReadingViewModel.uiState.collectAsState()
    var showSortMenu by remember { mutableStateOf(false) }
    var showFilterMenu by remember { mutableStateOf(false) }
    var showLocationMenu by remember { mutableStateOf(false) }
    var selectedLocation by remember { mutableStateOf<String?>(null) }
    var selectedFilter by remember { mutableStateOf<String?>(null) }

    // Calculate dynamic offset for centering dropdown
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val dropdownWidth = 200.dp // Approximate dropdown width
    val buttonPadding = 16.dp // Padding from screen edges
    val centerOffset = (screenWidth - dropdownWidth) / 2 - buttonPadding

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // First Row: Location Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Location Button with Dropdown
            Box(modifier = Modifier.weight(1f)) {
                OutlinedButton(
                    onClick = { showLocationMenu = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = "Location",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(selectedLocation ?: "Location")
                }

                DropdownMenu(
                    expanded = showLocationMenu,
                    onDismissRequest = { showLocationMenu = false },
                    offset = DpOffset(x = centerOffset, y = 4.dp)
                ) {
                    val locations = remember(uiState.allMeters) {
                        meterReadingViewModel.getAllLocations()
                    }

                    if (locations.isNotEmpty()) {
                        locations.forEach { location ->
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Text(location)
                                    }
                                },
                                onClick = {
                                    selectedLocation = location
                                    meterReadingViewModel.filterByLocation(location)
                                    showLocationMenu = false
                                },
                                contentPadding = PaddingValues(
                                    horizontal = 12.dp,
                                    vertical = 4.dp
                                )
                            )
                        }

                        androidx.compose.material3.HorizontalDivider(
                            modifier = Modifier.padding(vertical = 4.dp)
                        )

                        // Clear location filter
                        DropdownMenuItem(
                            text = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        "Show All",
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            },
                            onClick = {
                                selectedLocation = null
                                meterReadingViewModel.clearFilters()
                                showLocationMenu = false
                            },
                            contentPadding = PaddingValues(
                                horizontal = 12.dp,
                                vertical = 4.dp
                            )
                        )
                    } else {
                        DropdownMenuItem(
                            text = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        "No locations available",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            onClick = { },
                            enabled = false,
                            contentPadding = PaddingValues(
                                horizontal = 12.dp,
                                vertical = 4.dp
                            )
                        )
                    }
                }
            }
        }

        // Second Row: Filter, Sort, and Order buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Filter Button with Dropdown
            Box(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
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
                        Text(selectedFilter ?: "Filter")
                    }

                    // Clear Filter Button (X icon)
                    if (selectedFilter != null) {
                        IconButton(
                            onClick = {
                                selectedFilter = null
                                selectedLocation = null
                                meterReadingViewModel.clearFilters()
                            },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear Filter",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                DropdownMenu(
                    expanded = showFilterMenu,
                    onDismissRequest = { showFilterMenu = false },
                    offset = DpOffset(x = 0.dp, y = 4.dp)
                ) {
                    DropdownMenuItem(
                        text = { Text("Not Inspected") },
                        onClick = {
                            selectedFilter = "Not Inspected"
                            meterReadingViewModel.filterNotInspected()
                            showFilterMenu = false
                        }
                    )

                    DropdownMenuItem(
                        text = { Text("Inspected") },
                        onClick = {
                            selectedFilter = "Inspected"
                            meterReadingViewModel.filterInspected()
                            showFilterMenu = false
                        }
                    )

                    DropdownMenuItem(
                        text = { Text("Billing Not Printed") },
                        onClick = {
                            selectedFilter = "Billing Not Printed"
                            meterReadingViewModel.filterBillingNotPrinted()
                            showFilterMenu = false
                        }
                    )

                    DropdownMenuItem(
                        text = { Text("Billing Printed") },
                        onClick = {
                            selectedFilter = "Billing Printed"
                            meterReadingViewModel.filterBillingPrinted()
                            showFilterMenu = false
                        }
                    )

                    androidx.compose.material3.HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp)
                    )

                    DropdownMenuItem(
                        text = { Text("Online") },
                        onClick = {
                            selectedFilter = "Online"
                            meterReadingViewModel.filterOnline()
                            showFilterMenu = false
                        }
                    )

                    DropdownMenuItem(
                        text = { Text("Offline") },
                        onClick = {
                            selectedFilter = "Offline"
                            meterReadingViewModel.filterOffline()
                            showFilterMenu = false
                        }
                    )
                }
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
                            SortField.SERIAL_NUMBER -> "Serial Number"
                            SortField.LOCATION -> "Location"
                            SortField.LAST_MAINTENANCE_DATE -> "Last Maintenance"
                        },
                        maxLines = 1
                    )
                }

                DropdownMenu(
                    expanded = showSortMenu,
                    onDismissRequest = { showSortMenu = false },
                    offset = DpOffset(x = 0.dp, y = 4.dp)
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
    }
}
