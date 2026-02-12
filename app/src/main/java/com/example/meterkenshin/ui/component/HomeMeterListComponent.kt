package com.example.meterkenshin.ui.component

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.meterkenshin.R
import com.example.meterkenshin.model.Meter
import com.example.meterkenshin.data.RequiredFile
import com.example.meterkenshin.ui.component.card.MeterCard
import com.example.meterkenshin.ui.viewmodel.FileUploadViewModel
import com.example.meterkenshin.ui.viewmodel.MeterReadingViewModel
import com.example.meterkenshin.utils.getInspectionStatus

/**
 * Lightweight meter list specifically designed for HomeScreen
 * This component uses only Column (no scrollable components) to avoid nested scrolling issues
 * Shows latest 5 meters with the same ModernMeterCard design
 */
@Composable
fun HomeMeterList(
    fileUploadViewModel: FileUploadViewModel = viewModel(),
    meterReadingViewModel: MeterReadingViewModel = viewModel(),
    onMeterClick: (Meter) -> Unit = {},
    onViewAllClick: () -> Unit = {},
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by meterReadingViewModel.uiState.collectAsState()
    val uploadState by fileUploadViewModel.uploadState.collectAsState()

    // Check if meter.csv is uploaded
    val meterCsvFile = uploadState.requiredFiles.find { it.type == RequiredFile.FileType.METER }
    val isMeterCsvUploaded = meterCsvFile?.isUploaded == true

    // Load meters when CSV is available
    // Uses reloadMeters() to load from {YYYYMM}_meter.csv (which has DLMS-updated data)
    LaunchedEffect(isMeterCsvUploaded, Unit) {
        if (isMeterCsvUploaded) {
            meterReadingViewModel.reloadMeters(context)
        }
    }

    Column(modifier = modifier) {
        // Header with title and "View All" button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.recent_readings),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            TextButton(
                onClick = onViewAllClick,
                enabled = isMeterCsvUploaded && uiState.allMeters.isNotEmpty()
            ) {
                Text(stringResource(R.string.view_all_readings))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        when {
            // Check if meter file is uploaded
            !isMeterCsvUploaded -> {
                MeterFileNotUploadedCard()
            }

            // Loading state
            uiState.isLoading -> {
                LoadingCard()
            }

            // Error state
            uiState.errorMessage != null -> {
                ErrorCard(
                    message = uiState.errorMessage!!,
                    onRetry = { meterReadingViewModel.reloadMeters(context, forceReload = true) }
                )
            }

            // Empty state
            uiState.allMeters.isEmpty() -> {
                EmptyMetersCard()
            }

            // Success state with meters
            else -> {
                // Show top 5 meters, prioritizing those with readDate (most recent first)
                val metersToShow = uiState.filteredMeters
                    .sortedWith(compareByDescending(nullsFirst()) { it.readDate })
                    .take(5)

                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    metersToShow.forEach { meter ->
                        val isNearby = meterReadingViewModel.isMeterNearby(meter.bluetoothId ?: "")
                        val discoveredDevices by meterReadingViewModel.discoveredDevices.collectAsState()
                        val signalStrength = discoveredDevices[meter.bluetoothId?.uppercase() ?: ""]

                        MeterCard(
                            meter = meter,
                            onClick = { onMeterClick(meter) },
                            modifier = Modifier.fillMaxWidth(),
                            isNearby = isNearby,
                            inspectionStatus = getInspectionStatus(meter),
                            signalStrength = signalStrength
                        )
                    }
                }
            }
        }
    }
}