package com.example.meterkenshin.ui.component.dialog

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.meterkenshin.printer.BluetoothPrinterManager
import com.example.meterkenshin.ui.viewmodel.PrinterBluetoothViewModel

/**
 * PrinterStatusErrorDialog - Shows printer error with real-time status and retry option
 *
 * Features:
 * - Error message at top
 * - Connection status (Connected/Disconnected)
 * - Real-time printer status (Paper & Cover) - updates automatically
 * - Connect button when printer is not connected
 * - Retry and Cancel buttons
 */
@Composable
fun PrinterStatusErrorDialog(
    errorMessage: String,
    paperStatus: PrinterBluetoothViewModel.PaperStatus,
    coverStatus: PrinterBluetoothViewModel.CoverStatus,
    printerViewModel: PrinterBluetoothViewModel? = null,
    onRetry: () -> Unit,
    onCancel: () -> Unit
) {
    // Collect connection state if printerViewModel is provided
    val connectionState by (printerViewModel?.connectionState?.collectAsState()
        ?: remember { mutableStateOf(null) })
    val isConnecting = connectionState == BluetoothPrinterManager.ConnectionState.CONNECTING
    val isConnected = connectionState == BluetoothPrinterManager.ConnectionState.CONNECTED
    val isNotConnected = !isConnected

    LaunchedEffect(isConnected) {
        if (isConnected && printerViewModel != null) {
            // Reset status to UNKNOWN before checking
            printerViewModel.resetStatus()
            // Request immediate status check
            printerViewModel.checkPrinterStatusNow()
        }
    }

    AlertDialog(
        onDismissRequest = onCancel,
        icon = {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = Color(0xFFFF9800),
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = "Printer Error",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. Error Message
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color(0xFFD32F2F),
                    fontWeight = FontWeight.Medium
                )

                HorizontalDivider(color = Color.LightGray)

                // 2. Printer Status Title with Connection Status
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Printer Status:",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )

                    // Connection Status Badge
                    Text(
                        text = when {
                            isConnecting -> "Connecting..."
                            isConnected -> "Connected"
                            else -> "Disconnected"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            isConnecting -> Color(0xFFFF9800)
                            isConnected -> Color(0xFF4CAF50)
                            else -> Color(0xFFF44336)
                        }
                    )
                }

                // 3. Paper & Cover Status Card
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = 1.dp,
                            color = Color(0xFFE0E0E0),
                            shape = RoundedCornerShape(8.dp)
                        ),
                    color = Color(0xFFF5F5F5),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    if (isConnected) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Paper Status Row
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Article,
                                    contentDescription = "Paper Status",
                                    tint = when (paperStatus) {
                                        PrinterBluetoothViewModel.PaperStatus.OK -> Color(0xFF4CAF50)
                                        PrinterBluetoothViewModel.PaperStatus.OUT -> Color(
                                            0xFFF44336
                                        )

                                        else -> Color.Gray
                                    },
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = when (paperStatus) {
                                        PrinterBluetoothViewModel.PaperStatus.OK -> "Paper: OK"
                                        PrinterBluetoothViewModel.PaperStatus.OUT -> "Paper: OUT"
                                        else -> "Paper: Checking..."
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = when (paperStatus) {
                                        PrinterBluetoothViewModel.PaperStatus.OK -> Color(0xFF4CAF50)
                                        PrinterBluetoothViewModel.PaperStatus.OUT -> Color(
                                            0xFFF44336
                                        )

                                        else -> Color.Gray
                                    },
                                    fontWeight = when (paperStatus) {
                                        PrinterBluetoothViewModel.PaperStatus.OUT -> FontWeight.Bold
                                        else -> FontWeight.Normal
                                    }
                                )
                            }

                            // Cover Status Row
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = if (coverStatus == PrinterBluetoothViewModel.CoverStatus.OPEN)
                                        Icons.Default.LockOpen else Icons.Default.Lock,
                                    contentDescription = "Cover Status",
                                    tint = when (coverStatus) {
                                        PrinterBluetoothViewModel.CoverStatus.CLOSED -> Color(
                                            0xFF4CAF50
                                        )

                                        PrinterBluetoothViewModel.CoverStatus.OPEN -> Color(
                                            0xFFFF9800
                                        )

                                        else -> Color.Gray
                                    },
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = when (coverStatus) {
                                        PrinterBluetoothViewModel.CoverStatus.CLOSED -> "Cover: Closed"
                                        PrinterBluetoothViewModel.CoverStatus.OPEN -> "Cover: Open"
                                        else -> "Cover: Checking..."
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = when (coverStatus) {
                                        PrinterBluetoothViewModel.CoverStatus.CLOSED -> Color(
                                            0xFF4CAF50
                                        )

                                        PrinterBluetoothViewModel.CoverStatus.OPEN -> Color(
                                            0xFFFF9800
                                        )

                                        else -> Color.Gray
                                    },
                                    fontWeight = when (coverStatus) {
                                        PrinterBluetoothViewModel.CoverStatus.OPEN -> FontWeight.Bold
                                        else -> FontWeight.Normal
                                    }
                                )
                            }
                        }
                    }
                }

                // 4. Connect Button (shown when printer is not connected)
                if (isNotConnected && printerViewModel != null) {
                    OutlinedButton(
                        onClick = {
                            printerViewModel.startAutoConnect()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isConnecting
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bluetooth,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            if (isConnecting) "Connecting..." else "Connect to Printer"
                        )
                    }
                }

                // Help text
                Text(
                    text = if (isNotConnected) {
                        "Please connect to printer and try again."
                    } else {
                        "Please resolve the issue above and try again."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF1976D2)
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Retry")
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text("Cancel")
            }
        }
    )
}