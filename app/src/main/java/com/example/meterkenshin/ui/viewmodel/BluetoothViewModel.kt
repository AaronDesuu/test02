package com.example.meterkenshin.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.example.meterkenshin.bluetooth.BluetoothManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for managing Bluetooth connection state in Compose UI
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

    /**
     * Initialize the Bluetooth manager
     */
    fun initializeBluetoothManager(manager: BluetoothManager) {
        if (bluetoothManager != null) return

        bluetoothManager = manager

        // Observe LiveData and convert to StateFlow
        viewModelScope.launch {
            manager.connectionState.asLiveData().observeForever { state ->
                _connectionState.value = state
            }

            manager.isBluetoothEnabled.asLiveData().observeForever { enabled ->
                _isBluetoothEnabled.value = enabled
            }

            manager.connectedDevice.asLiveData().observeForever { device ->
                _connectedDevice.value = device
            }

            manager.statusMessage.asLiveData().observeForever { message ->
                _statusMessage.value = message
            }

            _isInitialized.value = true
        }
    }

    /**
     * Start auto-connection process
     */
    fun startAutoConnect() {
        bluetoothManager?.startAutoConnect()
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

    override fun onCleared() {
        super.onCleared()
        bluetoothManager?.cleanup()
    }
}