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
import java.io.File
import java.io.FileReader
import java.io.InputStreamReader

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
private const val EXPECTED_RATE_COUNT = 23

private val EXPECTED_HEADERS = listOf(
    "Rate Type", "Generation System Charge", "Transmission Demand Charge",
    "System Loss Charge", "Distribution Demand Charge", "Supply Fix Charge",
    "Metering Fix Charge", "Reinvestment Fund for CAPEX", "Member CAPEX Contribution",
    "Lifeline Discount Subsidy", "Senior Citizen Subsidy", "Missionary Elec(NPC-SPUG)",
    "Missionary Elec(RED)", "Environmetal Charge", "Feed In Tariff Allowance",
    "NPC Stranded Contract", "NPC Stranded Debts", "Sustainable CAPEX VAT",
    "Transmisson VAT", "Generation VAT", "System Loss VAT", "Universal Charges VAT",
    "Distribution VAT", "Other VAT"
)

/**
 * Validation result for rate CSV files
 */
data class RateValidationResult(
    val isValid: Boolean,
    val errors: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
    val rateType: String? = null,
    val rateCount: Int = 0
) {
    fun summary(): String {
        val parts = mutableListOf<String>()
        if (errors.isNotEmpty()) parts.addAll(errors)
        if (warnings.isNotEmpty()) parts.addAll(warnings)
        return parts.joinToString("\n")
    }
}

/**
 * Validate a rate CSV file before upload.
 * Checks: header columns, data row count, rate type presence, numeric values, expected count of 23 rates.
 */
fun validateRateCsv(context: Context, uri: android.net.Uri): RateValidationResult {
    val errors = mutableListOf<String>()
    val warnings = mutableListOf<String>()
    var rateType: String? = null
    var rateCount = 0

    try {
        val reader = BufferedReader(InputStreamReader(context.contentResolver.openInputStream(uri)))
        val lines = reader.readLines().filter { it.isNotBlank() && !it.startsWith("#") }
        reader.close()

        if (lines.isEmpty()) {
            return RateValidationResult(false, listOf("File is empty"))
        }

        // Validate header row
        val headerCells = lines[0].split(",").map { it.trim() }
        val expectedColumnCount = EXPECTED_RATE_COUNT + 1 // +1 for Rate Type column

        if (headerCells.size < expectedColumnCount) {
            errors.add("Header has ${headerCells.size} columns, expected $expectedColumnCount")
        }

        // Check for missing headers
        val missingHeaders = EXPECTED_HEADERS.filter { expected ->
            headerCells.none { it.equals(expected, ignoreCase = true) }
        }
        if (missingHeaders.isNotEmpty()) {
            warnings.add("Missing headers: ${missingHeaders.joinToString(", ")}")
        }

        // Validate data rows
        val dataLines = lines.drop(1) // skip header
        if (dataLines.isEmpty()) {
            errors.add("No data rows found (only header)")
            return RateValidationResult(false, errors, warnings)
        }

        if (dataLines.size > 1) {
            warnings.add("Found ${dataLines.size} data rows, only the first row will be used")
        }

        // Validate first data row
        val dataCells = dataLines[0].split(",").map { it.trim() }

        // Check rate type column
        val firstCell = dataCells.getOrNull(0) ?: ""
        if (firstCell.toFloatOrNull() != null) {
            warnings.add("First column appears numeric ($firstCell), expected Rate Type text (e.g. 'LARGE COMMERCIAL')")
        } else if (firstCell.isBlank()) {
            errors.add("Rate Type column is empty")
        } else {
            rateType = firstCell.substringBefore(" ").uppercase()
        }

        // Parse and validate numeric rate values
        val rateValues = mutableListOf<Float>()
        val invalidCells = mutableListOf<String>()

        dataCells.drop(1).forEachIndexed { index, cell ->
            val value = cell.toFloatOrNull()
            if (value != null) {
                rateValues.add(value)
            } else {
                invalidCells.add("Column ${index + 2} ('$cell')")
            }
        }

        rateCount = rateValues.size

        if (invalidCells.isNotEmpty()) {
            errors.add("Non-numeric values: ${invalidCells.joinToString(", ")}")
        }

        if (rateCount < EXPECTED_RATE_COUNT) {
            errors.add("Found $rateCount rate values, expected $EXPECTED_RATE_COUNT")
        } else if (rateCount > EXPECTED_RATE_COUNT) {
            warnings.add("Found $rateCount rate values, expected $EXPECTED_RATE_COUNT (extra values will be ignored)")
        }

        // Validate VAT multipliers (rates[21] and rates[22]) should be between 0 and 1
        if (rateValues.size >= 23) {
            val distVat = rateValues[21]
            val otherVat = rateValues[22]
            if (distVat > 1f || distVat < 0f) {
                warnings.add("Distribution VAT multiplier ($distVat) should be between 0 and 1 (e.g. 0.12 for 12%)")
            }
            if (otherVat > 1f || otherVat < 0f) {
                warnings.add("Other VAT multiplier ($otherVat) should be between 0 and 1 (e.g. 0.12 for 12%)")
            }
        }

    } catch (e: Exception) {
        return RateValidationResult(false, listOf("Failed to read file: ${e.message}"))
    }

    return RateValidationResult(
        isValid = errors.isEmpty(),
        errors = errors,
        warnings = warnings,
        rateType = rateType,
        rateCount = rateCount
    )
}

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
