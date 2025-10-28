package com.example.meterkenshin.ui.screen

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.meterkenshin.data.BillingData
import com.example.meterkenshin.data.getDefaultRates
import com.example.meterkenshin.model.RequiredFile
import com.example.meterkenshin.ui.component.ReceiptPrintButton
import com.example.meterkenshin.ui.component.createReceiptDataFromBilling
import com.example.meterkenshin.ui.component.createSampleReceiptData
import com.example.meterkenshin.ui.viewmodel.FileUploadViewModel
import com.example.meterkenshin.ui.viewmodel.PrinterBluetoothViewModel
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun ReceiptScreen(
    fileUploadViewModel: FileUploadViewModel = viewModel(),
    printerBluetoothViewModel: PrinterBluetoothViewModel = viewModel(),
    onNavigateToFileUpload: () -> Unit = {}
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    val uploadState by fileUploadViewModel.uploadState.collectAsState()
    val bluetoothConnectionState by printerBluetoothViewModel.connectionState.collectAsState()
    val isBluetoothEnabled by printerBluetoothViewModel.isBluetoothEnabled.collectAsState()

    var rateData by remember { mutableStateOf<FloatArray?>(null) }
    var showRateDialog by remember { mutableStateOf(false) }

    val rateCsvFile = uploadState.requiredFiles.find { it.type == RequiredFile.FileType.RATE }
    val isRateCsvUploaded = rateCsvFile?.isUploaded == true

    // Sample billing data - using BillingData from data package
    val billingData = remember {
        BillingData().apply {
            Period = "December 2024"
            Commercial = "LARGE"
            SerialID = "12345678"
            Multiplier = 1.0f
            PeriodFrom = "11/15/2024"
            PeriodTo = "12/15/2024"
            PrevReading = 1000.0f
            PresReading = 1500.0f
            MaxDemand = 50.0f
            Reader = "Fuji Taro"
            Version = "v1.00.2"
            Discount = 10.0f
            Interest = 10.0f
        }
    }

    LaunchedEffect(isRateCsvUploaded) {
        try {
            if (isRateCsvUploaded) {
                val rates = rateCsvFile?.let { loadRateDataFromFile(context, it.fileName) }
                rateData = rates
                Log.d("Receipt", "Loaded ${rates?.size ?: 0} rates from ${rateCsvFile?.fileName}")
            } else {
                rateData = null
                Log.d("Receipt", "No rate.csv uploaded, using default rates")
            }
        } catch (e: Exception) {
            Log.e("Receipt", "Failed to load rate data", e)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Upload Rate CSV Card (matching screenshot)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = Color.White
            ),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.FileUpload,
                            contentDescription = null,
                            tint = Color(0xFF1976D2),
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Upload Rate CSV",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                    }

                    if (isRateCsvUploaded) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color(0xFFE8F5E9)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF4CAF50),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Uploaded",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color(0xFF4CAF50),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                Text(
                    text = "Upload a CSV file containing billing rate data to preview how the receipt will be calculated with your custom rates.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray
                )

                Button(
                    onClick = onNavigateToFileUpload,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1976D2)
                    ),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FileUpload,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Manage Rate Files",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Receipt Preview Card (matching screenshot)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = Color.White
            ),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Receipt,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Receipt Preview",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    // Print Button with proper integration
                    ReceiptPrintButton(
                        receiptData = if (rateData != null) {
                            val calculatedData = calculateBillingData(billingData, rateData!!)
                            createReceiptDataFromBilling(billingData, calculatedData)
                        } else {
                            createSampleReceiptData(
                                period = billingData.Period ?: "December 2024",
                                serialID = billingData.SerialID ?: "12345678",
                                reader = billingData.Reader ?: "Fuji Taro"
                            )
                        },
                        printerBluetoothViewModel = printerBluetoothViewModel,
                        bluetoothConnectionState = bluetoothConnectionState,
                        isBluetoothEnabled = isBluetoothEnabled
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    rateData?.let {
                        TextButton(
                            onClick = { showRateDialog = true }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Visibility,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("View Rates", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                // Receipt Preview Content
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Color.LightGray)
                ) {
                    ReceiptPreview(
                        billingData = billingData,
                        rateData = rateData,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
    }

    if (showRateDialog && rateData != null) {
        RateDataDialog(
            rateData = rateData!!,
            onDismiss = { showRateDialog = false }
        )
    }
}

@SuppressLint("DefaultLocale")
@Composable
private fun ReceiptPreview(
    billingData: BillingData,
    rateData: FloatArray?,
    modifier: Modifier = Modifier
) {
    val rates = rateData ?: getDefaultRates()
    val calculatedData = calculateBillingData(billingData, rates)

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        // Header
        Text(
            text = "SAMPLE RECEIPT",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "H.V Dela Costa St Salcedo Village Makati 1227,",
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = "Metro Manila Philippines",
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = "Electric Philippines Inc.",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = "TEL:000-000-0000",
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 12.dp),
            thickness = 1.dp,
            color = Color.Black
        )

        // Billing Information
        ReceiptLine("Period", "${billingData.Period}       Rate Type: ${billingData.Commercial} COMMERCIAL")
        ReceiptLine("Meter", "${billingData.SerialID}       Multiplier: ${billingData.Multiplier ?: 1.0f}")
        ReceiptLine("Period To", "${billingData.PeriodTo}       Pres Reading: ${String.format("%.3f", billingData.PresReading ?: 0f)}")
        ReceiptLine("Period From", "${billingData.PeriodFrom}       Prev Reading: ${String.format("%.3f", billingData.PrevReading ?: 0f)}")
        ReceiptLine("Demand KW", "${String.format("%.3f", billingData.MaxDemand ?: 0f)}       Total KWH Used: ${String.format("%.3f", calculatedData.totalUse)}")

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 12.dp),
            thickness = 1.dp,
            color = Color.Black
        )

        // Charges breakdown
        ChargesSection("GEN/TRANS CHARGES", calculatedData.genTransCharges, rates, calculatedData)
        ChargesSection("DISTRIBUTION CHARGES", calculatedData.distributionCharges, rates, calculatedData)
        ChargesSection("REINVESTMENT FUND FOR\nSUSTAINABLE CAPEX", calculatedData.sustainableCapex, rates, calculatedData)
        ChargesSection("OTHER CHARGES", calculatedData.otherCharges, rates, calculatedData)
        ChargesSection("UNIVERSAL CHARGES", calculatedData.universalCharges, rates, calculatedData)
        ChargesSection("VALUE ADDED TAX", calculatedData.valueAddedTax, rates, calculatedData)

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 12.dp),
            thickness = 1.dp,
            color = Color.Black
        )

        // Total amounts
        ReceiptLine("CURRENT BILL", String.format("Php %,.2f", calculatedData.totalAmount))
        Text(
            text = String.format("TOTAL AMOUNT       Php %,.2f", calculatedData.totalAmount),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 12.dp),
            thickness = 1.dp,
            color = Color.Black
        )

        // Payment terms
        ReceiptLine("Discount", String.format("%.2f", billingData.Discount ?: 0f))
        ReceiptLine("Amount Before Due", String.format("%.2f", calculatedData.totalAmount - (billingData.Discount ?: 0f)))
        ReceiptLine("Interest", String.format("%.2f", billingData.Interest ?: 0f))
        ReceiptLine("Amount After Due", String.format("%.2f", calculatedData.totalAmount + (billingData.Interest ?: 0f)))

        Spacer(modifier = Modifier.height(8.dp))

        ReceiptLine("DUE DATE", getCurrentDate(1, 0))
        ReceiptLine("DISCO DATE", getCurrentDate(1, 1))

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Please pay on time to avoid disconnection.",
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = "This is a computer generated receipt.",
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "PRESENT THIS STATEMENT WHEN PAYING",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        ReceiptLine("Reader", "${billingData.Reader}       ${getCurrentDateTime()}")
        ReceiptLine("Version", billingData.Version ?: "v1.00.2")
    }
}

@Composable
private fun ReceiptLine(label: String, value: String) {
    Text(
        text = "$label : $value",
        style = MaterialTheme.typography.bodySmall,
        fontFamily = FontFamily.Monospace
    )
}

@SuppressLint("DefaultLocale")
@Composable
private fun ChargesSection(
    title: String,
    subTotal: Float,
    rates: FloatArray,
    calculatedData: CalculatedBillingData
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace
    )

    when (title) {
        "GEN/TRANS CHARGES" -> {
            ChargeDetailLine("Generation System Charge", rates[0], calculatedData.totalUse * rates[0], "/kwh")
            ChargeDetailLine("Transmission Demand Charge", rates[1], calculatedData.maxDemand * rates[1], "/kw")
            ChargeDetailLine("System Loss Charge", rates[2], calculatedData.totalUse * rates[2], "/kwh")
        }
        "DISTRIBUTION CHARGES" -> {
            ChargeDetailLine("Distribution Demand Charge", rates[3], calculatedData.maxDemand * rates[3], "/kw")
            ChargeDetailLine("Supply Fix Charge", rates[4], 1 * rates[4], "/cst")
            ChargeDetailLine("Metering Fix Charge", rates[5], 1 * rates[5], "/cst")
        }
        "REINVESTMENT FUND FOR\nSUSTAINABLE CAPEX" -> {
            ChargeDetailLine("Reinvestment Fund for CAPEX", rates[6], calculatedData.totalUse * rates[6], "/kwh")
            ChargeDetailLine("Member's CAPEX Contribution", rates[7], calculatedData.totalUse * rates[7], "/kwh")
        }
        "OTHER CHARGES" -> {
            ChargeDetailLine("Lifeline Discount/Subsidy", rates[8], calculatedData.totalUse * rates[8], "/kwh")
            ChargeDetailLine("Senior Citizen Subsidy", rates[9], calculatedData.totalUse * rates[9], "/kwh")
        }
        "UNIVERSAL CHARGES" -> {
            ChargeDetailLine("Missionary Elec(NPC-SPUG)", rates[10], calculatedData.totalUse * rates[10], "/kwh")
            ChargeDetailLine("Missionary Elec(RED)", rates[11], calculatedData.totalUse * rates[11], "/kwh")
            ChargeDetailLine("Environmental Charge", rates[12], calculatedData.totalUse * rates[12], "/kwh")
            ChargeDetailLine("Feed In Tariff Allowance", rates[13], calculatedData.totalUse * rates[13], "/kwh")
            ChargeDetailLine("NPC Stranded Contract", rates[14], calculatedData.totalUse * rates[14], "/kwh")
            ChargeDetailLine("NPC Stranded Debts", rates[15], calculatedData.totalUse * rates[15], "/kwh")
        }
        "VALUE ADDED TAX" -> {
            ChargeDetailLine("Generation VAT", rates[16], calculatedData.totalUse * rates[16], "/kwh")
            ChargeDetailLine("Transmission VAT", rates[17], calculatedData.totalUse * rates[17], "/kwh")
            ChargeDetailLine("System Loss VAT", rates[18], calculatedData.totalUse * rates[18], "/kwh")
            ChargeDetailLine("Distribution VAT", rates[19], calculatedData.distributionCharges * rates[19], "%%")
            ChargeDetailLine("Other VAT", rates[20], calculatedData.otherCharges * rates[20], "%%")
        }
    }

    Text(
        text = String.format("SUB TOTAL: %,.2f", subTotal),
        style = MaterialTheme.typography.bodySmall,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(start = 8.dp, top = 4.dp)
    )
    Spacer(modifier = Modifier.height(8.dp))
}

@SuppressLint("DefaultLocale")
@Composable
private fun ChargeDetailLine(name: String, rate: Float, amount: Float, unit: String) {
    Text(
        text = "  $name : ${String.format("%.4f", rate)}$unit        ${String.format("%,.2f", amount)}",
        style = MaterialTheme.typography.bodySmall,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier.padding(start = 8.dp)
    )
}

data class CalculatedBillingData(
    val totalUse: Float,
    val genTransCharges: Float,
    val distributionCharges: Float,
    val sustainableCapex: Float,
    val otherCharges: Float,
    val universalCharges: Float,
    val valueAddedTax: Float,
    val totalAmount: Float,
    val maxDemand: Float
)

private fun calculateBillingData(
    billingData: BillingData,
    rates: FloatArray
): CalculatedBillingData {
    val totalUse = (billingData.PresReading ?: 0f) - (billingData.PrevReading ?: 0f)
    val maxDemand = billingData.MaxDemand ?: 0f

    val genTransCharges = totalUse * rates[0] + maxDemand * rates[1] + totalUse * rates[2]
    val distributionCharges = maxDemand * rates[3] + 1 * rates[4] + 1 * rates[5]
    val sustainableCapex = totalUse * rates[6] + totalUse * rates[7]
    val otherCharges = totalUse * rates[8] + totalUse * rates[9]
    val universalCharges = totalUse * rates[10] + totalUse * rates[11] + totalUse * rates[12] +
            totalUse * rates[13] + totalUse * rates[14] + totalUse * rates[15]
    val valueAddedTax = totalUse * rates[16] + totalUse * rates[17] + totalUse * rates[18] +
            distributionCharges * rates[19] + otherCharges * rates[20]

    val totalAmount = genTransCharges + distributionCharges + sustainableCapex +
            otherCharges + universalCharges + valueAddedTax

    return CalculatedBillingData(
        totalUse = totalUse,
        genTransCharges = genTransCharges,
        distributionCharges = distributionCharges,
        sustainableCapex = sustainableCapex,
        otherCharges = otherCharges,
        universalCharges = universalCharges,
        valueAddedTax = valueAddedTax,
        totalAmount = totalAmount,
        maxDemand = maxDemand
    )
}

private fun loadRateDataFromFile(context: Context, fileName: String): FloatArray? {
    return try {
        val file = File(context.filesDir, fileName)
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
                            columnHeaders!!.drop(1).forEach { cell ->
                                try {
                                    rates.add(cell.toFloat())
                                } catch (_: NumberFormatException) {
                                    Log.w("Receipt", "Skipping non-numeric value: $cell")
                                }
                            }
                        } catch (_: NumberFormatException) {
                            Log.d("Receipt", "Headers detected: $columnHeaders")
                        }
                    }
                } else {
                    val values = trimmedLine.split(",").map { it.trim() }
                    values.forEach { cell ->
                        if (cell.isNotEmpty()) {
                            try {
                                rates.add(cell.toFloat())
                            } catch (_: NumberFormatException) {
                                Log.w("Receipt", "Skipping non-numeric value: $cell")
                            }
                        }
                    }
                }
            }
        }

        Log.d("Receipt", "Parsed ${rates.size} rate values")

        if (rates.size >= 21) {
            rates.take(21).toFloatArray()
        } else {
            Log.w("Receipt", "Expected 21 rates, got ${rates.size}. Padding with defaults.")
            val defaultRates = getDefaultRates()
            val result = FloatArray(21)
            for (i in 0 until 21) {
                result[i] = if (i < rates.size) rates[i] else defaultRates[i]
            }
            result
        }
    } catch (e: Exception) {
        Log.e("Receipt", "Error loading rate data from file", e)
        null
    }
}

@SuppressLint("DefaultLocale")
@Composable
private fun RateDataDialog(
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

private fun getCurrentDate(
    @Suppress("SameParameterValue") monthOffset: Int,
    dayOffset: Int
): String {
    val calendar = Calendar.getInstance()
    calendar.add(Calendar.MONTH, monthOffset)
    calendar.add(Calendar.DAY_OF_MONTH, dayOffset)
    val format = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    return format.format(calendar.time)
}

private fun getCurrentDateTime(): String {
    val format = SimpleDateFormat("EEE dd MMM yyyy HH:mm:ss", Locale.getDefault())
    return format.format(Date())
}