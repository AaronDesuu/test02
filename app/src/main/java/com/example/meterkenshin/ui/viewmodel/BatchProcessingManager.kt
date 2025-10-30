package com.example.meterkenshin.ui.viewmodel

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.example.meterkenshin.model.Meter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Batch Processing Manager for Select & Print functionality
 * Handles sequential meter reading and printing operations
 */
class BatchProcessingManager(
    private val dlmsViewModel: DLMSViewModel,
    private val scope: CoroutineScope,
    private val context: Context
) {
    companion object {
        private const val TAG = "BatchProcessing"
        private const val READ_TIMEOUT_SECONDS = 60
        private const val DIALOG_TIMEOUT_SECONDS = 300 // 5 minutes for user to respond
        private const val PRINT_DELAY_MS = 2000L
        private const val OPERATION_DELAY_MS = 500L
    }

    // State flows
    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _currentProgress = MutableStateFlow("")
    val currentProgress: StateFlow<String> = _currentProgress.asStateFlow()

    private val _processedCount = MutableStateFlow(0)
    val processedCount: StateFlow<Int> = _processedCount.asStateFlow()

    private val _totalCount = MutableStateFlow(0)
    val totalCount: StateFlow<Int> = _totalCount.asStateFlow()

    private val _failedMeters = MutableStateFlow<List<String>>(emptyList())
    val failedMeters: StateFlow<List<String>> = _failedMeters.asStateFlow()

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
     * Main batch processing function
     * Processes selected meters: reads data if needed, then prints receipt
     */
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
                val failed = mutableListOf<String>()

                updateProgress("Starting batch processing for ${meters.size} meters...")
                Log.i(TAG, "Starting batch processing for ${meters.size} meters")

                for ((index, meter) in meters.withIndex()) {
                    val meterNum = index + 1
                    updateProgress("[$meterNum/${meters.size}] Processing: ${meter.serialNumber}")
                    Log.i(TAG, "Processing meter $meterNum/${meters.size}: ${meter.serialNumber}")

                    try {
                        // Initialize DLMS for this meter
                        updateProgress("[$meterNum/${meters.size}] Initializing DLMS: ${meter.serialNumber}")
                        Log.i(TAG, "Initializing DLMS for ${meter.serialNumber}")
                        dlmsViewModel.initializeDLMS(context, meter)
                        delay(1000)

                        // Check if billing data exists
                        val hasValidData = hasValidBillingData(meter)

                        if (!hasValidData) {
                            // Read data if needed
                            updateProgress("[$meterNum/${meters.size}] Reading data: ${meter.serialNumber}")
                            Log.i(TAG, "No valid billing data, performing readData for ${meter.serialNumber}")

                            dlmsViewModel.readData(meter, rates)

                            // Wait for readData to complete
                            if (!waitForOperationComplete()) {
                                Log.e(TAG, "ReadData failed or timeout for ${meter.serialNumber}")
                                failed.add(meter.serialNumber)
                                updateProgress("[$meterNum/${meters.size}] FAILED: ${meter.serialNumber}")
                                continue
                            }

                            Log.i(TAG, "ReadData completed successfully for ${meter.serialNumber}")
                            delay(500)

                            // PAUSE: Wait for user to handle print dialog
                            updateProgress("[$meterNum/${meters.size}] Waiting for print confirmation: ${meter.serialNumber}")
                            Log.i(TAG, "Waiting for print dialog interaction for ${meter.serialNumber}")

                            if (!waitForPrintDialog()) {
                                Log.e(TAG, "Print dialog timeout for ${meter.serialNumber}")
                                failed.add(meter.serialNumber)
                                updateProgress("[$meterNum/${meters.size}] TIMEOUT: ${meter.serialNumber}")
                                continue
                            }

                            Log.i(TAG, "Print dialog handled for ${meter.serialNumber}")
                            delay(PRINT_DELAY_MS)

                            // PAUSE: Wait for user to handle save dialog
                            updateProgress("[$meterNum/${meters.size}] Waiting for save confirmation: ${meter.serialNumber}")
                            Log.i(TAG, "Waiting for save dialog interaction for ${meter.serialNumber}")

                            if (!waitForSaveDialog()) {
                                Log.e(TAG, "Save dialog timeout for ${meter.serialNumber}")
                                failed.add(meter.serialNumber)
                                updateProgress("[$meterNum/${meters.size}] TIMEOUT: ${meter.serialNumber}")
                                continue
                            }

                            Log.i(TAG, "Save dialog handled for ${meter.serialNumber}")

                        } else {
                            // Meter already has valid data, just print
                            Log.i(TAG, "Valid billing data found for ${meter.serialNumber}, printing directly")
                            updateProgress("[$meterNum/${meters.size}] Printing: ${meter.serialNumber}")

                            printReceipt(meter)
                            delay(PRINT_DELAY_MS)
                        }

                        _processedCount.value = meterNum
                        updateProgress("[$meterNum/${meters.size}] Complete: ${meter.serialNumber}")
                        Log.i(TAG, "Successfully processed ${meter.serialNumber}")

                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing meter ${meter.serialNumber}: ${e.message}", e)
                        failed.add(meter.serialNumber)
                        updateProgress("[$meterNum/${meters.size}] ERROR: ${meter.serialNumber}")
                    }
                }

                _failedMeters.value = failed
                val success = failed.isEmpty()

                if (success) {
                    updateProgress("✅ Batch processing complete! All ${meters.size} meters processed.")
                    Log.i(TAG, "Batch processing completed successfully")
                } else {
                    updateProgress("⚠️ Completed with ${failed.size} failures")
                    Log.w(TAG, "Batch processing completed with ${failed.size} failures: $failed")
                }

                onComplete(success, failed)

            } catch (e: Exception) {
                Log.e(TAG, "Batch processing error: ${e.message}", e)
                updateProgress("❌ Batch processing error: ${e.message}")
                onComplete(false, emptyList())
            } finally {
                _isProcessing.value = false
            }
        }
    }

    /**
     * Wait for DLMS operation to complete
     * Returns true if successful, false if failed or timeout
     */
    private suspend fun waitForOperationComplete(): Boolean {
        var waitCount = 0
        val registrationState = dlmsViewModel.registrationState

        while (registrationState.value.isRunning && waitCount < READ_TIMEOUT_SECONDS) {
            delay(1000)
            waitCount++
        }

        return registrationState.value.isComplete && !registrationState.value.isRunning
    }

    /**
     * Wait for print dialog to be handled by user
     * Returns true if dialog was handled, false if timeout
     */
    private suspend fun waitForPrintDialog(): Boolean {
        var waitCount = 0

        // Wait for print dialog to appear
        while (!dlmsViewModel.showPrintDialog.value && waitCount < 10) {
            delay(500)
            waitCount++
        }

        if (!dlmsViewModel.showPrintDialog.value) {
            Log.w(TAG, "Print dialog never appeared")
            return false
        }

        // Wait for user to handle the dialog (dialog disappears)
        waitCount = 0
        while (dlmsViewModel.showPrintDialog.value && waitCount < DIALOG_TIMEOUT_SECONDS) {
            delay(1000)
            waitCount++
        }

        return !dlmsViewModel.showPrintDialog.value
    }

    /**
     * Wait for save dialog to be handled by user
     * Returns true if dialog was handled, false if timeout
     */
    private suspend fun waitForSaveDialog(): Boolean {
        var waitCount = 0

        // Wait for save dialog to appear
        while (!dlmsViewModel.showSaveDialog.value && waitCount < 10) {
            delay(500)
            waitCount++
        }

        if (!dlmsViewModel.showSaveDialog.value) {
            Log.w(TAG, "Save dialog never appeared")
            return false
        }

        // Wait for user to handle the dialog (dialog disappears)
        waitCount = 0
        while (dlmsViewModel.showSaveDialog.value && waitCount < DIALOG_TIMEOUT_SECONDS) {
            delay(1000)
            waitCount++
        }

        return !dlmsViewModel.showSaveDialog.value
    }

    /**
     * Print receipt for meter using saved billing data
     */
    private fun printReceipt(meter: Meter) {
        val savedData = dlmsViewModel.savedBillingData.value

        if (savedData != null && savedData.isValid()) {
            Log.i(TAG, "Initiating print for ${savedData.billing.SerialNumber}")
            dlmsViewModel.triggerPrintFromBatch()
        } else {
            Log.e(TAG, "No valid billing data available for printing: ${meter.serialNumber}")
            throw IllegalStateException("No valid billing data for ${meter.serialNumber}")
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
    }
}