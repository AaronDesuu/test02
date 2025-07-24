// app/src/main/java/com/example/meterkenshin/ui/screen/MeterReadingScreen.kt
package com.example.meterkenshin.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.meterkenshin.R
import com.example.meterkenshin.model.MeterData
import com.example.meterkenshin.model.MeterStatus
import com.example.meterkenshin.model.RequiredFile
import com.example.meterkenshin.ui.viewmodel.FileUploadViewModel
import com.example.meterkenshin.ui.viewmodel.MeterReadingViewModel

/**
 * Meter Reading Screen with updated UI for new data structure
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeterReadingScreen(
    fileUploadViewModel: FileUploadViewModel = viewModel(),
    meterReadingViewModel: MeterReadingViewModel = viewModel(),
    onBackPressed: () -> Unit = {},
    onNavigateToFileUpload: () -> Unit = {},
    onNavigateToMeterDetail: (MeterData) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Observe states
    val uploadState by fileUploadViewModel.uploadState.collectAsState()
    val meterUiState by meterReadingViewModel.uiState.collectAsState()
    val searchQuery by meterReadingViewModel.searchQuery.collectAsState()

    // Check if meter.csv is uploaded
    val meterCsvFile = uploadState.requiredFiles.find { it.type == RequiredFile.FileType.METER }
    val isMeterCsvUploaded = meterCsvFile?.isUploaded == true

    // Load meters when CSV is uploaded
    LaunchedEffect(isMeterCsvUploaded) {
        if (isMeterCsvUploaded && meterUiState.allMeters.isEmpty()) {
            meterReadingViewModel.loadMeters(context)
        }
    }

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // Top App Bar
        CenterAlignedTopAppBar(
            title = {
                Text(
                    text = stringResource(R.string.meter_reading_title),
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
                containerColor = MaterialTheme.colorScheme.surface,
                titleContentColor = MaterialTheme.colorScheme.onSurface
            )
        )

        // Main content
        if (!isMeterCsvUploaded) {
            // Show upload prompt when meter.csv is not uploaded
            MeterCsvUploadPrompt(
                onNavigateToFileUpload = onNavigateToFileUpload,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Show meter list when CSV is uploaded
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Statistics Card
                if (meterUiState.allMeters.isNotEmpty()) {
                    MeterStatisticsCard(
                        statistics = meterReadingViewModel.getMeterStatistics(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Search Bar
                SearchBar(
                    searchQuery = searchQuery,
                    onSearchQueryChange = meterReadingViewModel::updateSearchQuery,
                    modifier = Modifier.fillMaxWidth()
                )

                // Content based on state
                when {
                    meterUiState.isLoading -> {
                        LoadingContent(modifier = Modifier.fillMaxSize())
                    }

                    meterUiState.errorMessage != null -> {
                        ErrorCard(
                            errorMessage = meterUiState.errorMessage!!,
                            onRetry = { meterReadingViewModel.loadMeters(context) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    meterUiState.filteredMeters.isEmpty() && searchQuery.isNotBlank() -> {
                        EmptySearchResults(
                            searchQuery = searchQuery,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    meterUiState.filteredMeters.isEmpty() -> {
                        EmptyMeterList(modifier = Modifier.fillMaxWidth())
                    }

                    else -> {
                        // Meter List
                        MeterList(
                            meters = meterUiState.filteredMeters,
                            onMeterClick = onNavigateToMeterDetail,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}

/**
 * Upload prompt when meter.csv is not available
 */
@Composable
private fun MeterCsvUploadPrompt(
    onNavigateToFileUpload: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.FileUpload,
                    contentDescription = null,
                    modifier = Modifier.size(80.dp),
                    tint = MaterialTheme.colorScheme.primary
                )

                Text(
                    text = stringResource(R.string.meter_csv_required),
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = stringResource(R.string.meter_csv_required_description),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Button(
                    onClick = onNavigateToFileUpload,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.FileUpload,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.upload_files))
                }
            }
        }
    }
}

/**
 * Statistics card showing meter overview
 */
@Composable
private fun MeterStatisticsCard(
    statistics: com.example.meterkenshin.model.MeterStatistics,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.meter_overview),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            // First row: Basic counts
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatisticItem(
                    label = stringResource(R.string.total_meters),
                    value = statistics.totalCount.toString(),
                    color = MaterialTheme.colorScheme.primary
                )
                StatisticItem(
                    label = stringResource(R.string.active_meters),
                    value = statistics.activeCount.toString(),
                    color = colorResource(R.color.success_light)
                )
                StatisticItem(
                    label = stringResource(R.string.inactive_meters),
                    value = statistics.inactiveCount.toString(),
                    color = colorResource(R.color.outline_light)
                )
            }

            // Divider
            if (statistics.totalImportEnergy > 0 || statistics.totalExportEnergy > 0) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                // Second row: Energy totals
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatisticItem(
                        label = stringResource(R.string.total_imp_kwh),
                        value = String.format("%.1f", statistics.totalImportEnergy),
                        color = MaterialTheme.colorScheme.tertiary
                    )
                    StatisticItem(
                        label = stringResource(R.string.total_exp_kwh),
                        value = String.format("%.1f", statistics.totalExportEnergy),
                        color = MaterialTheme.colorScheme.secondary
                    )
                    StatisticItem(
                        label = stringResource(R.string.today_readings),
                        value = statistics.activeCount.toString(),
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
        }
    }
}

/**
 * Individual statistic item
 */
@Composable
private fun StatisticItem(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Search bar component
 */
@Composable
private fun SearchBar(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = searchQuery,
        onValueChange = onSearchQueryChange,
        modifier = modifier,
        placeholder = {
            Text(text = stringResource(R.string.search_meters_hint))
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingIcon = {
            if (searchQuery.isNotEmpty()) {
                IconButton(onClick = { onSearchQueryChange("") }) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = stringResource(R.string.clear_search),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        singleLine = true,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            focusedIndicatorColor = MaterialTheme.colorScheme.primary,
            unfocusedIndicatorColor = MaterialTheme.colorScheme.outline
        )
    )
}

/**
 * Loading content
 */
@Composable
private fun LoadingContent(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp),
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = stringResource(R.string.loading_meters),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Empty search results
 */
@Composable
private fun EmptySearchResults(
    searchQuery: String,
    modifier: Modifier = Modifier
) {
    EmptyStateCard(
        title = stringResource(R.string.no_search_results),
        description = stringResource(R.string.no_meters_found_for_query, searchQuery),
        icon = Icons.Default.Search,
        modifier = modifier
    )
}

/**
 * Empty meter list
 */
@Composable
private fun EmptyMeterList(
    modifier: Modifier = Modifier
) {
    EmptyStateCard(
        title = stringResource(R.string.no_meters_found),
        description = stringResource(R.string.meter_csv_empty),
        icon = Icons.Default.Assessment,
        modifier = modifier
    )
}

/**
 * Meter list component
 */
@Composable
private fun MeterList(
    meters: List<MeterData>,
    onMeterClick: (MeterData) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(
            items = meters,
            key = { it.uid }
        ) { meter ->
            MeterCard(
                meter = meter,
                onClick = { onMeterClick(meter) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * Individual meter card
 */
@Composable
private fun MeterCard(
    meter: MeterData,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .clickable { onClick() }
            .fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Status indicator
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(getStatusColor(meter.status))
            )

            Spacer(modifier = Modifier.width(16.dp))

            // Meter info
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = meter.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = meter.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Energy readings row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Text(
                        text = stringResource(R.string.imp_kwh_format, meter.formattedImpKwh),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = if (meter.hasReadingData)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (meter.expKwh != null) {
                        Text(
                            text = stringResource(R.string.exp_kwh_format, meter.formattedExpKwh),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }

                // Read date if available
                if (!meter.readDate.isNullOrBlank()) {
                    Text(
                        text = stringResource(R.string.read_date_format, meter.readDate),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Status badge
            StatusBadge(
                status = meter.status,
                modifier = Modifier.padding(start = 12.dp)
            )
        }
    }
}

/**
 * Status badge for meter
 */
@Composable
private fun StatusBadge(
    status: MeterStatus,
    modifier: Modifier = Modifier
) {
    val backgroundColor = getStatusColor(status).copy(alpha = 0.12f)
    val textColor = getStatusColor(status)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(backgroundColor)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = status.displayName,
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * Get color for meter status
 */
@Composable
private fun getStatusColor(status: MeterStatus): Color {
    return when (status) {
        MeterStatus.ACTIVE -> colorResource(R.color.success_light)
        MeterStatus.INACTIVE -> colorResource(R.color.outline_light)
        MeterStatus.NO_DATA -> colorResource(R.color.warning_light)
        MeterStatus.ERROR -> colorResource(R.color.error_light)
    }
}

/**
 * Error card component
 */
@Composable
private fun ErrorCard(
    errorMessage: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = colorResource(R.color.error_container_light)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = colorResource(R.color.error_light),
                modifier = Modifier.size(40.dp)
            )

            Text(
                text = stringResource(R.string.error_loading_meters),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = colorResource(R.color.on_error_container_light),
                textAlign = TextAlign.Center
            )

            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = colorResource(R.color.on_error_container_light),
                textAlign = TextAlign.Center
            )

            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colorResource(R.color.error_light)
                )
            ) {
                Text(
                    text = stringResource(R.string.retry),
                    color = Color.White
                )
            }
        }
    }
}

/**
 * Empty state card
 */
@Composable
private fun EmptyStateCard(
    title: String,
    description: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Text(
                text = description,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}