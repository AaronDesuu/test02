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
import com.example.meterkenshin.ui.manager.SessionManager
import com.example.meterkenshin.ui.viewmodel.FileUploadViewModel
import java.io.BufferedReader
import java.io.FileReader

/**
 * Holds rate data parsed from rate.csv: the numeric rates and the rate type string.
 */
data class RateData(
    val rates: FloatArray,
    val rateType: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RateData) return false
        return rates.contentEquals(other.rates) && rateType == other.rateType
    }

    override fun hashCode(): Int {
        return 31 * rates.contentHashCode() + rateType.hashCode()
    }
}

private const val DEFAULT_RATE_TYPE = "LARGE"

fun loadMeterRates(
    context: Context,
    fileUploadViewModel: FileUploadViewModel
): RateData {
    val uploadState = fileUploadViewModel.uploadState.value
    val rateCsvFile = uploadState.requiredFiles.find { it.type == RequiredFile.FileType.RATE }

    if (rateCsvFile?.isUploaded == true) {
        try {
            // Get user-specific rate file
            val sessionManager = SessionManager.getInstance(context)
            val rateFile = UserFileManager.getRateFile(context, sessionManager)

            if (rateFile.exists()) {
                Log.d("MeterDetail", "Loading rates from: ${rateFile.absolutePath}")

                val rates = mutableListOf<Float>()
                var rateType = DEFAULT_RATE_TYPE
                val reader = BufferedReader(FileReader(rateFile))

                var isFirstLine = true
                reader.useLines { lines ->
                    lines.forEach { line ->
                        if (line.isNotBlank() && !line.startsWith("#")) {
                            if (isFirstLine) {
                                // First line is headers — skip it
                                isFirstLine = false
                            } else {
                                // Data row: first column is Rate Type string, rest are numeric rates
                                val cells = line.split(",")
                                if (cells.isNotEmpty()) {
                                    // Extract Rate Type from first column (e.g., "LARGE COMMERCIAL")
                                    val firstCell = cells[0].trim()
                                    if (firstCell.toFloatOrNull() != null) {
                                        // No rate type column — all numeric
                                        rates.add(firstCell.toFloat())
                                    } else {
                                        // First cell is the rate type string
                                        rateType = firstCell.substringBefore(" ").uppercase()
                                    }
                                    // Parse remaining cells as numeric rates
                                    cells.drop(1).forEach { cell ->
                                        rates.add(cell.trim().toFloatOrNull() ?: 0f)
                                    }
                                }
                            }
                        }
                    }
                }

                Log.d("MeterDetail", "Parsed ${rates.size} rate values from CSV, rateType=$rateType")

                if (rates.size >= 23) {
                    return RateData(rates.take(23).toFloatArray(), rateType)
                }
            }
        } catch (e: Exception) {
            Log.e("MeterDetail", "Error loading rates: ${e.message}", e)
        }
    }

    Log.i("MeterDetail", "Using default rates")
    return RateData(getDefaultRates(), DEFAULT_RATE_TYPE)
}

@SuppressLint("DefaultLocale")
@Composable
fun RateDataDialog(
    rateData: FloatArray,
    onDismiss: () -> Unit
) {
    val rateLabels = listOf(
        "Gen/Trans: Generation System" to 0,
        "Gen/Trans: Transmission Demand" to 1,
        "Gen/Trans: System Loss" to 2,
        "Distribution: Demand Charge" to 3,
        "Distribution: Supply Fix" to 4,
        "Distribution: Metering Fix" to 5,
        "Sustainable CAPEX: Reinvestment" to 6,
        "Sustainable CAPEX: Member CAPEX" to 7,
        "Other: Lifeline Discount" to 8,
        "Other: Senior Citizen Subsidy" to 9,
        "Universal: Missionary(NPC-SPUG)" to 10,
        "Universal: Missionary(RED)" to 11,
        "Universal: Environmental" to 12,
        "Universal: Feed In Tariff" to 13,
        "Universal: NPC Stranded Contract" to 14,
        "Universal: NPC Stranded Debts" to 15,
        "VAT: Sustainable CAPEX" to 16,
        "VAT: Transmission" to 17,
        "VAT: Generation" to 18,
        "VAT: System Loss" to 19,
        "VAT: Universal Charges" to 20,
        "VAT: Distribution Multiplier" to 21,
        "VAT: Other Charges Multiplier" to 22
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
