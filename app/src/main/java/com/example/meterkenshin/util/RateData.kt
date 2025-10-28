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
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.lang.NumberFormatException

fun loadRateDataFromFile(context: Context, fileName: String): FloatArray? {
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
