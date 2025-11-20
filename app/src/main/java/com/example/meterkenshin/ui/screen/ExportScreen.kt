package com.example.meterkenshin.ui.screen

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
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.meterkenshin.ui.manager.SessionManager
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

    val context = LocalContext.current
    val sessionManager = remember { SessionManager.getInstance(context) }

    LaunchedEffect(Unit) {
        val username = sessionManager.getSession()?.username
        if (username != null) {
            viewModel.setCurrentUser(username)
        }
        viewModel.loadFiles()
    }

    LaunchedEffect(Unit) {
        viewModel.loadFiles()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
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
            onValueChange = viewModel::updateSearchQuery,
            label = { Text("Search files...") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search"
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { viewModel.updateSearchQuery("") }) {
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
        if (files.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${selectedFiles.size} of ${files.size} selected",
                    style = MaterialTheme.typography.bodyMedium
                )
                Row {
                    TextButton(onClick = { viewModel.selectAll() }) {
                        Text("Select All")
                    }
                    TextButton(onClick = { viewModel.selectNone() }) {
                        Text("Clear")
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
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

@Composable
fun FileItem(
    fileName: String,
    fileSize: Long,
    isSelected: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onDeleteClick: () -> Unit,
    enabled: Boolean
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Checkbox
            Checkbox(
                checked = isSelected,
                onCheckedChange = onCheckedChange,
                enabled = enabled
            )

            Spacer(modifier = Modifier.width(12.dp))

            // File Info
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = fileName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = formatFileSize(fileSize),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Delete Button
            IconButton(
                onClick = onDeleteClick,
                enabled = enabled
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete file",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> "${bytes / (1024 * 1024)} MB"
    }
}