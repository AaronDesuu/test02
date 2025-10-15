package com.example.meterkenshin.ui.component

import android.annotation.SuppressLint
import android.util.Log
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.dp
import com.example.meterkenshin.R
import com.example.meterkenshin.printer.BluetoothPrinterManager
import com.example.meterkenshin.ui.viewmodel.PrinterBluetoothViewModel
import com.woosim.printer.WoosimCmd

// Data class for receipt printing
data class ReceiptData(
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
    val totalUse: Float,
    val genTransCharges: Float,
    val distributionCharges: Float,
    val sustainableCapex: Float,
    val otherCharges: Float,
    val universalCharges: Float,
    val valueAddedTax: Float,
    val totalAmount: Float
)

@Composable
fun ReceiptPrintButton(
    receiptData: ReceiptData,
    printerBluetoothViewModel: PrinterBluetoothViewModel,
    bluetoothConnectionState: BluetoothPrinterManager.ConnectionState?,
    isBluetoothEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    val isPrinterReady = bluetoothConnectionState == BluetoothPrinterManager.ConnectionState.CONNECTED
    val canPrint = isPrinterReady && isBluetoothEnabled

    Button(
        onClick = {
            if (canPrint) {
                printReceipt(receiptData, printerBluetoothViewModel)
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
                !isPrinterReady -> "Connect Printer"
                else -> "Print"
            },
            style = MaterialTheme.typography.bodySmall
        )
    }
}

// Print function that handles the actual printing with WoosimLib
@SuppressLint("DefaultLocale")
private fun printReceipt(
    receiptData: ReceiptData,
    printerBluetoothViewModel: PrinterBluetoothViewModel
) {
    val commands = mutableListOf<ByteArray>()

    // Initialize printer
    commands.add(WoosimCmd.initPrinter())
    commands.add(WoosimCmd.setTextAlign(WoosimCmd.ALIGN_CENTER))

    // Header - bold, centered
    commands.add(WoosimCmd.setTextStyle(true, false, false, 1, 1))
    commands.add("SAMPLE RECEIPT\n".toByteArray())

    // Company info - normal font, centered
    commands.add(WoosimCmd.setTextStyle(false, false, false, 1, 1))
    commands.add("H.V Dela Costa St Salcedo Village Makati 1227,\n".toByteArray())
    commands.add("Metro Manila Philippines\n".toByteArray())

    // Company name - bold, centered
    commands.add(WoosimCmd.setTextStyle(true, false, false, 1, 1))
    commands.add("Fuji Electric Sales Philippines Inc.\n".toByteArray())
    commands.add(WoosimCmd.setTextStyle(false, false, false, 1, 1))
    commands.add("TEL:000-000-0000\n".toByteArray())

    // Switch to left alignment for billing data
    commands.add(WoosimCmd.setTextAlign(WoosimCmd.ALIGN_LEFT))

    // Long separator line
    commands.add("================================================\n".toByteArray())

    // Billing information section
    commands.add("Period    :${receiptData.period}     Rate Type : ${receiptData.commercial} COMMERCIAL\n".toByteArray())
    commands.add("Meter     :${receiptData.serialID}               Multiplier: ${String.format("%.1f", receiptData.multiplier)}\n".toByteArray())
    commands.add("Period To :${receiptData.periodTo}               Pres Reading: ${String.format("%8.3f", receiptData.presReading)}\n".toByteArray())
    commands.add("Period From:${receiptData.periodFrom}              Prev Reading: ${String.format("%8.3f", receiptData.prevReading)}\n".toByteArray())
    commands.add("Demand KW :${String.format("%.3f", receiptData.maxDemand)}                Total KWH Used: ${String.format("%8.3f", receiptData.totalUse)}\n".toByteArray())

    commands.add("================================================\n".toByteArray())

    // Charges header - bold
    commands.add(WoosimCmd.setTextStyle(true, false, false, 1, 1))
    commands.add("CHARGES                         RATE        AMOUNT\n".toByteArray())

    // Gen/Trans section - bold header, normal details
    commands.add("GEN/TRANS CHARGES\n".toByteArray())
    commands.add(WoosimCmd.setTextStyle(false, false, false, 1, 1))
    commands.add("  Generation System Charge      7.2467   ${String.format("%8.2f", receiptData.genTransCharges)}\n".toByteArray())

    // Distribution section
    commands.add(WoosimCmd.setTextStyle(true, false, false, 1, 1))
    commands.add("DISTRIBUTION CHARGES\n".toByteArray())
    commands.add(WoosimCmd.setTextStyle(false, false, false, 1, 1))
    commands.add("  Distribution Demand Charge            ${String.format("%8.2f", receiptData.distributionCharges)}\n".toByteArray())

    // Sustainable CAPEX section
    commands.add(WoosimCmd.setTextStyle(true, false, false, 1, 1))
    commands.add("REINVESTMENT FUND FOR SUSTAINABLE CAPEX\n".toByteArray())
    commands.add(WoosimCmd.setTextStyle(false, false, false, 1, 1))
    commands.add("  Reinvestment Fund for CAPEX           ${String.format("%8.2f", receiptData.sustainableCapex)}\n".toByteArray())

    // Other charges section
    commands.add(WoosimCmd.setTextStyle(true, false, false, 1, 1))
    commands.add("OTHER CHARGES\n".toByteArray())
    commands.add(WoosimCmd.setTextStyle(false, false, false, 1, 1))
    commands.add("  Other Charges                         ${String.format("%8.2f", receiptData.otherCharges)}\n".toByteArray())

    // Universal charges section
    commands.add(WoosimCmd.setTextStyle(true, false, false, 1, 1))
    commands.add("UNIVERSAL CHARGES\n".toByteArray())
    commands.add(WoosimCmd.setTextStyle(false, false, false, 1, 1))
    commands.add("  Universal Charges                     ${String.format("%8.2f", receiptData.universalCharges)}\n".toByteArray())

    // VAT section
    commands.add(WoosimCmd.setTextStyle(true, false, false, 1, 1))
    commands.add("VALUE ADDED TAX\n".toByteArray())
    commands.add(WoosimCmd.setTextStyle(false, false, false, 1, 1))
    commands.add("  VAT                                   ${String.format("%8.2f", receiptData.valueAddedTax)}\n".toByteArray())

    commands.add("================================================\n".toByteArray())

    // Total amount - bold, large
    commands.add(WoosimCmd.setTextStyle(true, false, false, 1, 1))
    commands.add("AMOUNT DUE:                   ${String.format("%12.2f", receiptData.totalAmount)}\n".toByteArray())
    commands.add("================================================\n".toByteArray())

    // Footer notes
    commands.add(WoosimCmd.setTextStyle(false, false, false, 1, 1))
    commands.add("NOTE:Please pay this electric bill on or before DUE DATE\n".toByteArray())
    commands.add("Payment of this bill does not mean payment of previous\n".toByteArray())
    commands.add("delinquencies if any.\n\n".toByteArray())

    // Centered footer
    commands.add(WoosimCmd.setTextAlign(WoosimCmd.ALIGN_CENTER))
    commands.add("**PLEASE PRESENT THIS STATEMENT UPON PAYMENT**\n".toByteArray())
    commands.add("Reader:${receiptData.reader}\n".toByteArray())
    commands.add("Version: ${receiptData.version}\n".toByteArray())

    // Print and cut
    commands.add(WoosimCmd.printData())
    commands.add(WoosimCmd.printLineFeed(3))
    commands.add(WoosimCmd.cutPaper(WoosimCmd.CUT_FULL))

    // Send all commands to printer with small delays
    commands.forEach { command ->
        printerBluetoothViewModel.sendDataToPrinter(command)
        Thread.sleep(10) // Small delay for thermal printer processing
    }

    Log.d("ReceiptPrinter", "Receipt sent to printer with WoosimLib commands")
}

// Helper function to create sample receipt data with random numbers
fun createSampleReceiptData(
    period: String = "December 2024",
    serialID: String = "12345678",
    reader: String = "Fuji Taro"
): ReceiptData {
    // Generate realistic values similar to the project01 receipt
    val prevReading = (800..1200).random().toFloat()
    val presReading = prevReading + (300..700).random().toFloat()
    val maxDemand = (30..80).random().toFloat()
    val totalUse = presReading - prevReading

    // Use rates similar to project01 for realistic calculations
    val rates = floatArrayOf(
        7.2467f, 273.58f, 1.089f,      // Gen/Trans charges (0-2)
        219.80f, 42.92f, 35.94f,       // Distribution charges (3-5)
        0.2504f, 0.1632f,              // Sustainable CAPEX (6-7)
        -0.0020f, 0.0002f,             // Other charges (8-9)
        0.1805f, 0.12f, 0.0010f, 0.0636f, 0.0000f, 0.0896f,  // Universal charges (10-15)
        0.0000f, 0.0428f, 0.0000f, 0.15f, 0.0000f  // VAT (16-20)
    )

    // Calculate charges using the same formula as your existing calculation
    val genTransCharges = totalUse * rates[0] + maxDemand * rates[1] + totalUse * rates[2]
    val distributionCharges = maxDemand * rates[3] + 1 * rates[4] + 1 * rates[5]
    val sustainableCapex = totalUse * rates[6] + totalUse * rates[7]
    val otherCharges = totalUse * rates[8] + totalUse * rates[9]
    val universalCharges = totalUse * rates[10] + sustainableCapex * rates[11] +
            totalUse * rates[12] + totalUse * rates[13] +
            totalUse * rates[14] + totalUse * rates[15]
    val valueAddedTax = totalUse * rates[16] + totalUse * rates[17] + totalUse * rates[18] +
            distributionCharges * rates[19] + otherCharges * rates[20]

    val totalAmount = genTransCharges + distributionCharges + sustainableCapex +
            otherCharges + universalCharges + valueAddedTax

    return ReceiptData(
        period = period,
        commercial = "LARGE",
        serialID = serialID,
        multiplier = 1.0f,
        periodFrom = "11/15/2024",
        periodTo = "12/15/2024",
        prevReading = prevReading,
        presReading = presReading,
        maxDemand = maxDemand,
        reader = reader,
        version = "v1.00.2",
        totalUse = totalUse,
        genTransCharges = genTransCharges,
        distributionCharges = distributionCharges,
        sustainableCapex = sustainableCapex,
        otherCharges = otherCharges,
        universalCharges = universalCharges,
        valueAddedTax = valueAddedTax,
        totalAmount = totalAmount
    )
}

// Helper function to convert BillingData and CalculatedBillingData to ReceiptData
fun createReceiptDataFromBilling(
    billingData: com.example.meterkenshin.ui.screen.BillingData,
    calculatedData: com.example.meterkenshin.ui.screen.CalculatedBillingData
): ReceiptData {
    return ReceiptData(
        period = billingData.period,
        commercial = billingData.commercial,
        serialID = billingData.serialID,
        multiplier = billingData.multiplier,
        periodFrom = billingData.periodFrom,
        periodTo = billingData.periodTo,
        prevReading = billingData.prevReading,
        presReading = billingData.presReading,
        maxDemand = billingData.maxDemand,
        reader = billingData.reader,
        version = billingData.version,
        totalUse = calculatedData.totalUse,
        genTransCharges = calculatedData.genTransCharges,
        distributionCharges = calculatedData.distributionCharges,
        sustainableCapex = calculatedData.sustainableCapex,
        otherCharges = calculatedData.otherCharges,
        universalCharges = calculatedData.universalCharges,
        valueAddedTax = calculatedData.valueAddedTax,
        totalAmount = calculatedData.totalAmount
    )
}