package com.example.meterkenshin.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.meterkenshin.R
import com.example.meterkenshin.model.RequiredFile

/**
 * Warning banner shown when files are missing
 */
@Composable
fun MissingFilesWarningBanner(
    missingFiles: List<RequiredFile>,
    onUploadClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (missingFiles.isEmpty()) return

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = colorResource(R.color.upload_warning_foreground),
                shape = RoundedCornerShape(12.dp)
            ),
        colors = CardDefaults.cardColors(
            containerColor = colorResource(R.color.upload_warning_background)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = colorResource(R.color.upload_warning_foreground),
                    modifier = Modifier.size(24.dp)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    text = stringResource(R.string.system_not_ready, missingFiles.size),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colorResource(R.color.upload_warning_foreground)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Missing files list
            Text(
                text = stringResource(R.string.incomplete_upload_message),
                style = MaterialTheme.typography.bodyMedium,
                color = colorResource(R.color.upload_warning_foreground)
            )

            Spacer(modifier = Modifier.height(8.dp))

            missingFiles.forEach { file ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Circle,
                        contentDescription = null,
                        tint = colorResource(R.color.upload_warning_foreground),
                        modifier = Modifier.size(6.dp)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = file.displayName,
                        style = MaterialTheme.typography.bodySmall,
                        color = colorResource(R.color.upload_warning_foreground)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action button
            Button(
                onClick = onUploadClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colorResource(R.color.upload_warning_foreground)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.CloudUpload,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.upload_required_files))
            }
        }
    }
}

/**
 * Compact warning indicator for navigation/status bars
 */
@Composable
fun UploadStatusIndicator(
    allFilesUploaded: Boolean,
    missingCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (allFilesUploaded) {
        colorResource(R.color.upload_success_background)
    } else {
        colorResource(R.color.upload_warning_background)
    }

    val contentColor = if (allFilesUploaded) {
        colorResource(R.color.upload_success_foreground)
    } else {
        colorResource(R.color.upload_warning_foreground)
    }

    val icon = if (allFilesUploaded) {
        Icons.Default.CheckCircle
    } else {
        Icons.Default.Warning
    }

    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = backgroundColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(16.dp)
            )

            Spacer(modifier = Modifier.width(6.dp))

            Text(
                text = if (allFilesUploaded) {
                    stringResource(R.string.all_files_uploaded)
                } else {
                    stringResource(R.string.system_not_ready, missingCount)
                },
                style = MaterialTheme.typography.bodySmall,
                color = contentColor,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * Full-screen warning dialog when user tries to proceed without uploading all files
 */
@Composable
fun UploadRequiredDialog(
    missingFiles: List<RequiredFile>,
    onDismiss: () -> Unit,
    onUploadFiles: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.CloudUpload,
                contentDescription = null,
                tint = colorResource(R.color.upload_warning_foreground),
                modifier = Modifier.size(48.dp)
            )
        },
        title = {
            Text(
                text = stringResource(R.string.upload_warning_title),
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.incomplete_upload_message),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                // List missing files
                missingFiles.forEach { file ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = colorResource(R.color.file_pending_background)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = null,
                                tint = colorResource(R.color.file_pending_border),
                                modifier = Modifier.size(20.dp)
                            )

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = file.displayName,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = file.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = colorResource(R.color.upload_warning_foreground),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onDismiss()
                    onUploadFiles()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = colorResource(R.color.upload_warning_foreground)
                )
            ) {
                Icon(
                    imageVector = Icons.Default.CloudUpload,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.upload_required_files))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

/**
 * Success state when all files are uploaded
 */
@Composable
fun AllFilesUploadedCard(
    onProceed: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = colorResource(R.color.upload_success_foreground),
                shape = RoundedCornerShape(12.dp)
            ),
        colors = CardDefaults.cardColors(
            containerColor = colorResource(R.color.upload_success_background)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = colorResource(R.color.upload_success_foreground),
                modifier = Modifier.size(48.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = stringResource(R.string.all_files_uploaded),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = colorResource(R.color.upload_success_foreground),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.ready_to_proceed),
                style = MaterialTheme.typography.bodyMedium,
                color = colorResource(R.color.upload_success_foreground),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = onProceed,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colorResource(R.color.upload_success_foreground)
                ),
                modifier = Modifier.fillMaxWidth()
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
        }
    }
}