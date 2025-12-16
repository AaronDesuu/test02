package com.example.meterkenshin.ui.component.card

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.example.meterkenshin.model.UserRole
import com.example.meterkenshin.ui.manager.AppPreferences

/**
 * DLMS Functions Card Component
 *
 * Displays DLMS function buttons based on user role:
 * - ADMIN: All buttons (Registration, Read data, Load profile, Event log, Billing data, Set Clock)
 * - READER: Only Read data button
 */
@Composable
fun DLMSFunctionsCard(
    context: Context,
    modifier: Modifier = Modifier,
    meterActivate: Int = 0,
    userRole: UserRole? = null,
    onRegistration: () -> Unit,
    onReadData: () -> Unit,
    onLoadProfile: () -> Unit,
    onEventLog: () -> Unit,
    onBillingData: () -> Unit,
    onSetClock: () -> Unit,
    isProcessing: Boolean = false,
) {
    var showConfirmDialog by remember { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var dialogTitle by remember { mutableStateOf("") }
    var dialogMessage by remember { mutableStateOf("") }

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
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "DLMS Functions",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            // Registration button - only enabled when activate = 0 (ADMIN only)
            if (userRole != UserRole.READER) {
                DLMSFunctionButton(
                    text = "Registration",
                    icon = Icons.Default.Person,
                    onClick = onRegistration,
                    enabled = !isProcessing && meterActivate == 0
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Other buttons - enabled when activate ≠ 0
            // Read data button - Available to all users
            DLMSFunctionButton(
                text = "Read data",
                icon = Icons.Default.Assessment,
                onClick = {
                    if (AppPreferences.isDlmsConfirmEnabled(context)) {
                        dialogTitle = "Read Data"
                        dialogMessage = "Read meter data?"
                        pendingAction = onReadData
                        showConfirmDialog = true
                    } else {
                        onReadData()
                    }
                },
                enabled = !isProcessing && meterActivate != 0
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Admin-only buttons
            if (userRole != UserRole.READER) {
                DLMSFunctionButton(
                    text = "Load profile",
                    icon = Icons.Default.Storage,
                    onClick = {
                        if (AppPreferences.isDlmsConfirmEnabled(context)) {
                            dialogTitle = "Load Profile"
                            dialogMessage = "Load meter profile?"
                            pendingAction = onLoadProfile
                            showConfirmDialog = true
                        } else {
                            onLoadProfile()
                        }
                    },
                    enabled = !isProcessing && meterActivate != 0
                )
                Spacer(modifier = Modifier.height(8.dp))

                DLMSFunctionButton(
                    text = "Event log",
                    icon = Icons.Default.Event,
                    onClick = {
                        if (AppPreferences.isDlmsConfirmEnabled(context)) {
                            dialogTitle = "Event Log"
                            dialogMessage = "Retrieve event log?"
                            pendingAction = onEventLog
                            showConfirmDialog = true
                        } else {
                            onEventLog()
                        }
                    },
                    enabled = !isProcessing && meterActivate != 0
                )
                Spacer(modifier = Modifier.height(8.dp))

                DLMSFunctionButton(
                    text = "Billing data",
                    icon = Icons.Default.Payment,
                    onClick = {
                        if (AppPreferences.isDlmsConfirmEnabled(context)) {
                            dialogTitle = "Billing Data"
                            dialogMessage = "Retrieve billing data?"
                            pendingAction = onBillingData
                            showConfirmDialog = true
                        } else {
                            onBillingData()
                        }
                    },
                    enabled = !isProcessing && meterActivate != 0
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Set Clock - ADMIN only, ALWAYS enabled (no meterActivate check)
                DLMSFunctionButton(
                    text = "Set Clock",
                    icon = Icons.Default.Schedule,
                    onClick = {
                        if (AppPreferences.isDlmsConfirmEnabled(context)) {
                            dialogTitle = "Set Clock"
                            dialogMessage = "Set meter clock to current time?"
                            pendingAction = onSetClock
                            showConfirmDialog = true
                        } else {
                            onSetClock()
                        }
                    },
                    enabled = !isProcessing  // No meterActivate check
                )
            }

            // Confirmation Dialog
            if (showConfirmDialog) {
                AlertDialog(
                    onDismissRequest = {
                        showConfirmDialog = false
                        pendingAction = null
                    },
                    title = { Text(dialogTitle) },
                    text = { Text(dialogMessage) },
                    confirmButton = {
                        Button(
                            onClick = {
                                pendingAction?.invoke()
                                showConfirmDialog = false
                                pendingAction = null
                            }
                        ) {
                            Text("Confirm")
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                showConfirmDialog = false
                                pendingAction = null
                            }
                        ) {
                            Text("Cancel")
                        }
                    }
                )
            }
        }
    }
}

/**
 * Individual DLMS function button
 */
@Composable
private fun DLMSFunctionButton(
    modifier: Modifier = Modifier,
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
        }
    }
}