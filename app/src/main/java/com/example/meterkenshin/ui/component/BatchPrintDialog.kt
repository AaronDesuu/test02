package com.example.meterkenshin.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.meterkenshin.ui.viewmodel.BatchPrintMode

/**
 * Batch Print Dialogs
 *
 * Contains reusable dialog components for batch printing operations:
 * 1. BatchPrintOptionsDialog - Mode selection dialog
 * 2. BatchPrintProgressDialog - Progress tracking dialog
 *
 * Note: For printer error dialogs, use PrinterStatusErrorDialog from PrinterStatusErrorDialog.kt
 */

/**
 * Batch Print Options Dialog
 * Lets user choose between printing all meters or only not-yet-printed meters
 */
@Composable
fun BatchPrintOptionsDialog(
    selectedMode: BatchPrintMode,
    onModeSelected: (BatchPrintMode) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Batch Printing Options",
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column {
                Text(
                    text = "Choose which meters to print:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))

                // Option 1: Print ALL
                BatchPrintModeOption(
                    selected = selectedMode == BatchPrintMode.ALL,
                    onClick = { onModeSelected(BatchPrintMode.ALL) },
                    title = "Print All",
                    description = "Print all meters with available billing data"
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Option 2: Print NOT_PRINTED_ONLY
                BatchPrintModeOption(
                    selected = selectedMode == BatchPrintMode.NOT_PRINTED_ONLY,
                    onClick = { onModeSelected(BatchPrintMode.NOT_PRINTED_ONLY) },
                    title = "Print Not Yet Printed",
                    description = "Print only meters with status: Inspected, Billing not Printed"
                )
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("Start Printing")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Reusable radio button option for batch print mode selection
 */
@Composable
private fun BatchPrintModeOption(
    selected: Boolean,
    onClick: () -> Unit,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Batch Print Progress Dialog
 * Shows real-time progress of batch printing operation
 */
@Composable
fun BatchPrintProgressDialog(
    processedCount: Int,
    totalCount: Int,
    currentStep: Int,
    currentStepDescription: String,
    currentMeterSerial: String?,
    errorCount: Int,
    isProcessing: Boolean,
    onCancel: () -> Unit,
    onClose: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { /* Prevent dismiss during processing */ },
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Batch Printing",
                    style = MaterialTheme.typography.headlineSmall
                )

                // Cancel button (only show while processing)
                if (isProcessing) {
                    IconButton(onClick = onCancel) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cancel"
                        )
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Progress bar
                LinearProgressIndicator(
                    progress = {
                        if (totalCount > 0) {
                            processedCount.toFloat() / totalCount.toFloat()
                        } else 0f
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                )

                // Progress count
                Text(
                    text = "Progress: $processedCount / $totalCount",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Current step
                Text(
                    text = "Step $currentStep of 3",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Current meter serial number
                if (currentMeterSerial != null) {
                    Text(
                        text = "Current: $currentMeterSerial",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }

                // Step description
                Text(
                    text = currentStepDescription,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Error count indicator
                if (errorCount > 0) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Errors",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Errors: $errorCount",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        },
        confirmButton = {
            // Only show close button when processing is complete
            if (!isProcessing) {
                Button(onClick = onClose) {
                    Text("Close")
                }
            }
        }
    )
}