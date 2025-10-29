package com.example.meterkenshin.utils

import com.example.meterkenshin.model.Billing

/**
 * Calculate and populate billing charges directly into Billing object
 * No separate CalculatedBillingData needed - uses Billing's existing fields
 */
fun calculateBillingData(
    billingData: Billing,
    rates: FloatArray
): Billing {
    val totalUse = (billingData.PresReading ?: 0f) - (billingData.PrevReading ?: 0f)
    val maxDemand = billingData.MaxDemand ?: 0f

    val genTransCharges = totalUse * rates[0] + maxDemand * rates[1] + totalUse * rates[2]
    val distributionCharges = maxDemand * rates[3] + 1f * rates[4] + 1f * rates[5]
    val sustainableCapex = totalUse * rates[6] + totalUse * rates[7]
    val otherCharges = totalUse * rates[8] + totalUse * rates[9]
    val universalCharges = totalUse * rates[10] + sustainableCapex * rates[11] +
            totalUse * rates[12] + totalUse * rates[13] +
            totalUse * rates[14] + totalUse * rates[15]
    val valueAddedTax = totalUse * rates[16] + totalUse * rates[17] + totalUse * rates[18] +
            distributionCharges * rates[19] + otherCharges * rates[20]

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
