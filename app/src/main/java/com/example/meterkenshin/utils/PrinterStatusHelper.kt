package com.example.meterkenshin.utils

import android.util.Log
import com.example.meterkenshin.printer.BluetoothPrinterManager
import com.example.meterkenshin.ui.viewmodel.PrinterBluetoothViewModel
import kotlinx.coroutines.delay

/**
 * Universal helper for checking printer status before printing operations
 * Extracts common printer checking logic for reusability across the app
 */
object PrinterStatusHelper {

    private const val TAG = "PrinterStatusHelper"
    private const val STATUS_CHECK_DELAY = 500L

    /**
     * Check printer readiness and execute action if ready
     *
     * @param printerViewModel The PrinterBluetoothViewModel instance
     * @param onNotConnected Callback when printer is not connected
     * @param onNotReady Callback when printer is connected but not ready (paper out, cover open, etc.)
     * @param onReady Callback to execute when printer is ready
     */
    suspend fun checkPrinterReadyAndExecute(
        printerViewModel: PrinterBluetoothViewModel,
        onNotConnected: () -> Unit = {},
        onNotReady: (reason: String) -> Unit = {},
        onReady: suspend () -> Unit
    ) {
        Log.d(TAG, "Starting printer status check")

        // Check if printer is connected
        val connectionState = printerViewModel.connectionState.value
        if (connectionState != BluetoothPrinterManager.ConnectionState.CONNECTED) {
            Log.w(TAG, "Printer not connected - state: $connectionState")
            onNotConnected()
            return
        }

        Log.d(TAG, "Printer connected, checking detailed status")

        // Request printer status
        printerViewModel.startStatusMonitoring()
        delay(STATUS_CHECK_DELAY) // Wait for status response

        // Check paper status
        val paperStatus = printerViewModel.paperStatus.value
        if (paperStatus == PrinterBluetoothViewModel.PaperStatus.OUT) {
            Log.w(TAG, "Printer paper out")
            onNotReady("Paper out. Please load paper.")
            return
        }

        // Check cover status
        val coverStatus = printerViewModel.coverStatus.value
        if (coverStatus == PrinterBluetoothViewModel.CoverStatus.OPEN) {
            Log.w(TAG, "Printer cover open")
            onNotReady("Cover open. Please close printer cover.")
            return
        }

        // All checks passed - printer is ready
        Log.d(TAG, "Printer ready, executing action")
        onReady()
    }

    /**
     * Simple check if printer is connected (no detailed status check)
     * Useful for quick UI state updates
     *
     * @param printerViewModel The PrinterBluetoothViewModel instance
     * @return true if printer is connected
     */
    fun isPrinterConnected(printerViewModel: PrinterBluetoothViewModel): Boolean {
        return printerViewModel.connectionState.value == BluetoothPrinterManager.ConnectionState.CONNECTED
    }

    /**
     * Get human-readable printer status message
     *
     * @param printerViewModel The PrinterBluetoothViewModel instance
     * @return Status message string
     */
    fun getPrinterStatusMessage(printerViewModel: PrinterBluetoothViewModel): String {
        val connectionState = printerViewModel.connectionState.value
        if (connectionState != BluetoothPrinterManager.ConnectionState.CONNECTED) {
            return "Printer not connected"
        }

        val paperStatus = printerViewModel.paperStatus.value
        val coverStatus = printerViewModel.coverStatus.value

        return when {
            paperStatus == PrinterBluetoothViewModel.PaperStatus.OUT -> "Paper out"
            coverStatus == PrinterBluetoothViewModel.CoverStatus.OPEN -> "Cover open"
            else -> "Printer ready"
        }
    }
}