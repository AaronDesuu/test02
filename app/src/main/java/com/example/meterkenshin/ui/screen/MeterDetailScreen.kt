package com.example.meterkenshin.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.meterkenshin.R

/**
 * Modern Meter Detail Screen with updated design and theme consistency
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeterDetailScreen(
    meter: Meter,
    onBackPressed: () -> Unit = {},
    onRegistration: () -> Unit = {},
    onReadData: () -> Unit = {},
    onLoadProfile: () -> Unit = {},
    onEventLog: () -> Unit = {},
    onBillingData: () -> Unit = {},
    onSetClock: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // Modern Top App Bar matching other screens
        CenterAlignedTopAppBar(
            title = {
                Text(
                    text = "S/N: ${meter.serialNo}", // Changed from SerialID to S/N
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            navigationIcon = {
                IconButton(onClick = onBackPressed) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back)
                    )
                }
            },
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface, // Match other screens
                titleContentColor = MaterialTheme.colorScheme.onSurface
            )
        )

        // Main content with modern design
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Connection status card
            ConnectionStatusCard(meter = meter)

            // DLMS function buttons with modern design
            DLMSFunctionsCard(
                onRegistration = onRegistration,
                onReadData = onReadData,
                onLoadProfile = onLoadProfile,
                onEventLog = onEventLog,
                onBillingData = onBillingData,
                onSetClock = onSetClock
            )

            // Meter specifications card
            MeterSpecificationsCard(meter = meter)

            // Bottom padding for system bars
            Spacer(modifier = Modifier.height(16.dp))
            Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
}


/**
 * DLMS functions in a modern card design
 */
@Composable
private fun DLMSFunctionsCard(
    onRegistration: () -> Unit,
    onReadData: () -> Unit,
    onLoadProfile: () -> Unit,
    onEventLog: () -> Unit,
    onBillingData: () -> Unit,
    onSetClock: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Actions",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Registration button (active)
            ModernDLMSButton(
                text = "Registration",
                icon = Icons.Default.Person,
                onClick = onRegistration,
                isActive = true
            )

            // Other function buttons (inactive)
            ModernDLMSButton(
                text = "Read data",
                icon = Icons.Default.Assessment,
                onClick = onReadData,
                isActive = false
            )

            ModernDLMSButton(
                text = "Load profile",
                icon = Icons.Default.Storage,
                onClick = onLoadProfile,
                isActive = false
            )

            ModernDLMSButton(
                text = "Event log",
                icon = Icons.Default.Event,
                onClick = onEventLog,
                isActive = false
            )

            ModernDLMSButton(
                text = "Billing data",
                icon = Icons.Default.Payment,
                onClick = onBillingData,
                isActive = false
            )

            // Set Clock button (active)
            ModernDLMSButton(
                text = "Set Clock",
                icon = Icons.Default.AccessTime,
                onClick = onSetClock,
                isActive = true
            )
        }
    }
}

/**
 * Modern DLMS function button
 */
@Composable
private fun ModernDLMSButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isActive) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            contentColor = if (isActive) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        ),
        shape = RoundedCornerShape(16.dp),
        enabled = true
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

/**
 * Meter specifications card
 */
@Composable
private fun MeterSpecificationsCard(
    meter: Meter,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Meter Specifications",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Specification details in modern format
            SpecificationRow(
                label = "Meter Number",
                value = meter.uid,
                isHighlighted = true
            )
            SpecificationRow(label = "Serial Number", value = meter.serialNo)
            SpecificationRow(label = "Location", value = "1st Floor")
            SpecificationRow(label = "Protocol", value = "IEC 62056")
            SpecificationRow(label = "Meter Model", value = "F5LWF")
            SpecificationRow(label = "Meter Type", value = "1 phase 2 wire")
            SpecificationRow(label = "V/A", value = "200V/120A")
            SpecificationRow(label = "Frequency", value = "50Hz")
        }
    }
}

/**
 * Connection status card
 */
@Composable
private fun ConnectionStatusCard(
    meter: Meter,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Cable,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Connection Status",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }

            SpecificationRow(
                label = "Status",
                value = "Connected",
                valueColor = Color(0xFF4CAF50)
            )
            SpecificationRow(label = "Signal Strength", value = "-65 dBm")
            SpecificationRow(label = "Last Communication", value = "2 minutes ago")
            SpecificationRow(label = "Last Billing Read", value = "30 days ago")
        }
    }
}

/**
 * Modern specification row component
 */
@Composable
private fun SpecificationRow(
    label: String,
    value: String,
    isHighlighted: Boolean = false,
    isSensitive: Boolean = false,
    valueColor: Color? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )

        Text(
            text = if (isSensitive) "••••••••••••••••" else value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.Medium,
            color = valueColor ?: if (isHighlighted) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * Helper composable for detail rows
 */
@Composable
private fun DetailRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f)
        )
    }
}