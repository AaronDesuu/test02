package com.example.meterkenshin.ui.component

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.meterkenshin.R
import com.example.meterkenshin.printer.BluetoothPrinterManager
import com.example.meterkenshin.ui.viewmodel.PrinterBluetoothViewModel

@Composable
fun BluetoothStatusComponent(
    connectionState: BluetoothPrinterManager.ConnectionState?,
    isBluetoothEnabled: Boolean,
    connectedDevice: android.bluetooth.BluetoothDevice?,
    statusMessage: String?,
    paperStatus: PrinterBluetoothViewModel.PaperStatus,
    coverStatus: PrinterBluetoothViewModel.CoverStatus,
    printerBluetoothViewModel: PrinterBluetoothViewModel
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = colorResource(R.color.printer_card_background)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Section Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.printer_config_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Woosim Printer Connection",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Bluetooth Icon based on state
                Icon(
                    imageVector = when {
                        !isBluetoothEnabled -> Icons.Default.BluetoothDisabled
                        connectionState == BluetoothPrinterManager.ConnectionState.CONNECTED -> Icons.Default.BluetoothConnected
                        connectionState == BluetoothPrinterManager.ConnectionState.CONNECTING -> Icons.AutoMirrored.Filled.BluetoothSearching
                        else -> Icons.Default.Bluetooth
                    },
                    contentDescription = null,
                    tint = colorResource(R.color.primary_light),
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Connection Status Card
            BluetoothConnectionStatusCard(
                connectionState = connectionState,
                isBluetoothEnabled = isBluetoothEnabled,
                connectedDevice = connectedDevice,
                statusMessage = statusMessage
            )

            // Printer Status (Paper & Cover) - Only show when connected
            if (connectionState == BluetoothPrinterManager.ConnectionState.CONNECTED) {
                Spacer(modifier = Modifier.height(12.dp))
                PrinterStatusIndicators(
                    paperStatus = paperStatus,
                    coverStatus = coverStatus
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action Buttons
            BluetoothActionButtons(
                connectionState = connectionState,
                isBluetoothEnabled = isBluetoothEnabled,
                printerBluetoothViewModel = printerBluetoothViewModel
            )
        }
    }
}

@Composable
private fun BluetoothConnectionStatusCard(
    connectionState: BluetoothPrinterManager.ConnectionState?,
    isBluetoothEnabled: Boolean,
    connectedDevice: android.bluetooth.BluetoothDevice?,
    statusMessage: String?
) {
    val (backgroundColor, foregroundColor, borderColor, statusText, statusIcon) = when {
        !isBluetoothEnabled -> listOf(
            colorResource(R.color.bluetooth_disabled_background),
            colorResource(R.color.bluetooth_disabled_foreground),
            colorResource(R.color.bluetooth_disabled_border),
            "Bluetooth Disabled",
            Icons.Default.BluetoothDisabled
        )
        connectionState == BluetoothPrinterManager.ConnectionState.CONNECTED -> listOf(
            colorResource(R.color.bluetooth_connected_background),
            colorResource(R.color.bluetooth_connected_foreground),
            colorResource(R.color.bluetooth_connected_border),
            stringResource(R.string.connection_state_connected),
            Icons.Default.CheckCircle
        )
        connectionState == BluetoothPrinterManager.ConnectionState.CONNECTING -> listOf(
            colorResource(R.color.bluetooth_connecting_background),
            colorResource(R.color.bluetooth_connecting_foreground),
            colorResource(R.color.bluetooth_connecting_border),
            stringResource(R.string.connection_state_connecting),
            Icons.Default.Sync
        )
        connectionState == BluetoothPrinterManager.ConnectionState.ERROR -> listOf(
            colorResource(R.color.bluetooth_error_background),
            colorResource(R.color.bluetooth_error_foreground),
            colorResource(R.color.bluetooth_error_border),
            stringResource(R.string.connection_state_error),
            Icons.Default.Error
        )
        else -> listOf(
            colorResource(R.color.bluetooth_disconnected_background),
            colorResource(R.color.bluetooth_disconnected_foreground),
            colorResource(R.color.bluetooth_disconnected_border),
            stringResource(R.string.connection_state_disconnected),
            Icons.Default.Warning
        )
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = borderColor as Color,
                shape = RoundedCornerShape(8.dp)
            ),
        color = backgroundColor as Color,
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = statusIcon as androidx.compose.ui.graphics.vector.ImageVector,
                contentDescription = null,
                tint = foregroundColor as Color,
                modifier = Modifier.size(20.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = statusText as String,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = foregroundColor
                )

                // Show device info when connected
                if (connectionState == BluetoothPrinterManager.ConnectionState.CONNECTED && connectedDevice != null) {
                    val deviceName = try {
                        connectedDevice.name
                    } catch (_: SecurityException) {
                        null
                    } ?: "Unknown Device"

                    val deviceAddress = connectedDevice.address

                    Text(
                        text = stringResource(R.string.bluetooth_device_info, deviceName, deviceAddress),
                        style = MaterialTheme.typography.bodySmall,
                        color = foregroundColor.copy(alpha = 0.8f)
                    )
                }

                // Show status message if available
                statusMessage?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = foregroundColor.copy(alpha = 0.7f)
                    )
                }
            }

            // Show loading indicator when connecting
            if (connectionState == BluetoothPrinterManager.ConnectionState.CONNECTING) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = foregroundColor
                )
            }
        }
    }
}

@Composable
private fun PrinterStatusIndicators(
    paperStatus: PrinterBluetoothViewModel.PaperStatus,
    coverStatus: PrinterBluetoothViewModel.CoverStatus
) {
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
                .padding(12.dp)
        ) {
            Text(
                text = "Printer Status",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Paper Status
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
                    modifier = Modifier.size(18.dp)
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

            Spacer(modifier = Modifier.height(6.dp))

            // Cover Status
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
                    modifier = Modifier.size(18.dp)
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
}

@Composable
private fun BluetoothActionButtons(
    connectionState: BluetoothPrinterManager.ConnectionState?,
    isBluetoothEnabled: Boolean,
    printerBluetoothViewModel: PrinterBluetoothViewModel
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        when {
            !isBluetoothEnabled -> {
                OutlinedButton(
                    onClick = { /* Open Bluetooth settings - handled by MainActivity */ },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Enable Bluetooth")
                }
            }
            connectionState == BluetoothPrinterManager.ConnectionState.ERROR -> {
                OutlinedButton(
                    onClick = {
                        // Retry connection
                        printerBluetoothViewModel.retryConnection()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Retry")
                }
            }
            connectionState == BluetoothPrinterManager.ConnectionState.CONNECTED -> {
                // Test print button
                OutlinedButton(
                    onClick = {
                        printerBluetoothViewModel.printSampleData()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Print,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Test Print")
                }

                // Disconnect button
                OutlinedButton(
                    onClick = {
                        printerBluetoothViewModel.disconnect()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.BluetoothDisabled,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Disconnect")
                }
            }
            else -> {
                // Connect button (when disconnected)
                OutlinedButton(
                    onClick = {
                        printerBluetoothViewModel.startAutoConnect()
                    },
                    modifier = Modifier.weight(1f),
                    enabled = connectionState != BluetoothPrinterManager.ConnectionState.CONNECTING
                ) {
                    Icon(
                        imageVector = Icons.Default.Bluetooth,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        if (connectionState == BluetoothPrinterManager.ConnectionState.CONNECTING)
                            "Connecting..."
                        else
                            "Connect"
                    )
                }
            }
        }
    }
}