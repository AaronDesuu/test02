package com.example.meterkenshin.ui.viewmodel

import android.annotation.SuppressLint
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
import android.os.Handler
import android.os.Looper
import com.woosim.printer.WoosimCmd

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

    private val _printerConfigInfo = MutableStateFlow<String?>(null)

    private val _isAutoConnecting = MutableStateFlow(false)

    // Computed property for printer connection status
    private val _isPrinterConnected = MutableStateFlow(false)

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

    private val _paperStatus = MutableStateFlow<PaperStatus>(PaperStatus.UNKNOWN)
    val paperStatus: StateFlow<PaperStatus> = _paperStatus.asStateFlow()

    private val _coverStatus = MutableStateFlow<CoverStatus>(CoverStatus.UNKNOWN)
    val coverStatus: StateFlow<CoverStatus> = _coverStatus.asStateFlow()

    enum class PaperStatus { OK, OUT, UNKNOWN }
    enum class CoverStatus { CLOSED, OPEN, UNKNOWN }

    // Status monitoring
    private var statusHandler: Handler? = null
    private var statusRunnable: Runnable? = null
    private val statusCheckInterval = 5000L
    private var statusWaiting = 0

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

        // Set status callback
        manager.setStatusCallback { data ->
            parseStatusResponse(data)
        }

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
    @SuppressLint("MissingPermission")
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


    fun startStatusMonitoring() {
        if (statusHandler != null) {
            Log.w(TAG, "Status monitoring already running")
            return
        }

        statusWaiting = 0 // Reset counter
        statusHandler = Handler(Looper.getMainLooper())
        statusRunnable = object : Runnable {
            override fun run() {
                if (_connectionState.value == BluetoothPrinterManager.ConnectionState.CONNECTED) {
                    requestPrinterStatus()
                    statusHandler?.postDelayed(this, statusCheckInterval)
                }
            }
        }
        statusHandler?.post(statusRunnable!!)
        Log.d(TAG, "Status monitoring started - querying every ${statusCheckInterval}ms")
    }

    fun stopStatusMonitoring() {
        statusHandler?.removeCallbacks(statusRunnable!!)
        statusHandler = null
        Log.d(TAG, "Status monitoring stopped")
    }

    private fun requestPrinterStatus() {
        statusWaiting++
        sendDataToPrinter(WoosimCmd.queryStatus())
        Log.d(TAG, "Status query sent, waiting=$statusWaiting")
    }

    fun parseStatusResponse(data: ByteArray) {
        if (data.isEmpty()) return

        // Check if we're expecting a status response
        if (statusWaiting > 0 && data.size == 1 && (data[0].toInt() and 0x30) == 0x30) {
            statusWaiting--

            val status = data[0].toInt() and 0xFF

            Log.d(TAG, "Parsing status byte: 0x${String.format("%02X", status)}")

            when (status) {
                0x30 -> { // OK
                    _paperStatus.value = PaperStatus.OK
                    _coverStatus.value = CoverStatus.CLOSED
                    Log.d(TAG, "Status: Paper OK, Cover Closed")
                }
                0x31 -> { // Paper out
                    _paperStatus.value = PaperStatus.OUT
                    _coverStatus.value = CoverStatus.CLOSED
                    Log.d(TAG, "Status: Paper OUT, Cover Closed")
                }
                0x32 -> { // Cover open
                    _paperStatus.value = PaperStatus.OK
                    _coverStatus.value = CoverStatus.OPEN
                    Log.d(TAG, "Status: Paper OK, Cover OPEN")
                }
                0x33 -> { // Both
                    _paperStatus.value = PaperStatus.OUT
                    _coverStatus.value = CoverStatus.OPEN
                    Log.d(TAG, "Status: Paper OUT, Cover OPEN")
                }
                else -> {
                    // Bit parsing for other printer models (from samples)
                    if ((status and 0x01) != 0) {
                        _paperStatus.value = PaperStatus.OUT
                    } else {
                        _paperStatus.value = PaperStatus.OK
                    }
                    if ((status and 0x02) != 0) {
                        _coverStatus.value = CoverStatus.OPEN
                    } else {
                        _coverStatus.value = CoverStatus.CLOSED
                    }
                    Log.d(TAG, "Status (bit parsing): Paper=${_paperStatus.value}, Cover=${_coverStatus.value}")
                }
            }
        }
    }
    /**
     * Request immediate printer status check (for on-demand checking before print)
     * PUBLIC method that can be called from UI
     * Returns true if request was sent successfully
     */
    fun checkPrinterStatusNow(): Boolean {
        if (_connectionState.value != BluetoothPrinterManager.ConnectionState.CONNECTED) {
            Log.w(TAG, "Cannot check status: Printer not connected")
            return false
        }

        Log.d(TAG, "Requesting immediate printer status check")

        // Increment statusWaiting counter so parseStatusResponse will process the response
        statusWaiting++

        // Send query command to printer
        sendDataToPrinter(WoosimCmd.queryStatus())

        Log.d(TAG, "Status query sent, waiting=$statusWaiting")
        return true
    }

    /**
     * Get current printer status summary
     * Returns a Pair of (isReady: Boolean, errorMessage: String?)
     * - isReady = true means printer is ready to print (paper OK, cover closed)
     * - errorMessage = specific error message if not ready, null if ready
     */
    fun getPrinterStatusSummary(): Pair<Boolean, String?> {
        return when {
            _connectionState.value != BluetoothPrinterManager.ConnectionState.CONNECTED -> {
                Pair(false, "Printer not connected")
            }
            _paperStatus.value == PaperStatus.OUT -> {
                Pair(false, "Paper out - Please load paper")
            }
            _coverStatus.value == CoverStatus.OPEN -> {
                Pair(false, "Cover open - Please close cover")
            }
            _paperStatus.value == PaperStatus.UNKNOWN || _coverStatus.value == CoverStatus.UNKNOWN -> {
                // If status is unknown, we'll allow printing but warn the user
                Pair(true, null)
            }
            else -> {
                // Paper OK, Cover Closed - all good!
                Pair(true, null)
            }
        }
    }

    /**
     * Reset status to UNKNOWN (useful before checking)
     */
    fun resetStatus() {
        _paperStatus.value = PaperStatus.UNKNOWN
        _coverStatus.value = CoverStatus.UNKNOWN
        Log.d(TAG, "Status reset to UNKNOWN")
    }

    override fun onCleared() {
        super.onCleared()
        stopStatusMonitoring()
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