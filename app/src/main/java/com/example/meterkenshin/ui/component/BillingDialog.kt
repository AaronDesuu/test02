package com.example.meterkenshin.ui.component

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight

/**
 * Print Receipt Dialog
 * Shows FIRST after read data completes
 * User can print or skip to save dialog
 *
 * Note: SaveJSONDialog already exists in your codebase at:
 * com.example.meterkenshin.ui.component.SaveJSONDialog
 */
@Composable
fun PrintReceiptDialog(
    serialNumber: String?,
    onConfirmPrint: () -> Unit,
    onSkipPrint: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { /* Prevent dismissal by clicking outside */ },
        icon = {
            Icon(
                imageVector = Icons.Default.Print,
                contentDescription = null,
                tint = Color(0xFF1976D2)
            )
        },
        text = {
            Text("Serial Number: ${serialNumber ?: "Unknown"}\n\nWould you like to print the receipt?")
        },
        confirmButton = {
            Button(onClick = onConfirmPrint) {
                Icon(imageVector = Icons.Default.Print, contentDescription = null)
                Text(" Print")
            }
        },
        dismissButton = {
            TextButton(onClick = onSkipPrint) {
                Text("Skip")
            }
        }
    )
}

/**
 * SaveJSONDialog - Confirmation dialog for saving billing data to JSON
 * Shows after readData completes successfully
 */
@Composable
fun SaveJSONDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Save Data as .JSON",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                text = "Read Data completed successfully. Would you like to save the billing data to JSON file?"
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Save JSON")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Skip")
            }
        }
    )
}