package com.example.meterkenshin.ui.viewmodel

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.meterkenshin.manager.PrinterManager
import com.example.meterkenshin.model.MeterReading
import com.example.meterkenshin.model.PrinterConnectionState
import com.example.meterkenshin.printing.ReceiptPrinter

/**
 * ViewModel for managing printer operations
 */
class PrinterViewModel : ViewModel() {

    private var printerManager: PrinterManager? = null
    private var receiptPrinter: ReceiptPrinter? = null

    // Provide default empty LiveData until PrinterManager is initialized
    private val _connectionState = MutableLiveData<PrinterConnectionState>()
    private val _toastMessage = MutableLiveData<String>()

    val connectionState: LiveData<PrinterConnectionState>
        get() = if (printerManager != null) {
            printerManager!!.connectionState
        } else {
            _connectionState.apply { value = PrinterConnectionState.DISCONNECTED }
        }

    val toastMessage: LiveData<String>
        get() = if (printerManager != null) {
            printerManager!!.toastMessage
        } else {
            _toastMessage.apply { value = null }
        }

    /**
     * Initialize printer manager
     */
    fun initializePrinterManager(context: Context) {
        try {
            if (printerManager == null) {
                printerManager = PrinterManager(context)
                printerManager?.initializePrinterServices()

                // Initialize receipt printer when print service is available
                printerManager?.getPrintService()?.let { printService ->
                    receiptPrinter = ReceiptPrinter(printService)
                }

                // Set initial state
                _connectionState.value = PrinterConnectionState.DISCONNECTED
            }
        } catch (e: Exception) {
            android.util.Log.e("PrinterViewModel", "Error initializing printer manager", e)
            _toastMessage.value = "Failed to initialize printer: ${e.message}"
        }
    }

    /**
     * Auto-connect to printer
     */
    fun autoConnectPrinter() {
        try {
            if (printerManager == null) {
                _toastMessage.value = "Printer manager not initialized"
                return
            }
            printerManager?.autoConnectPrinter()
        } catch (e: Exception) {
            android.util.Log.e("PrinterViewModel", "Error auto-connecting printer", e)
            _toastMessage.value = "Failed to connect printer: ${e.message}"
        }
    }

    /**
     * Reconnect to printer
     */
    fun reconnectPrinter() {
        try {
            if (printerManager == null) {
                _toastMessage.value = "Printer manager not initialized"
                return
            }
            printerManager?.reconnectPrinter()
        } catch (e: Exception) {
            android.util.Log.e("PrinterViewModel", "Error reconnecting printer", e)
            _toastMessage.value = "Failed to reconnect printer: ${e.message}"
        }
    }

    /**
     * Disconnect from printer
     */
    fun disconnectPrinter() {
        try {
            printerManager?.disconnectPrinter()
        } catch (e: Exception) {
            android.util.Log.e("PrinterViewModel", "Error disconnecting printer", e)
        }
    }

    /**
     * Print sample receipt
     */
    fun printSampleReceipt() {
        try {
            if (receiptPrinter == null) {
                // Try to reinitialize receipt printer
                printerManager?.getPrintService()?.let { printService ->
                    receiptPrinter = ReceiptPrinter(printService)
                }
            }

            if (receiptPrinter == null) {
                _toastMessage.value = "Printer not ready"
                return
            }

            receiptPrinter?.printSampleReceipt()
            _toastMessage.value = "Printing sample receipt..."
        } catch (e: Exception) {
            android.util.Log.e("PrinterViewModel", "Error printing sample receipt", e)
            _toastMessage.value = "Failed to print: ${e.message}"
        }
    }

    /**
     * Print meter reading receipt
     */
    fun printMeterReceipt(meterReading: MeterReading, rates: FloatArray? = null) {
        try {
            if (receiptPrinter == null) {
                // Try to reinitialize receipt printer
                printerManager?.getPrintService()?.let { printService ->
                    receiptPrinter = ReceiptPrinter(printService)
                }
            }

            if (receiptPrinter == null) {
                _toastMessage.value = "Printer not ready"
                return
            }

            receiptPrinter?.printMeterReceipt(meterReading, rates)
            _toastMessage.value = "Printing meter receipt..."
        } catch (e: Exception) {
            android.util.Log.e("PrinterViewModel", "Error printing meter receipt", e)
            _toastMessage.value = "Failed to print: ${e.message}"
        }
    }

    /**
     * Check if printer is connected
     */
    fun isConnected(): Boolean {
        return try {
            printerManager?.isConnected() == true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Check if Bluetooth is enabled
     */
    fun isBluetoothEnabled(): Boolean {
        return try {
            printerManager?.isBluetoothEnabled() == true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Check if printer manager is initialized
     */
    fun isInitialized(): Boolean {
        return printerManager != null
    }

    override fun onCleared() {
        super.onCleared()
        try {
            printerManager?.cleanup()
        } catch (e: Exception) {
            android.util.Log.e("PrinterViewModel", "Error cleaning up printer manager", e)
        }
    }
}