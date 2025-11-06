package com.example.meterkenshin.ui.component

import android.annotation.SuppressLint
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.meterkenshin.R
import com.example.meterkenshin.model.Billing
import com.example.meterkenshin.ui.viewmodel.PrinterBluetoothViewModel
import com.example.meterkenshin.utils.PrinterStatusHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * SavedBillingDataCard - Shows saved billing data with options to print, export JSON, or clear
 * Displays data that's available for up to 30 days after readData
 *
 * UPDATED: Now uses PrinterStatusHelper for universal printer status checking
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

    // Function to attempt printing using PrinterStatusHelper
    fun attemptPrint() {
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

                    Toast.makeText(
                        context,
                        "✅ Printing receipt...",
                        Toast.LENGTH_SHORT
                    ).show()

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

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Print button - CHANGED: Always enabled (except during 5-sec cooldown)
                Button(
                    onClick = { attemptPrint() },
                    enabled = isPrintButtonEnabled, // Only disable during cooldown
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Print,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(1.dp))
                    Text("Print")
                }

                // Save JSON button
                OutlinedButton(
                    onClick = onSaveJSON,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Save,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(1.dp))
                    Text("Save")
                }

                // Clear button
                OutlinedButton(
                    onClick = { showDeleteConfirmDialog = true }, // Changed from onClearData
                    modifier = Modifier.weight(0.6f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        tint = Color.Red,
                        modifier = Modifier.size(18.dp)
                    )
                }

// Add dialog at the end of the composable (before closing braces)
                if (showDeleteConfirmDialog) {
                    AlertDialog(
                        onDismissRequest = { showDeleteConfirmDialog = false },
                        title = { Text(stringResource(R.string.delete_billing_data_title)) },
                        text = { Text(stringResource(R.string.delete_billing_data_message)) },
                        confirmButton = {
                            Button(
                                onClick = {
                                    showDeleteConfirmDialog = false
                                    onClearData()
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Text("Delete")
                            }
                        },
                        dismissButton = {
                            Button(
                                onClick = { showDeleteConfirmDialog = false },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Text("Cancel")
                            }
                        }
                    )
                }
            }
        }
    }

    // Show error dialog when there's a printer issue
    if (showErrorDialog) {
        PrinterStatusErrorDialog(
            errorMessage = errorMessage,
            paperStatus = paperStatus,
            coverStatus = coverStatus,
            printerViewModel = printerViewModel,
            onRetry = {
                showErrorDialog = false
                // Retry printing
                attemptPrint()
            },
            onCancel = {
                showErrorDialog = false
            }
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