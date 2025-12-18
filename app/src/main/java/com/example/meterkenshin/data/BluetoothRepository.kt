package com.example.meterkenshin.data

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.example.meterkenshin.bluetooth.BluetoothLeService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Repository to manage BluetoothLE service lifecycle and prevent memory leaks.
 * This singleton properly manages the service connection and broadcast receiver registration.
 */
class BluetoothRepository private constructor(private val appContext: Context) {

    companion object {
        private const val TAG = "BluetoothRepository"

        @Volatile
        private var INSTANCE: BluetoothRepository? = null

        fun getInstance(context: Context): BluetoothRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: BluetoothRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    // Service connection state
    private val _isServiceBound = MutableStateFlow(false)
    val isServiceBound: StateFlow<Boolean> = _isServiceBound.asStateFlow()

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _dataReceived = MutableStateFlow<ByteArray?>(null)
    val dataReceived: StateFlow<ByteArray?> = _dataReceived.asStateFlow()

    private var bluetoothLeService: BluetoothLeService? = null
    private var isReceiverRegistered = false
    private var mConnected = 0
    private var mArrived = 0

    // BroadcastReceiver for GATT events
    private val gattUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothLeService.ACTION_GATT_CONNECTED -> {
                    mConnected = 1
                    Log.i(TAG, "ACTION_GATT_CONNECTED")
                }
                BluetoothLeService.ACTION_GATT_DISCONNECTED -> {
                    Log.i(TAG, "ACTION_GATT_DISCONNECTED")
                    mConnected = -1
                    _connectionState.value = ConnectionState.DISCONNECTED
                }
                BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED -> {
                    Log.i(TAG, "ACTION_GATT_SERVICES_DISCOVERED")
                    mConnected = 2
                    mArrived = 0
                    _connectionState.value = ConnectionState.CONNECTED
                }
                BluetoothLeService.ACTION_GATT_ERROR -> {
                    Log.i(TAG, "ACTION_GATT_ERROR")
                    _connectionState.value = ConnectionState.FAILED
                }
                BluetoothLeService.ACTION_DATA_AVAILABLE -> {
                    Log.i(TAG, "ACTION_DATA_AVAILABLE")
                    val data = intent.getByteArrayExtra(BluetoothLeService.EXTRA_DATA)
                    _dataReceived.value = data
                    mArrived++
                }
            }
        }
    }

    // Service connection callbacks
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(componentName: ComponentName, service: IBinder) {
            bluetoothLeService = (service as BluetoothLeService.LocalBinder).service
            if (bluetoothLeService?.initialize() != true) {
                Log.e(TAG, "Failed to initialize Bluetooth service")
                _isServiceBound.value = false
            } else {
                Log.i(TAG, "Successfully initialized Bluetooth service")
                _isServiceBound.value = true
            }
        }

        override fun onServiceDisconnected(componentName: ComponentName) {
            bluetoothLeService = null
            _isServiceBound.value = false
            Log.i(TAG, "Service disconnected")
        }
    }

    /**
     * Bind to BluetoothLE service and register broadcast receiver
     */
    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    fun startBLEOperations() {
        try {
            // Register broadcast receiver if not already registered
            if (!isReceiverRegistered) {
                val intentFilter = IntentFilter().apply {
                    addAction(BluetoothLeService.ACTION_GATT_CONNECTED)
                    addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED)
                    addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED)
                    addAction(BluetoothLeService.ACTION_DATA_AVAILABLE)
                    addAction(BluetoothLeService.ACTION_GATT_ERROR)
                }
                // For Android 13+ (API 33), use RECEIVER_NOT_EXPORTED flag
                // For older versions, flag is not required
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    appContext.registerReceiver(gattUpdateReceiver, intentFilter, Context.RECEIVER_NOT_EXPORTED)
                } else {
                    appContext.registerReceiver(gattUpdateReceiver, intentFilter)
                }
                isReceiverRegistered = true
                Log.i(TAG, "BroadcastReceiver registered")
            }

            // Bind service if not already bound
            if (!_isServiceBound.value) {
                val gattServiceIntent = Intent(appContext, BluetoothLeService::class.java)
                appContext.bindService(gattServiceIntent, serviceConnection, Context.BIND_AUTO_CREATE)
                Log.i(TAG, "Service binding initiated")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting BLE operations", e)
        }
    }

    /**
     * Unbind from service and unregister broadcast receiver
     */
    fun stopBLEOperations() {
        try {
            // Unregister broadcast receiver
            if (isReceiverRegistered) {
                try {
                    appContext.unregisterReceiver(gattUpdateReceiver)
                    isReceiverRegistered = false
                    Log.i(TAG, "BroadcastReceiver unregistered")
                } catch (e: IllegalArgumentException) {
                    Log.w(TAG, "Receiver was not registered", e)
                }
            }

            // Unbind service
            if (_isServiceBound.value) {
                appContext.unbindService(serviceConnection)
                _isServiceBound.value = false
                bluetoothLeService = null
                Log.i(TAG, "Service unbound")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping BLE operations", e)
        }
    }

    /**
     * Get the BluetoothLE service instance
     */
    fun getService(): BluetoothLeService? = bluetoothLeService

    /**
     * Check if service is active and ready
     */
    fun isServiceActive(): Boolean = _isServiceBound.value && bluetoothLeService != null

    /**
     * Get current connection status
     */
    fun getConnectionStatus(): Int = mConnected

    /**
     * Get arrived packet count
     */
    fun getArrivedCount(): Int = mArrived

    /**
     * Reset arrived count
     */
    fun resetArrivedCount() {
        mArrived = 0
    }

    /**
     * Cleanup method - call when repository is no longer needed
     */
    fun cleanup() {
        stopBLEOperations()
        Log.i(TAG, "Repository cleaned up")
    }
}

// Connection states
enum class ConnectionState {
    DISCONNECTED,
    CONNECTED,
    FAILED
}
