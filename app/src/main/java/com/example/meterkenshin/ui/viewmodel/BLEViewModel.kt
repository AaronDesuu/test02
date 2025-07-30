package com.example.meterkenshin.ui.viewmodel

import android.Manifest
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.meterkenshin.R
import com.example.meterkenshin.bluetooth.BluetoothLeService
import com.example.meterkenshin.model.Meter
import com.example.meterkenshin.model.MeterStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewModel for managing BLE connections to meter devices
 * Integrates with existing BluetoothLeService and MeterModel structure
 */
class BLEViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "BLEViewModel"
        private const val CONNECTION_TIMEOUT_MS = 10000L
        private const val MAX_RETRY_ATTEMPTS = 3
        private const val SCAN_PERIOD = 10000L // 10 seconds
    }

    // Application context
    private val context: Context = application.applicationContext

    // Bluetooth components
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothLeService: BluetoothLeService? = null
    private var serviceConnected = false

    // Connection states using StateFlow for Compose
    private val _connectionState = MutableStateFlow(BLEConnectionState.DISCONNECTED)
    val connectionState: StateFlow<BLEConnectionState> = _connectionState.asStateFlow()

    private val _isBluetoothEnabled = MutableStateFlow(false)
    val isBluetoothEnabled: StateFlow<Boolean> = _isBluetoothEnabled.asStateFlow()

    private val _connectedMeter = MutableStateFlow<Meter?>(null)
    val connectedMeter: StateFlow<Meter?> = _connectedMeter.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<BluetoothDevice>> = _discoveredDevices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    // Connection statistics
    private val _connectionAttempts = MutableStateFlow(0)
    val connectionAttempts: StateFlow<Int> = _connectionAttempts.asStateFlow()

    private val _lastConnectionTime = MutableStateFlow<Long?>(null)
    val lastConnectionTime: StateFlow<Long?> = _lastConnectionTime.asStateFlow()

    // DLMS communication state
    private val _dlmsSessionEstablished = MutableStateFlow(false)
    val dlmsSessionEstablished: StateFlow<Boolean> = _dlmsSessionEstablished.asStateFlow()

    // Permission states
    private val _permissionsGranted = MutableStateFlow(false)
    val permissionsGranted: StateFlow<Boolean> = _permissionsGranted.asStateFlow()

    private val _missingPermissions = MutableStateFlow<List<String>>(emptyList())
    val missingPermissions: StateFlow<List<String>> = _missingPermissions.asStateFlow()

    // Service connection callback
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(componentName: ComponentName, service: IBinder) {
            Log.d(TAG, "BluetoothLeService connected")
            bluetoothLeService = (service as BluetoothLeService.LocalBinder).service
            serviceConnected = true
            updateStatusMessage(context.getString(R.string.bluetooth_initializing))

            // Initialize service if needed
            bluetoothLeService?.initialize()
        }

        override fun onServiceDisconnected(componentName: ComponentName) {
            Log.d(TAG, "BluetoothLeService disconnected")
            bluetoothLeService = null
            serviceConnected = false
            _connectionState.value = BLEConnectionState.DISCONNECTED
            updateStatusMessage(context.getString(R.string.bluetooth_connection_failed))
        }
    }

    init {
        initializeBluetooth()
        bindBluetoothService()
        checkBluetoothPermissions()
    }

    /**
     * Initialize Bluetooth adapter
     */
    private fun initializeBluetooth() {
        val bluetoothManager =
            context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        if (bluetoothAdapter == null) {
            Log.e(TAG, "Bluetooth not supported")
            updateStatusMessage(context.getString(R.string.bluetooth_not_supported))
            return
        }

        _isBluetoothEnabled.value = bluetoothAdapter?.isEnabled == true

        if (_isBluetoothEnabled.value) {
            updateStatusMessage(context.getString(R.string.bluetooth_enabled))
        }
    }

    /**
     * Bind to BluetoothLeService
     */
    private fun bindBluetoothService() {
        val gattServiceIntent = Intent(context, BluetoothLeService::class.java)
        context.bindService(gattServiceIntent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    /**
     * Check and update Bluetooth permissions status
     */
    fun checkBluetoothPermissions() {
        val requiredPermissions = getRequiredBluetoothPermissions()
        val missingPerms = mutableListOf<String>()

        for (permission in requiredPermissions) {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    permission
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                missingPerms.add(permission)
            }
        }

        _missingPermissions.value = missingPerms
        _permissionsGranted.value = missingPerms.isEmpty()

        if (!_permissionsGranted.value) {
            updateStatusMessage(context.getString(R.string.bluetooth_permissions_required))
        }
    }

    /**
     * Get required Bluetooth permissions based on Android version
     */
    private fun getRequiredBluetoothPermissions(): List<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        } else {
            listOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }
    }

    /**
     * Check if specific Bluetooth permission is granted
     */
    private fun hasBluetoothPermission(permission: String): Boolean {
        return ActivityCompat.checkSelfPermission(
            context,
            permission
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Check if all required Bluetooth permissions are granted
     */
    private fun hasAllBluetoothPermissions(): Boolean {
        return _permissionsGranted.value
    }

    /**
     * Connect to a specific meter using its Bluetooth ID
     */
    fun connectToMeter(meter: Meter) {
        if (meter.bluetoothId.isNullOrEmpty()) {
            updateStatusMessage(context.getString(R.string.ble_meter_no_bluetooth_id))
            return
        }

        // Check permissions first
        if (!hasAllBluetoothPermissions()) {
            _connectionState.value = BLEConnectionState.FAILED
            updateStatusMessage(context.getString(R.string.bluetooth_permissions_required))
            return
        }

        viewModelScope.launch {
            try {
                _connectionState.value = BLEConnectionState.CONNECTING
                _connectionAttempts.value = _connectionAttempts.value + 1
                updateStatusMessage(context.getString(R.string.connecting_to_meter))

                // Wait for service to be ready
                if (!waitForServiceConnection()) {
                    _connectionState.value = BLEConnectionState.FAILED
                    updateStatusMessage(context.getString(R.string.ble_service_not_available))
                    return@launch
                }

                // Connect using the service with permission check
                val success = withContext(Dispatchers.IO) {
                    try {
                        if (hasBluetoothPermission(Manifest.permission.BLUETOOTH_CONNECT) ||
                            hasBluetoothPermission(Manifest.permission.BLUETOOTH)
                        ) {
                            bluetoothLeService?.connect(meter.bluetoothId!!) ?: false
                        } else {
                            false
                        }
                    } catch (e: SecurityException) {
                        Log.e(TAG, "Permission denied for Bluetooth connection", e)
                        false
                    }
                }

                if (success) {
                    // Wait for connection with timeout
                    val connected = waitForConnection()
                    if (connected) {
                        _connectionState.value = BLEConnectionState.CONNECTED
                        _connectedMeter.value = meter
                        _lastConnectionTime.value = System.currentTimeMillis()
                        updateStatusMessage(context.getString(R.string.operation_successful))

                        // Attempt to establish DLMS session
                        establishDLMSSession()
                    } else {
                        _connectionState.value = BLEConnectionState.FAILED
                        updateStatusMessage(context.getString(R.string.meter_communication_error))
                    }
                } else {
                    _connectionState.value = BLEConnectionState.FAILED
                    updateStatusMessage(context.getString(R.string.bluetooth_connection_failed))
                }

            } catch (e: Exception) {
                Log.e(TAG, "Connection error", e)
                _connectionState.value = BLEConnectionState.FAILED
                updateStatusMessage("Connection error: ${e.message}")
            }
        }
    }

    /**
     * Connect to meter by Bluetooth ID string
     */
    fun connectToMeterByBluetoothId(bluetoothId: String) {
        // Check permissions first
        if (!hasAllBluetoothPermissions()) {
            _connectionState.value = BLEConnectionState.FAILED
            updateStatusMessage(context.getString(R.string.bluetooth_permissions_required))
            return
        }

        viewModelScope.launch {
            try {
                _connectionState.value = BLEConnectionState.CONNECTING
                _connectionAttempts.value = _connectionAttempts.value + 1
                updateStatusMessage(context.getString(R.string.connecting_to_meter))

                if (!waitForServiceConnection()) {
                    _connectionState.value = BLEConnectionState.FAILED
                    updateStatusMessage(context.getString(R.string.ble_service_not_available))
                    return@launch
                }

                val success = withContext(Dispatchers.IO) {
                    try {
                        if (hasBluetoothPermission(Manifest.permission.BLUETOOTH_CONNECT) ||
                            hasBluetoothPermission(Manifest.permission.BLUETOOTH)
                        ) {
                            bluetoothLeService?.connect(bluetoothId) ?: false
                        } else {
                            false
                        }
                    } catch (e: SecurityException) {
                        Log.e(TAG, "Permission denied for Bluetooth connection", e)
                        false
                    }
                }

                if (success) {
                    val connected = waitForConnection()
                    if (connected) {
                        _connectionState.value = BLEConnectionState.CONNECTED
                        _lastConnectionTime.value = System.currentTimeMillis()
                        updateStatusMessage(context.getString(R.string.operation_successful))
                        establishDLMSSession()
                    } else {
                        _connectionState.value = BLEConnectionState.FAILED
                        updateStatusMessage(context.getString(R.string.meter_communication_error))
                    }
                } else {
                    _connectionState.value = BLEConnectionState.FAILED
                    updateStatusMessage(context.getString(R.string.bluetooth_connection_failed))
                }

            } catch (e: Exception) {
                Log.e(TAG, "Connection error", e)
                _connectionState.value = BLEConnectionState.FAILED
                updateStatusMessage("Connection error: ${e.message}")
            }
        }
    }

    /**
     * Disconnect from current meter
     */
    /**
     * Disconnect from current meter
     */
    fun disconnect() {
        viewModelScope.launch {
            try {
                // Check permissions first before attempting to disconnect
                if (!hasBluetoothPermission(Manifest.permission.BLUETOOTH_CONNECT) &&
                    !hasBluetoothPermission(Manifest.permission.BLUETOOTH)
                ) {

                    Log.w(TAG, "Missing Bluetooth permissions for disconnect")
                    updateStatusMessage(context.getString(R.string.bluetooth_permissions_required))

                    // Still update the UI state even if we can't properly disconnect
                    _connectionState.value = BLEConnectionState.DISCONNECTED
                    _connectedMeter.value = null
                    _dlmsSessionEstablished.value = false
                    return@launch
                }

                // Attempt to disconnect from the BLE service
                try {
                    bluetoothLeService?.disconnect()
                    Log.d(TAG, "Bluetooth disconnect called Successfully")

                    // Update connection state
                    _connectionState.value = BLEConnectionState.DISCONNECTED
                    _connectedMeter.value = null
                    _dlmsSessionEstablished.value = false
                    updateStatusMessage(context.getString(R.string.connection_state_disconnected))

                } catch (e: SecurityException) {
                    Log.e(TAG, "Permission denied during disconnect", e)
                    updateStatusMessage(context.getString(R.string.bluetooth_permissions_required))

                    // Still clean up the state even if disconnect failed
                    _connectionState.value = BLEConnectionState.DISCONNECTED
                    _connectedMeter.value = null
                    _dlmsSessionEstablished.value = false
                }

            } catch (e: Exception) {
                Log.e(TAG, "Disconnect error", e)
                updateStatusMessage("Disconnect error: ${e.message}")

                // Ensure state is cleaned up even on unexpected errors
                _connectionState.value = BLEConnectionState.DISCONNECTED
                _connectedMeter.value = null
                _dlmsSessionEstablished.value = false
            }
        }
    }

    /**
     * Updated onCleared method with proper permission handling
     */
    override fun onCleared() {
        super.onCleared()
        try {
            // Unbind service first
            if (serviceConnected) {
                context.unbindService(serviceConnection)
            }

            // Try to disconnect if we have the proper permissions
            if (hasBluetoothPermission(Manifest.permission.BLUETOOTH_CONNECT) ||
                hasBluetoothPermission(Manifest.permission.BLUETOOTH)
            ) {
                try {
                    bluetoothLeService?.disconnect()
                } catch (e: SecurityException) {
                    Log.e(TAG, "Permission denied during cleanup disconnect", e)
                }
            }

        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied during cleanup", e)
        } catch (e: Exception) {
            Log.e(TAG, "Cleanup error", e)
        } finally {
            // Always clean up the state regardless of permission issues
            _connectionState.value = BLEConnectionState.DISCONNECTED
            _connectedMeter.value = null
            _dlmsSessionEstablished.value = false
        }
    }

    /**
     * Scan for nearby BLE devices
     */
    fun startDeviceScan() {
        if (!_isBluetoothEnabled.value) {
            updateStatusMessage(context.getString(R.string.bluetooth_required))
            return
        }

        // Check permissions for scanning
        if (!hasBluetoothPermission(Manifest.permission.BLUETOOTH_SCAN) &&
            !hasBluetoothPermission(Manifest.permission.BLUETOOTH_ADMIN)
        ) {
            updateStatusMessage(context.getString(R.string.bluetooth_permissions_required))
            return
        }

        if (_isScanning.value) {
            return // Already scanning
        }

        viewModelScope.launch {
            try {
                _isScanning.value = true
                _discoveredDevices.value = emptyList()
                updateStatusMessage(context.getString(R.string.auto_connect_scanning))

                // TODO: Implement BLE scanning using BluetoothAdapter
                // This would require additional BLE scanning implementation

                delay(SCAN_PERIOD)
                _isScanning.value = false
                updateStatusMessage(context.getString(R.string.ble_scan_completed))

            } catch (e: SecurityException) {
                Log.e(TAG, "Permission denied for BLE scan", e)
                _isScanning.value = false
                updateStatusMessage(context.getString(R.string.bluetooth_permissions_required))
            } catch (e: Exception) {
                Log.e(TAG, "Scan error", e)
                _isScanning.value = false
                updateStatusMessage("Scan error: ${e.message}")
            }
        }
    }

    /**
     * Stop device scanning
     */
    fun stopDeviceScan() {
        _isScanning.value = false
        updateStatusMessage("Scan stopped")
    }

    /**
     * Attempt to auto-connect to nearby meters
     */
    fun autoConnectToNearbyMeters(availableMeters: List<Meter>) {
        viewModelScope.launch {
            for (meter in availableMeters) {
                if (meter.bluetoothId != null && meter.status == MeterStatus.ACTIVE) {
                    updateStatusMessage("Attempting auto-connect to meter ${meter.serialNumber}")
                    connectToMeter(meter)

                    // Wait for connection attempt to complete
                    delay(5000)

                    if (_connectionState.value == BLEConnectionState.CONNECTED) {
                        updateStatusMessage(context.getString(R.string.auto_connect_success))
                        return@launch
                    }
                }
            }
            updateStatusMessage(context.getString(R.string.auto_connect_failed))
        }
    }

    /**
     * Wait for service connection with timeout
     */
    private suspend fun waitForServiceConnection(): Boolean {
        var attempts = 0
        while (!serviceConnected && attempts < 50) { // 5 seconds max
            delay(100)
            attempts++
        }
        return serviceConnected
    }

    /**
     * Wait for BLE connection to establish
     */
    private suspend fun waitForConnection(): Boolean {
        var attempts = 0
        val maxAttempts = (CONNECTION_TIMEOUT_MS / 100).toInt()

        while (attempts < maxAttempts) {
            // Check connection status from service
            bluetoothLeService?.let { service ->
                // This would need to be implemented in BluetoothLeService
                // For now, we'll simulate a connection check
                if (service.isConnected()) {
                    return true
                }
            }

            delay(100)
            attempts++
        }
        return false
    }

    /**
     * Establish DLMS session after BLE connection
     */
    private fun establishDLMSSession() {
        viewModelScope.launch {
            try {
                updateStatusMessage("Establishing DLMS session...")

                // This would integrate with your existing DLMS implementation
                // For now, we'll simulate the session establishment
                delay(2000)

                _dlmsSessionEstablished.value = true
                updateStatusMessage("DLMS session established")

            } catch (e: Exception) {
                Log.e(TAG, "DLMS session error", e)
                _dlmsSessionEstablished.value = false
                updateStatusMessage("DLMS session failed: ${e.message}")
            }
        }
    }

    /**
     * Read meter data using DLMS
     */
    fun readMeterData(): LiveData<MeterDataResult> {
        val result = MutableLiveData<MeterDataResult>()

        viewModelScope.launch {
            try {
                if (_connectionState.value != BLEConnectionState.CONNECTED) {
                    result.value = MeterDataResult.Error("Not connected to meter")
                    return@launch
                }

                if (!_dlmsSessionEstablished.value) {
                    result.value = MeterDataResult.Error("DLMS session not established")
                    return@launch
                }

                updateStatusMessage("Reading meter data...")
                result.value = MeterDataResult.Loading

                // This would integrate with your existing DLMS data reading
                // For now, we'll simulate data reading
                delay(3000)

                val meterData = mapOf(
                    "energy" to "1234.56",
                    "voltage" to "220.5",
                    "current" to "15.2"
                )

                result.value = MeterDataResult.Success(meterData)
                updateStatusMessage(context.getString(R.string.operation_successful))

            } catch (e: Exception) {
                Log.e(TAG, "Read data error", e)
                result.value = MeterDataResult.Error("Read error: ${e.message}")
                updateStatusMessage(context.getString(R.string.operation_failed))
            }
        }

        return result
    }

    /**
     * Update status message
     */
    private fun updateStatusMessage(message: String) {
        _statusMessage.value = message
        Log.d(TAG, "Status: $message")
    }

    /**
     * Check if Bluetooth is enabled
     */
    fun checkBluetoothStatus() {
        try {
            _isBluetoothEnabled.value = bluetoothAdapter?.isEnabled == true
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied for checking Bluetooth status", e)
            _isBluetoothEnabled.value = false
            updateStatusMessage(context.getString(R.string.bluetooth_permissions_required))
        }
    }

    /**
     * Get connection statistics
     */
    fun getConnectionStats(): ConnectionStats {
        return ConnectionStats(
            totalAttempts = _connectionAttempts.value,
            currentState = _connectionState.value,
            lastConnectionTime = _lastConnectionTime.value,
            isSessionEstablished = _dlmsSessionEstablished.value,
            connectedMeter = _connectedMeter.value
        )
    }


    /**
     * BLE Connection States
     */
    enum class BLEConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        FAILED,
        TIMEOUT
    }

    /**
     * Meter data reading results
     */
    sealed class MeterDataResult {
        object Loading : MeterDataResult()
        data class Success(val data: Map<String, String>) : MeterDataResult()
        data class Error(val message: String) : MeterDataResult()
    }

    /**
     * Connection statistics data class
     */
    data class ConnectionStats(
        val totalAttempts: Int,
        val currentState: BLEConnectionState,
        val lastConnectionTime: Long?,
        val isSessionEstablished: Boolean,
        val connectedMeter: Meter?
    )

    /**
     * Extension function to check if service is connected
     * This would need to be implemented in your BluetoothLeService
     */
    private fun BluetoothLeService.isConnected(): Boolean {
        // This method should be implemented in your BluetoothLeService
        // to return the current connection status
        return false // placeholder
    }
}