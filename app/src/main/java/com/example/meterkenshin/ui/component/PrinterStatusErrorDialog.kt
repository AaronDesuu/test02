package com.example.meterkenshin.ui.component

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.meterkenshin.ui.viewmodel.PrinterBluetoothViewModel

/**
 * PrinterStatusErrorDialog - Shows printer error with real-time status and retry option
 *
 * Features:
 * - Error message at top
 * - Real-time printer status (Paper & Cover) - updates automatically
 * - Retry and Cancel buttons
 */
@Composable
fun PrinterStatusErrorDialog(
    errorMessage: String,
    paperStatus: PrinterBluetoothViewModel.PaperStatus,
    coverStatus: PrinterBluetoothViewModel.CoverStatus,
    onRetry: () -> Unit,
    onCancel: () -> Unit
) {
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

                // 2. Printer Status (Real-time) - Similar to HomeScreen
                Text(
                    text = "Printer Status:",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

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
                                    PrinterBluetoothViewModel.PaperStatus.OUT -> Color(0xFFF44336)
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
                                    PrinterBluetoothViewModel.PaperStatus.OUT -> Color(0xFFF44336)
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
                                    PrinterBluetoothViewModel.CoverStatus.CLOSED -> Color(0xFF4CAF50)
                                    PrinterBluetoothViewModel.CoverStatus.OPEN -> Color(0xFFFF9800)
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
                                    PrinterBluetoothViewModel.CoverStatus.CLOSED -> Color(0xFF4CAF50)
                                    PrinterBluetoothViewModel.CoverStatus.OPEN -> Color(0xFFFF9800)
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

                // Help text
                Text(
                    text = "Please resolve the issue above and try again.",
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