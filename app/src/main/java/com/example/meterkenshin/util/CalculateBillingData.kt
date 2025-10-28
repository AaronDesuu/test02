package com.example.meterkenshin.util

import com.example.meterkenshin.data.BillingData

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

fun calculateBillingData(
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