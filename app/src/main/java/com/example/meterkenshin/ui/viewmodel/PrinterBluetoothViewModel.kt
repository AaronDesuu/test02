package com.example.meterkenshin.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.meterkenshin.printer.BluetoothPrinterManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for managing Bluetooth connection state in Compose UI
 * Enhanced with better state management and error handling
 */
class PrinterBluetoothViewModel(application: Application) : AndroidViewModel(application) {

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

    /**
     * Initialize the Bluetooth manager
     */
    fun initializeBluetoothManager(manager: BluetoothPrinterManager) {
        if (bluetoothPrinterManager != null) return

        bluetoothPrinterManager = manager

        // Observe LiveData and convert to StateFlow
        viewModelScope.launch {
            // Connection state observer
            manager.connectionState.observeForever { state: BluetoothPrinterManager.ConnectionState? ->
                _connectionState.value = state
                _isAutoConnecting.value = state == BluetoothPrinterManager.ConnectionState.CONNECTING
            }

            // Bluetooth enabled state observer
            manager.isBluetoothEnabled.observeForever { enabled: Boolean ->
                _isBluetoothEnabled.value = enabled
            }

            // Connected device observer
            manager.connectedDevice.observeForever { device: android.bluetooth.BluetoothDevice? ->
                _connectedDevice.value = device
            }

            // Status message observer
            manager.statusMessage.observeForever { message: String? ->
                _statusMessage.value = message
            }

            // Update printer configuration info
            updatePrinterConfigInfo()

            _isInitialized.value = true
        }
    }

    /**
     * Start auto-connection process
     */
    fun startAutoConnect() {
        viewModelScope.launch {
            _isAutoConnecting.value = true
            bluetoothPrinterManager?.startAutoConnect()
        }
    }

    /**
     * Disconnect from current device
     */
    fun disconnect() {
        bluetoothPrinterManager?.disconnect()
    }

    /**
     * Write data to connected printer
     */
    fun writeData(data: ByteArray): Boolean {
        return bluetoothPrinterManager?.write(data) == true
    }

    /**
     * Update printer configuration information
     */
    private fun updatePrinterConfigInfo() {
        _printerConfigInfo.value = bluetoothPrinterManager?.getPrinterConfigInfo()
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
            startAutoConnect()
        }
    }

    /**
     * Print sample data to test printer connection
     */
    fun printSampleData(): Boolean {
        val sampleData = "Sample Print Test\n\nWoosim Printer Connected\n\n\n\n".toByteArray()
        return writeData(sampleData)
    }

    override fun onCleared() {
        super.onCleared()
        bluetoothPrinterManager?.cleanup()
    }
}