package com.example.meterkenshin.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ReceiptViewModel : ViewModel() {

    private val _rateData = MutableStateFlow<FloatArray?>(null)
    val rateData: StateFlow<FloatArray?> = _rateData.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _previewData = MutableStateFlow<ReceiptPreviewData?>(null)
    val previewData: StateFlow<ReceiptPreviewData?> = _previewData.asStateFlow()

    fun updateRateData(rates: FloatArray) {
        viewModelScope.launch {
            _rateData.value = rates
            updatePreview()
        }
    }

    fun clearRateData() {
        _rateData.value = null
        _previewData.value = null
    }

    fun setLoading(loading: Boolean) {
        _isLoading.value = loading
    }

    fun setError(error: String?) {
        _errorMessage.value = error
    }

    fun clearError() {
        _errorMessage.value = null
    }

    private fun updatePreview() {
        val rates = _rateData.value ?: return

        // Create sample billing data for preview
        val sampleBilling = SampleBillingData(
            prevReading = 1000.0f,
            presReading = 1500.0f,
            maxDemand = 50.0f,
            serialID = "12345678",
            periodFrom = "11/15/2024",
            periodTo = "12/15/2024"
        )

        val preview = calculateReceiptPreview(sampleBilling, rates)
        _previewData.value = preview
    }

    private fun calculateReceiptPreview(billing: SampleBillingData, rates: FloatArray): ReceiptPreviewData {
        val totalUse = billing.presReading - billing.prevReading

        // Calculate charges based on the original formula
        val genTransCharges = totalUse * rates[0] + billing.maxDemand * rates[1] + totalUse * rates[2]
        val distributionCharges = billing.maxDemand * rates[3] + 1 * rates[4] + 1 * rates[5]
        val sustainableCapex = totalUse * rates[6] + totalUse * rates[7]
        val otherCharges = totalUse * rates[8] + totalUse * rates[9]
        val universalCharges = totalUse * rates[10] + sustainableCapex * rates[11] +
                totalUse * rates[12] + totalUse * rates[13] +
                totalUse * rates[14] + totalUse * rates[15]
        val valueAddedTax = totalUse * rates[16] + totalUse * rates[17] + totalUse * rates[18] +
                distributionCharges * rates[19] + otherCharges * rates[20]

        val totalAmount = genTransCharges + distributionCharges + sustainableCapex +
                otherCharges + universalCharges + valueAddedTax

        return ReceiptPreviewData(
            totalUse = totalUse,
            genTransCharges = genTransCharges,
            distributionCharges = distributionCharges,
            sustainableCapex = sustainableCapex,
            otherCharges = otherCharges,
            universalCharges = universalCharges,
            valueAddedTax = valueAddedTax,
            totalAmount = totalAmount,
            rates = rates
        )
    }
}

data class SampleBillingData(
    val prevReading: Float,
    val presReading: Float,
    val maxDemand: Float,
    val serialID: String,
    val periodFrom: String,
    val periodTo: String
)

data class ReceiptPreviewData(
    val totalUse: Float,
    val genTransCharges: Float,
    val distributionCharges: Float,
    val sustainableCapex: Float,
    val otherCharges: Float,
    val universalCharges: Float,
    val valueAddedTax: Float,
    val totalAmount: Float,
    val rates: FloatArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ReceiptPreviewData

        if (totalUse != other.totalUse) return false
        if (genTransCharges != other.genTransCharges) return false
        if (distributionCharges != other.distributionCharges) return false
        if (sustainableCapex != other.sustainableCapex) return false
        if (otherCharges != other.otherCharges) return false
        if (universalCharges != other.universalCharges) return false
        if (valueAddedTax != other.valueAddedTax) return false
        if (totalAmount != other.totalAmount) return false
        if (!rates.contentEquals(other.rates)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = totalUse.hashCode()
        result = 31 * result + genTransCharges.hashCode()
        result = 31 * result + distributionCharges.hashCode()
        result = 31 * result + sustainableCapex.hashCode()
        result = 31 * result + otherCharges.hashCode()
        result = 31 * result + universalCharges.hashCode()
        result = 31 * result + valueAddedTax.hashCode()
        result = 31 * result + totalAmount.hashCode()
        result = 31 * result + rates.contentHashCode()
        return result
    }
}