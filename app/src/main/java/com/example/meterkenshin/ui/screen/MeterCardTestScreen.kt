package com.example.meterkenshin.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.meterkenshin.model.Meter
import com.example.meterkenshin.ui.component.ModernMeterCard
import com.example.meterkenshin.ui.viewmodel.MeterTestViewModel

/**
 * Test Screen for ModernMeterCard Component
 * This screen allows you to test all possible states and variations of the ModernMeterCard
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeterCardTestScreen(
    testViewModel: MeterTestViewModel = viewModel()
) {
    val testMeters by testViewModel.testMeters.collectAsState()
    val isSimulating by testViewModel.isSimulating.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "ModernMeterCard Test",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (isSimulating) {
                        testViewModel.stopLiveSimulation()
                    } else {
                        testViewModel.startLiveSimulation()
                    }
                },
                containerColor = if (isSimulating) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                }
            ) {
                Icon(
                    imageVector = if (isSimulating) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = if (isSimulating) "Stop Simulation" else "Start Simulation"
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            // Test Controls Section
            TestControlsCard(
                isSimulating = isSimulating,
                onResetData = { testViewModel.resetTestData() },
                onGenerateStressTest = { testViewModel.generateStressTestMeters(20) }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Test Scenarios Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Test Scenarios (${testMeters.size} meters)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                if (isSimulating) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Live",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Meter Cards List
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(testMeters) { meter ->
                    TestMeterCardItem(
                        meter = meter,
                        onClick = { /* Handle click if needed */ }
                    )
                }
            }
        }
    }
}

/**
 * Test controls card with various testing options
 */
@Composable
private fun TestControlsCard(
    isSimulating: Boolean,
    onResetData: () -> Unit,
    onGenerateStressTest: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Test Controls",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onResetData,
                    modifier = Modifier.weight(1f),
                    enabled = !isSimulating
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Reset Data")
                }

                OutlinedButton(
                    onClick = onGenerateStressTest,
                    modifier = Modifier.weight(1f),
                    enabled = !isSimulating
                ) {
                    Text("Stress Test")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (isSimulating)
                    "Live simulation running - data updates every 3 seconds"
                else
                    "Click play button to start live data simulation",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * Individual test meter card item with scenario description
 */
@Composable
private fun TestMeterCardItem(
    meter: Meter,
    onClick: () -> Unit
) {
    Column {
        // Scenario description
        Text(
            text = getScenarioDescription(meter),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        // The actual ModernMeterCard being tested
        ModernMeterCard(
            meter = meter,
            onClick = onClick,
            showChevron = true,
            meterReadingViewModel = null // Test without ViewModel
        )
    }
}

/**
 * Get scenario description based on meter properties
 */
private fun getScenarioDescription(meter: Meter): String {
    return when (meter.id) {
        "1" -> "✅ Scenario 1: Perfect meter - Active (activate=1), has readings (impKWh>0), no alerts (alert=0) → GREEN"
        "2" -> "⚠️ Scenario 2: Active with minor alerts - Has readings but alert=1.5 → ORANGE"
        "3" -> "❌ Scenario 3: Offline meter - activate=0 → RED (Not Inspected)"
        "4" -> "⚠️ Scenario 4: Low voltage issue - minVoltV=195.5 (below 200V) → ORANGE"
        "5" -> "⚠️ Scenario 5: Critical alerts - alert=3.2 (above 2.0) → ORANGE"
        "6" -> "✅ Scenario 6: High consumption - Active, no alerts, good readings → GREEN"
        "7" -> "❌ Scenario 7: New installation - No maintenance history, impKWh=0 → RED"
        "8" -> "✅ Scenario 8: Solar/Export meter - Active, no alerts, good data → GREEN"
        else -> "🧪 Stress test meter - ID: ${meter.id}, Serial: ${meter.serialNumber}"
    }
}

