package com.example.meterkenshin.printer

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.meterkenshin.data.parser.PrinterCsvParser
import com.woosim.printer.WoosimService

/**
 * Enhanced Bluetooth Manager using WoosimLib
 */
class BluetoothPrinterManager(context: Context) {

    companion object {
        private const val TAG = "BluetoothPrinterManager"
    }

    enum class ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        ERROR
    }

    private val bluetoothManager: BluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter

    // Use WoosimBluetoothService
    private var printService: WoosimBluetoothService? = null

    // WoosimService for processing received data
    private var woosimService: WoosimService? = null

    private val printerCsvParser = PrinterCsvParser(context)

    // LiveData for observing state
    private val _connectionState = MutableLiveData<ConnectionState>()
    val connectionState: LiveData<ConnectionState> = _connectionState

    private val _statusMessage = MutableLiveData<String>()
    val statusMessage: LiveData<String> = _statusMessage

    private val _connectedDevice = MutableLiveData<BluetoothDevice?>()
    val connectedDevice: LiveData<BluetoothDevice?> = _connectedDevice

    // Add callback for status parsing
    private var statusCallback: ((ByteArray) -> Unit)? = null

    fun setStatusCallback(callback: (ByteArray) -> Unit) {
        statusCallback = callback
    }

    // Handler for Woosim callbacks
    private val handler = Handler(Looper.getMainLooper()) { msg ->
        when (msg.what) {
            WoosimBluetoothService.MESSAGE_STATE_CHANGE -> {
                handleStateChange(msg.arg1)
            }
            WoosimBluetoothService.MESSAGE_READ -> {
                val data = msg.obj as ByteArray
                val length = msg.arg1

                // CRITICAL: Parse status response FIRST before WoosimService processes it
                // Status responses are single byte responses (0x30-0x33)
                if (length == 1 && (data[0].toInt() and 0x30) == 0x30) {
                    Log.d(TAG, "Status response detected: 0x${String.format("%02X", data[0])}")
                    statusCallback?.invoke(data.copyOf(length))
                }

                // Then process with WoosimService for other data (MSR, etc.)
                woosimService?.processRcvData(data, length)
            }
            WoosimBluetoothService.MESSAGE_DEVICE_NAME -> {
                val deviceName = msg.data.getString(WoosimBluetoothService.DEVICE_NAME)
                updateStatus("Connected to $deviceName")
            }
            WoosimBluetoothService.MESSAGE_TOAST -> {
                val message = msg.data.getString(WoosimBluetoothService.TOAST)
                updateStatus(message ?: "Connection error")
            }
        }
        true
    }

    init {
        _connectionState.value = ConnectionState.DISCONNECTED
        _statusMessage.value = "Not connected"

        // Initialize WoosimService
        woosimService = WoosimService(Handler(Looper.getMainLooper()) { msg ->
            when (msg.what) {
                WoosimService.MESSAGE_PRINTER -> {
                    Log.d(TAG, "Printer message received: ${msg.arg1}")
                }
            }
            true
        })

        // Initialize print service immediately
        printService = WoosimBluetoothService(handler)
        printService?.start()

        Log.d(TAG, "BluetoothPrinterManager initialized with WoosimLib")
    }

    private fun handleStateChange(state: Int) {
        when (state) {
            WoosimBluetoothService.STATE_CONNECTED -> {
                _connectionState.value = ConnectionState.CONNECTED
                val printerConfig = printerCsvParser.getActivePrinterConfig()
                val printerInfo = if (printerConfig != null) {
                    "Connected to ${printerConfig.printerModel ?: "Printer"}\nMAC: ${printerConfig.bluetoothMacAddress}"
                } else {
                    "Connected to printer"
                }
                updateStatus(printerInfo)
            }
            WoosimBluetoothService.STATE_CONNECTING -> {
                _connectionState.value = ConnectionState.CONNECTING
                updateStatus("Connecting to printer...")
            }
            WoosimBluetoothService.STATE_NONE -> {
                _connectionState.value = ConnectionState.DISCONNECTED
                updateStatus("Disconnected")
            }
        }
    }

    fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled == true
    }

    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_CONNECT])
    fun connectToPrinter(device: BluetoothDevice) {
        Log.d(TAG, "Connecting to device: ${device.address}")
        _connectedDevice.value = device
        printService?.connect(device)
    }

    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN])
    fun autoConnect(): Boolean {
        if (!isBluetoothEnabled()) {
            Log.e(TAG, "Bluetooth not enabled")
            updateStatus("Bluetooth not enabled")
            _connectionState.value = ConnectionState.ERROR
            return false
        }

        if (!printerCsvParser.isPrinterCsvAvailable()) {
            Log.e(TAG, "Printer CSV file not found")
            updateStatus("No printer.csv found. Please upload printer configuration.")
            return false
        }

        val targetMac = printerCsvParser.getActivePrinterMacAddress()
        if (targetMac == null) {
            Log.e(TAG, "No active printer found in CSV")
            updateStatus("No active printer in printer.csv (set Activate=1)")
            return false
        }

        Log.d(TAG, "Auto-connecting to MAC: $targetMac")
        val device = findDeviceByMacAddress(targetMac)
        if (device != null) {
            connectToPrinter(device)
            return true
        }

        Log.e(TAG, "Device not found: $targetMac")
        updateStatus("Printer not found. Make sure it's paired.")
        return false
    }

    fun disconnect() {
        printService?.stop()
        _connectedDevice.value = null
        _connectionState.value = ConnectionState.DISCONNECTED
        updateStatus("Disconnected")
        Log.d(TAG, "Disconnected from printer")
    }

    fun sendData(data: ByteArray) {
        if (_connectionState.value != ConnectionState.CONNECTED) {
            Log.w(TAG, "Cannot send data: Not connected")
            return
        }
        printService?.write(data)
    }


    fun getPrinterConfigInfo(): String {
        return printerCsvParser.getConfigurationSummary()
    }

    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_CONNECT])
    private fun findDeviceByMacAddress(macAddress: String): BluetoothDevice? {
        val pairedDevices = bluetoothAdapter?.bondedDevices
        return pairedDevices?.find {
            it.address.equals(macAddress, ignoreCase = true)
        }
    }

    private fun updateStatus(message: String) {
        _statusMessage.postValue(message)
        Log.d(TAG, "Status: $message")
    }

    fun cleanup() {
        printService?.stop()
        printService = null
        woosimService = null
        _connectedDevice.value = null
        _connectionState.value = ConnectionState.DISCONNECTED
    }
}