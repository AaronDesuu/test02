package com.example.meterkenshin.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.example.meterkenshin.bluetooth.BluetoothManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for managing Bluetooth connection state in Compose UI
 * Enhanced with better state management and error handling
 */
class BluetoothViewModel(application: Application) : AndroidViewModel(application) {

    private var bluetoothManager: BluetoothManager? = null

    // State flows for Compose
    private val _connectionState = MutableStateFlow<BluetoothManager.ConnectionState?>(null)
    val connectionState: StateFlow<BluetoothManager.ConnectionState?> = _connectionState.asStateFlow()

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

    /**
     * Initialize the Bluetooth manager
     */
    fun initializeBluetoothManager(manager: BluetoothManager) {
        if (bluetoothManager != null) return

        bluetoothManager = manager

        // Observe LiveData and convert to StateFlow
        viewModelScope.launch {
            // Connection state observer
            manager.connectionState.observeForever { state: BluetoothManager.ConnectionState? ->
                _connectionState.value = state
                _isAutoConnecting.value = state == BluetoothManager.ConnectionState.CONNECTING
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
     * Connect to the specific Woosim device using hardcoded MAC address
     */
    fun connectToWoosimDevice() {
        viewModelScope.launch {
            _isAutoConnecting.value = true
            bluetoothManager?.connectToSpecificDevice()
        }
    }

    /**
     * Start auto-connection process
     */
    fun startAutoConnect() {
        viewModelScope.launch {
            _isAutoConnecting.value = true
            bluetoothManager?.startAutoConnect()
        }
    }

    /**
     * Start Bluetooth device scan
     */
    fun startScan() {
        bluetoothManager?.startScan()
    }

    /**
     * Disconnect from current device
     */
    fun disconnect() {
        bluetoothManager?.disconnect()
    }

    /**
     * Ensure the correct device is connected
     */
    fun ensureCorrectDeviceConnected() {
        bluetoothManager?.ensureCorrectDeviceConnected()
    }

    /**
     * Check if device is currently connected
     */
    fun isConnected(): Boolean {
        return bluetoothManager?.isConnected() == true
    }

    /**
     * Get the current print service
     */
    fun getPrintService() = bluetoothManager?.getPrintService()

    /**
     * Write data to connected printer
     */
    fun writeData(data: ByteArray): Boolean {
        return bluetoothManager?.write(data) == true
    }

    /**
     * Check if Bluetooth is supported
     */
    fun isBluetoothSupported(): Boolean {
        return bluetoothManager?.isBluetoothSupported() == true
    }

    /**
     * Check if printer configuration is valid
     */
    fun isPrinterConfigurationValid(): Boolean {
        return bluetoothManager?.isPrinterConfigurationValid() == true
    }

    /**
     * Get the configured printer MAC address
     */
    fun getConfiguredPrinterMacAddress(): String? {
        return bluetoothManager?.getConfiguredPrinterMacAddress()
    }

    /**
     * Update printer configuration information
     */
    private fun updatePrinterConfigInfo() {
        _printerConfigInfo.value = bluetoothManager?.getPrinterConfigInfo()
    }

    /**
     * Refresh printer configuration info
     */
    fun refreshPrinterConfigInfo() {
        updatePrinterConfigInfo()
    }

    /**
     * Get connection status as a user-friendly string
     */
    fun getConnectionStatusString(): String {
        return when (_connectionState.value) {
            BluetoothManager.ConnectionState.CONNECTED -> {
                val deviceInfo = try {
                    val device = _connectedDevice.value
                    device?.name ?: device?.address ?: "Unknown Device"
                } catch (e: SecurityException) {
                    _connectedDevice.value?.address ?: "Unknown Device"
                }
                "Connected to $deviceInfo"
            }
            BluetoothManager.ConnectionState.CONNECTING -> "Connecting..."
            BluetoothManager.ConnectionState.DISCONNECTED -> "Disconnected"
            BluetoothManager.ConnectionState.ERROR -> "Connection Error"
            null -> "Unknown Status"
        }
    }

    /**
     * Check if currently trying to connect
     */
    fun isConnecting(): Boolean {
        return _connectionState.value == BluetoothManager.ConnectionState.CONNECTING
    }

    /**
     * Check if there's a connection error
     */
    fun hasConnectionError(): Boolean {
        return _connectionState.value == BluetoothManager.ConnectionState.ERROR
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
        bluetoothManager?.cleanup()
    }
}