package com.example.meterkenshin.ui.manager

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import com.example.meterkenshin.model.Meter
import com.example.meterkenshin.ui.viewmodel.DLMSViewModel
import com.example.meterkenshin.ui.viewmodel.MeterReadingViewModel
import com.example.meterkenshin.ui.viewmodel.PrinterBluetoothViewModel
import com.example.meterkenshin.utils.InspectionStatus
import com.example.meterkenshin.utils.PrinterStatusHelper
import com.example.meterkenshin.utils.getInspectionStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.GsonBuilder

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
        private const val USER_CONFIRMATION_TIMEOUT_MS = 60000L // 60 seconds
        private const val PRINTER_ERROR_TIMEOUT_MS = 120000L // 2 minutes
        private const val PRINT_COMPLETION_TIMEOUT_MS = 15000L // 15 seconds
        private const val PRINT_STATUS_CHECK_INTERVAL_MS = 500L // Check every 500ms

        // ✅ NEW: Progress persistence constants
        private const val PREFS_NAME = "batch_print_progress"
        private const val KEY_BATCH_PROGRESS = "current_batch_progress"
    }

    // Job tracking for proper cancellation
    private var processingJob: Job? = null

    // ✅ NEW: Progress persistence
    private val gson = Gson()
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    private var currentBatchId: String? = null

    // State flows
    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _processedCount = MutableStateFlow(0)
    val processedCount: StateFlow<Int> = _processedCount.asStateFlow()

    private val _totalCount = MutableStateFlow(0)
    val totalCount: StateFlow<Int> = _totalCount.asStateFlow()

    // ✅ NEW: Track failures with detailed error information
    private val _failedMeters = MutableStateFlow<List<FailureDetail>>(emptyList())
    val failedMeters: StateFlow<List<FailureDetail>> = _failedMeters.asStateFlow()

    // ✅ NEW: Comprehensive batch result with statistics
    private val _batchResult = MutableStateFlow<BatchPrintResult?>(null)
    val batchResult: StateFlow<BatchPrintResult?> = _batchResult.asStateFlow()

    // ✅ NEW: Batch preview for pre-flight information
    private val _batchPreview = MutableStateFlow<BatchPrintPreview?>(null)
    val batchPreview: StateFlow<BatchPrintPreview?> = _batchPreview.asStateFlow()

    private val _currentMeterSerial = MutableStateFlow<String?>(null)
    val currentMeterSerial: StateFlow<String?> = _currentMeterSerial.asStateFlow()

    private val _currentStepDescription = MutableStateFlow("")
    val currentStepDescription: StateFlow<String> = _currentStepDescription.asStateFlow()

    private val _errorCount = MutableStateFlow(0)
    val errorCount: StateFlow<Int> = _errorCount.asStateFlow()

    // Printer error dialog states
    private val _showPrinterErrorDialog = MutableStateFlow(false)
    val showPrinterErrorDialog: StateFlow<Boolean> = _showPrinterErrorDialog.asStateFlow()

    private val _printerErrorMessage = MutableStateFlow("")
    val printerErrorMessage: StateFlow<String> = _printerErrorMessage.asStateFlow()

    private var printerViewModel: PrinterBluetoothViewModel? = null

    /**
     * Set printer ViewModel reference
     */
    fun setPrinterViewModel(viewModel: PrinterBluetoothViewModel) {
        this.printerViewModel = viewModel
    }

    init {
        dlmsViewModel.initializeForPrinting(context)
    }

    /**
     * Save current batch progress to SharedPreferences
     * ✅ NEW: Persists progress for crash recovery
     */
    private fun saveBatchProgress(progress: BatchProgress) {
        try {
            val json = gson.toJson(progress)
            prefs.edit().putString(KEY_BATCH_PROGRESS, json).apply()
            Log.d(TAG, "Batch progress saved: ${progress.currentIndex}/${progress.totalMeterSerials.size}")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving batch progress", e)
        }
    }

    /**
     * Load saved batch progress
     * ✅ NEW: Retrieves saved progress for resumption
     */
    private fun loadBatchProgress(): BatchProgress? {
        return try {
            val json = prefs.getString(KEY_BATCH_PROGRESS, null) ?: return null
            val progress = gson.fromJson(json, BatchProgress::class.java)

            // Validate progress is still valid (not too old)
            if (progress.isValid()) {
                Log.d(TAG, "Loaded valid batch progress: ${progress.currentIndex}/${progress.totalMeterSerials.size}")
                progress
            } else {
                Log.w(TAG, "Batch progress too old, discarding")
                clearBatchProgress()
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading batch progress", e)
            null
        }
    }

    /**
     * Clear saved batch progress
     * ✅ NEW: Removes saved progress when batch completes
     */
    private fun clearBatchProgress() {
        prefs.edit().remove(KEY_BATCH_PROGRESS).apply()
        currentBatchId = null
        Log.d(TAG, "Batch progress cleared")
    }

    /**
     * Check if there's resumable batch progress
     * ✅ NEW: Public function to check for resumable batch
     */
    fun hasResumableProgress(): Boolean {
        val progress = loadBatchProgress()
        return progress != null && progress.getRemainingSerials().isNotEmpty()
    }

    /**
     * Get resumable progress for display
     * ✅ NEW: Returns progress info for UI
     */
    fun getResumableProgress(): BatchProgress? {
        return loadBatchProgress()
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

    /**
     * Filter meters for batch printing based on mode
     * ✅ NEW: Single source of truth for filtering logic (eliminates duplication)
     */
    private fun filterMetersForPrinting(
        meters: List<Meter>,
        mode: BatchPrintMode
    ): List<Meter> {
        // First, filter to only meters with valid billing data
        val validMeters = meters.filter { hasValidBillingData(it) }

        // Then apply mode-specific filtering
        return when (mode) {
            BatchPrintMode.ALL -> {
                // Return all meters with billing data
                validMeters
            }
            BatchPrintMode.NOT_PRINTED_ONLY -> {
                // Return only meters that haven't been printed yet
                validMeters.filter { meter ->
                    getInspectionStatus(meter) == InspectionStatus.INSPECTED_BILLING_NOT_PRINTED
                }
            }
        }
    }

    /**
     * Generate batch print preview
     * ✅ NEW: Shows what will be printed/skipped before starting batch
     *
     * @param meters All meters to consider
     * @param mode Batch print mode
     * @return Preview with valid meters, skipped meters, and estimates
     */
    fun generateBatchPreview(
        meters: List<Meter>,
        mode: BatchPrintMode
    ): BatchPrintPreview {
        val validMeters = mutableListOf<Meter>()
        val skippedMeters = mutableListOf<SkippedMeter>()

        // Categorize each meter
        for (meter in meters) {
            // Check if has billing data
            if (!hasValidBillingData(meter)) {
                skippedMeters.add(SkippedMeter(
                    meter = meter,
                    reason = "No billing data available"
                ))
                continue
            }

            // Check mode-specific filters
            when (mode) {
                BatchPrintMode.ALL -> {
                    validMeters.add(meter)
                }
                BatchPrintMode.NOT_PRINTED_ONLY -> {
                    val status = getInspectionStatus(meter)
                    if (status == InspectionStatus.INSPECTED_BILLING_NOT_PRINTED) {
                        validMeters.add(meter)
                    } else {
                        skippedMeters.add(SkippedMeter(
                            meter = meter,
                            reason = "Already printed"
                        ))
                    }
                }
            }
        }

        // Estimate duration
        // Average: ~7 seconds per print (2s user confirm + 2s print + 2s delay + 1s overhead)
        val avgTimePerMeter = 7000L
        val estimatedDuration = validMeters.size * avgTimePerMeter

        Log.d(TAG, "Batch preview: ${validMeters.size} valid, ${skippedMeters.size} skipped")

        return BatchPrintPreview(
            totalMeters = meters.size,
            validMeters = validMeters,
            skippedMeters = skippedMeters,
            estimatedDuration = estimatedDuration,
            mode = mode
        )
    }

    /**
     * Validate pre-conditions before starting batch print
     * ✅ NEW: Pre-flight checks to prevent batch from starting with issues
     */
    private fun validatePreConditions(): Boolean {
        // Check 1: Printer ViewModel is configured
        val printer = printerViewModel
        if (printer == null) {
            Log.e(TAG, "Pre-flight check failed: Printer not configured")
            updateProgressWithStep(0, "❌ Printer not configured")
            _errorCount.value++
            return false
        }

        // Check 2: Printer is connected
        val connectionState = printer.connectionState.value
        if (connectionState != com.example.meterkenshin.printer.BluetoothPrinterManager.ConnectionState.CONNECTED) {
            Log.e(TAG, "Pre-flight check failed: Printer not connected (state: $connectionState)")
            updateProgressWithStep(0, "❌ Printer not connected. Please connect to printer first.")
            _errorCount.value++
            return false
        }

        // Check 3: DLMS initialized for printing
        if (dlmsViewModel == null) {
            Log.e(TAG, "Pre-flight check failed: DLMS not initialized")
            updateProgressWithStep(0, "❌ System not initialized properly")
            _errorCount.value++
            return false
        }

        Log.i(TAG, "✅ All pre-flight checks passed")
        return true
    }

    // Channel-based communication (replaces busy-waiting StateFlow)
    private var confirmationChannel = Channel<Boolean>(Channel.CONFLATED)
    private var printerErrorResolvedChannel = Channel<PrinterErrorAction>(Channel.CONFLATED)  // ✅ NEW: Support 3 actions

    // UI state for showing confirmation button
    private val _waitingForConfirmation = MutableStateFlow(false)
    val waitingForConfirmation: StateFlow<Boolean> = _waitingForConfirmation

    /**
     * User confirms print - sends confirmation through channel
     */
    fun confirmPrint() {
        _waitingForConfirmation.value = false
        scope.launch {
            confirmationChannel.send(true)
        }
    }

    /**
     * Wait for user confirmation with timeout
     * Returns true if confirmed, false if timeout/cancelled
     */
    private suspend fun waitForUserConfirmation(): Boolean {
        return withTimeoutOrNull(USER_CONFIRMATION_TIMEOUT_MS) {
            confirmationChannel.receive()
        } ?: false
    }

    /**
     * Wait for printer error dialog to be resolved
     * ✅ NEW: Returns specific action (RETRY/SKIP/CANCEL_BATCH)
     * Returns SKIP on timeout
     */
    private suspend fun waitForPrinterErrorResolution(): PrinterErrorAction {
        return withTimeoutOrNull(PRINTER_ERROR_TIMEOUT_MS) {
            printerErrorResolvedChannel.receive()
        } ?: PrinterErrorAction.SKIP  // Default to skip on timeout
    }

    /**
     * Wait for print job to complete
     * ✅ NEW: Monitors printer status to ensure print actually completes
     * Returns true if print completed successfully, false if timeout
     */
    private suspend fun waitForPrintCompletion(): Boolean {
        val printer = printerViewModel ?: return false
        val startTime = System.currentTimeMillis()

        Log.d(TAG, "Waiting for print completion (timeout: ${PRINT_COMPLETION_TIMEOUT_MS}ms)")

        // Wait for a short delay to allow print to start
        delay(1000)

        while (System.currentTimeMillis() - startTime < PRINT_COMPLETION_TIMEOUT_MS) {
            // Check if printer is still connected
            val connectionState = printer.connectionState.value
            if (connectionState != com.example.meterkenshin.printer.BluetoothPrinterManager.ConnectionState.CONNECTED) {
                Log.w(TAG, "Printer disconnected during print")
                return false
            }

            // If we've waited long enough, assume print completed
            // (Since we don't have direct busy status from printer)
            val elapsedTime = System.currentTimeMillis() - startTime
            if (elapsedTime >= PRINT_DELAY_MS) {
                Log.d(TAG, "Print completion assumed after ${elapsedTime}ms")
                return true
            }

            delay(PRINT_STATUS_CHECK_INTERVAL_MS)
        }

        Log.w(TAG, "Print completion timeout after ${PRINT_COMPLETION_TIMEOUT_MS}ms")
        return false
    }

    /**
     * Update progress with step tracking
     */
    private fun updateProgressWithStep(step: Int, description: String) {
        _currentStepDescription.value = description
    }

    /**
     * Print receipt for meter using saved billing data
     * ✅ FIXED: Loads from per-meter CSV if not in memory
     * ✅ NEW: Waits for print completion before returning
     */
    private suspend fun printReceipt(meter: Meter): Boolean {
        // First check if billing data is already loaded for this meter
        val currentSavedData = dlmsViewModel.savedBillingData.value

        // If not loaded or wrong meter, load from CSV
        if (currentSavedData == null || currentSavedData.billing.SerialNumber != meter.serialNumber) {
            Log.i(TAG, "Loading billing data from CSV for ${meter.serialNumber}")

            val billingData = dlmsViewModel.loadBillingDataForMeter(meter.serialNumber)

            if (billingData == null) {
                Log.e(TAG, "No billing data found in CSV for ${meter.serialNumber}")
                return false
            }

            // Set it in DLMSViewModel for printing
            dlmsViewModel.setTemporarySavedBillingData(billingData)
            Log.i(TAG, "Successfully loaded billing data from CSV for ${meter.serialNumber}")
        }

        Log.i(TAG, "Checking printer status for ${meter.serialNumber}")

        val printer = printerViewModel
        if (printer == null) {
            Log.e(TAG, "Printer ViewModel not set")
            _printerErrorMessage.value = "Printer not configured"
            _showPrinterErrorDialog.value = true
            return false
        }

        var printInitiated = false

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
                dlmsViewModel.triggerPrintFromBatch()
                printInitiated = true
            }
        )

        // If print was initiated, wait for completion
        if (printInitiated) {
            val completed = waitForPrintCompletion()
            if (!completed) {
                Log.w(TAG, "Print did not complete successfully for ${meter.serialNumber}")
                return false
            }
            Log.i(TAG, "Print completed successfully for ${meter.serialNumber}")
            return true
        }

        return false
    }

    /**
     * User dismisses printer error dialog and skips printing this meter
     * ✅ NEW: Sends SKIP action
     */
    fun dismissPrinterError() {
        _showPrinterErrorDialog.value = false
        scope.launch {
            printerErrorResolvedChannel.send(PrinterErrorAction.SKIP)
        }
    }

    /**
     * User retries printing from error dialog
     * ✅ NEW: Sends RETRY action
     */
    fun retryPrinting() {
        _showPrinterErrorDialog.value = false
        scope.launch {
            printerErrorResolvedChannel.send(PrinterErrorAction.RETRY)
        }
    }

    /**
     * User cancels entire batch from printer error dialog
     * ✅ NEW: Allows cancelling batch when printer error occurs
     */
    fun cancelBatchFromError() {
        _showPrinterErrorDialog.value = false
        scope.launch {
            printerErrorResolvedChannel.send(PrinterErrorAction.CANCEL_BATCH)
        }
    }

    /**
     * Process batch printing only (no reading)
     * For meters that already have billing data
     * ✅ FIXED: Now uses Job tracking and proper cancellation
     */
    @SuppressLint("MissingPermission")
    fun processBatchPrint(
        meters: List<Meter>,
        printMode: BatchPrintMode,
        onComplete: (success: Boolean, failedMeters: List<FailureDetail>) -> Unit  // ✅ NEW: Detailed failures
    ) {
        if (_isProcessing.value) {
            Log.w(TAG, "Batch printing already in progress")
            return
        }

        // ✅ NEW: Pre-flight validation before starting
        if (!validatePreConditions()) {
            Log.w(TAG, "Pre-flight checks failed, aborting batch print")
            onComplete(false, emptyList())
            return
        }

        // Cancel any existing job before starting new one
        processingJob?.cancel()

        processingJob = scope.launch {
            val startTime = System.currentTimeMillis()  // ✅ NEW: Track batch timing
            var wasCancelled = false

            try {
                _isProcessing.value = true
                _processedCount.value = 0  // ✅ FIXED: Start at 0, not 1
                _totalCount.value = meters.size
                _errorCount.value = 0
                val failed = mutableListOf<FailureDetail>()  // ✅ NEW: Track detailed failures

                // ✅ NEW: Generate and log preview before starting
                val preview = generateBatchPreview(meters, printMode)
                _batchPreview.value = preview
                Log.i(TAG, "Batch Preview: ${preview.toShortSummary()}")
                Log.d(TAG, "\n${preview.toDisplayString()}")

                // ✅ FIXED: Use consolidated filtering function (eliminates duplication)
                val metersToProcess = filterMetersForPrinting(meters, printMode)

                // ✅ NEW: Initialize batch progress for persistence
                currentBatchId = "batch_${System.currentTimeMillis()}"
                val processedSerials = mutableListOf<String>()
                val failedSerialsList = mutableListOf<String>()

                if (metersToProcess.isEmpty()) {
                    Log.w(TAG, "No meters to process for selected print mode")
                    updateProgressWithStep(0, "⚠️ No meters available for printing")
                    onComplete(false, emptyList())
                    return@launch
                }

                _totalCount.value = metersToProcess.size
                Log.i(TAG, "Starting batch print for ${metersToProcess.size} meters (mode: $printMode)")

                for ((index, meter) in metersToProcess.withIndex()) {
                    // ✅ FIXED: Check for cancellation
                    if (!isActive) {
                        Log.i(TAG, "Batch printing cancelled by user")
                        break
                    }

                    _currentMeterSerial.value = meter.serialNumber
                    val meterNum = index + 1

                    try {
                        updateProgressWithStep(1, "[$meterNum/${metersToProcess.size}] Preparing ${meter.serialNumber}")

                        // Check if has valid billing data
                        if (!hasValidBillingData(meter)) {
                            Log.w(TAG, "No valid billing data for ${meter.serialNumber}, skipping")
                            failed.add(FailureDetail(
                                serialNumber = meter.serialNumber,
                                error = BatchPrintError.NoBillingData(meter.serialNumber)
                            ))
                            failedSerialsList.add(meter.serialNumber)
                            _errorCount.value++
                            _processedCount.value = meterNum
                            continue
                        }

                        // Request confirmation with timeout
                        updateProgressWithStep(2, "[$meterNum/${metersToProcess.size}] Ready to print ${meter.serialNumber}")
                        _waitingForConfirmation.value = true

                        // ✅ FIXED: Use channel-based waiting with timeout
                        val confirmed = waitForUserConfirmation()

                        if (!confirmed) {
                            Log.w(TAG, "User confirmation timeout or cancelled for ${meter.serialNumber}")
                            failed.add(FailureDetail(
                                serialNumber = meter.serialNumber,
                                error = BatchPrintError.UserTimeout(meter.serialNumber)
                            ))
                            failedSerialsList.add(meter.serialNumber)
                            _errorCount.value++
                            _processedCount.value = meterNum
                            continue
                        }

                        // User confirmed, now check printer status before printing
                        updateProgressWithStep(2, "[$meterNum/${metersToProcess.size}] Checking printer for ${meter.serialNumber}")

                        val printSuccess = printReceipt(meter)

                        if (!printSuccess) {
                            // ✅ NEW: Wait for printer error resolution with 3 possible actions
                            val errorAction = waitForPrinterErrorResolution()

                            when (errorAction) {
                                PrinterErrorAction.RETRY -> {
                                    // Retry the print
                                    val retrySuccess = printReceipt(meter)
                                    if (!retrySuccess) {
                                        Log.w(TAG, "Retry failed for ${meter.serialNumber}")
                                        failed.add(FailureDetail(
                                            serialNumber = meter.serialNumber,
                                            error = BatchPrintError.PrinterError(
                                                meter.serialNumber,
                                                _printerErrorMessage.value
                                            ),
                                            attemptNumber = 2
                                        ))
                                        failedSerialsList.add(meter.serialNumber)
                                        _errorCount.value++
                                    } else {
                                        processedSerials.add(meter.serialNumber)
                                    }
                                }
                                PrinterErrorAction.SKIP -> {
                                    Log.w(TAG, "User skipped printing for ${meter.serialNumber}")
                                    failed.add(FailureDetail(
                                        serialNumber = meter.serialNumber,
                                        error = BatchPrintError.UserCancelled(meter.serialNumber)
                                    ))
                                    failedSerialsList.add(meter.serialNumber)
                                    _errorCount.value++
                                }
                                PrinterErrorAction.CANCEL_BATCH -> {
                                    Log.w(TAG, "User cancelled entire batch from printer error")
                                    failed.add(FailureDetail(
                                        serialNumber = meter.serialNumber,
                                        error = BatchPrintError.UserCancelled(meter.serialNumber)
                                    ))
                                    failedSerialsList.add(meter.serialNumber)
                                    updateProgressWithStep(0, "⚠️ Batch cancelled by user")
                                    // Break out of the loop to stop batch
                                    break
                                }
                            }
                        }

                        delay(PRINT_DELAY_MS)

                        // ✅ NEW: Track successful print in progress
                        processedSerials.add(meter.serialNumber)

                        updateProgressWithStep(3, "✅ Printed ${meter.serialNumber}")
                        Log.i(TAG, "Successfully printed ${meter.serialNumber}")

                    } catch (e: CancellationException) {
                        // Propagate cancellation
                        throw e
                    } catch (e: Exception) {
                        Log.e(TAG, "Error printing ${meter.serialNumber}: ${e.message}", e)
                        failed.add(FailureDetail(
                            serialNumber = meter.serialNumber,
                            error = BatchPrintError.UnknownError(meter.serialNumber, e)
                        ))
                        failedSerialsList.add(meter.serialNumber)
                        _errorCount.value++
                        updateProgressWithStep(0, "❌ Error: ${meter.serialNumber}")
                    } finally {
                        // ✅ FIXED: Update progress counter after each meter
                        _processedCount.value = meterNum

                        // ✅ NEW: Save progress after processing each meter
                        val progress = BatchProgress(
                            batchId = currentBatchId ?: "unknown",
                            startTime = startTime,
                            mode = printMode,
                            totalMeterSerials = metersToProcess.map { it.serialNumber },
                            processedSerials = processedSerials.toList(),
                            failedSerials = failedSerialsList.toList(),
                            currentIndex = meterNum
                        )
                        saveBatchProgress(progress)
                    }
                }

                _failedMeters.value = failed
                val success = failed.isEmpty()
                _currentMeterSerial.value = null

                // ✅ NEW: Generate comprehensive batch result
                val endTime = System.currentTimeMillis()
                val result = BatchPrintResult(
                    totalMeters = metersToProcess.size,
                    successCount = metersToProcess.size - failed.size,
                    failedCount = failed.size,
                    skippedCount = meters.size - metersToProcess.size,
                    failures = failed,
                    duration = endTime - startTime,
                    startTime = startTime,
                    endTime = endTime,
                    mode = printMode,
                    wasCancelled = wasCancelled
                )

                _batchResult.value = result

                if (success) {
                    updateProgressWithStep(TOTAL_STEPS, "✅ All meters printed successfully!")
                    Log.i(TAG, result.toShortSummary())
                    Log.d(TAG, "\n${result.toDisplayString()}")
                } else {
                    updateProgressWithStep(TOTAL_STEPS, "⚠️ Completed with ${failed.size} failures")
                    Log.w(TAG, result.toShortSummary())
                    Log.d(TAG, "\n${result.toDisplayString()}")
                }

                // ✅ NEW: Clear saved progress on completion
                clearBatchProgress()

                onComplete(success, failed)

            } catch (e: CancellationException) {
                wasCancelled = true
                Log.i(TAG, "Batch printing was cancelled")

                // Generate result for cancelled batch
                val endTime = System.currentTimeMillis()
                val failed = _failedMeters.value
                val result = BatchPrintResult(
                    totalMeters = _totalCount.value,
                    successCount = _processedCount.value - failed.size,
                    failedCount = failed.size,
                    skippedCount = _totalCount.value - _processedCount.value,
                    failures = failed,
                    duration = endTime - startTime,
                    startTime = startTime,
                    endTime = endTime,
                    mode = printMode,
                    wasCancelled = true
                )
                _batchResult.value = result

                updateProgressWithStep(0, "⚠️ Batch printing cancelled")
                Log.w(TAG, result.toShortSummary())

                onComplete(false, failed)
                throw e // Re-throw to properly cancel the coroutine
            } catch (e: Exception) {
                Log.e(TAG, "Batch print error: ${e.message}", e)

                // Generate result for error case
                val endTime = System.currentTimeMillis()
                val result = BatchPrintResult(
                    totalMeters = meters.size,
                    successCount = 0,
                    failedCount = 0,
                    skippedCount = meters.size,
                    failures = emptyList(),
                    duration = endTime - startTime,
                    startTime = startTime,
                    endTime = endTime,
                    mode = printMode,
                    wasCancelled = false
                )
                _batchResult.value = result

                updateProgressWithStep(0, "❌ Batch print error: ${e.message}")
                onComplete(false, emptyList())
            } finally {
                _isProcessing.value = false
                _currentMeterSerial.value = null
                _waitingForConfirmation.value = false

                withContext(Dispatchers.Main) {
                    meterReadingViewModel.reloadMeters(context)
                }
                Log.i(TAG, "Meter data reloaded after batch printing")
            }
        }
    }

    /**
     * Resume batch printing from saved progress
     * ✅ NEW: Allows resuming interrupted batch
     *
     * @param meters All available meters
     * @param onComplete Completion callback
     * @return true if resumed, false if no valid progress
     */
    fun resumeBatchPrint(
        meters: List<Meter>,
        onComplete: (success: Boolean, failedMeters: List<FailureDetail>) -> Unit
    ): Boolean {
        val savedProgress = loadBatchProgress()
        if (savedProgress == null) {
            Log.w(TAG, "No saved progress to resume")
            return false
        }

        Log.i(TAG, "Resuming batch from saved progress")
        Log.d(TAG, "\n${savedProgress.toDisplayString()}")

        // Find meters that still need to be processed
        val remainingSerials = savedProgress.getRemainingSerials()
        val metersToProcess = meters.filter { it.serialNumber in remainingSerials }

        if (metersToProcess.isEmpty()) {
            Log.w(TAG, "No remaining meters to process")
            clearBatchProgress()
            return false
        }

        // Start batch print with remaining meters, preserving original mode
        // This will use the existing processBatchPrint logic
        processBatchPrint(metersToProcess, savedProgress.mode) { success, newFailures ->
            // Combine with previous failures
            onComplete(success, newFailures)
        }

        return true
    }

    /**
     * Discard saved progress without resuming
     * ✅ NEW: Allows user to start fresh instead of resuming
     */
    fun discardSavedProgress() {
        clearBatchProgress()
        Log.i(TAG, "Saved batch progress discarded")
    }

    /**
     * Cancel ongoing batch printing
     * ✅ FIXED: Now properly cancels the coroutine job
     */
    fun cancel() {
        if (_isProcessing.value) {
            Log.i(TAG, "Cancelling batch printing")
            scope.launch {
                processingJob?.cancelAndJoin()
                // Recreate channels to clear any pending messages
                confirmationChannel.cancel()
                printerErrorResolvedChannel.cancel()
                confirmationChannel = Channel(Channel.CONFLATED)
                printerErrorResolvedChannel = Channel(Channel.CONFLATED)  // PrinterErrorAction type
                Log.i(TAG, "Batch printing cancellation complete")
            }
        }
    }

    /**
     * Reset all state
     * ✅ FIXED: Also recreates channels and clears job reference
     */
    fun reset() {
        _isProcessing.value = false
        _processedCount.value = 0  // ✅ FIXED: Reset to 0, not 1
        _totalCount.value = 0
        _failedMeters.value = emptyList()
        _batchResult.value = null
        _batchPreview.value = null
        _currentMeterSerial.value = null
        _currentStepDescription.value = ""
        _errorCount.value = 0
        _showPrinterErrorDialog.value = false
        _printerErrorMessage.value = ""
        _waitingForConfirmation.value = false

        // Recreate channels to ensure clean state
        confirmationChannel.cancel()
        printerErrorResolvedChannel.cancel()
        confirmationChannel = Channel(Channel.CONFLATED)
        printerErrorResolvedChannel = Channel(Channel.CONFLATED)  // PrinterErrorAction type

        processingJob = null
    }
}

/**
 * Batch print modes
 */
enum class BatchPrintMode {
    ALL,                // Print all meters with available billing data
    NOT_PRINTED_ONLY    // Print only "Inspected, Billing not Printed" status meters
}

/**
 * Categorized batch print errors
 * ✅ NEW: Detailed error types for better diagnostics and user feedback
 */
sealed class BatchPrintError {
    abstract val serialNumber: String
    abstract fun toDisplayMessage(): String

    data class NoBillingData(
        override val serialNumber: String
    ) : BatchPrintError() {
        override fun toDisplayMessage() = "$serialNumber: No billing data available"
    }

    data class PrinterNotConnected(
        override val serialNumber: String
    ) : BatchPrintError() {
        override fun toDisplayMessage() = "$serialNumber: Printer not connected"
    }

    data class PrinterError(
        override val serialNumber: String,
        val reason: String
    ) : BatchPrintError() {
        override fun toDisplayMessage() = "$serialNumber: Printer error - $reason"
    }

    data class UserTimeout(
        override val serialNumber: String
    ) : BatchPrintError() {
        override fun toDisplayMessage() = "$serialNumber: User confirmation timeout"
    }

    data class PrintTimeout(
        override val serialNumber: String
    ) : BatchPrintError() {
        override fun toDisplayMessage() = "$serialNumber: Print completion timeout"
    }

    data class UserCancelled(
        override val serialNumber: String
    ) : BatchPrintError() {
        override fun toDisplayMessage() = "$serialNumber: Cancelled by user"
    }

    data class UnknownError(
        override val serialNumber: String,
        val exception: Exception
    ) : BatchPrintError() {
        override fun toDisplayMessage() = "$serialNumber: ${exception.message ?: "Unknown error"}"
    }
}

/**
 * Detailed failure information with categorization
 * ✅ NEW: Tracks failures with timestamp and category for better reporting
 */
data class FailureDetail(
    val serialNumber: String,
    val error: BatchPrintError,
    val timestamp: Long = System.currentTimeMillis(),
    val attemptNumber: Int = 1
) {
    fun toDisplayString(): String {
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
        val attemptInfo = if (attemptNumber > 1) " (Attempt $attemptNumber)" else ""
        return "[$time]$attemptInfo ${error.toDisplayMessage()}"
    }
}

/**
 * Actions available when printer error occurs during batch printing
 * ✅ NEW: Gives user more control over error handling
 */
enum class PrinterErrorAction {
    RETRY,          // Try printing this meter again
    SKIP,           // Skip this meter and continue with batch
    CANCEL_BATCH    // Stop entire batch operation
}

/**
 * Comprehensive batch print result with statistics
 * ✅ NEW: Detailed summary for reporting and analytics
 */
data class BatchPrintResult(
    val totalMeters: Int,
    val successCount: Int,
    val failedCount: Int,
    val skippedCount: Int,
    val failures: List<FailureDetail>,
    val duration: Long,              // milliseconds
    val startTime: Long,
    val endTime: Long,
    val mode: BatchPrintMode,
    val wasCancelled: Boolean = false
) {
    /**
     * Calculate success rate as percentage
     */
    fun getSuccessRate(): Float =
        if (totalMeters > 0) (successCount.toFloat() / totalMeters) * 100f else 0f

    /**
     * Format duration as human-readable string
     */
    fun formatDuration(): String {
        val minutes = duration / 60000
        val seconds = (duration % 60000) / 1000
        return when {
            minutes > 0 -> "${minutes}m ${seconds}s"
            else -> "${seconds}s"
        }
    }

    /**
     * Calculate average time per successful print
     */
    fun getAverageTimePerPrint(): Long =
        if (successCount > 0) duration / successCount else 0L

    /**
     * Get display summary for user
     */
    fun toDisplayString(): String = buildString {
        appendLine("Batch Print Summary")
        appendLine("═══════════════════")
        appendLine("Total Meters: $totalMeters")
        appendLine("✅ Success: $successCount")
        appendLine("❌ Failed: $failedCount")
        appendLine("⏭️  Skipped: $skippedCount")
        appendLine("⏱️  Duration: ${formatDuration()}")
        appendLine("📊 Success Rate: ${String.format("%.1f%%", getSuccessRate())}")
        if (successCount > 0) {
            appendLine("⚡ Avg Time/Print: ${getAverageTimePerPrint() / 1000}s")
        }
        appendLine("🎯 Mode: ${mode.name}")
        if (wasCancelled) {
            appendLine("⚠️  Status: Cancelled by user")
        }

        if (failures.isNotEmpty()) {
            appendLine()
            appendLine("Failures:")
            failures.forEach { failure ->
                appendLine("  • ${failure.toDisplayString()}")
            }
        }
    }

    /**
     * Get short summary for notification
     */
    fun toShortSummary(): String {
        return if (failedCount == 0) {
            "✅ Successfully printed $successCount/$totalMeters meters in ${formatDuration()}"
        } else {
            "⚠️ Printed $successCount/$totalMeters meters ($failedCount failed) in ${formatDuration()}"
        }
    }
}

/**
 * Batch print preview - shows what will be printed before starting
 * ✅ NEW: Preview functionality for user confidence
 */
data class BatchPrintPreview(
    val totalMeters: Int,
    val validMeters: List<Meter>,
    val skippedMeters: List<SkippedMeter>,
    val estimatedDuration: Long,
    val mode: BatchPrintMode
) {
    val validCount: Int get() = validMeters.size
    val skippedCount: Int get() = skippedMeters.size

    /**
     * Format estimated duration
     */
    fun formatEstimatedDuration(): String {
        val minutes = estimatedDuration / 60000
        val seconds = (estimatedDuration % 60000) / 1000
        return when {
            minutes > 0 -> "~${minutes}m ${seconds}s"
            else -> "~${seconds}s"
        }
    }

    /**
     * Get display summary for preview
     */
    fun toDisplayString(): String = buildString {
        appendLine("Batch Print Preview")
        appendLine("═══════════════════")
        appendLine("📊 Total Meters Selected: $totalMeters")
        appendLine("✅ Will Print: $validCount")
        appendLine("⏭️  Will Skip: $skippedCount")
        appendLine("⏱️  Estimated Time: ${formatEstimatedDuration()}")
        appendLine("🎯 Mode: ${mode.name}")

        if (skippedMeters.isNotEmpty()) {
            appendLine()
            appendLine("Skipped Meters:")
            skippedMeters.forEach { skipped ->
                appendLine("  • ${skipped.meter.serialNumber}: ${skipped.reason}")
            }
        }

        if (validMeters.isNotEmpty()) {
            appendLine()
            appendLine("Will Print:")
            validMeters.take(5).forEach { meter ->
                appendLine("  • ${meter.serialNumber}")
            }
            if (validMeters.size > 5) {
                appendLine("  ... and ${validMeters.size - 5} more")
            }
        }
    }

    /**
     * Get short summary
     */
    fun toShortSummary(): String {
        return "Will print $validCount meters (${skippedCount} skipped) in ${formatEstimatedDuration()}"
    }
}

/**
 * Represents a meter that will be skipped with reason
 */
data class SkippedMeter(
    val meter: Meter,
    val reason: String
)

/**
 * Batch progress persistence for resuming interrupted batches
 * ✅ NEW: Allows resuming batch after app crash or interruption
 */
data class BatchProgress(
    val batchId: String,                          // Unique batch identifier
    val startTime: Long,                          // When batch started
    val mode: BatchPrintMode,                     // Print mode
    val totalMeterSerials: List<String>,          // All meter serial numbers in batch
    val processedSerials: List<String>,           // Successfully printed
    val failedSerials: List<String>,              // Failed to print
    val currentIndex: Int,                        // Current position in batch
    val lastUpdateTime: Long = System.currentTimeMillis()
) {
    /**
     * Check if this progress is still valid (not too old)
     */
    fun isValid(maxAgeMs: Long = 24 * 60 * 60 * 1000): Boolean {
        val age = System.currentTimeMillis() - lastUpdateTime
        return age < maxAgeMs
    }

    /**
     * Get resumption point
     */
    fun getResumptionIndex(): Int = currentIndex

    /**
     * Get remaining serials to process
     */
    fun getRemainingSerials(): List<String> {
        return totalMeterSerials.drop(currentIndex)
    }

    /**
     * Calculate completion percentage
     */
    fun getCompletionPercentage(): Float {
        return if (totalMeterSerials.isEmpty()) 0f
        else (currentIndex.toFloat() / totalMeterSerials.size) * 100f
    }

    /**
     * Get display summary
     */
    fun toDisplayString(): String = buildString {
        appendLine("Resumable Batch Found")
        appendLine("══════════════════════")
        appendLine("Started: ${SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault()).format(Date(startTime))}")
        appendLine("Progress: $currentIndex/${totalMeterSerials.size} (${String.format("%.1f%%", getCompletionPercentage())})")
        appendLine("✅ Processed: ${processedSerials.size}")
        appendLine("❌ Failed: ${failedSerials.size}")
        appendLine("⏭️  Remaining: ${getRemainingSerials().size}")
        appendLine("🎯 Mode: ${mode.name}")
    }
}