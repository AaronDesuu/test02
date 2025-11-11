package com.example.meterkenshin.ui.component

import android.content.Context
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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.meterkenshin.ui.manager.AppPreferences

/**
 * Batch Processing Progress Dialog
 * Shows real-time progress with integrated user choices:
 * - Use Existing Data or Read New (when billing data exists)
 * - Print/Save options after read completes
 * Respects AppPreferences settings for printing and JSON saving
 */
@Composable
fun BatchProcessingDialog(
    context: Context,
    isProcessing: Boolean,
    processedCount: Int,
    totalCount: Int,
    currentStepDescription: String,
    currentMeterSerial: String?,
    errorCount: Int,
    awaitingUserAction: Boolean,
    showUseExistingDialog: Boolean,
    useExistingDialogMeter: String?,
    shouldPrint: Boolean,
    shouldSaveJson: Boolean,
    onUseExistingClicked: () -> Unit,
    onReadNewClicked: () -> Unit,
    onPrintOptionChanged: (Boolean) -> Unit,
    onSaveJsonOptionChanged: (Boolean) -> Unit,
    onConfirmUserAction: () -> Unit,
    onCancel: () -> Unit
) {
    if (isProcessing) {
        AlertDialog(
            onDismissRequest = { /* Prevent dismiss */ },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Batch Processing",
                        style = MaterialTheme.typography.headlineSmall
                    )

                    // X button (only show during awaiting user action)
                    if (awaitingUserAction) {
                        IconButton(
                            onClick = onCancel,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cancel",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Progress Indicator
                    LinearProgressIndicator(
                        progress = { if (totalCount > 0) processedCount.toFloat() / totalCount.toFloat() else 0f },
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Progress Text
                    Text(
                        text = "Progress: $processedCount / $totalCount Meter(s)",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Current Step Description
                    Text(
                        text = currentStepDescription,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Current Meter Serial
                    if (currentMeterSerial != null) {
                        Text(
                            text = "Current Meter: $currentMeterSerial",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    // Error Count (if any)
                    if (errorCount > 0) {
                        Text(
                            text = "⚠️ Errors: $errorCount",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // Use Existing Data or Read New Choice
                    if (showUseExistingDialog && useExistingDialogMeter != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        UseExistingDataCard(
                            meterSerial = useExistingDialogMeter,
                            onUseExisting = onUseExistingClicked,
                            onReadNew = onReadNewClicked
                        )
                    }

                    // Print/Save Options (when awaiting user action and NOT showing use existing)
                    if (awaitingUserAction && !showUseExistingDialog) {
                        Spacer(modifier = Modifier.height(4.dp))
                        PrintSaveOptionsCard(
                            context = context,
                            shouldPrint = shouldPrint,
                            shouldSaveJson = shouldSaveJson,
                            onPrintChanged = onPrintOptionChanged,
                            onSaveJsonChanged = onSaveJsonOptionChanged,
                            onConfirm = onConfirmUserAction
                        )
                    }
                }
            },
            confirmButton = {
                // Only show Cancel button when NOT awaiting user action
                if (!awaitingUserAction) {
                    TextButton(onClick = onCancel) {
                        Text("Cancel")
                    }
                }
            }
        )
    }
}

/**
 * Card for "Use Existing or Read New" choice
 * Appears when meter already has billing data
 */
@Composable
private fun UseExistingDataCard(
    meterSerial: String,
    onUseExisting: () -> Unit,
    onReadNew: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "⚠️ Billing Data Available",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Text(
                text = "Meter: $meterSerial",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "This meter already has saved billing data. Choose an option:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Two action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onUseExisting,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Use Existing",
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }
                Button(
                    onClick = onReadNew,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Read Again",
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

/**
 * Card for Print/Save options
 * Appears after read operation completes
 * Respects AppPreferences settings - hides disabled options
 */
@Composable
private fun PrintSaveOptionsCard(
    context: Context,
    shouldPrint: Boolean,
    shouldSaveJson: Boolean,
    onPrintChanged: (Boolean) -> Unit,
    onSaveJsonChanged: (Boolean) -> Unit,
    onConfirm: () -> Unit
) {
    // Check AppPreferences
    val printingEnabled = AppPreferences.isPrintingEnabled(context)
    val jsonEnabled = AppPreferences.isJsonSavingEnabled(context)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Choose Actions",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            // Print Receipt Option - Only show if printing is enabled
            if (printingEnabled) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPrintChanged(!shouldPrint) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = shouldPrint,
                        onCheckedChange = onPrintChanged
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Print Receipt",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Print thermal receipt for this meter",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                // Show info that printing is disabled
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Printing is disabled in Settings",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontStyle = FontStyle.Italic
                    )
                }
            }

            // Save JSON Option - Only show if JSON saving is enabled
            if (jsonEnabled) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSaveJsonChanged(!shouldSaveJson) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = shouldSaveJson,
                        onCheckedChange = onSaveJsonChanged
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Save JSON",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Export billing data to JSON file",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                // Show info that JSON saving is disabled
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "JSON saving is disabled in Settings",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontStyle = FontStyle.Italic
                    )
                }
            }

            // Warning if nothing selected (considering what's enabled)
            val nothingSelected = when {
                printingEnabled && jsonEnabled -> !shouldPrint && !shouldSaveJson
                printingEnabled && !jsonEnabled -> !shouldPrint
                !printingEnabled && jsonEnabled -> !shouldSaveJson
                else -> false  // Both disabled, nothing to select
            }

            if (nothingSelected) {
                Text(
                    text = "⚠️ No action selected - meter will be skipped",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Continue button
            Button(
                onClick = onConfirm,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    if (printingEnabled || jsonEnabled) {
                        if (shouldPrint || shouldSaveJson) "Continue" else "Skip"
                    } else {
                        "Skip" // Both disabled
                    }
                )
            }
        }
    }
}