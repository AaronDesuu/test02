package com.example.meterkenshin.dlms

import android.util.Log
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
 */
class ReadDataPrinting(
    private val viewModelScope: CoroutineScope,
    private val appendLog: (String) -> Unit
) {
    companion object {
        private const val TAG = "ReadDataPrinting"
    }

    // State flows for print dialogs
    private val _showPrintDialog = MutableStateFlow(false)
    val showPrintDialog: StateFlow<Boolean> = _showPrintDialog.asStateFlow()

    private val _showSaveDialog = MutableStateFlow(false)
    val showSaveDialog: StateFlow<Boolean> = _showSaveDialog.asStateFlow()

    private val _showPrinterErrorDialog = MutableStateFlow(false)
    val showPrinterErrorDialog: StateFlow<Boolean> = _showPrinterErrorDialog.asStateFlow()

    private val _printerErrorMessage = MutableStateFlow("")
    val printerErrorMessage: StateFlow<String> = _printerErrorMessage.asStateFlow()

    private val _pendingBillingData = MutableStateFlow<Billing?>(null)
    val pendingBillingData: StateFlow<Billing?> = _pendingBillingData.asStateFlow()

    private var printerViewModel: PrinterBluetoothViewModel? = null
    private var savedRates: FloatArray? = null

    /**
     * Set printer view model reference
     */
    fun setPrinterViewModel(viewModel: PrinterBluetoothViewModel) {
        this.printerViewModel = viewModel
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
     * Print receipt with billing data
     * Uses PrinterStatusHelper for universal printer checking
     */
    fun printReceipt(billing: Billing, rates: FloatArray? = null) {
        viewModelScope.launch {
            try {
                val printer = printerViewModel
                if (printer == null) {
                    appendLog("ERROR: Printer not configured")
                    return@launch
                }

                appendLog("Checking printer status...")

                // Use PrinterStatusHelper for comprehensive status checking
                PrinterStatusHelper.checkPrinterReadyAndExecute(
                    printerViewModel = printer,
                    onNotConnected = {
                        appendLog("ERROR: Printer not connected")
                    },
                    onNotReady = { reason ->
                        appendLog("ERROR: Printer not ready - $reason")
                    },
                    onReady = {
                        // Printer is ready, proceed with printing
                        appendLog("Printer ready, preparing receipt...")

                        // Calculate billing if rates provided
                        if (rates != null) {
                            calculateBillingData(billing, rates)
                        }

                        // Create receipt data
                        val receiptData = createReceiptDataFromBilling(billing)

                        // Send to printer
                        appendLog("Sending receipt to printer...")
                        sendReceiptToPrinter(receiptData, printer)

                        appendLog("✅ Receipt sent to printer successfully")
                    }
                )
            } catch (e: Exception) {
                appendLog("ERROR: Failed to print receipt - ${e.message}")
                Log.e(TAG, "Print receipt error", e)
            }
        }
    }

    /**
     * Print pending receipt (from read data)
     * Called when user confirms print dialog
     */
    fun printPendingReceipt() {
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
     * Checks printer status before printing
     */
    fun confirmPrint() {
        viewModelScope.launch {
            val printer = printerViewModel
            if (printer == null) {
                appendLog("ERROR: Printer not configured")
                skipPrint() // Skip to save dialog if no printer
                return@launch
            }

            // Check printer status before attempting to print
            PrinterStatusHelper.checkPrinterReadyAndExecute(
                printerViewModel = printer,
                onNotConnected = {
                    appendLog("ERROR: Printer not connected - skipping print")
                    skipPrint() // Skip to save dialog
                },
                onNotReady = { reason ->
                    appendLog("ERROR: Printer not ready - $reason")
                    skipPrint() // Skip to save dialog
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
        _showPrinterErrorDialog.value = false
        confirmPrint() // Try again
    }

    /**
     * Cancel print from error dialog
     * Closes error dialog and goes to save dialog
     */
    fun cancelPrintFromError() {
        _showPrinterErrorDialog.value = false
        dismissPrintDialog()
        _showSaveDialog.value = true
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
    fun dismissPrintDialog() {
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
     * Show save dialog
     */
    fun showSaveDialog() {
        _showSaveDialog.value = true
    }

    /**
     * Clear pending billing data
     */
    fun clearPendingBillingData() {
        _pendingBillingData.value = null
    }
}