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

// Print function that handles the actual printing with WoosimLib
@SuppressLint("DefaultLocale")
private fun printReceipt(
    receiptData: ReceiptData,
    printerBluetoothViewModel: PrinterBluetoothViewModel
) {
    val commands = mutableListOf<ByteArray>()

    // Initialize printer
    commands.add(WoosimCmd.initPrinter())
    commands.add(WoosimCmd.PM_setPosition(0, 0))

    // Title 1 - SAMPLE RECEIPT (FONT_MEDIUM, bold)
    commands.add(WoosimCmd.setCodeTable(WoosimCmd.MCU_RX, WoosimCmd.CT_CP437, WoosimCmd.FONT_MEDIUM))
    commands.add(WoosimCmd.setTextStyle(true, false, false, 1, 1))
    commands.add("SAMPLE RECEIPT\n\n\n".toByteArray())

    // Title 2 - Address (FONT_MEDIUM, bold)
    commands.add(WoosimCmd.setCodeTable(WoosimCmd.MCU_RX, WoosimCmd.CT_CP437, WoosimCmd.FONT_MEDIUM))
    commands.add(WoosimCmd.setTextStyle(true, false, false, 1, 1))
    commands.add("           H.V Dela Costa St Salcedo Village Makati 1227,\n".toByteArray())
    commands.add("          Metro Manila Philippines\n".toByteArray())

    // Title 3 - Company Name (FONT_LARGE, bold, double height)
    commands.add(WoosimCmd.setCodeTable(WoosimCmd.MCU_RX, WoosimCmd.CT_CP437, WoosimCmd.FONT_LARGE))
    commands.add(WoosimCmd.setTextStyle(true, false, false, 1, 2))
    commands.add("        Fuji Electric Sales Philippines Inc.\n".toByteArray())

    // Title 4 - Phone (FONT_MEDIUM, normal)
    commands.add(WoosimCmd.setCodeTable(WoosimCmd.MCU_RX, WoosimCmd.CT_CP437, WoosimCmd.FONT_MEDIUM))
    commands.add(WoosimCmd.setTextStyle(false, false, false, 1, 1))
    commands.add("                TEL:000-000-0000\n".toByteArray())

    // Separator and billing info (FONT_MEDIUM)
    commands.add(WoosimCmd.setCodeTable(WoosimCmd.MCU_RX, WoosimCmd.CT_CP437, WoosimCmd.FONT_MEDIUM))
    commands.add("================================================================\n".toByteArray())
    commands.add(String.format("Period     :%s       Rate Type     : %s COMMERCIAL\n",
        receiptData.period, receiptData.commercial).toByteArray())
    commands.add(String.format("Meter      :%s       Multiplier    :%.1f\n",
        receiptData.serialID, receiptData.multiplier).toByteArray())
    commands.add(String.format("Period To  :%s                Pres Reading  : %6.3f\n",
        receiptData.periodTo, receiptData.presReading).toByteArray())
    commands.add(String.format("Period From:%s                Prev Reading  : %6.3f\n",
        receiptData.periodFrom, receiptData.prevReading).toByteArray())
    commands.add(String.format("Max Demand :%.2f                    Total Use     : %6.3f\n",
        receiptData.maxDemand, receiptData.totalUse).toByteArray())

    // GENERATION TRANSMISSION CHARGES (FONT_LARGE header)
    commands.add(WoosimCmd.setCodeTable(WoosimCmd.MCU_RX, WoosimCmd.CT_CP437, WoosimCmd.FONT_LARGE))
    commands.add(WoosimCmd.setTextStyle(true, false, false, 1, 1))
    commands.add("GENERATION TRANSMISSION CHARGES\n".toByteArray())

    // Details under Gen/Trans (FONT_MEDIUM)
    commands.add(WoosimCmd.setCodeTable(WoosimCmd.MCU_RX, WoosimCmd.CT_CP437, WoosimCmd.FONT_MEDIUM))
    commands.add(String.format("  Generation                                  %,8.2f\n",
        receiptData.genTransCharges * 0.6f).toByteArray())
    commands.add(String.format("  Transmission                                %,8.2f\n",
        receiptData.genTransCharges * 0.3f).toByteArray())
    commands.add(String.format("  System Loss                                 %,8.2f\n",
        receiptData.genTransCharges * 0.1f).toByteArray())
    commands.add("                                                ----------------\n".toByteArray())
    commands.add(String.format("                                       SUB TOTAL      %,6.2f\n\n",
        receiptData.genTransCharges).toByteArray())

    // DISTRIBUTION CHARGES (FONT_LARGE header)
    commands.add(WoosimCmd.setCodeTable(WoosimCmd.MCU_RX, WoosimCmd.CT_CP437, WoosimCmd.FONT_LARGE))
    commands.add(WoosimCmd.setTextStyle(true, false, false, 1, 1))
    commands.add("DISTRIBUTION CHARGES\n".toByteArray())

    // Details (FONT_MEDIUM)
    commands.add(WoosimCmd.setCodeTable(WoosimCmd.MCU_RX, WoosimCmd.CT_CP437, WoosimCmd.FONT_MEDIUM))
    commands.add(String.format("  Demand Charge                               %,8.2f\n",
        receiptData.distributionCharges * 0.7f).toByteArray())
    commands.add(String.format("  Distribution System Charge                  %,8.2f\n",
        receiptData.distributionCharges * 0.2f).toByteArray())
    commands.add(String.format("  Metering Fix Charge                         %,8.2f\n",
        receiptData.distributionCharges * 0.1f).toByteArray())
    commands.add("                                                ----------------\n".toByteArray())
    commands.add(String.format("                                       SUB TOTAL      %,6.2f\n\n",
        receiptData.distributionCharges).toByteArray())

    // REINVESTMENT FUND FOR SUSTAINABLE CAPEX (FONT_LARGE header)
    commands.add(WoosimCmd.setCodeTable(WoosimCmd.MCU_RX, WoosimCmd.CT_CP437, WoosimCmd.FONT_LARGE))
    commands.add(WoosimCmd.setTextStyle(true, false, false, 1, 1))
    commands.add("REINVESTMENT FUND FOR\n".toByteArray())
    commands.add("SUSTAINABLE CAPEX\n".toByteArray())

    // Details (FONT_MEDIUM)
    commands.add(WoosimCmd.setCodeTable(WoosimCmd.MCU_RX, WoosimCmd.CT_CP437, WoosimCmd.FONT_MEDIUM))
    commands.add(String.format("  Reinvestment Fund for CAPEX                 %,8.2f\n",
        receiptData.sustainableCapex * 0.6f).toByteArray())
    commands.add(String.format("  Member's CAPEX Contribution                 %,8.2f\n",
        receiptData.sustainableCapex * 0.4f).toByteArray())
    commands.add("                                                ----------------\n".toByteArray())
    commands.add(String.format("                                       SUB TOTAL        %,6.2f\n\n",
        receiptData.sustainableCapex).toByteArray())

    // OTHER CHARGES (FONT_LARGE header)
    commands.add(WoosimCmd.setCodeTable(WoosimCmd.MCU_RX, WoosimCmd.CT_CP437, WoosimCmd.FONT_LARGE))
    commands.add(WoosimCmd.setTextStyle(true, false, false, 1, 1))
    commands.add("OTHER CHARGES\n".toByteArray())

    // Details (FONT_MEDIUM)
    commands.add(WoosimCmd.setCodeTable(WoosimCmd.MCU_RX, WoosimCmd.CT_CP437, WoosimCmd.FONT_MEDIUM))
    commands.add(String.format("  Other Charges                               %,8.2f\n",
        receiptData.otherCharges).toByteArray())
    commands.add("                                                ----------------\n".toByteArray())
    commands.add(String.format("                                       SUB TOTAL        %,6.2f\n\n",
        receiptData.otherCharges).toByteArray())

    // UNIVERSAL CHARGES (FONT_LARGE header)
    commands.add(WoosimCmd.setCodeTable(WoosimCmd.MCU_RX, WoosimCmd.CT_CP437, WoosimCmd.FONT_LARGE))
    commands.add(WoosimCmd.setTextStyle(true, false, false, 1, 1))
    commands.add("UNIVERSAL CHARGES\n".toByteArray())

    // Details (FONT_MEDIUM)
    commands.add(WoosimCmd.setCodeTable(WoosimCmd.MCU_RX, WoosimCmd.CT_CP437, WoosimCmd.FONT_MEDIUM))
    commands.add(String.format("  Universal Charges                           %,8.2f\n",
        receiptData.universalCharges).toByteArray())
    commands.add("                                                ----------------\n".toByteArray())
    commands.add(String.format("                                       SUB TOTAL        %,6.2f\n\n",
        receiptData.universalCharges).toByteArray())

    // VALUE ADDED TAX (FONT_LARGE header)
    commands.add(WoosimCmd.setCodeTable(WoosimCmd.MCU_RX, WoosimCmd.CT_CP437, WoosimCmd.FONT_LARGE))
    commands.add(WoosimCmd.setTextStyle(true, false, false, 1, 1))
    commands.add("VALUE ADDED TAX\n".toByteArray())

    // Details (FONT_MEDIUM)
    commands.add(WoosimCmd.setCodeTable(WoosimCmd.MCU_RX, WoosimCmd.CT_CP437, WoosimCmd.FONT_MEDIUM))
    commands.add(String.format("  Generation VAT                              %,8.2f\n",
        receiptData.valueAddedTax * 0.3f).toByteArray())
    commands.add(String.format("  Transmission VAT                            %,8.2f\n",
        receiptData.valueAddedTax * 0.2f).toByteArray())
    commands.add(String.format("  System Loss VAT                             %,8.2f\n",
        receiptData.valueAddedTax * 0.2f).toByteArray())
    commands.add(String.format("  Distribution VAT                            %,8.2f\n",
        receiptData.valueAddedTax * 0.2f).toByteArray())
    commands.add(String.format("  Other VAT                                   %,8.2f\n",
        receiptData.valueAddedTax * 0.1f).toByteArray())
    commands.add("                                                ----------------\n".toByteArray())
    commands.add(String.format("                                       SUB TOTAL        %,6.2f\n\n",
        receiptData.valueAddedTax).toByteArray())

    commands.add("----------------------------------------------------------------\n".toByteArray())

    // TOTAL AMOUNT (FONT_LARGE, bold, double height)
    commands.add(WoosimCmd.setCodeTable(WoosimCmd.MCU_RX, WoosimCmd.CT_CP437, WoosimCmd.FONT_LARGE))
    commands.add(WoosimCmd.setTextStyle(true, false, false, 1, 2))
    commands.add(String.format("TOTAL AMOUNT                       Php %,6.2f\n",
        receiptData.totalAmount).toByteArray())

    commands.add(WoosimCmd.setCodeTable(WoosimCmd.MCU_RX, WoosimCmd.CT_CP437, WoosimCmd.FONT_MEDIUM))
    commands.add("================================================================\n".toByteArray())

    // Additional info (FONT_LARGE, bold)
    commands.add(WoosimCmd.setCodeTable(WoosimCmd.MCU_RX, WoosimCmd.CT_CP437, WoosimCmd.FONT_LARGE))
    commands.add(WoosimCmd.setTextStyle(true, false, false, 1, 1))
    commands.add("     DUE DATE     :Dec 31, 2024\n".toByteArray())
    commands.add("     DISCO DATE   :Jan 15, 2025\n\n".toByteArray())

    // Footer notes (FONT_SMALL)
    commands.add(WoosimCmd.setCodeTable(WoosimCmd.MCU_RX, WoosimCmd.CT_CP437, WoosimCmd.FONT_SMALL))
    commands.add("NOTE:Please pay this electric bill on or before DUE DATE otherwise,\n".toByteArray())
    commands.add("     we will be forced to discontinue serving your electric needs.\n\n".toByteArray())
    commands.add("This is not an Official Receipt.\n".toByteArray())
    commands.add("Payment of this bill does not mean \n".toByteArray())
    commands.add("payment of previous delinquencies if any.\n\n".toByteArray())
    commands.add("             **PLEASE PRESENT THIS STATEMENT UPON PAYMENT**\n".toByteArray())
    commands.add(String.format("Reader:%s\n\n", receiptData.reader).toByteArray())
    commands.add(String.format("Version : %s\n\n\n\n", receiptData.version).toByteArray())

    // Print and cut
    commands.add(WoosimCmd.PM_printStdMode())

    // Send all commands to printer with small delays
    commands.forEach { command ->
        printerBluetoothViewModel.sendDataToPrinter(command)
        Thread.sleep(10)
    }

    Log.d("ReceiptPrinter", "Receipt sent to printer with WoosimLib commands")
}

// Helper function to create sample receipt data with random numbers
fun createSampleReceiptData(
    period: String = "December 2024",
    serialID: String = "12345678",
    reader: String = "Fuji Taro"
): ReceiptData {
    val prevReading = (800..1200).random().toFloat()
    val presReading = prevReading + (300..700).random().toFloat()
    val maxDemand = (30..80).random().toFloat()
    val totalUse = presReading - prevReading

    val rates = floatArrayOf(
        7.2467f, 273.58f, 1.089f,
        219.80f, 42.92f, 35.94f,
        0.2504f, 0.1632f,
        -0.0020f, 0.0002f,
        0.1805f, 0.12f, 0.0010f, 0.0636f, 0.0000f, 0.0896f,
        0.0000f, 0.0428f, 0.0000f, 0.15f, 0.0000f
    )

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
        commercial = "Type A",
        serialID = serialID,
        multiplier = 1.0f,
        periodFrom = "11/01/2024",
        periodTo = "12/01/2024",
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

// Helper function to create receipt data from billing data (for actual meter readings)
fun createReceiptDataFromBilling(
    billingData: com.example.meterkenshin.data.BillingData,
    calculatedData: com.example.meterkenshin.ui.screen.CalculatedBillingData
): ReceiptData {
    return ReceiptData(
        period = billingData.Period ?: "",
        commercial = billingData.Commercial ?: "",
        serialID = billingData.SerialID ?: "",
        multiplier = billingData.Multiplier ?: 0f,
        periodFrom = billingData.PeriodFrom ?: "",
        periodTo = billingData.PeriodTo ?: "",
        prevReading = billingData.PrevReading ?: 0f,
        presReading = billingData.PresReading ?: 0f,
        maxDemand = billingData.MaxDemand ?: 0f,
        reader = billingData.Reader ?: "",
        version = billingData.Version ?: "",
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