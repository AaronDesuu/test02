package com.example.meterkenshin.utils

import android.util.Log
import com.example.meterkenshin.model.Billing

/**
 * Calculate and populate billing charges directly into Billing object
 * No separate CalculatedBillingData needed - uses Billing's existing fields
 */
fun calculateBillingData(
    billingData: Billing,
    rates: FloatArray
): Billing {
    // Validate that both readings exist before calculating
    val presReading = billingData.PresReading
    val prevReading = billingData.PrevReading

    val totalUse = when {
        presReading == null || prevReading == null -> {
            Log.w("CalculateBillingData", "Missing readings: PresReading=$presReading, PrevReading=$prevReading for ${billingData.SerialNumber}")
            0f
        }
        presReading < prevReading -> {
            Log.w("CalculateBillingData", "Invalid readings: Current ($presReading) < Previous ($prevReading) for ${billingData.SerialNumber}")
            0f
        }
        presReading < 0f || prevReading < 0f -> {
            Log.w("CalculateBillingData", "Negative readings detected: PresReading=$presReading, PrevReading=$prevReading for ${billingData.SerialNumber}")
            0f
        }
        else -> presReading - prevReading
    }

    val maxDemand = when {
        billingData.MaxDemand == null -> {
            Log.w("CalculateBillingData", "MaxDemand is null for ${billingData.SerialNumber}, using 0")
            0f
        }
        billingData.MaxDemand!! < 0f -> {
            Log.w("CalculateBillingData", "Negative MaxDemand detected: ${billingData.MaxDemand} for ${billingData.SerialNumber}")
            0f
        }
        else -> billingData.MaxDemand!!
    }

    val genTransCharges = totalUse * rates[0] + maxDemand * rates[1] + totalUse * rates[2]
    val distributionCharges = maxDemand * rates[3] + 1f * rates[4] + 1f * rates[5]
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

    // Populate the Billing object's calculated fields
    billingData.TotalUse = totalUse
    billingData.GenTransCharges = genTransCharges
    billingData.DistributionCharges = distributionCharges
    billingData.SustainableCapex = sustainableCapex
    billingData.OtherCharges = otherCharges
    billingData.UniversalCharges = universalCharges
    billingData.ValueAddedTax = valueAddedTax
    billingData.TotalAmount = totalAmount

    return billingData
}
