package com.example.meterkenshin.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.QueuePlayNext
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * Print Actions Dropdown Component
 * A dropdown menu next to the search bar with printer icon and print-related actions
 */
@Composable
fun PrintActionsDropdown(
    modifier: Modifier = Modifier,
    onBatchReading: () -> Unit = {},
    onBatchPrinting: () -> Unit = {},
    onSelectAndPrint: () -> Unit = {},
    isPrintingEnabled: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        // Printer Icon Button
        FilledIconButton(
            onClick = { expanded = true },
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ),
            modifier = Modifier.size(56.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Print,
                contentDescription = "Print Actions",
                modifier = Modifier.size(24.dp)
            )
        }

        // Dropdown Menu
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
        ) {
            // Batch Reading
            PrintDropdownMenuItem(
                text = "Batch Reading",
                icon = Icons.Default.QueuePlayNext,
                onClick = {
                    expanded = false
                    onBatchReading()
                }
            )

            // Batch Printing
            if (isPrintingEnabled) {
                PrintDropdownMenuItem(
                    text = "Batch Printing",
                    icon = Icons.Default.Print,
                    onClick = {
                        expanded = false
                        onBatchPrinting()
                    }
                )
            }

            // Select & Print
            PrintDropdownMenuItem(
                text = "Select & Read",
                icon = Icons.Default.SelectAll,
                onClick = {
                    expanded = false
                    onSelectAndPrint()
                }
            )
        }
    }
}

/**
 * Custom dropdown menu item for print actions
 */
@Composable
private fun PrintDropdownMenuItem(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    DropdownMenuItem(
        text = {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        },
        onClick = onClick,
        modifier = modifier
    )
}