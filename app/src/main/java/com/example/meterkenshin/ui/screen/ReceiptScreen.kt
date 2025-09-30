package com.example.meterkenshin.ui.screen

import android.content.Context
import android.util.Log
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.meterkenshin.R
import com.example.meterkenshin.model.RequiredFile
import com.example.meterkenshin.ui.component.ReceiptPrintButton
import com.example.meterkenshin.ui.component.createReceiptDataFromBilling
import com.example.meterkenshin.ui.component.createSampleReceiptData
import com.example.meterkenshin.ui.viewmodel.PrinterBluetoothViewModel
import com.example.meterkenshin.ui.viewmodel.FileUploadViewModel
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptScreen(
    fileUploadViewModel: FileUploadViewModel = viewModel(),
    printerBluetoothViewModel: PrinterBluetoothViewModel = viewModel(),
    onBackPressed: () -> Unit = {},
    onNavigateToFileUpload: () -> Unit = {}
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    // Observe upload state from FileUploadViewModel
    val uploadState by fileUploadViewModel.uploadState.collectAsState()

    val bluetoothConnectionState by printerBluetoothViewModel.connectionState.collectAsState()
    val isBluetoothEnabled by printerBluetoothViewModel.isBluetoothEnabled.collectAsState()


    var rateData by remember { mutableStateOf<FloatArray?>(null) }
    var showRateDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Check if rate.csv is uploaded from the upload state
    val rateCsvFile = uploadState.requiredFiles.find { it.type == RequiredFile.FileType.RATE }
    val isRateCsvUploaded = rateCsvFile?.isUploaded == true

    // Sample billing data for preview
    val billingData = remember {
        BillingData(
            period = "December 2024",
            commercial = "LARGE",
            serialID = "12345678",
            multiplier = 1.0f,
            periodFrom = "11/15/2024",
            periodTo = "12/15/2024",
            prevReading = 1000.0f,
            presReading = 1500.0f,
            maxDemand = 50.0f,
            reader = "Fuji Taro",
            version = "v1.00.2"
        )
    }

    // Load rate data when rate.csv is uploaded
    LaunchedEffect(isRateCsvUploaded) {
        try {
            errorMessage = null
            if (isRateCsvUploaded && rateCsvFile != null) {
                val rates = loadRateDataFromFile(context, rateCsvFile.fileName)
                rateData = rates
                Log.d("Receipt", "Loaded ${rates?.size ?: 0} rate values from uploaded CSV")
            } else {
                // Use default rates if no file is uploaded
                rateData = getDefaultRates()
                Log.d("Receipt", "Using default rate values")
            }
        } catch (e: Exception) {
            errorMessage = "Error loading rate data: ${e.message}"
            rateData = getDefaultRates()
            Log.e("Receipt", "Error loading rate data", e)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colorResource(R.color.background_light))
    ) {


        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp)
                .padding(bottom = 32.dp), // Add extra bottom padding here
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Upload Rate CSV Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = colorResource(R.color.surface_light)
                ),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.UploadFile,
                            contentDescription = null,
                            tint = colorResource(R.color.primary_light),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.receipt_upload_rates),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )

                        // Rate CSV Status Indicator
                        RateCsvStatusIndicator(
                            isUploaded = isRateCsvUploaded,
                            modifier = Modifier
                        )
                    }

                    Text(
                        text = stringResource(R.string.receipt_upload_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colorResource(R.color.on_surface_variant_light)
                    )

                    Button(
                        onClick = {
                            onNavigateToFileUpload()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colorResource(R.color.primary_light)
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.FileUpload,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isRateCsvUploaded) {
                                stringResource(R.string.receipt_manage_rate_files)
                            } else {
                                stringResource(R.string.receipt_upload_rate_files)
                            }
                        )
                    }

                    if (isRateCsvUploaded) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = colorResource(R.color.success_container_light)
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = colorResource(R.color.success_light),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = stringResource(R.string.receipt_rate_csv_uploaded),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = colorResource(R.color.on_success_container_light)
                                    )
                                    rateData?.let { rates ->
                                        Text(
                                            text = stringResource(
                                                R.string.receipt_rates_loaded,
                                                rates.size
                                            ),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colorResource(R.color.on_success_container_light)
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = colorResource(R.color.warning_container_light)
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = colorResource(R.color.warning_light),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.receipt_rate_csv_missing),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colorResource(R.color.on_warning_container_light)
                                )
                            }
                        }
                    }

                    errorMessage?.let { error ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = colorResource(R.color.error_container_light)
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Error,
                                    contentDescription = null,
                                    tint = colorResource(R.color.error_light),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = error,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colorResource(R.color.on_error_container_light)
                                )
                            }
                        }
                    }
                }
            }

            // Rate Data Preview Section (only show when rate.csv is uploaded)
            if (isRateCsvUploaded && rateData != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = colorResource(R.color.surface_light)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.TableChart,
                                contentDescription = null,
                                tint = colorResource(R.color.primary_light),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.receipt_rate_data_preview),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )

                            TextButton(
                                onClick = { showRateDialog = true }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Visibility,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = stringResource(R.string.receipt_view_all_rates),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }

                        Text(
                            text = stringResource(R.string.receipt_rate_data_description),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colorResource(R.color.on_surface_variant_light)
                        )

                        // Rate Data Table Preview
                        RateDataPreviewTable(rateData = rateData!!)
                    }
                }
            }

            // Receipt Preview Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = colorResource(R.color.surface_light)
                ),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Receipt,
                            contentDescription = null,
                            tint = colorResource(R.color.secondary_light),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.receipt_preview),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.weight(1f))

                        ReceiptPrintButton(
                            receiptData = if (rateData != null) {
                                // Use calculated data when rate.csv is available
                                val calculatedData = calculateBillingData(billingData, rateData!!)
                                createReceiptDataFromBilling(billingData, calculatedData)
                            } else {
                                // Use sample data with random numbers when no rate.csv
                                createSampleReceiptData(
                                    period = billingData.period,
                                    serialID = billingData.serialID,
                                    reader = billingData.reader
                                )
                            },
                            printerBluetoothViewModel = printerBluetoothViewModel,
                            bluetoothConnectionState = bluetoothConnectionState,
                            isBluetoothEnabled = isBluetoothEnabled
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        // Show rate data button
                        rateData?.let { rates ->
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

                    // Receipt Preview Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = Color.White
                        ),
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            colorResource(R.color.outline_light)
                        )
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
    }

    // Rate Data Dialog
    if (showRateDialog && rateData != null) {
        RateDataDialog(
            rateData = rateData!!,
            onDismiss = { showRateDialog = false }
        )
    }
}

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
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Header
        Text(
            text = stringResource(R.string.receipt_sample_receipt),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.receipt_company_address),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Text(
            text = stringResource(R.string.receipt_company_name),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Text(
            text = stringResource(R.string.receipt_company_phone),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Divider(
            color = Color.Black,
            thickness = 1.dp,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        // Billing Information
        ReceiptLine(
            "Period",
            "${billingData.period}       Rate Type: ${billingData.commercial} COMMERCIAL"
        )
        ReceiptLine(
            "Meter",
            String.format("%-15s Multiplier: %.1f", billingData.serialID, billingData.multiplier)
        )
        ReceiptLine(
            "Period To",
            String.format(
                "%-15s Pres Reading: %8.3f",
                billingData.periodTo,
                billingData.presReading
            )
        )
        ReceiptLine(
            "Period From",
            String.format(
                "%-15s Prev Reading: %8.3f",
                billingData.periodFrom,
                billingData.prevReading
            )
        )
        ReceiptLine(
            "Demand KW",
            String.format(
                "%.3f                Total KWH Used: %8.3f",
                billingData.maxDemand,
                calculatedData.totalUse
            )
        )

        Divider(
            color = Color.Black,
            thickness = 1.dp,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        // Charges breakdown using actual rates
        ChargesSection(
            "GEN/TRANS CHARGES",
            calculatedData.genTransCharges,
            rates,
            calculatedData,
            0..2
        )
        ChargesSection(
            "DISTRIBUTION CHARGES",
            calculatedData.distributionCharges,
            rates,
            calculatedData,
            3..5
        )
        ChargesSection(
            "REINVESTMENT FUND FOR\nSUSTAINABLE CAPEX",
            calculatedData.sustainableCapex,
            rates,
            calculatedData,
            6..7
        )
        ChargesSection("OTHER CHARGES", calculatedData.otherCharges, rates, calculatedData, 8..9)
        ChargesSection(
            "UNIVERSAL CHARGES",
            calculatedData.universalCharges,
            rates,
            calculatedData,
            10..15
        )
        ChargesSection(
            "VALUE ADDED TAX",
            calculatedData.valueAddedTax,
            rates,
            calculatedData,
            16..20
        )

        Divider(
            color = Color.Black,
            thickness = 1.dp,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        // Total amounts
        ReceiptLine("CURRENT BILL", String.format("Php %,8.2f", calculatedData.totalAmount))
        Text(
            text = String.format(
                "TOTAL AMOUNT                       Php %,8.2f",
                calculatedData.totalAmount
            ),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )

        Divider(
            color = Color.Black,
            thickness = 1.dp,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        // Payment terms
        ReceiptLine("Discount", String.format("%8.2f", billingData.discount))
        ReceiptLine(
            "Amount Before Due",
            String.format("%8.2f", calculatedData.totalAmount - billingData.discount)
        )
        ReceiptLine("Interest", String.format("%8.2f", billingData.interest))
        ReceiptLine(
            "Amount After Due",
            String.format("%8.2f", calculatedData.totalAmount + billingData.interest)
        )

        Spacer(modifier = Modifier.height(8.dp))

        ReceiptLine("DUE DATE", getCurrentDate(1, 0))
        ReceiptLine("DISCO DATE", getCurrentDate(1, 1))

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.receipt_payment_note),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace
        )

        Text(
            text = stringResource(R.string.receipt_disclaimer),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.receipt_present_statement),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        ReceiptLine("Reader", "${billingData.reader}                   ${getCurrentDateTime()}")
        ReceiptLine("Version", billingData.version)
    }
}

@Composable
private fun ReceiptLine(label: String, value: String) {
    Text(
        text = String.format("%-12s: %s", label, value),
        style = MaterialTheme.typography.bodySmall,
        fontFamily = FontFamily.Monospace
    )
}

@Composable
private fun ChargesSection(
    title: String,
    subTotal: Float,
    rates: FloatArray,
    calculatedData: CalculatedBillingData,
    rateRange: IntRange
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace
    )

    // Add specific charge lines based on the section using actual rates
    when (title) {
        "GEN/TRANS CHARGES" -> {
            ChargeDetailLine(
                "Generation System Charge",
                rates[0],
                calculatedData.totalUse * rates[0],
                "/kwh"
            )
            ChargeDetailLine(
                "Transmission Demand Charge",
                rates[1],
                calculatedData.maxDemand * rates[1],
                "/kw"
            )
            ChargeDetailLine(
                "System Loss Charge",
                rates[2],
                calculatedData.totalUse * rates[2],
                "/kwh"
            )
        }

        "DISTRIBUTION CHARGES" -> {
            ChargeDetailLine(
                "Distribution Demand Charge",
                rates[3],
                calculatedData.maxDemand * rates[3],
                "/kw"
            )
            ChargeDetailLine("Supply Fix Charge", rates[4], 1 * rates[4], "/cst")
            ChargeDetailLine("Metering Fix Charge", rates[5], 1 * rates[5], "/cst")
        }

        "REINVESTMENT FUND FOR\nSUSTAINABLE CAPEX" -> {
            ChargeDetailLine(
                "Reinvestment Fund for CAPEX",
                rates[6],
                calculatedData.totalUse * rates[6],
                "/kwh"
            )
            ChargeDetailLine(
                "Member's CAPEX Contribution",
                rates[7],
                calculatedData.totalUse * rates[7],
                "/kwh"
            )
        }

        "OTHER CHARGES" -> {
            ChargeDetailLine(
                "Lifeline Discount/Subsidy",
                rates[8],
                calculatedData.totalUse * rates[8],
                "/kwh"
            )
            ChargeDetailLine(
                "Senior Citizen Subsidy",
                rates[9],
                calculatedData.totalUse * rates[9],
                "/kwh"
            )
        }

        "UNIVERSAL CHARGES" -> {
            ChargeDetailLine(
                "Missionary Elec(NPC-SPUG)",
                rates[10],
                calculatedData.totalUse * rates[10],
                "/kwh"
            )
            ChargeDetailLine(
                "Missionary Elec(RED)",
                rates[11],
                calculatedData.totalUse * rates[11],
                "/kwh"
            )
            ChargeDetailLine(
                "Environmental Charge",
                rates[12],
                calculatedData.totalUse * rates[12],
                "/kwh"
            )
            ChargeDetailLine(
                "Feed In Tariff Allowance",
                rates[13],
                calculatedData.totalUse * rates[13],
                "/kwh"
            )
            ChargeDetailLine(
                "NPC Stranded Contract",
                rates[14],
                calculatedData.totalUse * rates[14],
                "/kwh"
            )
            ChargeDetailLine(
                "NPC Stranded Debts",
                rates[15],
                calculatedData.totalUse * rates[15],
                "/kwh"
            )
        }

        "VALUE ADDED TAX" -> {
            ChargeDetailLine(
                "Generation VAT",
                rates[16],
                calculatedData.totalUse * rates[16],
                "/kwh"
            )
            ChargeDetailLine(
                "Transmission VAT",
                rates[17],
                calculatedData.totalUse * rates[17],
                "/kwh"
            )
            ChargeDetailLine(
                "System Loss VAT",
                rates[18],
                calculatedData.totalUse * rates[18],
                "/kwh"
            )
            ChargeDetailLine(
                "Distribution VAT",
                rates[19],
                calculatedData.distributionCharges * rates[19],
                "%%"
            )
            ChargeDetailLine("Other VAT", rates[20], calculatedData.otherCharges * rates[20], "%%")
        }
    }

    Text(
        text = String.format(
            "                                       SUB TOTAL        %,8.2f",
            subTotal
        ),
        style = MaterialTheme.typography.bodySmall,
        fontFamily = FontFamily.Monospace
    )
    Spacer(modifier = Modifier.height(4.dp))
}

@Composable
private fun ChargeDetailLine(name: String, rate: Float, amount: Float, unit: String) {
    Text(
        text = String.format("  %-30s: %8.4f%s        %,8.2f", name, rate, unit, amount),
        style = MaterialTheme.typography.bodySmall,
        fontFamily = FontFamily.Monospace
    )
}

@Composable
private fun RateDataPreviewTable(
    rateData: FloatArray,
    modifier: Modifier = Modifier
) {
    // Rate categories and their descriptions
    val rateCategories = listOf(
        RateCategory(
            "Generation/Transmission", 0..2, listOf(
                "Generation System Charge",
                "Transmission Demand Charge",
                "System Loss Charge"
            ), listOf("/kwh", "/kw", "/kwh")
        ),

        RateCategory(
            "Distribution", 3..5, listOf(
                "Distribution Demand Charge",
                "Supply Fix Charge",
                "Metering Fix Charge"
            ), listOf("/kw", "/cst", "/cst")
        ),

        RateCategory(
            "Sustainable CAPEX", 6..7, listOf(
                "Reinvestment Fund for CAPEX",
                "Member's CAPEX Contribution"
            ), listOf("/kwh", "/kwh")
        ),

        RateCategory(
            "Other Charges", 8..9, listOf(
                "Lifeline Discount/Subsidy",
                "Senior Citizen Subsidy"
            ), listOf("/kwh", "/kwh")
        ),

        RateCategory(
            "Universal Charges", 10..15, listOf(
                "Missionary Elec(NPC-SPUG)",
                "Missionary Elec(RED)",
                "Environmental Charge",
                "Feed In Tariff Allowance",
                "NPC Stranded Contract",
                "NPC Stranded Debts"
            ), listOf("/kwh", "/kwh", "/kwh", "/kwh", "/kwh", "/kwh")
        ),

        RateCategory(
            "Value Added Tax", 16..20, listOf(
                "Generation VAT",
                "Transmission VAT",
                "System Loss VAT",
                "Distribution VAT",
                "Other VAT"
            ), listOf("/kwh", "/kwh", "/kwh", "%%", "%%")
        )
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = colorResource(R.color.preview_card_background)
        ),
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            colorResource(R.color.outline_light)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Charge Type",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(2f)
                )
                Text(
                    text = "Rate",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "Unit",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(0.7f)
                )
            }

            Divider(
                color = colorResource(R.color.outline_light),
                thickness = 1.dp,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            // Rate categories
            rateCategories.forEach { category ->
                // Category header
                Text(
                    text = category.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = colorResource(R.color.primary_light),
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                // Rate items in this category
                category.range.forEachIndexed { index, rateIndex ->
                    if (rateIndex < rateData.size) {
                        RateDataRow(
                            name = category.items[index],
                            rate = rateData[rateIndex],
                            unit = category.units[index],
                            isIndented = true
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun RateDataRow(
    name: String,
    rate: Float,
    unit: String,
    isIndented: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (isIndented) "  $name" else name,
            style = MaterialTheme.typography.bodySmall,
            color = if (isIndented)
                colorResource(R.color.on_surface_variant_light)
            else
                colorResource(R.color.on_surface_light),
            modifier = Modifier.weight(2f)
        )

        Text(
            text = String.format("%.4f", rate),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f)
        )

        Text(
            text = unit,
            style = MaterialTheme.typography.bodySmall,
            color = colorResource(R.color.on_surface_variant_light),
            textAlign = TextAlign.End,
            modifier = Modifier.weight(0.7f)
        )
    }
}

data class RateCategory(
    val name: String,
    val range: IntRange,
    val items: List<String>,
    val units: List<String>
)

@Composable
private fun RateDataDialog(
    rateData: FloatArray,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = stringResource(R.string.receipt_rate_data_title))
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
                Text(stringResource(R.string.ok))
            }
        }
    )
}

@Composable
private fun RateCsvStatusIndicator(
    isUploaded: Boolean,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (isUploaded) {
        colorResource(R.color.success_container_light)
    } else {
        colorResource(R.color.warning_container_light)
    }

    val contentColor = if (isUploaded) {
        colorResource(R.color.success_light)
    } else {
        colorResource(R.color.warning_light)
    }

    val icon = if (isUploaded) {
        Icons.Default.CheckCircle
    } else {
        Icons.Default.Warning
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = backgroundColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(16.dp)
            )

            Spacer(modifier = Modifier.width(6.dp))

            Text(
                text = if (isUploaded) {
                    stringResource(R.string.receipt_rate_csv_status_uploaded)
                } else {
                    stringResource(R.string.receipt_rate_csv_status_missing)
                },
                style = MaterialTheme.typography.bodySmall,
                color = contentColor,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

// Data classes and helper functions
data class BillingData(
    val period: String,
    val commercial: String,
    val serialID: String,
    val multiplier: Float,
    val periodFrom: String,
    val periodTo: String,
    val prevReading: Float,
    val presReading: Float,
    val maxDemand: Float,
    val reader: String,
    val version: String,
    val discount: Float = 10.0f,
    val interest: Float = 10.0f
)

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
    val totalUse = billingData.presReading - billingData.prevReading

    // Calculate charges using the exact formula from project01
    val genTransCharges =
        totalUse * rates[0] + billingData.maxDemand * rates[1] + totalUse * rates[2]
    val distributionCharges = billingData.maxDemand * rates[3] + 1 * rates[4] + 1 * rates[5]
    val sustainableCapex = totalUse * rates[6] + totalUse * rates[7]
    val otherCharges = totalUse * rates[8] + totalUse * rates[9]
    val universalCharges =
        totalUse * rates[10] + sustainableCapex * rates[11] + totalUse * rates[12] +
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
        maxDemand = billingData.maxDemand
    )
}

private fun getDefaultRates(): FloatArray {
    return floatArrayOf(
        2.5f, 150.0f, 0.1f,     // Gen/Trans charges (0-2)
        50.0f, 100.0f, 50.0f,   // Distribution charges (3-5)
        0.05f, 0.03f,           // Sustainable CAPEX (6-7)
        0.02f, 0.01f,           // Other charges (8-9)
        0.001f, 0.12f, 0.0025f, 0.04f, 0.1f, 0.25f,  // Universal charges (10-15)
        0.3f, 0.3f, 0.012f, 0.12f, 0.12f  // VAT (16-20)
    )
}

// Load rate data from uploaded CSV file based on project01 parsing logic
private fun loadRateDataFromFile(context: Context, fileName: String): FloatArray? {
    return try {
        val externalFilesDir = context.getExternalFilesDir(null)
        val rateFile = File(externalFilesDir, fileName)

        if (!rateFile.exists()) {
            Log.w("Receipt", "Rate file does not exist: ${rateFile.absolutePath}")
            return null
        }

        Log.d("Receipt", "Loading rate data from: ${rateFile.absolutePath}")

        // Parse CSV file similar to project01 CSVParser logic
        val rates = mutableListOf<Float>()
        val reader = BufferedReader(FileReader(rateFile))

        var isFirstLine = true
        var columnHeaders: List<String>? = null

        reader.useLines { lines ->
            lines.forEach { line ->
                if (line.isNotBlank() && !line.startsWith("#")) {
                    if (isFirstLine) {
                        // First line might be headers
                        columnHeaders = line.split(",").map { it.trim() }
                        isFirstLine = false

                        // Check if first line contains headers or data
                        val firstCell = columnHeaders?.firstOrNull()?.trim()
                        if (firstCell != null) {
                            try {
                                // If we can parse the first cell as float, treat this line as data
                                val firstRate = firstCell.toFloat()
                                rates.add(firstRate)
                                // Continue parsing rest of the line
                                columnHeaders?.drop(1)?.forEach { cell ->
                                    try {
                                        rates.add(cell.toFloat())
                                    } catch (e: NumberFormatException) {
                                        Log.w("Receipt", "Skipping non-numeric value: $cell")
                                    }
                                }
                            } catch (e: NumberFormatException) {
                                // First line is headers, continue to next line
                                Log.d("Receipt", "Headers detected: $columnHeaders")
                            }
                        }
                    } else {
                        // Parse data line
                        val values = line.split(",").map { it.trim() }
                        values.forEach { cell ->
                            if (cell.isNotEmpty()) {
                                try {
                                    rates.add(cell.toFloat())
                                } catch (e: NumberFormatException) {
                                    Log.w("Receipt", "Skipping non-numeric value: $cell")
                                }
                            }
                        }
                    }
                }
            }
        }

        Log.d("Receipt", "Parsed ${rates.size} rate values: ${rates.take(5)}...")

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

private fun getCurrentDate(monthOffset: Int, dayOffset: Int): String {
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