package com.example.meterkenshin.manager

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.meterkenshin.bluetooth.BluetoothPrintService
import com.example.meterkenshin.model.PrinterConnectionState
import com.example.meterkenshin.woosim.WoosimService
import java.io.File

/**
 * Manager class for handling printer connections and operations
 */
class PrinterManager(private val context: Context) {

    companion object {
        private const val TAG = "PrinterManager"
        private const val PRINTER_CSV_FILE = "printer.csv"
    }

    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var printService: BluetoothPrintService? = null
    private var woosimService: WoosimService? = null

    private val _connectionState = MutableLiveData<PrinterConnectionState>()
    val connectionState: LiveData<PrinterConnectionState> = _connectionState

    private val _toastMessage = MutableLiveData<String>()
    val toastMessage: LiveData<String> = _toastMessage

    private val handler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                BluetoothPrintService.MESSAGE_STATE_CHANGE -> {
                    when (msg.arg1) {
                        BluetoothPrintService.STATE_CONNECTED -> {
                            _connectionState.value = PrinterConnectionState.CONNECTED
                            _toastMessage.value = "Printer connected"
                        }

                        BluetoothPrintService.STATE_CONNECTING -> {
                            _connectionState.value = PrinterConnectionState.CONNECTING
                        }

                        BluetoothPrintService.STATE_LISTEN, BluetoothPrintService.STATE_NONE -> {
                            _connectionState.value = PrinterConnectionState.DISCONNECTED
                        }
                    }
                }

                BluetoothPrintService.MESSAGE_DEVICE_NAME -> {
                    val deviceName = msg.data.getString(BluetoothPrintService.DEVICE_NAME)
                    _toastMessage.value = "Connected to $deviceName"
                }

                BluetoothPrintService.MESSAGE_TOAST -> {
                    val toast = msg.data.getString(BluetoothPrintService.TOAST)
                    _toastMessage.value = toast ?: "Unknown error"
                    _connectionState.value = PrinterConnectionState.DISCONNECTED
                }
            }
        }
    }

    init {
        _connectionState.value = PrinterConnectionState.DISCONNECTED
    }

    /**
     * Initialize printer services
     */
    fun initializePrinterServices() {
        if (bluetoothAdapter?.isEnabled == true) {
            if (printService == null) {
                printService = BluetoothPrintService(handler, context)
                woosimService = WoosimService(handler)
            } else {
                if (printService?.getState() == BluetoothPrintService.STATE_NONE) {
                    printService?.start()
                }
            }
        }
    }

    /**
     * Auto-connect to printer using address from CSV
     */
    fun autoConnectPrinter() {
        val printerAddress = getPrinterAddressFromCsv()
        if (printerAddress.isNotEmpty()) {
            connectToPrinter(printerAddress)
        } else {
            _toastMessage.value = "No printer address found in CSV"
        }
    }

    /**
     * Connect to printer with specific address
     */
    fun connectToPrinter(address: String) {
        if (bluetoothAdapter?.isEnabled != true) {
            _toastMessage.value = "Bluetooth is not enabled"
            return
        }

        try {
            val device: BluetoothDevice = bluetoothAdapter.getRemoteDevice(address)
            printService?.connect(device, false)
        } catch (e: Exception) {
            Log.e(TAG, "Error connecting to printer", e)
            _toastMessage.value = "Failed to connect to printer"
        }
    }

    /**
     * Disconnect from printer
     */
    fun disconnectPrinter() {
        printService?.stop()
        _connectionState.value = PrinterConnectionState.DISCONNECTED
    }

    /**
     * Reconnect to printer
     */
    fun reconnectPrinter() {
        disconnectPrinter()
        // Wait a moment before reconnecting
        Handler(Looper.getMainLooper()).postDelayed({
            autoConnectPrinter()
        }, 1000)
    }

    /**
     * Get printer service for printing operations
     */
    fun getPrintService(): BluetoothPrintService? {
        return printService
    }

    /**
     * Check if printer is connected
     */
    fun isConnected(): Boolean {
        return printService?.getState() == BluetoothPrintService.STATE_CONNECTED
    }

    /**
     * Get printer MAC address from CSV file
     */
    private fun getPrinterAddressFromCsv(): String {
        try {
            val filesDir = context.filesDir
            val csvFile = File(filesDir, PRINTER_CSV_FILE)

            if (!csvFile.exists()) {
                Log.w(TAG, "Printer CSV file not found")
                return ""
            }

            val lines = csvFile.readLines()
            if (lines.size >= 2) {
                // Skip header line, get first data line
                val dataLine = lines[1]
                val columns = dataLine.split(",")

                // Assuming format: Activate,Bluetooth ID
                if (columns.size >= 2) {
                    val activate = columns[0].trim()
                    val bluetoothId = columns[1].trim()

                    // Check if printer is activated
                    if (activate == "1") {
                        return bluetoothId
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading printer CSV", e)
        }

        return ""
    }

    /**
     * Check if Bluetooth is enabled
     */
    fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled == true
    }

    /**
     * Clean up resources
     */
    fun cleanup() {
        printService?.stop()
        printService = null
        woosimService = null
    }
}