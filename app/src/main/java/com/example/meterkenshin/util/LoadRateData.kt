package com.example.meterkenshin.util

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.example.meterkenshin.data.getDefaultRates
import com.example.meterkenshin.model.RequiredFile
import com.example.meterkenshin.ui.viewmodel.FileUploadViewModel
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.lang.NumberFormatException

fun loadRates(context: Context, fileName: String): FloatArray? {
    return try {
        val externalFilesDir = context.getExternalFilesDir(null)
        if (externalFilesDir == null) {
            Log.e("Receipt", "External files directory not available")
            return null
        }

        // File is in app_files subdirectory
        val appFilesDir = File(externalFilesDir, "app_files")
        val file = File(appFilesDir, fileName)

        if (!file.exists()) {
            Log.e("Receipt", "Rate file not found: ${file.absolutePath}")
            return null
        }

        val rates = mutableListOf<Float>()
        var columnHeaders: List<String>? = null

        BufferedReader(FileReader(file)).use { reader ->
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val trimmedLine = line?.trim() ?: continue
                if (trimmedLine.isEmpty()) continue

                if (columnHeaders == null) {
                    columnHeaders = trimmedLine.split(",").map { it.trim() }
                    val firstCell = columnHeaders!!.firstOrNull()?.trim()
                    if (firstCell != null) {
                        try {
                            val firstRate = firstCell.toFloat()
                            rates.add(firstRate)
                            columnHeaders!!
                                .drop(1)
                                .forEach { cell ->
                                    rates.add(cell.trim().toFloatOrNull() ?: 0f)
                                }
                        } catch (e: NumberFormatException) {
                            // First row is headers, skip
                        }
                    }
                } else {
                    trimmedLine.split(",").forEach { cell ->
                        rates.add(cell.trim().toFloatOrNull() ?: 0f)
                    }
                }
            }
        }

        rates.toFloatArray()
    } catch (e: Exception) {
        Log.e("Receipt", "Error loading rate data from file", e)
        null
    }
}

fun loadMeterRates(
    context: Context,
    fileUploadViewModel: FileUploadViewModel
): FloatArray {
    val uploadState = fileUploadViewModel.uploadState.value
    val rateCsvFile = uploadState.requiredFiles.find { it.type == RequiredFile.FileType.RATE }

    // Check if rate.csv is uploaded
    if (rateCsvFile?.isUploaded == true) {
        try {
            // Get the uploaded file from external files directory
            val externalFilesDir = context.getExternalFilesDir(null)
            if (externalFilesDir != null) {
                val rateFile = File(externalFilesDir, rateCsvFile.fileName)

                if (rateFile.exists()) {
                    Log.d("MeterDetail", "Loading rates from: ${rateFile.absolutePath}")

                    // Parse CSV file
                    val rates = mutableListOf<Float>()
                    val reader = BufferedReader(FileReader(rateFile))

                    var isFirstLine = true
                    reader.useLines { lines ->
                        lines.forEach { line ->
                            if (line.isNotBlank() && !line.startsWith("#")) {
                                if (isFirstLine) {
                                    // First line might be headers, try to parse it
                                    val firstCell = line.split(",").firstOrNull()?.trim()
                                    if (firstCell != null) {
                                        try {
                                            // If we can parse as float, it's data not headers
                                            rates.add(firstCell.toFloat())
                                            // Parse rest of first line
                                            line.split(",").drop(1).forEach { cell ->
                                                try {
                                                    rates.add(cell.trim().toFloat())
                                                } catch (_: NumberFormatException) {
                                                    // Skip non-numeric values
                                                }
                                            }
                                        } catch (_: NumberFormatException) {
                                            // First line is headers, skip to next line
                                            Log.d("MeterDetail", "Headers detected, skipping")
                                        }
                                    }
                                    isFirstLine = false
                                } else {
                                    // Parse data line
                                    line.split(",").forEach { cell ->
                                        val trimmed = cell.trim()
                                        if (trimmed.isNotEmpty()) {
                                            try {
                                                rates.add(trimmed.toFloat())
                                            } catch (_: NumberFormatException) {
                                                // Skip non-numeric values
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Log.d("MeterDetail", "Parsed ${rates.size} rate values from CSV")

                    if (rates.size >= 21) {
                        return rates.take(21).toFloatArray()
                    } else {
                        Log.w("MeterDetail", "Insufficient rates in CSV (${rates.size}), using defaults")
                        return getDefaultRates()
                    }
                } else {
                    Log.w("MeterDetail", "Rate file not found: ${rateFile.absolutePath}")
                    return getDefaultRates()
                }
            } else {
                Log.e("MeterDetail", "External files directory not available")
                return getDefaultRates()
            }
        } catch (e: Exception) {
            Log.e("MeterDetail", "Error loading rates from CSV: ${e.message}", e)
            return getDefaultRates()
        }
    } else {
        Log.i("MeterDetail", "rate.csv not uploaded, using default rates")
        return getDefaultRates()
    }
}


@SuppressLint("DefaultLocale")
@Composable
fun RateDataDialog(
    rateData: FloatArray,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "Rate Data")
        },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(rateData.size) { index ->
                    Text(
                        text = String.format("Rate[%d]: %.6f", index, rateData[index]),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("OK")
            }
        }
    )
}
