package com.example.meterkenshin.ui.screen

import android.annotation.SuppressLint
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ProgressIndicatorDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.meterkenshin.R
import com.example.meterkenshin.model.FileUploadState
import com.example.meterkenshin.model.RequiredFile
import com.example.meterkenshin.ui.viewmodel.FileUploadViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileUploadScreen(
    viewModel: FileUploadViewModel = viewModel(),
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uploadState by viewModel.uploadState.collectAsState()
    var showReplaceDialog by remember { mutableStateOf<RequiredFile.FileType?>(null) }
    var showDeleteDialog by remember { mutableStateOf<RequiredFile.FileType?>(null) }

    // Check for existing files when screen loads
    LaunchedEffect(Unit) {
        viewModel.checkExistingFiles(context)
    }

    // File picker launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.selectFile(it, context) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Header
            Text(
                text = stringResource(R.string.file_upload_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Text(
                text = stringResource(R.string.upload_instructions),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            // Upload status summary
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (uploadState.allFilesUploaded)
                        colorResource(R.color.upload_success_background)
                    else
                        MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (uploadState.allFilesUploaded) Icons.Default.CheckCircle else Icons.Default.Upload,
                            contentDescription = null,
                            tint = if (uploadState.allFilesUploaded)
                                colorResource(R.color.upload_success_foreground)
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 8.dp)
                        )

                        Text(
                            text = if (uploadState.allFilesUploaded)
                                stringResource(R.string.file_upload_complete)
                            else
                                stringResource(R.string.file_upload_incomplete),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Text(
                        text = "${uploadState.uploadedFilesCount} of ${uploadState.requiredFiles.size} files uploaded",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            // File list
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(uploadState.requiredFiles) { file ->
                    FileUploadCard(
                        file = file,
                        onSelectFile = { filePickerLauncher.launch("*/*") },
                        onUploadFile = { viewModel.uploadFile(file.type, context) },
                        onReplaceFile = {
                            if (file.isUploaded) {
                                showReplaceDialog = file.type
                            } else {
                                filePickerLauncher.launch("*/*")
                            }
                        },
                        onRemoveFile = { showDeleteDialog = file.type },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // Replace file confirmation dialog
        showReplaceDialog?.let { fileType ->
            AlertDialog(
                onDismissRequest = { showReplaceDialog = null },
                title = { Text(stringResource(R.string.replace_file_title)) },
                text = { Text(stringResource(R.string.replace_file_message)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            filePickerLauncher.launch("*/*")
                            showReplaceDialog = null
                        }
                    ) {
                        Text(stringResource(R.string.ok))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showReplaceDialog = null }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }

        // Delete file confirmation dialog
        showDeleteDialog?.let { fileType ->
            AlertDialog(
                onDismissRequest = { showDeleteDialog = null },
                title = { Text(stringResource(R.string.delete_file_title)) },
                text = { Text(stringResource(R.string.delete_file_message)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.removeFile(fileType, context)
                            showDeleteDialog = null
                        }
                    ) {
                        Text(stringResource(R.string.delete))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = null }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }

        // Removed automatic navigation - users can stay on this screen even when files are uploaded
    }
}

@Composable
fun FileUploadCard(
    file: RequiredFile,
    onSelectFile: () -> Unit,
    onUploadFile: () -> Unit,
    onReplaceFile: () -> Unit,
    onRemoveFile: () -> Unit,
    modifier: Modifier
) {
    val cardColors = when (file.status) {
        FileUploadState.FileStatus.PENDING -> CardDefaults.cardColors(
            containerColor = colorResource(R.color.file_pending_background)
        )

        FileUploadState.FileStatus.SELECTED -> CardDefaults.cardColors(
            containerColor = colorResource(R.color.file_selected_background)
        )

        FileUploadState.FileStatus.UPLOADING -> CardDefaults.cardColors(
            containerColor = colorResource(R.color.upload_progress_background)
        )

        FileUploadState.FileStatus.UPLOADED -> CardDefaults.cardColors(
            containerColor = colorResource(R.color.file_uploaded_background)
        )

        FileUploadState.FileStatus.ERROR -> CardDefaults.cardColors(
            containerColor = colorResource(R.color.file_error_background)
        )
    }

    Card(
        modifier = modifier,
        colors = cardColors,
        border = BorderStroke(
            width = 1.dp,
            color = when (file.status) {
                FileUploadState.FileStatus.PENDING -> colorResource(R.color.file_pending_border)
                FileUploadState.FileStatus.SELECTED -> colorResource(R.color.file_selected_border)
                FileUploadState.FileStatus.UPLOADING -> colorResource(R.color.upload_progress_foreground)
                FileUploadState.FileStatus.UPLOADED -> colorResource(R.color.file_uploaded_border)
                FileUploadState.FileStatus.ERROR -> colorResource(R.color.file_error_border)
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // File header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = file.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = file.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Status icon
                Icon(
                    imageVector = when (file.status) {
                        FileUploadState.FileStatus.PENDING -> Icons.Default.CloudUpload
                        FileUploadState.FileStatus.SELECTED -> Icons.Default.Description
                        FileUploadState.FileStatus.UPLOADING -> Icons.Default.CloudUpload
                        FileUploadState.FileStatus.UPLOADED -> Icons.Default.CheckCircle
                        FileUploadState.FileStatus.ERROR -> Icons.Default.Error
                    },
                    contentDescription = null,
                    tint = when (file.status) {
                        FileUploadState.FileStatus.PENDING -> MaterialTheme.colorScheme.onSurfaceVariant
                        FileUploadState.FileStatus.SELECTED -> colorResource(R.color.file_selected_border)
                        FileUploadState.FileStatus.UPLOADING -> colorResource(R.color.upload_progress_foreground)
                        FileUploadState.FileStatus.UPLOADED -> colorResource(R.color.upload_success_foreground)
                        FileUploadState.FileStatus.ERROR -> colorResource(R.color.upload_error_foreground)
                    }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // File information
            if (file.selectedFileName != null || file.isUploaded) {
                Column {
                    if (file.selectedFileName != null) {
                        Text(
                            text = "File: ${file.selectedFileName}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    if (file.fileSize > 0) {
                        Text(
                            text = stringResource(R.string.file_size, file.formattedFileSize),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (file.uploadedAt != null) {
                        val dateFormat =
                            SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
                        Text(
                            text = stringResource(
                                R.string.uploaded_at,
                                dateFormat.format(Date(file.uploadedAt))
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
            }

            // Upload progress
            if (file.status == FileUploadState.FileStatus.UPLOADING) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(
                                R.string.upload_in_progress,
                                file.uploadProgress
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "${file.uploadProgress}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    LinearProgressIndicator(
                        progress = { file.uploadProgress / 100f },
                        modifier = Modifier.fillMaxWidth(),
                        color = colorResource(R.color.upload_progress_foreground),
                        trackColor = colorResource(R.color.progress_track),
                        strokeCap = ProgressIndicatorDefaults.LinearStrokeCap,
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
            }

            // Error message
            if (file.status == FileUploadState.FileStatus.ERROR && file.errorMessage != null) {
                Text(
                    text = file.errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = colorResource(R.color.upload_error_foreground),
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                when (file.status) {
                    FileUploadState.FileStatus.PENDING -> {
                        Button(
                            onClick = onSelectFile,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Upload,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.select_file))
                        }
                    }

                    FileUploadState.FileStatus.SELECTED -> {
                        Button(
                            onClick = onUploadFile,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudUpload,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.upload_file))
                        }

                        OutlinedButton(
                            onClick = onSelectFile,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.replace_file))
                        }
                    }

                    FileUploadState.FileStatus.UPLOADING -> {
                        Button(
                            onClick = { },
                            enabled = false,
                            modifier = Modifier.weight(1f)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.file_upload_in_progress))
                        }
                    }

                    FileUploadState.FileStatus.UPLOADED -> {
                        OutlinedButton(
                            onClick = onReplaceFile,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.replace_file))
                        }

                        OutlinedButton(
                            onClick = onRemoveFile,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = colorResource(R.color.upload_error_foreground)
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.remove_file))
                        }
                    }

                    FileUploadState.FileStatus.ERROR -> {
                        Button(
                            onClick = onUploadFile,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.retry))
                        }

                        OutlinedButton(
                            onClick = onSelectFile,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Upload,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.select_file))
                        }
                    }
                }
            }
        }
    }
}

