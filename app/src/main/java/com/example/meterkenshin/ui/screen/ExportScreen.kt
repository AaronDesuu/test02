package com.example.meterkenshin.ui.screen

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.meterkenshin.ui.viewmodel.ExportViewModel

@Composable
fun ExportScreen(
    viewModel: ExportViewModel = viewModel()
) {
    val files by viewModel.files.collectAsState()
    val selectedFiles by viewModel.selectedFiles.collectAsState()
    val isExporting by viewModel.isExporting.collectAsState()
    val fileGroups by viewModel.filteredGroups.collectAsState() // Use filtered groups
    val searchQuery by viewModel.searchQuery.collectAsState()

    // Confirmation dialog states
    var showDeleteFileDialog by remember { mutableStateOf(false) }
    var showDeleteSelectedDialog by remember { mutableStateOf(false) }
    var fileToDelete by remember { mutableStateOf<String?>(null) }

    // On phone landscape, make header/search/controls scroll with the list
    val configuration = LocalConfiguration.current
    val scrollFixedParts = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            && configuration.smallestScreenWidthDp < 600

    LaunchedEffect(Unit) {
        viewModel.loadFiles()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Fixed parts on tablet/portrait
        if (!scrollFixedParts) {
            ExportHeaderControls(
                searchQuery = searchQuery,
                onSearchChange = viewModel::updateSearchQuery,
                filesCount = files.size,
                selectedCount = selectedFiles.size,
                onSelectAll = { viewModel.selectAll() },
                onSelectNone = { viewModel.selectNone() }
            )
        }

        // Files List with Grouping
        if (files.isEmpty() && !isExporting) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.FileDownload,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No files available to export",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f)
            ) {
                // Scrollable parts on phone landscape
                if (scrollFixedParts) {
                    item(key = "header_controls") {
                        ExportHeaderControls(
                            searchQuery = searchQuery,
                            onSearchChange = viewModel::updateSearchQuery,
                            filesCount = files.size,
                            selectedCount = selectedFiles.size,
                            onSelectAll = { viewModel.selectAll() },
                            onSelectNone = { viewModel.selectNone() }
                        )
                    }
                }

                fileGroups.forEach { group ->
                    // Group Header
                    if (group.files.isNotEmpty()) {
                        item(key = "header_${group.name}") {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                                )
                            ) {
                                Text(
                                    text = group.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                    }

                    // Group Files
                    items(
                        items = group.files,
                        key = { file -> file.absolutePath }
                    ) { file ->
                        FileItem(
                            fileName = file.name,
                            fileSize = file.length(),
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

        Spacer(modifier = Modifier.height(16.dp))

        // Bottom Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Delete Selected Button
            OutlinedButton(
                onClick = { showDeleteSelectedDialog = true },
                modifier = Modifier.weight(1f),
                enabled = selectedFiles.isNotEmpty() && !isExporting,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                ),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.error
                )
            ) {
                Icon(Icons.Default.Delete, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Delete ${selectedFiles.size} File(s)")
            }

            // Export Button
            Button(
                onClick = { viewModel.exportSelectedFiles() },
                modifier = Modifier.weight(1f),
                enabled = selectedFiles.isNotEmpty() && !isExporting
            ) {
                if (isExporting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Exporting...")
                } else {
                    Icon(Icons.Default.FileDownload, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Export ${selectedFiles.size} File(s)")
                }
            }
        }
    }

    // Delete Single File Confirmation Dialog
    if (showDeleteFileDialog && fileToDelete != null) {
        AlertDialog(
            onDismissRequest = {
                showDeleteFileDialog = false
                fileToDelete = null
            },
            title = {
                Text("Delete File")
            },
            text = {
                Text("Are you sure you want to delete '${fileToDelete}'? This action cannot be undone.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        fileToDelete?.let { viewModel.deleteFile(it) }
                        showDeleteFileDialog = false
                        fileToDelete = null
                    }
                ) {
                    Text(
                        "Delete",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDeleteFileDialog = false
                        fileToDelete = null
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete Selected Files Confirmation Dialog
    if (showDeleteSelectedDialog) {
        AlertDialog(
            onDismissRequest = {
                showDeleteSelectedDialog = false
            },
            title = {
                Text("Delete Selected Files")
            },
            text = {
                Text("Are you sure you want to delete ${selectedFiles.size} selected file(s)? This action cannot be undone.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteSelectedFiles()
                        showDeleteSelectedDialog = false
                    }
                ) {
                    Text(
                        "Delete",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDeleteSelectedDialog = false
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FileItem(
    fileName: String,
    fileSize: Long,
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
            .padding(vertical = 3.dp),
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
            // Checkbox
            Checkbox(
                checked = isSelected,
                onCheckedChange = onCheckedChange,
                enabled = enabled
            )

            Spacer(modifier = Modifier.width(4.dp))

            // File Info
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // File name - show clean display name
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
                    // File type tag (LP, EL, BD)
                    val typeLabel = fileInfo.typeLabel
                    if (typeLabel != null) {
                        FileChip(
                            text = typeLabel,
                            containerColor = fileInfo.typeColor(),
                            contentColor = fileInfo.typeOnColor()
                        )
                    }

                    // All Data / By Period tag
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

                    // File size tag
                    FileChip(
                        text = formatFileSize(fileSize),
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Period date range (only for By Period files)
                if (periodInfo != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                        Text(
                            text = "${periodInfo.first}  ~  ${periodInfo.second}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
            }

            // Delete Button
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

/** Parsed metadata from a filename like "1286_EL_20260318_134707_from20260301_to20260318.csv" */
private data class FileInfo(
    val serial: String?,
    val typeCode: String?,   // "LP", "EL", "BD"
    val readDate: String?,   // "2026-03-18 13:47:07"
    val displayName: String  // cleaned display name
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
    // Pattern: {serial}_{type}_{YYYYMMDD}_{HHMMSS}[_from..._to...].csv
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

/**
 * Extracted header card, search bar, and selection controls so they can be
 * placed either fixed above the LazyColumn or inside it as a scrollable item.
 */
@Composable
private fun ExportHeaderControls(
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    filesCount: Int,
    selectedCount: Int,
    onSelectAll: () -> Unit,
    onSelectNone: () -> Unit
) {
    Column {
        // Header Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Export Files",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Select files to export to Download/kenshinApp",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            label = { Text("Search files...") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search"
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchChange("") }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear search"
                        )
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Selection Controls
        if (filesCount > 0) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$selectedCount of $filesCount selected",
                    style = MaterialTheme.typography.bodyMedium
                )
                Row {
                    TextButton(onClick = onSelectAll) {
                        Text("Select All")
                    }
                    TextButton(onClick = onSelectNone) {
                        Text("Clear")
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}