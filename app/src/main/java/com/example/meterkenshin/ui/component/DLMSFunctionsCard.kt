package com.example.meterkenshin.ui.component

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

/**
 * DLMS Functions Card Component
 *
 * Displays all 6 DLMS function buttons:
 * - Registration
 * - Read data
 * - Load profile
 * - Event log
 * - Billing data
 * - Set Clock
 */
@Composable
fun DLMSFunctionsCard(
    modifier: Modifier = Modifier,
    meterActivate: Int = 0,
    onRegistration: () -> Unit,
    onReadData: () -> Unit,
    onLoadProfile: () -> Unit,
    onEventLog: () -> Unit,
    onBillingData: () -> Unit,
    onSetClock: () -> Unit,
    isProcessing: Boolean = false,
) {
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

            // Registration button - only enabled when activate = 0
            DLMSFunctionButton(
                text = "Registration",
                icon = Icons.Default.Person,
                onClick = onRegistration,
                enabled = !isProcessing && meterActivate == 0
            )
            Spacer(modifier = Modifier.height(8.dp))


            // Other buttons - enabled when activate ≠ 0
            DLMSFunctionButton(
                text = "Read data",
                icon = Icons.Default.Assessment,
                onClick = onReadData,
                enabled = !isProcessing && meterActivate != 0
            )
            Spacer(modifier = Modifier.height(8.dp))

            DLMSFunctionButton(
                text = "Load profile",
                icon = Icons.Default.Storage,
                onClick = onLoadProfile,
                enabled = !isProcessing && meterActivate != 0
            )
            Spacer(modifier = Modifier.height(8.dp))

            DLMSFunctionButton(
                text = "Event log",
                icon = Icons.Default.Event,
                onClick = onEventLog,
                enabled = !isProcessing && meterActivate != 0
            )
            Spacer(modifier = Modifier.height(8.dp))

            DLMSFunctionButton(
                text = "Billing data",
                icon = Icons.Default.Payment,
                onClick = onBillingData,
                enabled = !isProcessing && meterActivate != 0
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Set Clock - ALWAYS enabled (exception)
            DLMSFunctionButton(
                text = "Set Clock",
                icon = Icons.Default.Schedule,
                onClick = onSetClock,
                enabled = !isProcessing  // No meterActivate check
            )
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