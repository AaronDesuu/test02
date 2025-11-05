package com.example.meterkenshin.dlms

import com.example.meterkenshin.model.Billing
import com.example.meterkenshin.ui.component.createReceiptDataFromBilling
import com.example.meterkenshin.ui.component.printReceipt as sendReceiptToPrinter
import com.example.meterkenshin.ui.viewmodel.PrinterBluetoothViewModel
import com.example.meterkenshin.utils.PrinterStatusHelper
import com.example.meterkenshin.utils.calculateBillingData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ReadDataPrinting - Handles all printing operations for DLMS read data
 * Extracted from DLMSViewModel to separate printing concerns
 *
 * UPDATED: Now shows PrinterStatusErrorDialog with real-time status checking
 */
class ReadDataPrinting(
    private val viewModelScope: CoroutineScope,
    private val appendLog: (String) -> Unit
) {

    // State flows for print dialogs
    private val _showPrintDialog = MutableStateFlow(false)
    val showPrintDialog: StateFlow<Boolean> = _showPrintDialog.asStateFlow()

    private val _showSaveDialog = MutableStateFlow(false)
    val showSaveDialog: StateFlow<Boolean> = _showSaveDialog.asStateFlow()

    private val _showPrinterErrorDialog = MutableStateFlow(false)
    val showPrinterErrorDialog: StateFlow<Boolean> = _showPrinterErrorDialog.asStateFlow()

    private val _printerErrorMessage = MutableStateFlow("")
    val printerErrorMessage: StateFlow<String> = _printerErrorMessage.asStateFlow()

    // Real-time printer status for error dialog
    private val _printerPaperStatus = MutableStateFlow(PrinterBluetoothViewModel.PaperStatus.UNKNOWN)
    val printerPaperStatus: StateFlow<PrinterBluetoothViewModel.PaperStatus> = _printerPaperStatus.asStateFlow()

    private val _printerCoverStatus = MutableStateFlow(PrinterBluetoothViewModel.CoverStatus.UNKNOWN)
    val printerCoverStatus: StateFlow<PrinterBluetoothViewModel.CoverStatus> = _printerCoverStatus.asStateFlow()

    private val _pendingBillingData = MutableStateFlow<Billing?>(null)
    val pendingBillingData: StateFlow<Billing?> = _pendingBillingData.asStateFlow()

    private var printerViewModel: PrinterBluetoothViewModel? = null
    private var onPrintSuccess: ((serialNumber: String) -> Unit)? = null
    private var savedRates: FloatArray? = null

    /**
     * Set callback for successful print (called by DLMSViewModel)
     */
    fun setOnPrintSuccessCallback(callback: (String) -> Unit) {
        this.onPrintSuccess = callback
    }

    /**
     * Set printer view model reference and collect real-time status
     */
    fun setPrinterViewModel(viewModel: PrinterBluetoothViewModel) {
        this.printerViewModel = viewModel

        // Collect real-time printer status for error dialog
        viewModelScope.launch {
            viewModel.paperStatus.collect { _printerPaperStatus.value = it }
        }
        viewModelScope.launch {
            viewModel.coverStatus.collect { _printerCoverStatus.value = it }
        }
    }

    /**
     * Set saved rates for billing calculations
     */
    fun setSavedRates(rates: FloatArray?) {
        this.savedRates = rates
    }

    /**
     * Set pending billing data for printing
     */
    fun setPendingBillingData(billing: Billing?) {
        _pendingBillingData.value = billing
    }

    /**
     * Update printReceipt to trigger callback after successful print
     */
    fun printReceipt(billing: Billing, rates: FloatArray? = null): Boolean {
        var printSuccess = false
        viewModelScope.launch {
            try {
                val printer = printerViewModel
                if (printer == null) {
                    appendLog("ERROR: Printer not configured")
                    _printerErrorMessage.value = "Printer not configured"
                    _showPrinterErrorDialog.value = true
                    return@launch
                }

                PrinterStatusHelper.checkPrinterReadyAndExecute(
                    printerViewModel = printer,
                    onNotConnected = {
                        appendLog("ERROR: Printer not connected")
                        _printerErrorMessage.value = "Printer is not connected. Please connect to printer."
                        _showPrinterErrorDialog.value = true
                    },
                    onNotReady = { reason ->
                        appendLog("ERROR: Printer not ready - $reason")
                        _printerErrorMessage.value = reason
                        _showPrinterErrorDialog.value = true
                    },
                    onReady = {
                        appendLog("Printer ready, preparing receipt...")

                        if (rates != null) {
                            calculateBillingData(billing, rates)
                        }

                        val receiptData = createReceiptDataFromBilling(billing)
                        sendReceiptToPrinter(receiptData, printer)

                        appendLog("✅ Receipt sent to printer successfully")
                        printSuccess = true

                        // NEW: Trigger callback to update meter CSV
                        billing.SerialNumber?.let { serialNo ->
                            onPrintSuccess?.invoke(serialNo)
                        }
                    }
                )
            } catch (e: Exception) {
                appendLog("ERROR: Failed to print receipt - ${e.message}")
                _printerErrorMessage.value = "Failed to print receipt: ${e.message}"
                _showPrinterErrorDialog.value = true
            }
        }
        return printSuccess
    }

    /**
     * Print pending receipt (from read data)
     * Called when user confirms print dialog
     */
    private fun printPendingReceipt() {
        val billing = _pendingBillingData.value
        if (billing != null) {
            val rates = savedRates
            printReceipt(billing, rates)
        } else {
            appendLog("No pending billing data to print")
        }
    }

    /**
     * Confirm print - checks printer then prints receipt and shows save dialog next
     * UPDATED: Shows PrinterStatusErrorDialog instead of just skipping on error
     */
    fun confirmPrint() {
        viewModelScope.launch {
            val printer = printerViewModel
            if (printer == null) {
                appendLog("ERROR: Printer not configured")
                _printerErrorMessage.value = "Printer not configured"
                _showPrinterErrorDialog.value = true
                return@launch
            }

            // Check printer status before attempting to print
            PrinterStatusHelper.checkPrinterReadyAndExecute(
                printerViewModel = printer,
                onNotConnected = {
                    appendLog("ERROR: Printer not connected")
                    _printerErrorMessage.value = "Printer is not connected. Please connect to printer."
                    _showPrinterErrorDialog.value = true
                    // Don't dismiss print dialog or show save dialog
                },
                onNotReady = { reason ->
                    appendLog("ERROR: Printer not ready - $reason")
                    _printerErrorMessage.value = reason
                    _showPrinterErrorDialog.value = true
                    // Don't dismiss print dialog or show save dialog
                },
                onReady = {
                    // Printer ready, print the receipt
                    printPendingReceipt()
                    dismissPrintDialog()
                    // Show save dialog after print
                    _showSaveDialog.value = true
                }
            )
        }
    }

    /**
     * Retry print from error dialog
     * Re-checks status and attempts print again
     */
    fun retryPrint() {
        dismissPrinterErrorDialog()  // Close error dialog first
        confirmPrint() // Try again - will re-show error dialog if still failing
    }

    /**
     * Cancel print from error dialog
     * Closes error dialog and goes to save dialog
     */
    fun cancelPrintFromError() {
        dismissPrinterErrorDialog()  // Close error dialog
        dismissPrintDialog()         // Close print dialog
        _showSaveDialog.value = true // Show save dialog
    }

    /**
     * Dismiss printer error dialog
     */
    private fun dismissPrinterErrorDialog() {
        _showPrinterErrorDialog.value = false
    }

    /**
     * Show print dialog after read data completes
     */
    fun showPrintDialog() {
        _showPrintDialog.value = true
    }

    /**
     * Hide print dialog
     */
    private fun dismissPrintDialog() {
        _showPrintDialog.value = false
    }

    /**
     * Skip print - goes directly to save dialog
     */
    fun skipPrint() {
        dismissPrintDialog()
        // Show save dialog
        _showSaveDialog.value = true
    }

    /**
     * Dismiss save dialog
     */
    fun dismissSaveDialog() {
        _showSaveDialog.value = false
    }

    /**
     * Clear pending billing data
     */
    fun clearPendingBillingData() {
        _pendingBillingData.value = null
    }
}