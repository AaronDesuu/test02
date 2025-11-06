package com.example.meterkenshin.utils

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
