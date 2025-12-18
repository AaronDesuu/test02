package com.example.meterkenshin.bluetooth

import android.Manifest
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresPermission
import java.util.UUID


/**
 * Service for managing connection and data communication with a GATT server hosted on a
 * given Bluetooth LE device.
 */
@Suppress("DEPRECATION", "OVERRIDE_DEPRECATION", "SameParameterValue")
class BluetoothLeService : Service() {
    private var mBluetoothManager: BluetoothManager? = null
    private var mBluetoothAdapter: BluetoothAdapter? = null
    private var mBluetoothDeviceAddress: String? = null
    private var mBluetoothGatt: BluetoothGatt? = null
    private var mConnectionState = STATE_INITIATE
    private var mService: BluetoothGattService? = null
    private var mCharacteristic: BluetoothGattCharacteristic? = null

    private val uuidService: UUID = UUID.fromString("b973f2e0-b19e-11e2-9e96-0800200c9a66")
    private val uuidRead: UUID = UUID.fromString("d973f2e1-b19e-11e2-9e96-0800200c9a66")
    private val uuidWrite: UUID = UUID.fromString("e973f2e2-b19e-11e2-9e96-0800200c9a66")
    private val uuidConfig: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    // Implements callback methods for GATT events that the app cares about.  For example,
    // connection change and services discovered.
    private val mGattCallback: BluetoothGattCallback = object : BluetoothGattCallback() {
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                if (mConnectionState == STATE_CONNECTING) {
                    mConnectionState = STATE_CONNECTED
                    broadcastUpdate(ACTION_GATT_CONNECTED)
                    if (mBluetoothGatt != null) {
                        mBluetoothGatt?.discoverServices()
                        Log.i(TAG, "Connected to GATT server and call discoverServices.")
                    } else {
                        Log.e(TAG, "mBluetoothGatt is null, cannot discover services")
                        broadcastUpdate(ACTION_GATT_ERROR)
                    }
                } else {
                    broadcastUpdate(ACTION_GATT_ERROR)
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                if (mConnectionState == STATE_CONNECTING) {
                    mBluetoothDeviceAddress = null
                }
                mConnectionState = STATE_DISCONNECTED
                broadcastUpdate(ACTION_GATT_DISCONNECTED)
                Log.i(TAG, "Disconnected from GATT server.")
            } else {
                Log.i(TAG, String.format("Other state received: %d", newState))
            }
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (mBluetoothGatt == null) {
                    Log.e(TAG, "mBluetoothGatt is null in onServicesDiscovered")
                    broadcastUpdate(ACTION_GATT_ERROR)
                    return
                }

                mService = mBluetoothGatt?.getService(uuidService)
                if (mService == null) {
                    Log.e(TAG, "Required service not found: $uuidService")
                    broadcastUpdate(ACTION_GATT_ERROR)
                    return
                }

                mCharacteristic = mService?.getCharacteristic(uuidRead)
                if (mCharacteristic == null) {
                    Log.e(TAG, "Required characteristic not found: $uuidRead")
                    broadcastUpdate(ACTION_GATT_ERROR)
                    return
                }

                val descriptor = mCharacteristic?.getDescriptor(uuidConfig)
                if (descriptor == null) {
                    Log.e(TAG, "Required descriptor not found: $uuidConfig")
                    broadcastUpdate(ACTION_GATT_ERROR)
                    return
                }

                mConnectionState = STATE_DISCOVERED
                //              mBluetoothGatt.requestConnectionPriority(CONNECTION_PRIORITY_HIGH);
                mBluetoothGatt?.setCharacteristicNotification(mCharacteristic, true)
                descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                mBluetoothGatt?.writeDescriptor(descriptor)
                broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED)
            } else {
                broadcastUpdate(ACTION_GATT_ERROR)
            }
            Log.w(TAG, "onServicesDiscovered received: $status")
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?, status: Int
        ) {
            super.onCharacteristicWrite(gatt, characteristic, status)
            Log.w(TAG, "onCharacteristicWrite: $status")
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            super.onCharacteristicRead(gatt, characteristic, status)
            Log.w(TAG, "onCharacteristicRead: $status")
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic
        ) {
            super.onCharacteristicChanged(gatt, characteristic)
            broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic)
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun write(`in`: ByteArray): Boolean {
        if (mConnectionState == STATE_DISCOVERED && mBluetoothGatt != null) {
            Log.i(TAG, String.format("write:%d", `in`.size))
            val service = mBluetoothGatt?.getService(uuidService)
            if (service == null) {
                Log.e(TAG, "Service not available for write")
                return false
            }
            val characteristic = service.getCharacteristic(uuidWrite)
            if (characteristic == null) {
                Log.e(TAG, "Write characteristic not available")
                return false
            }
            characteristic.setValue(`in`)
            mBluetoothGatt?.writeCharacteristic(characteristic)
            return true
        } else {
            Log.i(TAG, "Dose not ready to write")
            return false
        }
    }

    private fun broadcastUpdate(action: String?) {
        val intent = Intent(action)
        sendBroadcast(intent)
    }

    private fun broadcastUpdate(
        action: String?,
        characteristic: BluetoothGattCharacteristic
    ) {
        val intent = Intent(action)

        if (uuidRead == characteristic.uuid) {
            // For all other profiles, writes the data formatted in HEX.
            val data = characteristic.value
            if (data != null && data.isNotEmpty()) {
                intent.putExtra(EXTRA_DATA, data)
            }
        }
        sendBroadcast(intent)
    }

    inner class LocalBinder : Binder() {
        val service: BluetoothLeService
            get() = this@BluetoothLeService
    }

    override fun onBind(intent: Intent?): IBinder {
        return mBinder
    }

    private val mBinder: IBinder = LocalBinder()

    /**
     * Initializes a reference to the local Bluetooth adapter.
     *
     * @return Return true if the initialization is successful.
     */
    fun initialize(): Boolean {
        // For API level 18 and above, get a reference to BluetoothAdapter through
        // BluetoothPrinterManager.
        if (mBluetoothManager == null) {
            mBluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager?
            if (mBluetoothManager == null) {
                Log.e(TAG, "Unable to initialize BluetoothPrinterManager.")
                return false
            }
        }
        mBluetoothAdapter = mBluetoothManager!!.adapter
        if (mBluetoothAdapter == null) {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter.")
            return false
        } else {
            mConnectionState = STATE_INITIALIZE
        }
        return true
    }

    /**
     * Connects to the GATT server hosted on the Bluetooth LE device.
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun connect(address: String?): Boolean {
        if (mBluetoothAdapter == null || address == null) {
            Log.w(TAG, "BluetoothAdapter not initialized or unspecified address.")
            return false
        }

        // Previously connected device. Try to reconnect.
        if (address == mBluetoothDeviceAddress && mBluetoothGatt != null) {
            Log.d(TAG, "Trying to use an existing mBluetoothGatt for connection.")
            mConnectionState = STATE_CONNECTING
            return mBluetoothGatt?.connect() ?: false
        }

        val device = mBluetoothAdapter?.getRemoteDevice(address)
        if (device == null) {
            Log.w(TAG, "Device not found. Unable to connect.")
            return false
        }

        // We want to directly connect to the device, so we are setting autoConnect to false.
        mBluetoothGatt = device.connectGatt(this, false, mGattCallback)
        Log.d(TAG, "Trying to create a new connection.")
        mBluetoothDeviceAddress = address
        mConnectionState = STATE_CONNECTING
        return true
    }

    /**
     * Disconnects an existing connection or cancel a pending connection.
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun disconnect() {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized")
            return
        }
        mBluetoothGatt?.disconnect()
    }

    /**
     * After using a given BLE device, the app must call this method to ensure resources are released properly.
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun close() {
        if (mBluetoothGatt == null) {
            return
        }
        mBluetoothGatt?.close()
        mBluetoothGatt = null
        mConnectionState = STATE_DISCONNECTED
    }

    companion object {
        private const val TAG = "BluetoothLeService"

        // Connection States
        private const val STATE_INITIATE = -1
        private const val STATE_INITIALIZE = 0
        private const val STATE_DISCONNECTED = 1
        private const val STATE_CONNECTING = 2
        private const val STATE_CONNECTED = 3
        private const val STATE_DISCOVERED = 4

        // Broadcast Actions
        const val ACTION_GATT_ERROR = "com.example.meterkenshin.bluetooth.ACTION_GATT_ERROR"
        const val ACTION_GATT_CONNECTED = "com.example.meterkenshin.bluetooth.ACTION_GATT_CONNECTED"
        const val ACTION_GATT_DISCONNECTED = "com.example.meterkenshin.bluetooth.ACTION_GATT_DISCONNECTED"
        const val ACTION_GATT_SERVICES_DISCOVERED = "com.example.meterkenshin.bluetooth.ACTION_GATT_SERVICES_DISCOVERED"
        const val ACTION_DATA_AVAILABLE = "com.example.meterkenshin.bluetooth.ACTION_DATA_AVAILABLE"
        const val EXTRA_DATA = "com.example.meterkenshin.bluetooth.EXTRA_DATA"
    }
}