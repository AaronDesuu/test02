package com.example.meterkenshin.ui.screen

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.meterkenshin.R
import com.example.meterkenshin.model.Billing
import com.example.meterkenshin.data.RequiredFile
import com.example.meterkenshin.printer.BluetoothPrinterManager
import com.example.meterkenshin.ui.component.ReceiptData
import com.example.meterkenshin.ui.component.ReceiptPreview
import com.example.meterkenshin.ui.component.createReceiptDataFromBilling
import com.example.meterkenshin.ui.component.createSampleReceiptData
import com.example.meterkenshin.ui.component.printReceipt
import com.example.meterkenshin.ui.viewmodel.FileUploadViewModel
import com.example.meterkenshin.ui.viewmodel.PrinterBluetoothViewModel
import com.example.meterkenshin.utils.RateDataDialog
import com.example.meterkenshin.utils.calculateBillingData
import com.example.meterkenshin.utils.loadMeterRates

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
        Billing().apply {
            Period = "December 2024"
            Commercial = "LARGE"
            SerialNumber = "12345678"
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
                val rates = loadMeterRates(context, fileUploadViewModel)
                rateData = rates
                Log.d("Receipt", "Loaded ${rates.size} rates from ${rateCsvFile?.fileName}")
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

                if (isRateCsvUploaded && rateData != null) {
                    OutlinedButton(
                        onClick = {
                            Log.d("Receipt", "View Rates button clicked, showRateDialog = true")
                            showRateDialog = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFF1976D2)
                        ),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Visibility,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "View Rate Data",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                OutlinedButton(
                    onClick = onNavigateToFileUpload,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1976D2)
                    )
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
                            calculateBillingData(billingData, rateData!!)  // Populates billing fields
                            createReceiptDataFromBilling(billingData)  // No calculatedData needed
                        } else {
                            createSampleReceiptData(
                                period = billingData.Period ?: "December 2024",
                                serialNumber = billingData.SerialNumber ?: "12345678",
                                reader = billingData.Reader ?: "Fuji Taro"
                            )
                        },
                        printerBluetoothViewModel = printerBluetoothViewModel,
                        bluetoothConnectionState = bluetoothConnectionState,
                        isBluetoothEnabled = isBluetoothEnabled
                    )
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

@Composable
fun ReceiptPrintButton(
    modifier: Modifier = Modifier,
    receiptData: ReceiptData,
    printerBluetoothViewModel: PrinterBluetoothViewModel,
    bluetoothConnectionState: BluetoothPrinterManager.ConnectionState?,
    isBluetoothEnabled: Boolean,
    onNavigateToHome: () -> Unit = {},
) {
    val isPrinterReady = bluetoothConnectionState == BluetoothPrinterManager.ConnectionState.CONNECTED
    val canPrint = isPrinterReady && isBluetoothEnabled

    Button(
        onClick = {
            if (canPrint) {
                printReceipt(receiptData, printerBluetoothViewModel)
            } else if (!isPrinterReady) {
                // Navigate to home when "Connect Printer" is clicked
                onNavigateToHome()
            }
        },
        enabled = canPrint,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (canPrint) colorResource(R.color.print_button_background)
            else colorResource(R.color.print_button_disabled),
            contentColor = colorResource(R.color.print_button_text)
        ),
        modifier = modifier.height(36.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Print,
            contentDescription = null,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = when {
                !isBluetoothEnabled -> "Enable BT"
                !isPrinterReady -> "Connect"
                else -> "Print"
            },
            style = MaterialTheme.typography.bodySmall
        )
    }
}
