package com.example.meterkenshin.ui.component

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.meterkenshin.model.Billing
import com.example.meterkenshin.data.getDefaultRates
import com.example.meterkenshin.util.calculateBillingData
import com.example.meterkenshin.util.getCurrentDate
import com.example.meterkenshin.util.getCurrentDateTime

@SuppressLint("DefaultLocale")
@Composable
fun ReceiptPreview(
    billingData: Billing,
    rateData: FloatArray?,
    modifier: Modifier = Modifier
) {
    val rates = rateData ?: getDefaultRates()

    // Calculate and populate billing data fields directly
    calculateBillingData(billingData, rates)

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
        ReceiptLine("Meter", "${billingData.SerialNumber}       Multiplier: ${billingData.Multiplier ?: 1.0f}")
        ReceiptLine("Period To", "${billingData.PeriodTo}       Pres Reading: ${String.format("%.3f", billingData.PresReading ?: 0f)}")
        ReceiptLine("Period From", "${billingData.PeriodFrom}       Prev Reading: ${String.format("%.3f", billingData.PrevReading ?: 0f)}")
        ReceiptLine("Demand KW", "${String.format("%.3f", billingData.MaxDemand ?: 0f)}       Total KWH Used: ${String.format("%.3f", billingData.TotalUse ?: 0f)}")

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 12.dp),
            thickness = 1.dp,
            color = Color.Black
        )

        // Charges breakdown - now using Billing fields directly
        ChargesSection("GEN/TRANS CHARGES", billingData.GenTransCharges ?: 0f, rates, billingData)
        ChargesSection("DISTRIBUTION CHARGES", billingData.DistributionCharges ?: 0f, rates, billingData)
        ChargesSection("REINVESTMENT FUND FOR\nSUSTAINABLE CAPEX", billingData.SustainableCapex ?: 0f, rates, billingData)
        ChargesSection("OTHER CHARGES", billingData.OtherCharges ?: 0f, rates, billingData)
        ChargesSection("UNIVERSAL CHARGES", billingData.UniversalCharges ?: 0f, rates, billingData)
        ChargesSection("VALUE ADDED TAX", billingData.ValueAddedTax ?: 0f, rates, billingData)

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 12.dp),
            thickness = 1.dp,
            color = Color.Black
        )

        // Total amounts
        ReceiptLine("CURRENT BILL", String.format("Php %,.2f", billingData.TotalAmount ?: 0f))
        Text(
            text = String.format("TOTAL AMOUNT       Php %,.2f", billingData.TotalAmount ?: 0f),
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
        ReceiptLine("Amount Before Due", String.format("%.2f", (billingData.TotalAmount ?: 0f) - (billingData.Discount ?: 0f)))
        ReceiptLine("Interest", String.format("%.2f", billingData.Interest ?: 0f))
        ReceiptLine("Amount After Due", String.format("%.2f", (billingData.TotalAmount ?: 0f) + (billingData.Interest ?: 0f)))

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
fun ReceiptLine(label: String, value: String) {
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
    billingData: Billing  // Changed from CalculatedBillingData
) {
    val totalUse = billingData.TotalUse ?: 0f
    val maxDemand = billingData.MaxDemand ?: 0f
    val distributionCharges = billingData.DistributionCharges ?: 0f
    val otherCharges = billingData.OtherCharges ?: 0f

    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace
    )

    when (title) {
        "GEN/TRANS CHARGES" -> {
            ChargeDetailLine("Generation System Charge", rates[0], totalUse * rates[0], "/kwh")
            ChargeDetailLine("Transmission Demand Charge", rates[1], maxDemand * rates[1], "/kw")
            ChargeDetailLine("System Loss Charge", rates[2], totalUse * rates[2], "/kwh")
        }
        "DISTRIBUTION CHARGES" -> {
            ChargeDetailLine("Distribution Demand Charge", rates[3], maxDemand * rates[3], "/kw")
            ChargeDetailLine("Supply Fix Charge", rates[4], 1 * rates[4], "/cst")
            ChargeDetailLine("Metering Fix Charge", rates[5], 1 * rates[5], "/cst")
        }
        "REINVESTMENT FUND FOR\nSUSTAINABLE CAPEX" -> {
            ChargeDetailLine("Reinvestment Fund for CAPEX", rates[6], totalUse * rates[6], "/kwh")
            ChargeDetailLine("Member's CAPEX Contribution", rates[7], totalUse * rates[7], "/kwh")
        }
        "OTHER CHARGES" -> {
            ChargeDetailLine("Lifeline Discount/Subsidy", rates[8], totalUse * rates[8], "/kwh")
            ChargeDetailLine("Senior Citizen Subsidy", rates[9], totalUse * rates[9], "/kwh")
        }
        "UNIVERSAL CHARGES" -> {
            ChargeDetailLine("Missionary Elec(NPC-SPUG)", rates[10], totalUse * rates[10], "/kwh")
            ChargeDetailLine("Missionary Elec(RED)", rates[11], totalUse * rates[11], "/kwh")
            ChargeDetailLine("Environmental Charge", rates[12], totalUse * rates[12], "/kwh")
            ChargeDetailLine("Feed In Tariff Allowance", rates[13], totalUse * rates[13], "/kwh")
            ChargeDetailLine("NPC Stranded Contract", rates[14], totalUse * rates[14], "/kwh")
            ChargeDetailLine("NPC Stranded Debts", rates[15], totalUse * rates[15], "/kwh")
        }
        "VALUE ADDED TAX" -> {
            ChargeDetailLine("Generation VAT", rates[16], totalUse * rates[16], "/kwh")
            ChargeDetailLine("Transmission VAT", rates[17], totalUse * rates[17], "/kwh")
            ChargeDetailLine("System Loss VAT", rates[18], totalUse * rates[18], "/kwh")
            ChargeDetailLine("Distribution VAT", rates[19], distributionCharges * rates[19], "%%")
            ChargeDetailLine("Other VAT", rates[20], otherCharges * rates[20], "%%")
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