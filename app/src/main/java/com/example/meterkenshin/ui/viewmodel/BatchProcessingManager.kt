package com.example.meterkenshin.ui.viewmodel

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.example.meterkenshin.model.Meter
import com.example.meterkenshin.utils.PrinterStatusHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Batch Processing Manager with Embedded User Options
 * No separate dialogs - print/save options are radio buttons in progress dialog
 *
 * FIXED: Corrected method signatures for readData, saveStoredBillingToJSON, and printReceipt
 */
class BatchProcessingManager(
    private val dlmsViewModel: DLMSViewModel,
    private val meterReadingViewModel: MeterReadingViewModel,
    private val scope: CoroutineScope,
    private val context: Context
) {
    companion object {
        private const val TAG = "BatchProcessing"
        private const val READ_TIMEOUT_SECONDS = 60
        private const val PRINT_DELAY_MS = 2000L
        private const val OPERATION_DELAY_MS = 500L
        private const val TOTAL_STEPS = 7
    }

    // State flows
    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _currentProgress = MutableStateFlow("")

    private val _processedCount = MutableStateFlow(0)
    val processedCount: StateFlow<Int> = _processedCount.asStateFlow()

    private val _totalCount = MutableStateFlow(0)
    val totalCount: StateFlow<Int> = _totalCount.asStateFlow()

    private val _failedMeters = MutableStateFlow<List<String>>(emptyList())

    // User action states
    private val _currentMeterSerial = MutableStateFlow<String?>(null)
    val currentMeterSerial: StateFlow<String?> = _currentMeterSerial.asStateFlow()

    private val _awaitingUserAction = MutableStateFlow(false)
    val awaitingUserAction: StateFlow<Boolean> = _awaitingUserAction.asStateFlow()

    private val _userActionRequired = MutableStateFlow<UserActionType?>(null)

    // ⭐ NEW: Step progress tracking
    private val _currentStep = MutableStateFlow(0)
    val currentStep: StateFlow<Int> = _currentStep.asStateFlow()

    private val _currentStepDescription = MutableStateFlow("")
    val currentStepDescription: StateFlow<String> = _currentStepDescription.asStateFlow()

    // ⭐ NEW: Error count tracking
    private val _errorCount = MutableStateFlow(0)
    val errorCount: StateFlow<Int> = _errorCount.asStateFlow()

    // User selections (default to both enabled)
    private val _shouldPrint = MutableStateFlow(true)
    val shouldPrint: StateFlow<Boolean> = _shouldPrint.asStateFlow()

    private val _shouldSaveJson = MutableStateFlow(true)
    val shouldSaveJson: StateFlow<Boolean> = _shouldSaveJson.asStateFlow()

    private var userActionCallback: (() -> Unit)? = null

    enum class UserActionType {
        PRINT_AND_SAVE_OPTIONS
    }

    // ADD: Printer error dialog states
    private val _showPrinterErrorDialog = MutableStateFlow(false)
    val showPrinterErrorDialog: StateFlow<Boolean> = _showPrinterErrorDialog.asStateFlow()

    private val _printerErrorMessage = MutableStateFlow("")
    val printerErrorMessage: StateFlow<String> = _printerErrorMessage.asStateFlow()

    private var printerViewModel: PrinterBluetoothViewModel? = null

    // ADD: Set printer ViewModel
    fun setPrinterViewModel(viewModel: PrinterBluetoothViewModel) {
        this.printerViewModel = viewModel
    }

    /**
     * Update print option
     */
    fun setPrintOption(enabled: Boolean) {
        _shouldPrint.value = enabled
    }

    /**
     * Update save JSON option
     */
    fun setSaveJsonOption(enabled: Boolean) {
        _shouldSaveJson.value = enabled
    }

    /**
     * User confirms their choices (print/save)
     */
    fun confirmUserAction() {
        Log.i(TAG, "User confirmed: print=${_shouldPrint.value}, saveJson=${_shouldSaveJson.value}")
        _awaitingUserAction.value = false
        _userActionRequired.value = null
        userActionCallback?.invoke()
        userActionCallback = null
    }

    /**
     * Check if meter has valid saved billing data
     */
    private fun hasValidBillingData(meter: Meter): Boolean {
        val savedData = dlmsViewModel.savedBillingData.value
        return savedData != null &&
                savedData.billing.SerialNumber == meter.serialNumber &&
                savedData.isValid()
    }

    /**
     * Update progress with step tracking
     */
    private fun updateProgressWithStep(step: Int, description: String) {
        _currentStep.value = step
        _currentStepDescription.value = description
        _currentProgress.value = description
    }

    /**
     * Main batch processing function
     */
    @SuppressLint("MissingPermission")
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun processBatch(
        meters: List<Meter>,
        rates: FloatArray,
        onComplete: (success: Boolean, failedMeters: List<String>) -> Unit
    ) {
        if (_isProcessing.value) {
            Log.w(TAG, "Batch processing already running")
            return
        }

        scope.launch {
            try {
                _isProcessing.value = true
                _processedCount.value = 0
                _totalCount.value = meters.size
                _failedMeters.value = emptyList()
                _errorCount.value = 0
                val failed = mutableListOf<String>()

                meterReadingViewModel.stopBLEScanning()

                updateProgressWithStep(0, "Starting batch processing for ${meters.size} meters...")
                Log.i(TAG, "Starting batch processing for ${meters.size} meters")

                for ((index, meter) in meters.withIndex()) {
                    val meterNum = index + 1
                    _currentMeterSerial.value = meter.serialNumber

                    try {
                        // Step 1: Initialize DLMS
                        updateProgressWithStep(1, "Initializing DLMS connection...")
                        Log.i(TAG, "Processing meter $meterNum/${meters.size}: ${meter.serialNumber}")
                        dlmsViewModel.initializeDLMS(context, meter)
                        delay(1000)

                        // Step 2: Check existing data
                        updateProgressWithStep(2, "Checking for existing data...")
                        val hasValidData = hasValidBillingData(meter)

                        if (!hasValidData) {
                            // Step 3: Read meter data
                            updateProgressWithStep(3, "Reading meter data...")
                            Log.i(TAG, "Reading data for ${meter.serialNumber}")
                            dlmsViewModel.readData(meter, rates)

                            // Wait for read operation
                            if (!waitForOperationComplete()) {
                                Log.e(TAG, "Read timeout for ${meter.serialNumber}")
                                failed.add(meter.serialNumber)
                                _errorCount.value++
                                updateProgressWithStep(0, "⚠️ Timeout reading ${meter.serialNumber}")
                                continue
                            }

                            // Step 4: Finalizing read
                            updateProgressWithStep(4, "Finalizing data read...")
                            delay(2000)
                            Log.i(TAG, "DLMS finalization delay complete for ${meter.serialNumber}")
                            delay(OPERATION_DELAY_MS)
                        } else {
                            // Skip to step 4 if data already exists
                            updateProgressWithStep(4, "Using existing data...")
                        }

                        // Step 5: Await user action
                        updateProgressWithStep(5, "Awaiting user confirmation...")
                        if (!waitForUserAction()) {
                            Log.e(TAG, "User action timeout for ${meter.serialNumber}")
                            failed.add(meter.serialNumber)
                            _errorCount.value++
                            updateProgressWithStep(0, "⚠️ User action timeout for ${meter.serialNumber}")
                            continue
                        }

                        // Step 6: Execute actions
                        if (_shouldPrint.value || _shouldSaveJson.value) {
                            updateProgressWithStep(6, "Executing selected actions...")

                            if (_shouldPrint.value) {
                                Log.i(TAG, "Printing receipt for ${meter.serialNumber}")
                                val printSuccess = printReceipt(meter)

                                if (!printSuccess) {
                                    // Wait for user to handle printer error
                                    while (_showPrinterErrorDialog.value) {
                                        delay(500)
                                    }
                                }
                                delay(PRINT_DELAY_MS)
                            }

                            if (_shouldSaveJson.value) {
                                Log.i(TAG, "Saving JSON for ${meter.serialNumber}")
                                saveJson()
                                delay(500)
                            }
                        }

                        // Step 7: Complete
                        updateProgressWithStep(7, "✅ Completed ${meter.serialNumber}")
                        _processedCount.value = meterNum
                        Log.i(TAG, "Successfully processed ${meter.serialNumber}")

                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing meter ${meter.serialNumber}: ${e.message}", e)
                        failed.add(meter.serialNumber)
                        _errorCount.value++
                        updateProgressWithStep(0, "❌ Error: ${meter.serialNumber}")
                    }
                }

                _failedMeters.value = failed
                val success = failed.isEmpty()
                _currentMeterSerial.value = null

                if (success) {
                    updateProgressWithStep(TOTAL_STEPS, "✅ All meters processed successfully!")
                    Log.i(TAG, "Batch processing completed successfully")
                } else {
                    updateProgressWithStep(TOTAL_STEPS, "⚠️ Completed with ${failed.size} failures")
                    Log.w(TAG, "Batch processing completed with ${failed.size} failures: $failed")
                }

                onComplete(success, failed)

            } catch (e: Exception) {
                Log.e(TAG, "Batch processing error: ${e.message}", e)
                updateProgressWithStep(0, "❌ Batch processing error: ${e.message}")
                onComplete(false, emptyList())
            } finally {
                meterReadingViewModel.startBLEScanning()
                _isProcessing.value = false
                _awaitingUserAction.value = false
                _userActionRequired.value = null
                _currentMeterSerial.value = null

                withContext(Dispatchers.Main) {
                    meterReadingViewModel.reloadMeters(context)
                }
                Log.i(TAG, "Meter data reloaded after batch processing")
            }
        }
    }

    /**
     * Wait for user to make print/save choices and confirm
     */
    private suspend fun waitForUserAction(): Boolean {
        _awaitingUserAction.value = true
        _userActionRequired.value = UserActionType.PRINT_AND_SAVE_OPTIONS

        var confirmed = false
        userActionCallback = {
            confirmed = true
        }

        // Wait up to 5 minutes for user to confirm
        var waitCount = 0
        while (_awaitingUserAction.value && waitCount < 300) {
            delay(1000)
            waitCount++
        }

        return confirmed
    }

    /**
     * Wait for DLMS operation to complete
     */
    private suspend fun waitForOperationComplete(): Boolean {
        var waitCount = 0
        val registrationState = dlmsViewModel.registrationState

        // Wait for operation to start first (give it 2 seconds)
        var startWaitCount = 0
        while (!registrationState.value.isRunning && startWaitCount < 2) {
            delay(1000)
            startWaitCount++
        }

        // Now wait for it to complete
        while (registrationState.value.isRunning && waitCount < READ_TIMEOUT_SECONDS) {
            delay(1000)
            waitCount++
        }

        // ⭐ FIX: Check if we have valid billing data instead of just state
        val hasValidData = dlmsViewModel.savedBillingData.value?.isValid() == true

        Log.d(TAG, "Operation complete check: isComplete=${registrationState.value.isComplete}, " +
                "isRunning=${registrationState.value.isRunning}, hasValidData=$hasValidData")

        // Success if we have valid data OR state shows complete
        return hasValidData || (registrationState.value.isComplete && !registrationState.value.isRunning)
    }

    /**
     * Print receipt for meter using saved billing data
     * FIXED: triggerPrintFromBatch already checks printer status via PrinterStatusHelper
     */
    private suspend fun printReceipt(meter: Meter): Boolean {
        val savedData = dlmsViewModel.savedBillingData.value

        if (savedData == null || !savedData.isValid()) {
            Log.e(TAG, "No valid billing data available for printing: ${meter.serialNumber}")
            updateProgress("ERROR: No billing data")
            return false
        }

        Log.i(TAG, "Checking printer status for ${savedData.billing.SerialNumber}")

        val printer = printerViewModel
        if (printer == null) {
            Log.e(TAG, "Printer ViewModel not set")
            return false
        }

        var printSuccess = false

        // Check printer status using PrinterStatusHelper
        PrinterStatusHelper.checkPrinterReadyAndExecute(
            printerViewModel = printer,
            onNotConnected = {
                _printerErrorMessage.value = "Printer not connected"
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
     * Save JSON for current meter
     * FIXED: Uses saveStoredBillingToJSON from DLMSViewModel
     */
    private fun saveJson() {
        val savedData = dlmsViewModel.savedBillingData.value

        if (savedData != null && savedData.isValid()) {
            Log.i(TAG, "Saving JSON for ${savedData.billing.SerialNumber}")
            // FIXED: Use the correct method name from DLMSViewModel
            dlmsViewModel.saveStoredBillingToJSON()
        } else {
            Log.e(TAG, "No valid billing data available for saving JSON")
        }
    }

    /**
     * Update progress message
     */
    private fun updateProgress(message: String) {
        _currentProgress.value = message
    }

    /**
     * Cancel ongoing batch processing
     */
    fun cancel() {
        if (_isProcessing.value) {
            Log.i(TAG, "Cancelling batch processing")
            _isProcessing.value = false
            _awaitingUserAction.value = false
            _userActionRequired.value = null
            _currentMeterSerial.value = null
            updateProgress("Batch processing cancelled")
        }
    }

    /**
     * Reset all state
     */
    fun reset() {
        _isProcessing.value = false
        _currentProgress.value = ""
        _processedCount.value = 0
        _totalCount.value = 0
        _failedMeters.value = emptyList()
        _awaitingUserAction.value = false
        _userActionRequired.value = null
        _currentMeterSerial.value = null
        _shouldPrint.value = true
        _shouldSaveJson.value = true
        userActionCallback = null
    }
}