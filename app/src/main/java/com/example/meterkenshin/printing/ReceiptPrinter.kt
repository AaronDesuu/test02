package com.example.meterkenshin.printing

import com.example.meterkenshin.bluetooth.BluetoothPrintService
import com.example.meterkenshin.model.MeterReading
import com.example.meterkenshin.woosim.WoosimCmd
import java.text.SimpleDateFormat
import java.util.*

/**
 * Receipt Printer utility for formatting and printing meter receipts
 * Based on your project's BillingData structure and ReceiptScreen implementation
 */
class ReceiptPrinter(private val printService: BluetoothPrintService) {

    companion object {
        private const val TAG = "ReceiptPrinter"
    }

    /**
     * Print a sample receipt
     */
    fun printSampleReceipt() {
        if (printService.getState() != BluetoothPrintService.STATE_CONNECTED) {
            return
        }

        val receiptContent = formatSampleReceipt()
        printReceipt(receiptContent)
    }

    /**
     * Print a meter reading receipt using BillingData structure
     */
    fun printMeterReceipt(meterReading: MeterReading, rates: FloatArray? = null) {
        if (printService.getState() != BluetoothPrintService.STATE_CONNECTED) {
            return
        }

        // Convert MeterReading to BillingData structure
        val billingData = createBillingDataFromMeterReading(meterReading)
        val receiptContent = formatMeterReceiptFromBilling(billingData, rates)
        printReceipt(receiptContent)
    }

    /**
     * Print receipt using BillingData structure (like in ReceiptScreen)
     */
    fun printBillingReceipt(billingData: BillingData, rates: FloatArray? = null) {
        if (printService.getState() != BluetoothPrintService.STATE_CONNECTED) {
            return
        }

        val receiptContent = formatMeterReceiptFromBilling(billingData, rates)
        printReceipt(receiptContent)
    }

    /**
     * Format sample receipt content (matching ReceiptScreen structure)
     */
    private fun formatSampleReceipt(): String {
        val currentDate = SimpleDateFormat("MM/dd/yyyy HH:mm", Locale.getDefault()).format(Date())

        return buildString {
            append("SAMPLE RECEIPT\n\n\n")
            append("           H.V Dela Costa St Salcedo Village Makati 1227,\n")
            append("          Metro Manila Philippines\n")
            append("        Electric Philippines Inc.\n")
            append("                TEL:000-000-0000\n")
            append("================================================================\n")
            append("Period     :${currentDate}       Rate Type     : COMMERCIAL\n")
            append("Meter      :SAMPLE123       Multiplier    :1.000\n")
            append("Period To  :${currentDate}                Pres Reading  :  1000.000\n")
            append("Period From:${currentDate}                Prev Reading  :   900.000\n")
            append("Demand KW  :50.000                Total KWH Used:   100.000\n")
            append("================================================================\n")
            append("Generation System Charge   :  0.5000/kwh        50.00\n")
            append("Transmission Demand Charge :  0.1000/kw         10.00\n")
            append("System Loss Charge         :  0.0500/kwh         5.00\n")
            append("Distribution Demand Charge :  0.2000/kw         20.00\n")
            append("Supply Fix Charge          :  0.3000/cst        30.00\n")
            append("Metering Fix Charge        :  0.1500/cst        15.00\n")
            append("================================================================\n")
            append("                                       SUB TOTAL        130.00\n")
            append("                                       TOTAL            130.00\n")
            append("================================================================\n")
            append("Thank you for using MeterKenshin!\n")
            append("Generated on: ${currentDate}\n\n\n")
        }
    }

    /**
     * Create BillingData from MeterReading
     */
    private fun createBillingDataFromMeterReading(meterReading: MeterReading): BillingData {
        val currentDate = SimpleDateFormat("MM/dd/yyyy", Locale.getDefault()).format(Date())
        val previousDate = SimpleDateFormat("MM/dd/yyyy", Locale.getDefault()).format(
            Date(System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000) // 30 days ago
        )

        return BillingData(
            period = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date()),
            commercial = "COMMERCIAL",
            serialID = meterReading.meterId,
            multiplier = 1.0f,
            periodFrom = previousDate,
            periodTo = currentDate,
            prevReading = (meterReading.reading - 100.0).toFloat(), // Simulate previous reading
            presReading = meterReading.reading.toFloat(),
            maxDemand = 50.0f, // Default max demand
            reader = meterReading.readBy,
            version = "v1.0"
        )
    }

    /**
     * Format meter receipt content using BillingData structure
     */
    private fun formatMeterReceiptFromBilling(billingData: BillingData, rates: FloatArray?): String {
        val currentDate = SimpleDateFormat("MM/dd/yyyy HH:mm", Locale.getDefault()).format(Date())
        val defaultRates = getDefaultRates()
        val usedRates = rates ?: defaultRates

        val totalUse = billingData.presReading - billingData.prevReading

        return buildString {
            append("METER READING RECEIPT\n\n\n")
            append("        Electric Philippines Inc.\n")
            append("           H.V Dela Costa St Salcedo Village\n")
            append("              Makati 1227, Metro Manila\n")
            append("                TEL:000-000-0000\n")
            append("================================================================\n")
            append("Period     :${billingData.period}       Rate Type     : ${billingData.commercial}\n")
            append("Meter      :${billingData.serialID}       Multiplier    :${String.format("%.1f", billingData.multiplier)}\n")
            append("Period To  :${billingData.periodTo}                Pres Reading  : ${String.format("%8.3f", billingData.presReading)}\n")
            append("Period From:${billingData.periodFrom}                Prev Reading  : ${String.format("%8.3f", billingData.prevReading)}\n")
            append("Demand KW  :${String.format("%.3f", billingData.maxDemand)}                Total KWH Used: ${String.format("%8.3f", totalUse)}\n")
            append("================================================================\n")

            // Add rate calculations if available
            if (usedRates.size >= 6) {
                append("BILLING DETAILS:\n")
                append("Generation System Charge   :${String.format("%8.4f", usedRates[0])}/kwh      ${String.format("%8.2f", totalUse * usedRates[0])}\n")
                append("Transmission Demand Charge :${String.format("%8.4f", usedRates[1])}/kw       ${String.format("%8.2f", billingData.maxDemand * usedRates[1])}\n")
                append("System Loss Charge         :${String.format("%8.4f", usedRates[2])}/kwh      ${String.format("%8.2f", totalUse * usedRates[2])}\n")
                append("Distribution Demand Charge :${String.format("%8.4f", usedRates[3])}/kw       ${String.format("%8.2f", billingData.maxDemand * usedRates[3])}\n")
                append("Supply Fix Charge          :${String.format("%8.4f", usedRates[4])}/cst      ${String.format("%8.2f", usedRates[4])}\n")
                append("Metering Fix Charge        :${String.format("%8.4f", usedRates[5])}/cst      ${String.format("%8.2f", usedRates[5])}\n")

                val subtotal = (totalUse * (usedRates[0] + usedRates[2])) +
                        (billingData.maxDemand * (usedRates[1] + usedRates[3])) +
                        usedRates[4] + usedRates[5]

                append("================================================================\n")
                append("                                       SUB TOTAL      ${String.format("%8.2f", subtotal)}\n")
                append("                                       TOTAL          ${String.format("%8.2f", subtotal)}\n")
                append("================================================================\n")
            }

            append("Reader: ${billingData.reader}\n")
            append("System Version: ${billingData.version}\n")
            append("\n")
            append("Thank you for using MeterKenshin!\n")
            append("Generated on: ${currentDate}\n\n\n")
        }
    }

    /**
     * Send formatted content to printer
     */
    private fun printReceipt(content: String) {
        try {
            // Initialize printer
            printService.write(WoosimCmd.initPrinter())

            // Set code table and font
            printService.write(WoosimCmd.setCodeTable(WoosimCmd.MCU_RX, WoosimCmd.CT_CP437, WoosimCmd.FONT_MEDIUM))

            // Split content into sections and apply formatting
            val lines = content.split("\n")

            for (line in lines) {
                when {
                    line.contains("RECEIPT") || line.contains("Electric Philippines") -> {
                        // Header - larger, bold, centered
                        printService.write(WoosimCmd.setAlignment(1)) // Center
                        printService.write(WoosimCmd.setTextStyle(true, false, false, 2, 1))
                        printService.write(line.toByteArray())
                        printService.write(WoosimCmd.printAndFeed())
                    }
                    line.contains("===") -> {
                        // Separator lines
                        printService.write(WoosimCmd.setAlignment(0)) // Left
                        printService.write(WoosimCmd.setTextStyle(false, false, false, 1, 1))
                        printService.write(line.toByteArray())
                        printService.write(WoosimCmd.printAndFeed())
                    }
                    line.contains("TOTAL") || line.contains("SUB TOTAL") -> {
                        // Total lines - bold
                        printService.write(WoosimCmd.setAlignment(0)) // Left
                        printService.write(WoosimCmd.setTextStyle(true, false, false, 1, 1))
                        printService.write(line.toByteArray())
                        printService.write(WoosimCmd.printAndFeed())
                    }
                    line.isNotEmpty() -> {
                        // Regular content
                        printService.write(WoosimCmd.setAlignment(0)) // Left
                        printService.write(WoosimCmd.setTextStyle(false, false, false, 1, 1))
                        printService.write(line.toByteArray())
                        printService.write(WoosimCmd.printAndFeed())
                    }
                    else -> {
                        // Empty line
                        printService.write(WoosimCmd.printAndFeed())
                    }
                }
            }

            // Feed and cut paper
            printService.write(WoosimCmd.feedAndCut(4))

        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error printing receipt", e)
        }
    }

    /**
     * Get default rates matching your ReceiptScreen structure
     */
    private fun getDefaultRates(): FloatArray {
        return floatArrayOf(
            0.5000f,   // Generation System Charge
            0.1000f,   // Transmission Demand Charge
            0.0500f,   // System Loss Charge
            0.2000f,   // Distribution Demand Charge
            0.3000f,   // Supply Fix Charge
            0.1500f,   // Metering Fix Charge
            0.0750f,   // Additional rates...
            0.0250f,
            0.0100f,
            0.0050f,
            0.1200f,
            0.0800f,
            0.0600f,
            0.0400f,
            0.0300f,
            0.0900f,
            0.0700f,
            0.0450f,
            0.0350f,
            0.0150f,
            0.0080f
        )
    }

    /**
     * Check if printer is connected
     */
    fun isConnected(): Boolean {
        return printService.getState() == BluetoothPrintService.STATE_CONNECTED
    }
}

/**
 * BillingData class matching your ReceiptScreen implementation
 */
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
    val version: String
)