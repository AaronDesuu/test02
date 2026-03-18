package com.example.meterkenshin.ui.screen

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.meterkenshin.ui.viewmodel.ExportViewModel
import com.example.meterkenshin.ui.viewmodel.FileTypeFilter
import com.example.meterkenshin.ui.viewmodel.RetrievalModeFilter

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExportScreen(
    viewModel: ExportViewModel = viewModel()
) {
    val files by viewModel.files.collectAsState()
    val displayFiles by viewModel.displayFiles.collectAsState()
    val selectedFiles by viewModel.selectedFiles.collectAsState()
    val isExporting by viewModel.isExporting.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val fileDataPeriods by viewModel.fileDataPeriods.collectAsState()
    val typeFilter by viewModel.typeFilter.collectAsState()
    val modeFilter by viewModel.modeFilter.collectAsState()
    val sortAscending by viewModel.sortAscending.collectAsState()

    var showDeleteFileDialog by remember { mutableStateOf(false) }
    var showDeleteSelectedDialog by remember { mutableStateOf(false) }
    var fileToDelete by remember { mutableStateOf<String?>(null) }

    val configuration = LocalConfiguration.current
    val scrollFixedParts = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            && configuration.smallestScreenWidthDp < 600

    // Group display files by type for category headers
    val groupedFiles = remember(displayFiles) {
        val lp = displayFiles.filter { it.name.contains("_LP_") }
        val el = displayFiles.filter { it.name.contains("_EL_") }
        val bd = displayFiles.filter { it.name.contains("_BD_") }
        val other = displayFiles.filter {
            !it.name.contains("_LP_") && !it.name.contains("_EL_") && !it.name.contains("_BD_")
        }
        buildList {
            if (lp.isNotEmpty()) add("Load Profile" to lp)
            if (el.isNotEmpty()) add("Event Log" to el)
            if (bd.isNotEmpty()) add("Billing Data" to bd)
            if (other.isNotEmpty()) add("Other" to other)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.loadFiles()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Fixed header on tablet/portrait
        if (!scrollFixedParts) {
            ExportControls(
                searchQuery = searchQuery,
                onSearchChange = viewModel::updateSearchQuery,
                totalCount = files.size,
                displayCount = displayFiles.size,
                selectedCount = selectedFiles.size,
                typeFilter = typeFilter,
                modeFilter = modeFilter,
                sortAscending = sortAscending,
                onTypeFilter = viewModel::setTypeFilter,
                onModeFilter = viewModel::setModeFilter,
                onToggleSortOrder = viewModel::toggleSortOrder,
                onSelectAll = viewModel::selectAll,
                onSelectNone = viewModel::selectNone
            )
        }

        // File list
        if (files.isEmpty() && !isExporting) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No files available",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Read meter data to generate export files",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)
                    )
                }
            }
        } else if (displayFiles.isEmpty() && files.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No files match current filters",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f)
            ) {
                // Scrollable header on phone landscape
                if (scrollFixedParts) {
                    item(key = "header_controls") {
                        ExportControls(
                            searchQuery = searchQuery,
                            onSearchChange = viewModel::updateSearchQuery,
                            totalCount = files.size,
                            displayCount = displayFiles.size,
                            selectedCount = selectedFiles.size,
                            typeFilter = typeFilter,
                            modeFilter = modeFilter,
                            sortAscending = sortAscending,
                            onTypeFilter = viewModel::setTypeFilter,
                            onModeFilter = viewModel::setModeFilter,
                            onToggleSortOrder = viewModel::toggleSortOrder,
                            onSelectAll = viewModel::selectAll,
                            onSelectNone = viewModel::selectNone
                        )
                    }
                }

                groupedFiles.forEach { (groupName, groupFiles) ->
                    // Category header
                    item(key = "header_$groupName") {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = groupName,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = "${groupFiles.size}",
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.SemiBold
                                        ),
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                        }
                    }

                    // Files in this category
                    items(
                        items = groupFiles,
                        key = { it.absolutePath }
                    ) { file ->
                        FileItem(
                            fileName = file.name,
                            fileSize = file.length(),
                            dataPeriod = fileDataPeriods[file.name],
                            isSelected = selectedFiles.contains(file.name),
                            onCheckedChange = { checked ->
                                viewModel.toggleFileSelection(file.name, checked)
                            },
                            onDeleteClick = {
                                fileToDelete = file.name
                                showDeleteFileDialog = true
                            },
                            enabled = !isExporting
                        )
                    }
                }
            }
        }

        // Bottom action buttons
        AnimatedVisibility(
            visible = selectedFiles.isNotEmpty(),
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { showDeleteSelectedDialog = true },
                    modifier = Modifier.weight(1f),
                    enabled = selectedFiles.isNotEmpty() && !isExporting,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (selectedFiles.isNotEmpty()) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Delete (${selectedFiles.size})")
                }

                Button(
                    onClick = { viewModel.exportSelectedFiles() },
                    modifier = Modifier.weight(1f),
                    enabled = selectedFiles.isNotEmpty() && !isExporting,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isExporting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Exporting...")
                    } else {
                        Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Export (${selectedFiles.size})")
                    }
                }
            }
        }
    }

    // Delete single file dialog
    if (showDeleteFileDialog && fileToDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteFileDialog = false; fileToDelete = null },
            title = { Text("Delete File") },
            text = { Text("Are you sure you want to delete '${fileToDelete}'? This action cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    fileToDelete?.let { viewModel.deleteFile(it) }
                    showDeleteFileDialog = false; fileToDelete = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteFileDialog = false; fileToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete selected files dialog
    if (showDeleteSelectedDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteSelectedDialog = false },
            title = { Text("Delete Selected Files") },
            text = { Text("Are you sure you want to delete ${selectedFiles.size} selected file(s)? This action cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteSelectedFiles()
                    showDeleteSelectedDialog = false
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteSelectedDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

// ──────────────────────────────────────────────────────────────
// Controls: Search + Filter row (matching FilterSortControlRow pattern)
// ──────────────────────────────────────────────────────────────

@Composable
private fun ExportControls(
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    totalCount: Int,
    displayCount: Int,
    selectedCount: Int,
    typeFilter: FileTypeFilter,
    modeFilter: RetrievalModeFilter,
    sortAscending: Boolean,
    onTypeFilter: (FileTypeFilter) -> Unit,
    onModeFilter: (RetrievalModeFilter) -> Unit,
    onToggleSortOrder: () -> Unit,
    onSelectAll: () -> Unit,
    onSelectNone: () -> Unit
) {
    var showTypeFilterMenu by remember { mutableStateOf(false) }
    var showModeFilterMenu by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Search bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            placeholder = { Text("Search by serial or filename...") },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(20.dp))
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchChange("") }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(20.dp))
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium
        )

        // Single row: Type Filter + Mode Filter + Asc/Desc toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Type Filter Button
            Box(modifier = Modifier.weight(1f)) {
                OutlinedButton(
                    onClick = { showTypeFilterMenu = true },
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FilterList,
                        contentDescription = "Type Filter",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = when (typeFilter) {
                            FileTypeFilter.ALL -> "Type"
                            FileTypeFilter.LP -> "LP"
                            FileTypeFilter.EL -> "EL"
                            FileTypeFilter.BD -> "BD"
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (typeFilter != FileTypeFilter.ALL) {
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
                            contentDescription = "Clear Type Filter",
                            modifier = Modifier
                                .size(16.dp)
                                .clickable { onTypeFilter(FileTypeFilter.ALL) }
                        )
                    }
                }

                DropdownMenu(
                    expanded = showTypeFilterMenu,
                    onDismissRequest = { showTypeFilterMenu = false },
                    offset = DpOffset(x = 0.dp, y = 4.dp)
                ) {
                    DropdownMenuItem(
                        text = { Text("All Types") },
                        onClick = {
                            onTypeFilter(FileTypeFilter.ALL)
                            showTypeFilterMenu = false
                        },
                        leadingIcon = {
                            if (typeFilter == FileTypeFilter.ALL) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                        }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    DropdownMenuItem(
                        text = { Text("Load Profile") },
                        onClick = {
                            onTypeFilter(FileTypeFilter.LP)
                            showTypeFilterMenu = false
                        },
                        leadingIcon = {
                            if (typeFilter == FileTypeFilter.LP) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                        }
                    )

                    DropdownMenuItem(
                        text = { Text("Event Log") },
                        onClick = {
                            onTypeFilter(FileTypeFilter.EL)
                            showTypeFilterMenu = false
                        },
                        leadingIcon = {
                            if (typeFilter == FileTypeFilter.EL) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                        }
                    )

                    DropdownMenuItem(
                        text = { Text("Billing Data") },
                        onClick = {
                            onTypeFilter(FileTypeFilter.BD)
                            showTypeFilterMenu = false
                        },
                        leadingIcon = {
                            if (typeFilter == FileTypeFilter.BD) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                        }
                    )
                }
            }

            // Mode Filter Button
            Box(modifier = Modifier.weight(1f)) {
                OutlinedButton(
                    onClick = { showModeFilterMenu = true },
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = "Mode Filter",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = when (modeFilter) {
                            RetrievalModeFilter.ALL -> "Mode"
                            RetrievalModeFilter.ALL_DATA -> "All Data"
                            RetrievalModeFilter.BY_PERIOD -> "Period"
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (modeFilter != RetrievalModeFilter.ALL) {
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
                            contentDescription = "Clear Mode Filter",
                            modifier = Modifier
                                .size(16.dp)
                                .clickable { onModeFilter(RetrievalModeFilter.ALL) }
                        )
                    }
                }

                DropdownMenu(
                    expanded = showModeFilterMenu,
                    onDismissRequest = { showModeFilterMenu = false },
                    offset = DpOffset(x = 0.dp, y = 4.dp)
                ) {
                    DropdownMenuItem(
                        text = { Text("All Modes") },
                        onClick = {
                            onModeFilter(RetrievalModeFilter.ALL)
                            showModeFilterMenu = false
                        },
                        leadingIcon = {
                            if (modeFilter == RetrievalModeFilter.ALL) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                        }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    DropdownMenuItem(
                        text = { Text("All Data") },
                        onClick = {
                            onModeFilter(RetrievalModeFilter.ALL_DATA)
                            showModeFilterMenu = false
                        },
                        leadingIcon = {
                            if (modeFilter == RetrievalModeFilter.ALL_DATA) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                        }
                    )

                    DropdownMenuItem(
                        text = { Text("By Period") },
                        onClick = {
                            onModeFilter(RetrievalModeFilter.BY_PERIOD)
                            showModeFilterMenu = false
                        },
                        leadingIcon = {
                            if (modeFilter == RetrievalModeFilter.BY_PERIOD) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                        }
                    )
                }
            }

            // Ascending/Descending Toggle Button
            OutlinedButton(
                onClick = onToggleSortOrder,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Icon(
                    imageVector = if (sortAscending) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                    contentDescription = if (sortAscending) "Oldest first" else "Newest first",
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // File count + selection controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (displayCount == totalCount) "$totalCount files"
                       else "$displayCount of $totalCount files",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (displayCount > 0) {
                Row {
                    TextButton(onClick = onSelectAll) {
                        Text("Select All", style = MaterialTheme.typography.labelMedium)
                    }
                    if (selectedCount > 0) {
                        TextButton(onClick = onSelectNone) {
                            Text("Clear", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────
// File Item
// ──────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FileItem(
    fileName: String,
    fileSize: Long,
    dataPeriod: Pair<String, String>? = null,
    isSelected: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onDeleteClick: () -> Unit,
    enabled: Boolean
) {
    val fileInfo = remember(fileName) { parseFileInfo(fileName) }
    val periodInfo = remember(fileName) { parsePeriodFromFilename(fileName) }
    val isByPeriod = periodInfo != null

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            else
                MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isSelected) 2.dp else 0.5.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = onCheckedChange,
                enabled = enabled
            )

            Spacer(modifier = Modifier.width(4.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // File name
                Text(
                    text = fileInfo.displayName,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Tags row
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (isByPeriod) {
                        FileChip(
                            text = "By Period",
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    } else {
                        FileChip(
                            text = "All Data",
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }

                    FileChip(
                        text = formatFileSize(fileSize),
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Period date range
                val displayPeriod = periodInfo ?: dataPeriod
                if (displayPeriod != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = if (isByPeriod) MaterialTheme.colorScheme.tertiary
                                   else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${displayPeriod.first}  ~  ${displayPeriod.second}",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isByPeriod) MaterialTheme.colorScheme.tertiary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            IconButton(
                onClick = onDeleteClick,
                enabled = enabled,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete file",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                )
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────
// Helper composables & functions
// ──────────────────────────────────────────────────────────────

@Composable
private fun FileChip(
    text: String,
    containerColor: Color,
    contentColor: Color
) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = containerColor
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold
            ),
            color = contentColor
        )
    }
}

private data class FileInfo(
    val serial: String?,
    val typeCode: String?,
    val readDate: String?,
    val displayName: String
) {
    val typeLabel: String?
        get() = when (typeCode) {
            "LP" -> "Load Profile"
            "EL" -> "Event Log"
            "BD" -> "Billing Data"
            else -> null
        }

    @Composable
    fun typeColor() = when (typeCode) {
        "LP" -> MaterialTheme.colorScheme.primaryContainer
        "EL" -> MaterialTheme.colorScheme.errorContainer
        "BD" -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    @Composable
    fun typeOnColor() = when (typeCode) {
        "LP" -> MaterialTheme.colorScheme.onPrimaryContainer
        "EL" -> MaterialTheme.colorScheme.onErrorContainer
        "BD" -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}

private fun parseFileInfo(fileName: String): FileInfo {
    val regex = Regex("""^(\d+)_(LP|EL|BD)_(\d{4})(\d{2})(\d{2})_(\d{2})(\d{2})(\d{2})""")
    val match = regex.find(fileName)
    return if (match != null) {
        val (serial, type, y, m, d, hh, mm, ss) = match.destructured
        FileInfo(
            serial = serial,
            typeCode = type,
            readDate = "$y-$m-$d $hh:$mm:$ss",
            displayName = fileName.removeSuffix(".csv")
        )
    } else {
        FileInfo(null, null, null, fileName.removeSuffix(".csv"))
    }
}

private fun parsePeriodFromFilename(fileName: String): Pair<String, String>? {
    val regex = Regex("""_from(\d{4})(\d{2})(\d{2})_to(\d{4})(\d{2})(\d{2})""")
    val match = regex.find(fileName) ?: return null
    val (fy, fm, fd, ty, tm, td) = match.destructured
    return "$fy-$fm-$fd" to "$ty-$tm-$td"
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> "${bytes / (1024 * 1024)} MB"
    }
}
