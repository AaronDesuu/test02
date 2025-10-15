package com.example.meterkenshin.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Observer
import androidx.lifecycle.viewModelScope
import com.example.meterkenshin.printer.BluetoothPrinterManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for managing Bluetooth printer connection state in Compose UI
 * Updated for WoosimLib integration
 */
class PrinterBluetoothViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "PrinterBluetoothViewModel"
    }

    private var bluetoothPrinterManager: BluetoothPrinterManager? = null

    // State flows for Compose
    private val _connectionState = MutableStateFlow<BluetoothPrinterManager.ConnectionState?>(null)
    val connectionState: StateFlow<BluetoothPrinterManager.ConnectionState?> = _connectionState.asStateFlow()

    private val _isBluetoothEnabled = MutableStateFlow(false)
    val isBluetoothEnabled: StateFlow<Boolean> = _isBluetoothEnabled.asStateFlow()

    private val _connectedDevice = MutableStateFlow<android.bluetooth.BluetoothDevice?>(null)
    val connectedDevice: StateFlow<android.bluetooth.BluetoothDevice?> = _connectedDevice.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    private val _printerConfigInfo = MutableStateFlow<String?>(null)
    val printerConfigInfo: StateFlow<String?> = _printerConfigInfo.asStateFlow()

    private val _isAutoConnecting = MutableStateFlow(false)
    val isAutoConnecting: StateFlow<Boolean> = _isAutoConnecting.asStateFlow()

    // Computed property for printer connection status
    private val _isPrinterConnected = MutableStateFlow(false)
    val isPrinterConnected: StateFlow<Boolean> = _isPrinterConnected.asStateFlow()

    // LiveData observers to prevent memory leaks
    private var connectionStateObserver: Observer<BluetoothPrinterManager.ConnectionState>? = null
    private var connectedDeviceObserver: Observer<android.bluetooth.BluetoothDevice?>? = null
    private var statusMessageObserver: Observer<String>? = null

    init {
        // Initialize connection status tracking
        viewModelScope.launch {
            _connectionState.collect { state ->
                _isPrinterConnected.value = state == BluetoothPrinterManager.ConnectionState.CONNECTED
            }
        }
    }

    /**
     * Initialize the Bluetooth manager
     */
    fun initializeBluetoothManager(manager: BluetoothPrinterManager) {
        if (bluetoothPrinterManager != null) {
            Log.w(TAG, "Bluetooth manager already initialized")
            return
        }

        bluetoothPrinterManager = manager
        Log.d(TAG, "Initializing Bluetooth manager")

        try {
            // Update Bluetooth enabled state
            _isBluetoothEnabled.value = manager.isBluetoothEnabled()

            // Connection state observer
            connectionStateObserver = Observer { state ->
                Log.d(TAG, "Connection state changed: $state")
                _connectionState.value = state
                _isAutoConnecting.value = state == BluetoothPrinterManager.ConnectionState.CONNECTING
            }
            manager.connectionState.observeForever(connectionStateObserver!!)

            // Connected device observer
            connectedDeviceObserver = Observer { device ->
                Log.d(TAG, "Connected device: ${device?.address ?: "None"}")
                _connectedDevice.value = device
            }
            manager.connectedDevice.observeForever(connectedDeviceObserver!!)

            // Status message observer
            statusMessageObserver = Observer { message ->
                Log.d(TAG, "Status message: $message")
                _statusMessage.value = message
            }
            manager.statusMessage.observeForever(statusMessageObserver!!)

            // Update printer configuration info
            updatePrinterConfigInfo()

            _isInitialized.value = true
            Log.d(TAG, "Bluetooth manager initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Bluetooth manager", e)
            _statusMessage.value = "Initialization error: ${e.message}"
        }
    }

    /**
     * Start auto-connection process
     */
    fun startAutoConnect() {
        Log.d(TAG, "Starting auto-connect")
        viewModelScope.launch {
            try {
                _isAutoConnecting.value = true
                val success = bluetoothPrinterManager?.autoConnect() ?: false
                if (!success) {
                    _isAutoConnecting.value = false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during auto-connect", e)
                _statusMessage.value = "Auto-connect error: ${e.message}"
                _isAutoConnecting.value = false
            }
        }
    }

    /**
     * Disconnect from current device
     */
    fun disconnect() {
        Log.d(TAG, "Disconnecting from printer")
        viewModelScope.launch {
            try {
                bluetoothPrinterManager?.disconnect()
            } catch (e: Exception) {
                Log.e(TAG, "Error disconnecting", e)
                _statusMessage.value = "Disconnect error: ${e.message}"
            }
        }
    }

    /**
     * Send data to printer (for WoosimLib integration)
     * This is the main method to use for printing
     */
    fun sendDataToPrinter(data: ByteArray) {
        viewModelScope.launch {
            try {
                // Check Bluetooth enabled
                if (!_isBluetoothEnabled.value) {
                    val message = "Bluetooth not enabled"
                    Log.w(TAG, message)
                    _statusMessage.value = message
                    return@launch
                }

                // Check printer connected
                if (_connectionState.value != BluetoothPrinterManager.ConnectionState.CONNECTED) {
                    val message = "Printer not connected"
                    Log.w(TAG, message)
                    _statusMessage.value = message
                    return@launch
                }

                // Send data using sendData() method from BluetoothPrinterManager
                Log.d(TAG, "Sending ${data.size} bytes to printer")
                bluetoothPrinterManager?.sendData(data)

            } catch (e: Exception) {
                val message = "Print error: ${e.message}"
                Log.e(TAG, message, e)
                _statusMessage.value = message
            }
        }
    }

    /**
     * Write data to connected printer (legacy method for compatibility)
     * Use sendDataToPrinter() instead for new code
     */
    fun writeData(data: ByteArray): Boolean {
        return try {
            if (_connectionState.value != BluetoothPrinterManager.ConnectionState.CONNECTED) {
                Log.w(TAG, "Cannot write: printer not connected")
                return false
            }

            Log.d(TAG, "Writing ${data.size} bytes to printer")
            bluetoothPrinterManager?.sendData(data)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Write error", e)
            _statusMessage.value = "Write error: ${e.message}"
            false
        }
    }

    /**
     * Update printer configuration information
     */
    private fun updatePrinterConfigInfo() {
        try {
            _printerConfigInfo.value = bluetoothPrinterManager?.getPrinterConfigInfo()
            Log.d(TAG, "Printer config: ${_printerConfigInfo.value}")
        } catch (e: Exception) {
            Log.e(TAG, "Error getting printer config", e)
        }
    }

    /**
     * Refresh printer configuration info
     */
    fun refreshPrinterConfig() {
        updatePrinterConfigInfo()
    }

    /**
     * Check if there's a connection error
     */
    fun hasConnectionError(): Boolean {
        return _connectionState.value == BluetoothPrinterManager.ConnectionState.ERROR
    }

    /**
     * Retry connection after error
     */
    fun retryConnection() {
        if (hasConnectionError()) {
            Log.d(TAG, "Retrying connection")
            startAutoConnect()
        } else {
            Log.w(TAG, "Retry called but no error state")
        }
    }

    /**
     * Print sample data to test printer connection
     */
    fun printSampleData(): Boolean {
        Log.d(TAG, "Printing sample data")
        val sampleData = buildString {
            append("=== SAMPLE PRINT TEST ===\n")
            append("Woosim Printer Connected\n")
            append("Test successful!\n")
            append("\n\n\n")
        }.toByteArray()

        return writeData(sampleData)
    }

    /**
     * Get current connection status as string
     */
    fun getConnectionStatusText(): String {
        return when (_connectionState.value) {
            BluetoothPrinterManager.ConnectionState.CONNECTED -> "Connected"
            BluetoothPrinterManager.ConnectionState.CONNECTING -> "Connecting..."
            BluetoothPrinterManager.ConnectionState.DISCONNECTED -> "Disconnected"
            BluetoothPrinterManager.ConnectionState.ERROR -> "Error"
            null -> "Not initialized"
        }
    }

    /**
     * Check if printer is ready for printing
     */
    fun isPrinterReady(): Boolean {
        return _isBluetoothEnabled.value == true &&
                _isPrinterConnected.value == true
    }

    /**
     * Get detailed printer info for display
     */
    fun getPrinterInfo(): String {
        return buildString {
            append("Bluetooth: ${if (_isBluetoothEnabled.value) "Enabled" else "Disabled"}\n")
            append("Status: ${getConnectionStatusText()}\n")

            _connectedDevice.value?.let { device ->
                append("Device: ${device.address}\n")
            }

            _printerConfigInfo.value?.let { config ->
                append("Config: $config\n")
            }
        }
    }

    /**
     * Get printer configuration
     */
    fun getPrinterConfiguration(): com.example.meterkenshin.data.parser.PrinterCsvParser.PrinterConfig? {
        return bluetoothPrinterManager?.getPrinterConfiguration()
    }

    /**
     * Check if Bluetooth is enabled
     */
    fun checkBluetoothEnabled() {
        bluetoothPrinterManager?.let { manager ->
            _isBluetoothEnabled.value = manager.isBluetoothEnabled()
        }
    }

    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "ViewModel cleared, cleaning up")

        try {
            // Remove LiveData observers
            bluetoothPrinterManager?.let { manager ->
                connectionStateObserver?.let { manager.connectionState.removeObserver(it) }
                connectedDeviceObserver?.let { manager.connectedDevice.removeObserver(it) }
                statusMessageObserver?.let { manager.statusMessage.removeObserver(it) }
            }

            // Cleanup manager
            bluetoothPrinterManager?.cleanup()
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup", e)
        }
    }
}