package com.example.meterkenshin.utils

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.meterkenshin.data.getDefaultRates
import com.example.meterkenshin.data.RequiredFile
import com.example.meterkenshin.ui.viewmodel.FileUploadViewModel
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.lang.NumberFormatException

fun loadMeterRates(
    context: Context,
    fileUploadViewModel: FileUploadViewModel
): FloatArray {
    val uploadState = fileUploadViewModel.uploadState.value
    val rateCsvFile = uploadState.requiredFiles.find { it.type == RequiredFile.FileType.RATE }

    if (rateCsvFile?.isUploaded == true) {
        try {
            val externalFilesDir = context.getExternalFilesDir(null)
            if (externalFilesDir != null) {
                // ✅ FIX: Add app_files subdirectory
                val appFilesDir = File(externalFilesDir, "app_files")
                val rateFile = File(appFilesDir, rateCsvFile.fileName)

                if (rateFile.exists()) {
                    Log.d("MeterDetail", "Loading rates from: ${rateFile.absolutePath}")

                    val rates = mutableListOf<Float>()
                    val reader = BufferedReader(FileReader(rateFile))

                    var isFirstLine = true
                    reader.useLines { lines ->
                        lines.forEach { line ->
                            if (line.isNotBlank() && !line.startsWith("#")) {
                                if (isFirstLine) {
                                    val firstCell = line.split(",").firstOrNull()?.trim()
                                    if (firstCell != null) {
                                        try {
                                            rates.add(firstCell.toFloat())
                                            line.split(",").drop(1).forEach { cell ->
                                                rates.add(cell.trim().toFloatOrNull() ?: 0f)
                                            }
                                        } catch (e: NumberFormatException) {
                                            // First row is headers
                                        }
                                    }
                                    isFirstLine = false
                                } else {
                                    line.split(",").forEach { cell ->
                                        rates.add(cell.trim().toFloatOrNull() ?: 0f)
                                    }
                                }
                            }
                        }
                    }

                    Log.d("MeterDetail", "Parsed ${rates.size} rate values from CSV")

                    if (rates.size >= 21) {
                        return rates.take(21).toFloatArray()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("MeterDetail", "Error loading rates: ${e.message}", e)
        }
    }

    Log.i("MeterDetail", "Using default rates")
    return getDefaultRates()
}

@SuppressLint("DefaultLocale")
@Composable
fun RateDataDialog(
    rateData: FloatArray,
    onDismiss: () -> Unit
) {
    val rateLabels = listOf(
        "Gen/Trans: kWh Rate" to 0,
        "Gen/Trans: Demand Rate" to 1,
        "Gen/Trans: Additional kWh" to 2,
        "Distribution: Demand Rate" to 3,
        "Distribution: Fixed Charge 1" to 4,
        "Distribution: Fixed Charge 2" to 5,
        "Sustainable CAPEX: kWh Rate 1" to 6,
        "Sustainable CAPEX: kWh Rate 2" to 7,
        "Other Charges: kWh Rate 1" to 8,
        "Other Charges: kWh Rate 2" to 9,
        "Universal: kWh Base" to 10,
        "Universal: CAPEX Multiplier" to 11,
        "Universal: kWh Rate 1" to 12,
        "Universal: kWh Rate 2" to 13,
        "Universal: kWh Rate 3" to 14,
        "Universal: kWh Rate 4" to 15,
        "VAT: kWh Rate 1" to 16,
        "VAT: kWh Rate 2" to 17,
        "VAT: kWh Rate 3" to 18,
        "VAT: Distribution Multiplier" to 19,
        "VAT: Other Charges Multiplier" to 20
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "Rate Data Variables")
        },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(rateLabels.size) { index ->
                    val (label, rateIndex) = rateLabels[index]
                    Column {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = String.format("Rate[%d]: %.6f", rateIndex, rateData.getOrNull(rateIndex) ?: 0f),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
