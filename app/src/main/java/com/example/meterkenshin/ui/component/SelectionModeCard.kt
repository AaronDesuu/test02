package com.example.meterkenshin.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.meterkenshin.model.Meter

/**
 * Selection Mode UI Component
 * Displays the selection controls when user enters selection mode
 * Button Order: Process - Select All - Cancel
 */
@Composable
fun SelectionModeCard(
    modifier: Modifier = Modifier,
    selectedCount: Int,
    filteredMeters: List<Meter>,
    selectedMeters: Set<Int>,
    onSelectAll: () -> Unit,
    onCancel: () -> Unit,
    onBatchRead: (List<Meter>) -> Unit,
    isProcessing: Boolean = false,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left side - Selection count
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "$selectedCount selected",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            // Right side - Action buttons (Process - Select All - Cancel)
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Select All Button
                TextButton(
                    onClick = onSelectAll,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("Select All", style = MaterialTheme.typography.bodyMedium)
                }

                // Process Button
                Button(
                    onClick = {
                        val selected = filteredMeters.filter {
                            selectedMeters.contains(it.uid)
                        }
                        if (selected.isNotEmpty()) {
                            onBatchRead(selected)
                        }
                    },
                    enabled = selectedCount > 0 && !isProcessing,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text("Process", style = MaterialTheme.typography.bodyMedium)
                }

                // Cancel Button
                TextButton(
                    onClick = onCancel,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = Color.Red
                    )
                ) {
                    Text("Cancel", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}