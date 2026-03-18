package com.example.meterkenshin.ui.component.dialog

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private enum class ReadMode { ALL, BY_PERIOD }
private enum class Step { MODE_SELECT, DATE_PICK }

/**
 * Two-step dialog for DLMS data reads:
 *   Step 1 — choose "Get All Data" or "Get by Period"
 *   Step 2 — (if By Period) pick From / To dates
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataReadDialog(
    title: String,
    onConfirmAll: () -> Unit,
    onConfirmByPeriod: (fromDate: Date, toDate: Date) -> Unit,
    onDismiss: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }

    var step by remember { mutableStateOf(Step.MODE_SELECT) }
    var selectedMode by remember { mutableStateOf(ReadMode.ALL) }

    // Default: from = start of current month, to = today (both at UTC midnight for DatePicker)
    val defaultFrom = remember {
        Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
    val defaultTo = remember {
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999)
        }.timeInMillis
    }

    var fromMillis by remember { mutableStateOf(defaultFrom) }
    var toMillis by remember { mutableStateOf(defaultTo) }

    var showFromPicker by remember { mutableStateOf(false) }
    var showToPicker by remember { mutableStateOf(false) }

    // Date picker states
    val fromPickerState = rememberDatePickerState(initialSelectedDateMillis = fromMillis)
    val toPickerState = rememberDatePickerState(initialSelectedDateMillis = toMillis)

    // From date picker dialog
    if (showFromPicker) {
        DatePickerDialog(
            onDismissRequest = { showFromPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    fromPickerState.selectedDateMillis?.let { millis ->
                        // Set to start of day
                        val cal = Calendar.getInstance().apply {
                            timeInMillis = millis
                            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                        }
                        fromMillis = cal.timeInMillis
                    }
                    showFromPicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showFromPicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = fromPickerState)
        }
    }

    // To date picker dialog
    if (showToPicker) {
        DatePickerDialog(
            onDismissRequest = { showToPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    toPickerState.selectedDateMillis?.let { millis ->
                        // Set to end of day
                        val cal = Calendar.getInstance().apply {
                            timeInMillis = millis
                            set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59)
                            set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999)
                        }
                        toMillis = cal.timeInMillis
                    }
                    showToPicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showToPicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = toPickerState)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(16.dp),
        title = {
            Text(
                text = if (step == Step.MODE_SELECT) title
                       else "$title — By Period",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            when (step) {
                Step.MODE_SELECT -> {
                    Column(modifier = Modifier.selectableGroup()) {
                        ReadModeOption(
                            label = "Get All Data",
                            description = "Retrieve all available records",
                            selected = selectedMode == ReadMode.ALL,
                            onClick = { selectedMode = ReadMode.ALL }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        ReadModeOption(
                            label = "Get by Period",
                            description = "Filter records by date range",
                            selected = selectedMode == ReadMode.BY_PERIOD,
                            onClick = { selectedMode = ReadMode.BY_PERIOD }
                        )
                    }
                }
                Step.DATE_PICK -> {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "Select the date range to retrieve:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        DateSelectButton(
                            label = "From",
                            dateText = dateFormat.format(Date(fromMillis)),
                            onClick = { showFromPicker = true }
                        )
                        DateSelectButton(
                            label = "To",
                            dateText = dateFormat.format(Date(toMillis)),
                            onClick = { showToPicker = true }
                        )
                        if (fromMillis > toMillis) {
                            Text(
                                text = "\"From\" date must be before \"To\" date",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            when (step) {
                Step.MODE_SELECT -> {
                    Button(onClick = {
                        if (selectedMode == ReadMode.ALL) {
                            onConfirmAll()
                            onDismiss()
                        } else {
                            step = Step.DATE_PICK
                        }
                    }) {
                        Text(if (selectedMode == ReadMode.ALL) "Get" else "Next")
                    }
                }
                Step.DATE_PICK -> {
                    Button(
                        onClick = {
                            onConfirmByPeriod(Date(fromMillis), Date(toMillis))
                            onDismiss()
                        },
                        enabled = fromMillis <= toMillis
                    ) {
                        Text("Get")
                    }
                }
            }
        },
        dismissButton = {
            when (step) {
                Step.MODE_SELECT -> {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                }
                Step.DATE_PICK -> {
                    TextButton(onClick = { step = Step.MODE_SELECT }) { Text("Back") }
                }
            }
        }
    )
}

@Composable
private fun ReadModeOption(
    label: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    val contentAlpha = if (enabled) 1f else 0.38f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, enabled = enabled, onClick = onClick, role = Role.RadioButton)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha)
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha)
            )
        }
    }
}

@Composable
private fun DateSelectButton(
    label: String,
    dateText: String,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = dateText, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Default.CalendarMonth,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
