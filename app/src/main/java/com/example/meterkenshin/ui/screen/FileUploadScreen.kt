package com.example.meterkenshin.ui.screen

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.meterkenshin.R
import com.example.meterkenshin.ui.viewmodel.FileUploadViewModel
import com.example.meterkenshin.model.RequiredFile
import com.example.meterkenshin.model.FileUploadState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileUploadScreen(
    viewModel: FileUploadViewModel = viewModel(),
    onUploadComplete: () -> Unit = {}
) {
    val context = LocalContext.current
    val uploadState by viewModel.uploadState.collectAsState()
    var showWarningDialog by remember { mutableStateOf(false) }

    // File picker launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.selectFile(it, context) }
    }

    // Remove the automatic navigation LaunchedEffect (keeping it commented for reference)
    /*
    LaunchedEffect(uploadState.allFilesUploaded) {
        if (uploadState.allFilesUploaded) {
            onUploadComplete()
        }
    }
    */

    // Add system bar padding to avoid overlapping with taskbar
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars) // Add system bar padding
            .padding(16.dp)
    ) {
        // Header
        FileUploadHeader(
            uploadedCount = uploadState.uploadedFilesCount,
            totalCount = uploadState.requiredFiles.size
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Required Files List
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(uploadState.requiredFiles) { file ->
                RequiredFileCard(
                    file = file,
                    onSelectFile = { filePickerLauncher.launch("*/*") },
                    onUploadFile = { viewModel.uploadFile(file.type, context) },
                    onRemoveFile = { viewModel.removeFile(file.type) }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Action Buttons
        FileUploadActions(
            allFilesUploaded = uploadState.allFilesUploaded,
            hasIncompleteUploads = uploadState.hasIncompleteUploads,
            onProceed = onUploadComplete, // Only manual navigation via button
            onShowWarning = { showWarningDialog = true }
        )
    }

    // Warning Dialog
    if (showWarningDialog) {
        IncompleteUploadWarningDialog(
            missingFiles = uploadState.missingFiles,
            onDismiss = { showWarningDialog = false }
        )
    }
}

@Composable
private fun FileUploadHeader(
    uploadedCount: Int,
    totalCount: Int
) {
    Column {
        Text(
            text = stringResource(R.string.file_upload_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.upload_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Progress Indicator
        UploadProgressIndicator(
            uploadedCount = uploadedCount,
            totalCount = totalCount
        )
    }
}

@Composable
private fun UploadProgressIndicator(
    uploadedCount: Int,
    totalCount: Int
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.files_uploaded, uploadedCount),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = "$uploadedCount/$totalCount",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        LinearProgressIndicator(
            progress = { if (totalCount > 0) uploadedCount.toFloat() / totalCount else 0f },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp),
            color = if (uploadedCount == totalCount) {
                colorResource(R.color.upload_success_foreground)
            } else {
                MaterialTheme.colorScheme.primary
            },
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}

@Composable
private fun RequiredFileCard(
    file: RequiredFile,
    onSelectFile: () -> Unit,
    onUploadFile: () -> Unit,
    onRemoveFile: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = when (file.status) {
                    FileUploadState.FileStatus.PENDING -> colorResource(R.color.file_pending_border)
                    FileUploadState.FileStatus.SELECTED -> colorResource(R.color.file_selected_border)
                    FileUploadState.FileStatus.UPLOADING -> colorResource(R.color.upload_progress_foreground)
                    FileUploadState.FileStatus.UPLOADED -> colorResource(R.color.file_uploaded_border)
                    FileUploadState.FileStatus.ERROR -> colorResource(R.color.file_error_border)
                },
                shape = RoundedCornerShape(12.dp)
            ),
        colors = CardDefaults.cardColors(
            containerColor = when (file.status) {
                FileUploadState.FileStatus.PENDING -> colorResource(R.color.file_pending_background)
                FileUploadState.FileStatus.SELECTED -> colorResource(R.color.file_selected_background)
                FileUploadState.FileStatus.UPLOADING -> colorResource(R.color.upload_progress_background)
                FileUploadState.FileStatus.UPLOADED -> colorResource(R.color.file_uploaded_background)
                FileUploadState.FileStatus.ERROR -> colorResource(R.color.file_error_background)
            }
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // File Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FileStatusIcon(status = file.status)

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = file.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Text(
                            text = file.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Status Text
                Text(
                    text = when (file.status) {
                        FileUploadState.FileStatus.PENDING -> "Required"
                        FileUploadState.FileStatus.SELECTED -> "Selected"
                        FileUploadState.FileStatus.UPLOADING -> "Uploading..."
                        FileUploadState.FileStatus.UPLOADED -> "Uploaded"
                        FileUploadState.FileStatus.ERROR -> "Error"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = when (file.status) {
                        FileUploadState.FileStatus.PENDING -> colorResource(R.color.file_pending_border)
                        FileUploadState.FileStatus.SELECTED -> colorResource(R.color.file_selected_border)
                        FileUploadState.FileStatus.UPLOADING -> colorResource(R.color.upload_progress_foreground)
                        FileUploadState.FileStatus.UPLOADED -> colorResource(R.color.file_uploaded_border)
                        FileUploadState.FileStatus.ERROR -> colorResource(R.color.file_error_border)
                    }
                )
            }

            // Selected File Name
            if (file.selectedFileName != null) {
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = null,
                        tint = colorResource(R.color.csv_file_icon),
                        modifier = Modifier.size(16.dp)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = file.selectedFileName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Upload Progress
            if (file.status == FileUploadState.FileStatus.UPLOADING && file.uploadProgress > 0) {
                Spacer(modifier = Modifier.height(12.dp))

                Column {
                    Text(
                        text = stringResource(R.string.upload_progress, file.uploadProgress),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    LinearProgressIndicator(
                        progress = { file.uploadProgress / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp),
                        color = colorResource(R.color.upload_progress_foreground),
                        trackColor = Color.White.copy(alpha = 0.3f)
                    )
                }
            }

            // Error Message
            if (file.status == FileUploadState.FileStatus.ERROR && file.errorMessage != null) {
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = null,
                        tint = colorResource(R.color.upload_error_foreground),
                        modifier = Modifier.size(16.dp)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = file.errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = colorResource(R.color.upload_error_foreground)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action Buttons
            FileActionButtons(
                file = file,
                onSelectFile = onSelectFile,
                onUploadFile = onUploadFile,
                onRemoveFile = onRemoveFile
            )
        }
    }
}

@Composable
private fun FileStatusIcon(status: FileUploadState.FileStatus) {
    val (icon, tint) = when (status) {
        FileUploadState.FileStatus.PENDING -> Icons.Default.CloudUpload to colorResource(R.color.file_pending_border)
        FileUploadState.FileStatus.SELECTED -> Icons.Default.FilePresent to colorResource(R.color.file_selected_border)
        FileUploadState.FileStatus.UPLOADING -> Icons.Default.CloudSync to colorResource(R.color.upload_progress_foreground)
        FileUploadState.FileStatus.UPLOADED -> Icons.Default.CheckCircle to colorResource(R.color.file_uploaded_border)
        FileUploadState.FileStatus.ERROR -> Icons.Default.Error to colorResource(R.color.file_error_border)
    }

    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = tint,
        modifier = Modifier.size(24.dp)
    )
}

@Composable
private fun FileActionButtons(
    file: RequiredFile,
    onSelectFile: () -> Unit,
    onUploadFile: () -> Unit,
    onRemoveFile: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        when (file.status) {
            FileUploadState.FileStatus.PENDING -> {
                Button(
                    onClick = onSelectFile,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
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
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colorResource(R.color.upload_progress_foreground)
                    )
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
                    onClick = onRemoveFile,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.remove_file))
                }
            }

            FileUploadState.FileStatus.UPLOADING -> {
                Button(
                    onClick = { },
                    enabled = false,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.uploading_file, file.displayName))
                }
            }

            FileUploadState.FileStatus.UPLOADED -> {
                OutlinedButton(
                    onClick = onSelectFile,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = colorResource(R.color.file_uploaded_border)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.replace_file),
                        color = colorResource(R.color.file_uploaded_border)
                    )
                }
            }

            FileUploadState.FileStatus.ERROR -> {
                Button(
                    onClick = onUploadFile,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colorResource(R.color.upload_error_foreground)
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.retry_upload))
                }

                OutlinedButton(
                    onClick = onSelectFile,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
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

@Composable
private fun FileUploadActions(
    allFilesUploaded: Boolean,
    hasIncompleteUploads: Boolean,
    onProceed: () -> Unit,
    onShowWarning: () -> Unit
) {
    Column {
        if (allFilesUploaded) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = colorResource(R.color.upload_success_background)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = colorResource(R.color.upload_success_foreground),
                        modifier = Modifier.size(24.dp)
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.all_files_uploaded),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = colorResource(R.color.upload_success_foreground)
                        )

                        Text(
                            text = stringResource(R.string.ready_to_proceed),
                            style = MaterialTheme.typography.bodySmall,
                            color = colorResource(R.color.upload_success_foreground)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onProceed,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colorResource(R.color.upload_success_foreground)
                )
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.continue_action),
                    style = MaterialTheme.typography.titleMedium
                )
            }
        } else {
            Button(
                onClick = onShowWarning,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colorResource(R.color.upload_warning_foreground)
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.upload_all_files_first))
            }
        }
    }
}

@Composable
private fun IncompleteUploadWarningDialog(
    missingFiles: List<String>,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = colorResource(R.color.upload_warning_foreground),
                modifier = Modifier.size(48.dp)
            )
        },
        title = {
            Text(
                text = stringResource(R.string.missing_files_warning),
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.incomplete_upload_message),
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(modifier = Modifier.height(12.dp))

                missingFiles.forEach { missingFile ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Circle,
                            contentDescription = null,
                            tint = colorResource(R.color.upload_error_foreground),
                            modifier = Modifier.size(8.dp)
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Text(
                            text = missingFile,
                            style = MaterialTheme.typography.bodySmall,
                            color = colorResource(R.color.upload_error_foreground)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = colorResource(R.color.upload_warning_foreground)
                )
            ) {
                Text(stringResource(R.string.ok))
            }
        }
    )
}