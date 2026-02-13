package com.example.meterkenshin.ui.component

import android.annotation.SuppressLint
import android.util.Log
import com.example.meterkenshin.model.Billing
import com.example.meterkenshin.ui.viewmodel.PrinterBluetoothViewModel
import com.example.meterkenshin.utils.formattedMonthDay
import com.example.meterkenshin.utils.getCurrentDateTime
import com.woosim.printer.WoosimCmd

// Data class for receipt printing
data class ReceiptData(
    val period: String,
    val commercial: String,
    val serialNumber: String,
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
    val totalAmount: Float,
    val dueDate: String,
    val discoDate: String,
    val readDatetime: String
)

// Print function that handles the actual printing with WoosimLib
@SuppressLint("DefaultLocale")
fun printReceipt(
    receiptData: ReceiptData,
    printerBluetoothViewModel: PrinterBluetoothViewModel,
    rates: FloatArray? = null,
    isSample: Boolean = false
) {
    val commands = mutableListOf<ByteArray>()

    // Initialize printer
    commands.add(WoosimCmd.initPrinter())
    commands.add(WoosimCmd.PM_setPosition(0, 0))

    // Title 1 - SAMPLE RECEIPT (only for template screen)
    if (isSample) {
        commands.add(WoosimCmd.setCodeTable(WoosimCmd.MCU_RX, WoosimCmd.CT_CP437, WoosimCmd.FONT_MEDIUM))
        commands.add(WoosimCmd.setTextStyle(true, false, false, 1, 1))
        commands.add("SAMPLE RECEIPT\n\n\n".toByteArray())
    }

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
        receiptData.serialNumber, receiptData.multiplier).toByteArray())
    commands.add(String.format("Period To  :%s                \nPres Reading  : %6.3f\n",
        receiptData.periodTo, receiptData.presReading).toByteArray())
    commands.add(String.format("Period From:%s                \nPrev Reading  : %6.3f\n",
        receiptData.periodFrom, receiptData.prevReading).toByteArray())
    commands.add(String.format("Max Demand :%.2f                    Total Use     : %6.3f\n",
        receiptData.maxDemand, receiptData.totalUse).toByteArray())

    // GENERATION TRANSMISSION CHARGES (FONT_LARGE header)
    commands.add(WoosimCmd.setCodeTable(WoosimCmd.MCU_RX, WoosimCmd.CT_CP437, WoosimCmd.FONT_LARGE))
    commands.add(WoosimCmd.setTextStyle(true, false, false, 1, 1))
    commands.add("GENERATION TRANSMISSION CHARGES\n".toByteArray())

    // Details under Gen/Trans (FONT_MEDIUM)
    commands.add(WoosimCmd.setCodeTable(WoosimCmd.MCU_RX, WoosimCmd.CT_CP437, WoosimCmd.FONT_MEDIUM))
    if (rates != null && rates.size >= 23) {
        commands.add(String.format("  Generation System Charge                    %,8.2f\n",
            receiptData.totalUse * rates[0]).toByteArray())
        commands.add(String.format("  Transmission Demand Charge                  %,8.2f\n",
            receiptData.maxDemand * rates[1]).toByteArray())
        commands.add(String.format("  System Loss Charge                          %,8.2f\n",
            receiptData.totalUse * rates[2]).toByteArray())
    } else {
        commands.add(String.format("  Generation                                  %,8.2f\n",
            receiptData.genTransCharges * 0.6f).toByteArray())
        commands.add(String.format("  Transmission                                %,8.2f\n",
            receiptData.genTransCharges * 0.3f).toByteArray())
        commands.add(String.format("  System Loss                                 %,8.2f\n",
            receiptData.genTransCharges * 0.1f).toByteArray())
    }
    commands.add("                                                ----------------\n".toByteArray())
    commands.add(String.format("                                       SUB TOTAL      %,6.2f\n\n",
        receiptData.genTransCharges).toByteArray())

    // DISTRIBUTION CHARGES (FONT_LARGE header)
    commands.add(WoosimCmd.setCodeTable(WoosimCmd.MCU_RX, WoosimCmd.CT_CP437, WoosimCmd.FONT_LARGE))
    commands.add(WoosimCmd.setTextStyle(true, false, false, 1, 1))
    commands.add("DISTRIBUTION CHARGES\n".toByteArray())

    // Details (FONT_MEDIUM)
    commands.add(WoosimCmd.setCodeTable(WoosimCmd.MCU_RX, WoosimCmd.CT_CP437, WoosimCmd.FONT_MEDIUM))
    if (rates != null && rates.size >= 23) {
        commands.add(String.format("  Distribution Demand Charge                  %,8.2f\n",
            receiptData.maxDemand * rates[3]).toByteArray())
        commands.add(String.format("  Supply Fix Charge                           %,8.2f\n",
            1f * rates[4]).toByteArray())
        commands.add(String.format("  Metering Fix Charge                         %,8.2f\n",
            1f * rates[5]).toByteArray())
    } else {
        commands.add(String.format("  Demand Charge                               %,8.2f\n",
            receiptData.distributionCharges * 0.7f).toByteArray())
        commands.add(String.format("  Distribution System Charge                  %,8.2f\n",
            receiptData.distributionCharges * 0.2f).toByteArray())
        commands.add(String.format("  Metering Fix Charge                         %,8.2f\n",
            receiptData.distributionCharges * 0.1f).toByteArray())
    }
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
    if (rates != null && rates.size >= 23) {
        commands.add(String.format("  Reinvestment Fund for CAPEX                 %,8.2f\n",
            receiptData.totalUse * rates[6]).toByteArray())
        commands.add(String.format("  Member's CAPEX Contribution                 %,8.2f\n",
            receiptData.totalUse * rates[7]).toByteArray())
    } else {
        commands.add(String.format("  Reinvestment Fund for CAPEX                 %,8.2f\n",
            receiptData.sustainableCapex * 0.6f).toByteArray())
        commands.add(String.format("  Member's CAPEX Contribution                 %,8.2f\n",
            receiptData.sustainableCapex * 0.4f).toByteArray())
    }
    commands.add("                                                ----------------\n".toByteArray())
    commands.add(String.format("                                       SUB TOTAL        %,6.2f\n\n",
        receiptData.sustainableCapex).toByteArray())

    // OTHER CHARGES (FONT_LARGE header)
    commands.add(WoosimCmd.setCodeTable(WoosimCmd.MCU_RX, WoosimCmd.CT_CP437, WoosimCmd.FONT_LARGE))
    commands.add(WoosimCmd.setTextStyle(true, false, false, 1, 1))
    commands.add("OTHER CHARGES\n".toByteArray())

    // Details (FONT_MEDIUM)
    commands.add(WoosimCmd.setCodeTable(WoosimCmd.MCU_RX, WoosimCmd.CT_CP437, WoosimCmd.FONT_MEDIUM))
    if (rates != null && rates.size >= 23) {
        commands.add(String.format("  Lifeline Discount/Subsidy                   %,8.2f\n",
            receiptData.totalUse * rates[8]).toByteArray())
        commands.add(String.format("  Senior Citizen Subsidy                      %,8.2f\n",
            receiptData.totalUse * rates[9]).toByteArray())
    } else {
        commands.add(String.format("  Other Charges                               %,8.2f\n",
            receiptData.otherCharges).toByteArray())
    }
    commands.add("                                                ----------------\n".toByteArray())
    commands.add(String.format("                                       SUB TOTAL        %,6.2f\n\n",
        receiptData.otherCharges).toByteArray())

    // UNIVERSAL CHARGES (FONT_LARGE header)
    commands.add(WoosimCmd.setCodeTable(WoosimCmd.MCU_RX, WoosimCmd.CT_CP437, WoosimCmd.FONT_LARGE))
    commands.add(WoosimCmd.setTextStyle(true, false, false, 1, 1))
    commands.add("UNIVERSAL CHARGES\n".toByteArray())

    // Details (FONT_MEDIUM)
    commands.add(WoosimCmd.setCodeTable(WoosimCmd.MCU_RX, WoosimCmd.CT_CP437, WoosimCmd.FONT_MEDIUM))
    if (rates != null && rates.size >= 23) {
        commands.add(String.format("  Missionary Elec(NPC-SPUG)                   %,8.2f\n",
            receiptData.totalUse * rates[10]).toByteArray())
        commands.add(String.format("  Missionary Elec(RED)                        %,8.2f\n",
            receiptData.totalUse * rates[11]).toByteArray())
        commands.add(String.format("  Environmental Charge                        %,8.2f\n",
            receiptData.totalUse * rates[12]).toByteArray())
        commands.add(String.format("  Feed In Tariff Allowance                    %,8.2f\n",
            receiptData.totalUse * rates[13]).toByteArray())
        commands.add(String.format("  NPC Stranded Contract                       %,8.2f\n",
            receiptData.totalUse * rates[14]).toByteArray())
        commands.add(String.format("  NPC Stranded Debts                          %,8.2f\n",
            receiptData.totalUse * rates[15]).toByteArray())
    } else {
        commands.add(String.format("  Universal Charges                           %,8.2f\n",
            receiptData.universalCharges).toByteArray())
    }
    commands.add("                                                ----------------\n".toByteArray())
    commands.add(String.format("                                       SUB TOTAL        %,6.2f\n\n",
        receiptData.universalCharges).toByteArray())

    // VALUE ADDED TAX (FONT_LARGE header)
    commands.add(WoosimCmd.setCodeTable(WoosimCmd.MCU_RX, WoosimCmd.CT_CP437, WoosimCmd.FONT_LARGE))
    commands.add(WoosimCmd.setTextStyle(true, false, false, 1, 1))
    commands.add("VALUE ADDED TAX\n".toByteArray())

    // Details (FONT_MEDIUM)
    commands.add(WoosimCmd.setCodeTable(WoosimCmd.MCU_RX, WoosimCmd.CT_CP437, WoosimCmd.FONT_MEDIUM))
    if (rates != null && rates.size >= 23) {
        commands.add(String.format("  Sustainable CAPEX VAT                       %,8.2f\n",
            receiptData.totalUse * rates[16]).toByteArray())
        commands.add(String.format("  Transmission VAT                            %,8.2f\n",
            receiptData.totalUse * rates[17]).toByteArray())
        commands.add(String.format("  Generation VAT                              %,8.2f\n",
            receiptData.totalUse * rates[18]).toByteArray())
        commands.add(String.format("  System Loss VAT                             %,8.2f\n",
            receiptData.totalUse * rates[19]).toByteArray())
        commands.add(String.format("  Universal Charges VAT                       %,8.2f\n",
            receiptData.totalUse * rates[20]).toByteArray())
        commands.add(String.format("  Distribution VAT                            %,8.2f\n",
            receiptData.distributionCharges * rates[21]).toByteArray())
        commands.add(String.format("  Other VAT                                   %,8.2f\n",
            receiptData.otherCharges * rates[22]).toByteArray())
    } else {
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
    }
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
    commands.add(String.format("     DUE DATE     :%s\n", receiptData.dueDate).toByteArray())
    commands.add(String.format("     DISCO DATE   :%s\n\n", receiptData.discoDate).toByteArray())

    // Footer notes (FONT_SMALL)
    commands.add(WoosimCmd.setCodeTable(WoosimCmd.MCU_RX, WoosimCmd.CT_CP437, WoosimCmd.FONT_SMALL))
    commands.add("NOTE:Please pay this electric bill on or before DUE DATE otherwise,\n".toByteArray())
    commands.add("     we will be forced to discontinue serving your electric needs.\n\n".toByteArray())
    commands.add("This is not an Official Receipt.\n".toByteArray())
    commands.add("Payment of this bill does not mean ".toByteArray())
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
    serialNumber: String = "12345678",
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
        0.0000f, 0.0428f, 0.8692f, 0.13f, 0.0000f,   // VAT per kWh (16-20)
        0.12f, 0.12f                                    // VAT multipliers (21-22)
    )

    val genTransCharges = totalUse * rates[0] + maxDemand * rates[1] + totalUse * rates[2]
    val distributionCharges = maxDemand * rates[3] + 1 * rates[4] + 1 * rates[5]
    val sustainableCapex = totalUse * rates[6] + totalUse * rates[7]
    val otherCharges = totalUse * rates[8] + totalUse * rates[9]
    val universalCharges = totalUse * rates[10] + totalUse * rates[11] +
            totalUse * rates[12] + totalUse * rates[13] +
            totalUse * rates[14] + totalUse * rates[15]
    val valueAddedTax = totalUse * rates[16] + totalUse * rates[17] + totalUse * rates[18] +
            totalUse * rates[19] + totalUse * rates[20] +
            distributionCharges * rates[21] + otherCharges * rates[22]

    val totalAmount = genTransCharges + distributionCharges + sustainableCapex +
            otherCharges + universalCharges + valueAddedTax

    val now = java.util.Date()
    return ReceiptData(
        period = period,
        commercial = "Type A",
        serialNumber = serialNumber,
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
        totalAmount = totalAmount,
        dueDate = formattedMonthDay(now, 1, 0),
        discoDate = formattedMonthDay(now, 1, 15),
        readDatetime = getCurrentDateTime()
    )
}

fun createReceiptDataFromBilling(
    billingData: Billing
): ReceiptData {
    return ReceiptData(
        period = billingData.Period ?: "",
        commercial = billingData.Commercial ?: "",
        serialNumber = billingData.SerialNumber ?: "",
        multiplier = billingData.Multiplier ?: 0f,
        periodFrom = billingData.PeriodFrom ?: "",
        periodTo = billingData.PeriodTo ?: "",
        prevReading = billingData.PrevReading ?: 0f,
        presReading = billingData.PresReading ?: 0f,
        maxDemand = billingData.MaxDemand ?: 0f,
        reader = billingData.Reader ?: "",
        version = billingData.Version ?: "",
        totalUse = billingData.TotalUse ?: 0f,
        genTransCharges = billingData.GenTransCharges ?: 0f,
        distributionCharges = billingData.DistributionCharges ?: 0f,
        sustainableCapex = billingData.SustainableCapex ?: 0f,
        otherCharges = billingData.OtherCharges ?: 0f,
        universalCharges = billingData.UniversalCharges ?: 0f,
        valueAddedTax = billingData.ValueAddedTax ?: 0f,
        totalAmount = billingData.TotalAmount ?: 0f,
        dueDate = billingData.DueDate ?: formattedMonthDay(1, 0),
        discoDate = billingData.DiscoDate ?: formattedMonthDay(1, 15),
        readDatetime = billingData.ReadDatetime ?: getCurrentDateTime()
    )
}