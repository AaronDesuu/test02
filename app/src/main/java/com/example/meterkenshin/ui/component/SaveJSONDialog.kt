package com.example.meterkenshin.ui.component

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight

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
                text = "Save Billing Data",
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