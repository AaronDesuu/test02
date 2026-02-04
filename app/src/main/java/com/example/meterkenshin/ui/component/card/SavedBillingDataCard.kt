package com.example.meterkenshin.ui.component.card

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.meterkenshin.model.Billing
import com.example.meterkenshin.ui.component.dialog.PrinterStatusErrorDialog
import com.example.meterkenshin.ui.manager.AppPreferences
import com.example.meterkenshin.ui.viewmodel.PrinterBluetoothViewModel
import com.example.meterkenshin.ui.manager.NotificationManager
import com.example.meterkenshin.utils.PrinterStatusHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * SavedBillingDataCard - Shows saved billing data with options to print, export JSON, or clear
 * Displays data that's available for up to 30 days after readData
 *
 * UPDATED: Now uses PrinterStatusHelper for universal printer status checking
 * UPDATED: Buttons respect AppPreferences for printing and JSON saving
 * INCLUDES:
 * - Printer connectivity check before printing
 * - Paper and cover status check on button click
 * - Alert dialog with real-time status for printer errors
 * - 5-second button disable after print to prevent double printing
 */
@SuppressLint("DefaultLocale")
@Composable
fun SavedBillingDataCard(
    billing: Billing?,
    daysRemaining: Int,
    printerViewModel: PrinterBluetoothViewModel,
    onPrintReceipt: () -> Unit,
    onSaveJSON: () -> Unit,
    onClearData: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (billing == null) return

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Track print button state - disable for 5 seconds after print
    var isPrintButtonEnabled by remember { mutableStateOf(true) }

    // Track error dialog state
    var showErrorDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    // Collect real-time printer status for dialog
    val paperStatus by printerViewModel.paperStatus.collectAsState()
    val coverStatus by printerViewModel.coverStatus.collectAsState()

    // Check if printer is ready using helper
    PrinterStatusHelper.isPrinterConnected(printerViewModel)
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    // Get preferences
    val isPrintingEnabled = remember { AppPreferences.isPrintingEnabled(context) }
    val isJsonSavingEnabled = remember { AppPreferences.isJsonSavingEnabled(context) }

    // Function to attempt printing using PrinterStatusHelper
    fun attemptPrint() {
        if (!isPrintingEnabled) {
            NotificationManager.showWarning("Printing is disabled in settings")
            return
        }

        coroutineScope.launch {
            PrinterStatusHelper.checkPrinterReadyAndExecute(
                printerViewModel = printerViewModel,
                onNotConnected = {
                    errorMessage = "Printer is not connected. Please connect to printer."
                    showErrorDialog = true
                },
                onNotReady = { reason ->
                    errorMessage = reason
                    showErrorDialog = true
                },
                onReady = {
                    // All checks passed - proceed with printing
                    onPrintReceipt()

                    NotificationManager.showSuccess("Printing receipt...")

                    // Disable button for 5 seconds to prevent double printing
                    isPrintButtonEnabled = false
                    delay(5000)
                    isPrintButtonEnabled = true
                }
            )
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Inventory,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Saved Billing Data",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Available for $daysRemaining more days",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (daysRemaining < 7)
                            MaterialTheme.colorScheme.error
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Billing data summary
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BillingInfoRow("Period", billing.Period ?: "N/A")
                    BillingInfoRow("Serial Number", billing.SerialNumber ?: "N/A")
                    BillingInfoRow("Reading Date", billing.PeriodTo ?: "N/A")
                    BillingInfoRow(
                        "Total Use",
                        String.format("%.3f kWh", billing.TotalUse ?: 0f)
                    )
                    BillingInfoRow(
                        "Total Amount",
                        String.format("₱ %.2f", billing.TotalAmount ?: 0f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action buttons - only show enabled buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Print button - only show if printing enabled
                if (isPrintingEnabled) {
                    Button(
                        onClick = { attemptPrint() },
                        enabled = isPrintButtonEnabled, // Only disabled during cooldown
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Print,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("Print", style = MaterialTheme.typography.labelMedium)
                    }
                }

                // Save JSON button - only show if JSON saving enabled
                if (isJsonSavingEnabled) {
                    OutlinedButton(
                        onClick = onSaveJSON,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("JSON", style = MaterialTheme.typography.labelMedium)
                    }
                }

                // Clear data button - always visible
                OutlinedButton(
                    onClick = { showDeleteConfirmDialog = true },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text("Clear", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }

    // Delete confirmation dialog
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Clear Billing Data") },
            text = { Text("Are you sure you want to clear this saved billing data? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClearData()
                        showDeleteConfirmDialog = false
                    }
                ) {
                    Text("Clear", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Printer error dialog with real-time status
    if (showErrorDialog) {
        PrinterStatusErrorDialog(
            errorMessage = errorMessage,
            paperStatus = paperStatus,
            coverStatus = coverStatus,
            printerViewModel = printerViewModel,
            onRetry = {
                showErrorDialog = false
                attemptPrint()
            },
            onCancel = { showErrorDialog = false }
        )
    }
}

@Composable
private fun BillingInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}