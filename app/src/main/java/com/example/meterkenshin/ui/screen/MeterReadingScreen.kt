package com.example.meterkenshin.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.meterkenshin.model.Meter
import com.example.meterkenshin.ui.component.MeterListComponent
import com.example.meterkenshin.ui.viewmodel.FileUploadViewModel
import com.example.meterkenshin.ui.viewmodel.MeterReadingViewModel

/**
 * Meter Reading Screen using reusable MeterListComponent
 * This demonstrates how to use the MeterListComponent in a full-screen context
 * with complete functionality including search, statistics, and full meter list
 */
@Composable
fun MeterReadingScreen(
    fileUploadViewModel: FileUploadViewModel = viewModel(),
    meterReadingViewModel: MeterReadingViewModel = viewModel(),
    onNavigateToMeterDetail: (Meter) -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Use the reusable MeterListComponent with full functionality
        MeterListComponent(
            fileUploadViewModel = fileUploadViewModel,
            meterReadingViewModel = meterReadingViewModel,
            onMeterClick = onNavigateToMeterDetail,
            showSearch = true,      // Show search bar for filtering meters
            showStatistics = true,  // Show statistics row (Total, Showing, Online)
            maxItemsToShow = null,  // Show all meters (no limit)
            modifier = Modifier.fillMaxSize(),
            customHeader = null,    // No custom header needed for full screen
            customEmptyState = null, // Use default empty states
        )
    }
}