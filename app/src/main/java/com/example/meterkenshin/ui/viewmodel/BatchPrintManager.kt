package com.example.meterkenshin.ui.viewmodel

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import com.example.meterkenshin.model.Meter
import com.example.meterkenshin.utils.InspectionStatus
import com.example.meterkenshin.utils.PrinterStatusHelper
import com.example.meterkenshin.utils.getInspectionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Batch Print Manager
 * Handles batch printing operations for meters with existing billing data
 * Separated from BatchProcessingManager for cleaner separation of concerns
 */
class BatchPrintManager(
    private val dlmsViewModel: DLMSViewModel,
    private val meterReadingViewModel: MeterReadingViewModel,
    private val scope: CoroutineScope,
    private val context: Context
) {
    companion object {
        private const val TAG = "BatchPrintManager"
        private const val PRINT_DELAY_MS = 2000L
        private const val TOTAL_STEPS = 3
    }

    // State flows
    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _processedCount = MutableStateFlow(0)
    val processedCount: StateFlow<Int> = _processedCount.asStateFlow()

    private val _totalCount = MutableStateFlow(0)
    val totalCount: StateFlow<Int> = _totalCount.asStateFlow()

    private val _failedMeters = MutableStateFlow<List<String>>(emptyList())

    private val _currentMeterSerial = MutableStateFlow<String?>(null)
    val currentMeterSerial: StateFlow<String?> = _currentMeterSerial.asStateFlow()

    private val _currentStep = MutableStateFlow(0)

    private val _currentStepDescription = MutableStateFlow("")
    val currentStepDescription: StateFlow<String> = _currentStepDescription.asStateFlow()

    private val _errorCount = MutableStateFlow(0)
    val errorCount: StateFlow<Int> = _errorCount.asStateFlow()

    // Printer error dialog states
    private val _showPrinterErrorDialog = MutableStateFlow(false)
    val showPrinterErrorDialog: StateFlow<Boolean> = _showPrinterErrorDialog.asStateFlow()

    private val _printerErrorMessage = MutableStateFlow("")
    val printerErrorMessage: StateFlow<String> = _printerErrorMessage.asStateFlow()

    // Real-time printer status for error dialog
    private val _printerPaperStatus = MutableStateFlow(PrinterBluetoothViewModel.PaperStatus.UNKNOWN)

    private val _printerCoverStatus = MutableStateFlow(PrinterBluetoothViewModel.CoverStatus.UNKNOWN)

    private var printerViewModel: PrinterBluetoothViewModel? = null

    /**
     * Set printer ViewModel reference and collect real-time status
     */
    fun setPrinterViewModel(viewModel: PrinterBluetoothViewModel) {
        this.printerViewModel = viewModel

        // Collect real-time printer status for error dialog
        scope.launch {
            viewModel.paperStatus.collect { _printerPaperStatus.value = it }
        }
        scope.launch {
            viewModel.coverStatus.collect { _printerCoverStatus.value = it }
        }
    }

    /**
     * Check if meter has valid billing data (either from savedBillingData OR from CSV)
     * ✅ FIXED: Now checks BOTH temporary savedBillingData AND persistent CSV data
     */
    private fun hasValidBillingData(meter: Meter): Boolean {
        // Check 1: Temporary savedBillingData (in-memory)
        val savedData = dlmsViewModel.savedBillingData.value
        if (savedData != null &&
            savedData.billing.SerialNumber == meter.serialNumber &&
            savedData.isValid()) {
            return true
        }

        // Check 2: Persistent CSV data (readDate + impKWh stored in database)
        // This covers meters with status "Inspected & Billing Printed"
        if (meter.readDate != null && meter.impKWh != null) {
            return true
        }

        return false
    }

    private val _waitingForConfirmation = MutableStateFlow(false)
    val waitingForConfirmation: StateFlow<Boolean> = _waitingForConfirmation

    private val _confirmedToPrint = MutableStateFlow(false)

    fun confirmPrint() {
        _waitingForConfirmation.value = false
        _confirmedToPrint.value = true
    }

    /**
     * Update progress with step tracking
     */
    private fun updateProgressWithStep(step: Int, description: String) {
        _currentStep.value = step
        _currentStepDescription.value = description
    }

    /**
     * Print receipt for meter using saved billing data
     */
    private suspend fun printReceipt(meter: Meter): Boolean {
        val savedData = dlmsViewModel.savedBillingData.value

        if (savedData == null || !savedData.isValid()) {
            Log.e(TAG, "No valid billing data available for printing: ${meter.serialNumber}")
            return false
        }

        Log.i(TAG, "Checking printer status for ${savedData.billing.SerialNumber}")

        val printer = printerViewModel
        if (printer == null) {
            Log.e(TAG, "Printer ViewModel not set")
            _printerErrorMessage.value = "Printer not configured"
            _showPrinterErrorDialog.value = true
            return false
        }

        var printSuccess = false

        // Check printer status using PrinterStatusHelper
        PrinterStatusHelper.checkPrinterReadyAndExecute(
            printerViewModel = printer,
            onNotConnected = {
                _printerErrorMessage.value = "Printer not connected. Please connect to printer."
                _showPrinterErrorDialog.value = true
                Log.w(TAG, "Printer not connected")
            },
            onNotReady = { reason ->
                _printerErrorMessage.value = reason
                _showPrinterErrorDialog.value = true
                Log.w(TAG, "Printer not ready: $reason")
            },
            onReady = {
                // Printer ready, print
                dlmsViewModel.triggerPrintFromBatch()
                printSuccess = true
            }
        )

        return printSuccess
    }

    /**
     * User dismisses printer error dialog and skips printing
     */
    fun dismissPrinterError() {
        _showPrinterErrorDialog.value = false
    }

    /**
     * User retries printing from error dialog
     */
    suspend fun retryPrinting(meter: Meter) {
        _showPrinterErrorDialog.value = false
        printReceipt(meter)
    }

    /**
     * Process batch printing only (no reading)
     * For meters that already have billing data
     */
    @SuppressLint("MissingPermission")
    fun processBatchPrint(
        meters: List<Meter>,
        printMode: BatchPrintMode,
        onComplete: (success: Boolean, failedMeters: List<String>) -> Unit
    ) {
        if (_isProcessing.value) {
            Log.w(TAG, "Batch printing already in progress")
            return
        }

        scope.launch {
            try {
                _isProcessing.value = true
                _processedCount.value = 1
                _totalCount.value = meters.size
                _errorCount.value = 0
                val failed = mutableListOf<String>()

                // Filter meters based on print mode
                val metersToProcess = when (printMode) {
                    BatchPrintMode.ALL -> {
                        meters.filter { meter ->
                            // Has billing data (valid saved data or has readDate)
                            val savedData = dlmsViewModel.savedBillingData.value
                            (savedData != null && savedData.billing.SerialNumber == meter.serialNumber && savedData.isValid()) ||
                                    (meter.readDate != null && meter.impKWh != null)
                        }
                    }
                    BatchPrintMode.NOT_PRINTED_ONLY -> {
                        meters.filter { meter ->
                            getInspectionStatus(meter) == InspectionStatus.INSPECTED_BILLING_NOT_PRINTED
                        }
                    }
                }

                if (metersToProcess.isEmpty()) {
                    Log.w(TAG, "No meters to process for selected print mode")
                    updateProgressWithStep(0, "⚠️ No meters available for printing")
                    onComplete(false, emptyList())
                    return@launch
                }

                _totalCount.value = metersToProcess.size
                Log.i(TAG, "Starting batch print for ${metersToProcess.size} meters (mode: $printMode)")

                for ((index, meter) in metersToProcess.withIndex()) {
                    _currentMeterSerial.value = meter.serialNumber
                    val meterNum = index + 1

                    try {
                        updateProgressWithStep(1, "[$meterNum/${metersToProcess.size}] Preparing ${meter.serialNumber}")

                        // Check if has valid billing data
                        if (!hasValidBillingData(meter)) {
                            Log.w(TAG, "No valid billing data for ${meter.serialNumber}, skipping")
                            failed.add(meter.serialNumber)
                            _errorCount.value++
                            continue
                        }

                        // Request confirmation
                        updateProgressWithStep(2, "[$meterNum/${metersToProcess.size}] Ready to print ${meter.serialNumber}")
                        _waitingForConfirmation.value = true

                        // Wait for user confirmation
                        while (_waitingForConfirmation.value) {
                            delay(500)
                        }

                        // User confirmed, now check printer status before printing
                        _confirmedToPrint.value = false

                        updateProgressWithStep(2, "[$meterNum/${metersToProcess.size}] Checking printer for ${meter.serialNumber}")

                        val printSuccess = printReceipt(meter)

                        if (!printSuccess) {
                            // Wait for user to handle printer error dialog
                            while (_showPrinterErrorDialog.value) {
                                delay(500)
                            }
                        }
                        delay(PRINT_DELAY_MS)

                        updateProgressWithStep(3, "✅ Printed ${meter.serialNumber}")
                        _processedCount.value += meterNum
                        Log.i(TAG, "Successfully printed ${meter.serialNumber}")

                    } catch (e: Exception) {
                        Log.e(TAG, "Error printing ${meter.serialNumber}: ${e.message}", e)
                        failed.add(meter.serialNumber)
                        _errorCount.value++
                        updateProgressWithStep(0, "❌ Error: ${meter.serialNumber}")
                    }
                }

                _failedMeters.value = failed
                val success = failed.isEmpty()
                _currentMeterSerial.value = null

                if (success) {
                    updateProgressWithStep(TOTAL_STEPS, "✅ All meters printed successfully!")
                    Log.i(TAG, "Batch printing completed successfully")
                } else {
                    updateProgressWithStep(TOTAL_STEPS, "⚠️ Completed with ${failed.size} failures")
                    Log.w(TAG, "Batch printing completed with ${failed.size} failures: $failed")
                }

                onComplete(success, failed)

            } catch (e: Exception) {
                Log.e(TAG, "Batch print error: ${e.message}", e)
                updateProgressWithStep(0, "❌ Batch print error: ${e.message}")
                onComplete(false, emptyList())
            } finally {
                _isProcessing.value = false
                _currentMeterSerial.value = null

                withContext(Dispatchers.Main) {
                    meterReadingViewModel.reloadMeters(context)
                }
                Log.i(TAG, "Meter data reloaded after batch printing")
            }
        }
    }

    /**
     * Cancel ongoing batch printing
     */
    fun cancel() {
        if (_isProcessing.value) {
            Log.i(TAG, "Cancelling batch printing")
            _isProcessing.value = false
            _currentMeterSerial.value = null
        }
    }

    /**
     * Reset all state
     */
    fun reset() {
        _isProcessing.value = false
        _processedCount.value = 1
        _totalCount.value = 0
        _failedMeters.value = emptyList()
        _currentMeterSerial.value = null
        _currentStep.value = 0
        _currentStepDescription.value = ""
        _errorCount.value = 0
        _showPrinterErrorDialog.value = false
        _printerErrorMessage.value = ""
    }
}

/**
 * Batch print modes
 */
enum class BatchPrintMode {
    ALL,                // Print all meters with available billing data
    NOT_PRINTED_ONLY    // Print only "Inspected, Billing not Printed" status meters
}