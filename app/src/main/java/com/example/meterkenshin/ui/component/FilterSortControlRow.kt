package com.example.meterkenshin.ui.component

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.example.meterkenshin.ui.manager.AppPreferences
import com.example.meterkenshin.ui.viewmodel.MeterReadingViewModel
import com.example.meterkenshin.ui.viewmodel.SortField
import com.example.meterkenshin.ui.viewmodel.SortOrder

/**
 * Filter and Sort Control Row Component
 *
 * Provides filtering and sorting controls for meter lists:
 * - Location filter (centered dropdown)
 * - Status filters (Not Inspected, Inspected, Billing states, Online/Offline)
 * - Sort options (Serial Number, Location, Last Inspection)
 * - Sort order toggle (Ascending/Descending)
 */
@SuppressLint("ConfigurationScreenWidthHeight")
@Composable
fun FilterSortControlRow(
    meterReadingViewModel: MeterReadingViewModel,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val isPrintingEnabled = AppPreferences.isPrintingEnabled(context)
    val sortConfig by meterReadingViewModel.sortConfig.collectAsState()
    val uiState by meterReadingViewModel.uiState.collectAsState()
    var showSortMenu by remember { mutableStateOf(false) }
    var showFilterMenu by remember { mutableStateOf(false) }
    var showLocationMenu by remember { mutableStateOf(false) }
    var selectedLocations by remember { mutableStateOf<Set<String>>(emptySet()) }
    var selectedFilter by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // First Row: Location Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { showLocationMenu = true },
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = "Location",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = when {
                        selectedLocations.isEmpty() -> "Location"
                        selectedLocations.size == 1 -> selectedLocations.first()
                        else -> "${selectedLocations.size} locations selected"
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (selectedLocations.isNotEmpty()) {
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear Location",
                        modifier = Modifier
                            .size(16.dp)
                            .clickable {
                                selectedLocations = emptySet()
                                meterReadingViewModel.clearLocationFilter()
                            }
                    )
                }
            }
        }

        // Location Selection Dialog
        if (showLocationMenu) {
            var locationSearch by remember { mutableStateOf("") }
            var pendingSelections by remember { mutableStateOf(selectedLocations) }
            val locations = remember(uiState.allMeters) {
                meterReadingViewModel.getAllLocations()
            }
            val filteredLocations = remember(locations, locationSearch) {
                if (locationSearch.isBlank()) locations
                else locations.filter { it.contains(locationSearch, ignoreCase = true) }
            }

            Dialog(onDismissRequest = { showLocationMenu = false }) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 20.dp, bottom = 16.dp)
                    ) {
                        // Header with close X
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 24.dp, end = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Select Location",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = if (pendingSelections.isEmpty())
                                        "${filteredLocations.size} location${if (filteredLocations.size != 1) "s" else ""}"
                                    else
                                        "${pendingSelections.size} selected",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (pendingSelections.isEmpty())
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    else MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(onClick = { showLocationMenu = false }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        // Search Bar
                        TextField(
                            value = locationSearch,
                            onValueChange = { locationSearch = it },
                            placeholder = {
                                Text(
                                    "Search location...",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp),
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Search",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            trailingIcon = {
                                if (locationSearch.isNotEmpty()) {
                                    IconButton(onClick = { locationSearch = "" }) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Clear search",
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        )

                        Spacer(Modifier.height(12.dp))

                        // Location List
                        if (filteredLocations.isNotEmpty()) {
                            LazyColumn(
                                modifier = Modifier.heightIn(max = 320.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp)
                            ) {
                                items(filteredLocations.size) { index ->
                                    val location = filteredLocations[index]
                                    val isSelected = pendingSelections.contains(location)
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (isSelected)
                                            MaterialTheme.colorScheme.primaryContainer
                                        else Color.Transparent,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 2.dp)
                                            .clickable {
                                                pendingSelections = if (isSelected)
                                                    pendingSelections - location
                                                else
                                                    pendingSelections + location
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 14.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.LocationOn,
                                                    contentDescription = null,
                                                    tint = if (isSelected)
                                                        MaterialTheme.colorScheme.onPrimaryContainer
                                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Text(
                                                    text = location,
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                                    color = if (isSelected)
                                                        MaterialTheme.colorScheme.onPrimaryContainer
                                                    else MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                            if (isSelected) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LocationOn,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.size(40.dp)
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = if (locationSearch.isNotBlank()) "No matching locations"
                                        else "No locations available",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        // Footer: Clear All + Apply
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (pendingSelections.isNotEmpty()) {
                                OutlinedButton(
                                    onClick = { pendingSelections = emptySet() },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Clear All")
                                }
                            }
                            Button(
                                onClick = {
                                    selectedLocations = pendingSelections
                                    if (pendingSelections.isEmpty()) {
                                        meterReadingViewModel.clearLocationFilter()
                                    } else {
                                        meterReadingViewModel.filterByLocations(pendingSelections)
                                    }
                                    showLocationMenu = false
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    if (pendingSelections.isEmpty()) "Show All"
                                    else "Apply (${pendingSelections.size})"
                                )
                            }
                        }
                    }
                }
            }
        }

        // Second Row: Filter, Sort, and Order buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Filter Button with Dropdown
            Box(modifier = Modifier.weight(1f)) {
                OutlinedButton(
                    onClick = { showFilterMenu = true },
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FilterList,
                        contentDescription = "Filter",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Filter")
                    if (selectedFilter != null) {
                        Spacer(Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = CircleShape
                                )
                        )
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear Filter",
                            modifier = Modifier
                                .size(16.dp)
                                .clickable {
                                    selectedFilter = null
                                    selectedLocations = emptySet()
                                    meterReadingViewModel.clearFilters()
                                }
                        )
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
                        },
                        leadingIcon = {
                            if (selectedFilter == "Not Inspected") {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                        }
                    )

                    DropdownMenuItem(
                        text = { Text("Inspected") },
                        onClick = {
                            selectedFilter = "Inspected"
                            meterReadingViewModel.filterInspected()
                            showFilterMenu = false
                        },
                        leadingIcon = {
                            if (selectedFilter == "Inspected") {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                        }
                    )

                    if (isPrintingEnabled) {
                        DropdownMenuItem(
                            text = { Text("Billing Not Printed") },
                            onClick = {
                                selectedFilter = "Billing Not Printed"
                                meterReadingViewModel.filterBillingNotPrinted()
                                showFilterMenu = false
                            },
                            leadingIcon = {
                                if (selectedFilter == "Billing Not Printed") {
                                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                }
                            }
                        )

                        DropdownMenuItem(
                            text = { Text("Billing Printed") },
                            onClick = {
                                selectedFilter = "Billing Printed"
                                meterReadingViewModel.filterBillingPrinted()
                                showFilterMenu = false
                            },
                            leadingIcon = {
                                if (selectedFilter == "Billing Printed") {
                                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                }
                            }
                        )
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp)
                    )

                    DropdownMenuItem(
                        text = { Text("Online") },
                        onClick = {
                            selectedFilter = "Online"
                            meterReadingViewModel.filterOnline()
                            showFilterMenu = false
                        },
                        leadingIcon = {
                            if (selectedFilter == "Online") {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                        }
                    )

                    DropdownMenuItem(
                        text = { Text("Offline") },
                        onClick = {
                            selectedFilter = "Offline"
                            meterReadingViewModel.filterOffline()
                            showFilterMenu = false
                        },
                        leadingIcon = {
                            if (selectedFilter == "Offline") {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                        }
                    )
                }
            }

            // Sort Dropdown Button
            Box(modifier = Modifier.weight(1f)) {
                OutlinedButton(
                    onClick = { showSortMenu = true },
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Sort,
                        contentDescription = "Sort",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = when (sortConfig.field) {
                            SortField.SERIAL_NUMBER -> "Serial No."
                            SortField.LOCATION -> "Location"
                            SortField.LAST_INSPECTION_DATE -> "Inspection"
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
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
                                        SortField.LAST_INSPECTION_DATE -> "Last Inspection"
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
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
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
