package com.example.meterkenshin.ui.component.dialog

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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import com.example.meterkenshin.ui.manager.BatchPrintMode

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
    currentStepDescription: String,
    currentMeterSerial: String?,
    errorCount: Int,
    isProcessing: Boolean,
    showConfirmButton: Boolean,
    onConfirm: () -> Unit,
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
                Text("Batch Printing")
                if (!isProcessing) {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Progress indicator
                LinearProgressIndicator(
                    progress = { processedCount.toFloat() / totalCount.toFloat() },
                    modifier = Modifier.fillMaxWidth()
                )

                // Progress text
                Text(
                    text = "Progress: $processedCount / $totalCount",
                    style = MaterialTheme.typography.bodyMedium
                )

                // Current meter
                if (currentMeterSerial != null) {
                    Text(
                        text = "Current: $currentMeterSerial",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Step description
                Text(
                    text = currentStepDescription,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Error count if any
                if (errorCount > 0) {
                    Text(
                        text = "Errors: $errorCount",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                // Confirmation button
                if (showConfirmButton && currentMeterSerial != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Button(
                        onClick = onConfirm,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Confirm Print: $currentMeterSerial")
                    }
                }
            }
        },
        confirmButton = {
            if (isProcessing) {
                Button(
                    onClick = onCancel,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Cancel")
                }
            } else {
                Button(onClick = onClose) {
                    Text("Close")
                }
            }
        }
    )
}