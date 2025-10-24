package com.example.meterkenshin.dlms

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
import androidx.annotation.RequiresApi
import com.example.meterkenshin.bluetooth.BluetoothLeService
import com.example.meterkenshin.model.Meter
import kotlinx.coroutines.delay

/**
 * Handles DLMS initialization, Bluetooth service connection, and broadcast receiver management
 * Extracted from DLMSRegistrationViewModel for better separation of concerns
 */
@SuppressLint("MissingPermission")
class DLMSInit(
    private val onLogAppend: (String) -> Unit
) {
    companion object {
        private const val TAG = "DLMSInit"
    }

    // Public accessors for ViewModel
    var dlms: DLMS? = null
        private set

    var bluetoothLeService: BluetoothLeService? = null
        private set

    var isServiceBound: Boolean = false
        private set

    var isServiceActive: Boolean = false
        private set

    // Shared state variables
    var mData: ByteArray = ByteArray(0)
    var mArrived = 0

    private var mContext: Context? = null
    private var mReceiverRegistered = false
    private var currentMeter: Meter? = null

    /**
     * BroadcastReceiver for Bluetooth GATT events
     */
    private val mGattUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            Log.d(TAG, "=== BROADCAST RECEIVED: $action ===")

            when (action) {
                BluetoothLeService.Companion.ACTION_GATT_CONNECTED -> {
                    Log.i(TAG, "GATT Connected")
                }
                BluetoothLeService.Companion.ACTION_GATT_DISCONNECTED -> {
                    Log.i(TAG, "GATT Disconnected")
                }
                BluetoothLeService.Companion.ACTION_GATT_SERVICES_DISCOVERED -> {
                    Log.i(TAG, "!!! GATT Services Discovered - Setting mArrived = 0 !!!")
                    mArrived = 0  // THIS IS THE KEY FLAG
                    Log.i(TAG, "mArrived is now: $mArrived")
                }
                BluetoothLeService.Companion.ACTION_DATA_AVAILABLE -> {
                    val data = intent.getByteArrayExtra(BluetoothLeService.Companion.EXTRA_DATA)
                    if (data != null) {
                        mData = data
                        mArrived++
                        Log.d(TAG, "Data received: ${data.size} bytes, mArrived=$mArrived")
                    }
                }
                else -> {
                    Log.w(TAG, "Unknown action received: $action")
                }
            }
        }
    }

    /**
     * ServiceConnection for BluetoothLeService
     */
    private val mServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(componentName: ComponentName, service: IBinder) {
            bluetoothLeService = (service as BluetoothLeService.LocalBinder).service
            Log.i(TAG, "Success to initialize Bluetooth service.")
            isServiceBound = true
            isServiceActive = true
        }

        override fun onServiceDisconnected(componentName: ComponentName) {
            bluetoothLeService = null
            isServiceBound = false
            isServiceActive = false
            Log.i(TAG, "onServiceDisconnected")
        }
    }

    /**
     * Initialize DLMS and bind Bluetooth service
     * CRITICAL: Register receiver FIRST, then bind service
     *
     * @param context Application context
     * @param meter Meter configuration for DLMS
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    suspend fun initialize(context: Context, meter: Meter) {
        mContext = context
        currentMeter = meter

        // Initialize DLMS with meter configuration
        dlms = DLMS(context)
        dlms?.Password(meter.key, 1)
        dlms?.writeAddress(meter.logical, 1)
        dlms?.writeRank(String.format("%02x", meter.rank), 1)

        // Register receiver FIRST, before any BLE operations
        if (!mReceiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(BluetoothLeService.Companion.ACTION_GATT_CONNECTED)
                addAction(BluetoothLeService.Companion.ACTION_GATT_DISCONNECTED)
                addAction(BluetoothLeService.Companion.ACTION_GATT_SERVICES_DISCOVERED)
                addAction(BluetoothLeService.Companion.ACTION_DATA_AVAILABLE)
            }

            try {
                // CRITICAL: Use RECEIVER_EXPORTED for Android 13+
                // Service broadcasts need to reach this receiver within the same app
                context.registerReceiver(
                    mGattUpdateReceiver,
                    filter,
                    Context.RECEIVER_EXPORTED  // Allow broadcasts from our own service
                )
                mReceiverRegistered = true
                Log.i(TAG, "Broadcast receiver registered successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to register receiver: ${e.message}")
                onLogAppend("ERROR: Failed to register receiver")
                return
            }
        }

        // Small delay to ensure receiver is ready
        delay(100)

        // Bind service and WAIT for it
        val serviceIntent = Intent(context, BluetoothLeService::class.java)
        context.bindService(serviceIntent, mServiceConnection, Context.BIND_AUTO_CREATE)

        // Wait for service to be bound
        var waited = 0
        while (!isServiceBound && waited < 50) {
            delay(100)
            waited++
        }

        if (!isServiceBound) {
            onLogAppend("ERROR: Service binding timeout")
            Log.e(TAG, "Service binding timeout")
            return
        }

        onLogAppend("DLMS initialized and service bound")
        Log.i(TAG, "DLMS initialization complete")
    }

    /**
     * Clean up resources: unregister receiver, disconnect, and unbind service
     */
    fun cleanup(context: Context) {
        try {
            if (mReceiverRegistered) {
                context.unregisterReceiver(mGattUpdateReceiver)
                mReceiverRegistered = false
                Log.i(TAG, "Receiver unregistered")
            }

            bluetoothLeService?.disconnect()

            if (isServiceBound) {
                context.unbindService(mServiceConnection)
                isServiceBound = false
                isServiceActive = false
                Log.i(TAG, "Service unbound")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Cleanup error: ${e.message}")
        }
    }

    /**
     * Check if the initializer is ready for operations
     */
    fun isReady(): Boolean {
        return dlms != null && isServiceBound && isServiceActive
    }
}