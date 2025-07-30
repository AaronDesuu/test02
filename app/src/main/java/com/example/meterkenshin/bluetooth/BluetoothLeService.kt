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
class BluetoothLeService : Service() {
    private var mBluetoothManager: BluetoothManager? = null
    private var mBluetoothAdapter: BluetoothAdapter? = null
    private var mBluetoothDeviceAddress: String? = null
    private var mBluetoothGatt: BluetoothGatt? = null
    private var mConnectionState = STATE_INITIATE
    private var mService: BluetoothGattService? = null
    private var mCharacteristic: BluetoothGattCharacteristic? = null

    private val uuid_service: UUID = UUID.fromString("b973f2e0-b19e-11e2-9e96-0800200c9a66")
    private val uuid_read: UUID = UUID.fromString("d973f2e1-b19e-11e2-9e96-0800200c9a66")
    private val uuid_write: UUID = UUID.fromString("e973f2e2-b19e-11e2-9e96-0800200c9a66")
    private val uuid_config: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    // Implements callback methods for GATT events that the app cares about.  For example,
    // connection change and services discovered.
    private val mGattCallback: BluetoothGattCallback = object : BluetoothGattCallback() {
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                if (mConnectionState == STATE_CONNECTING) {
                    mConnectionState = STATE_CONNECTED
                    broadcastUpdate(ACTION_GATT_CONNECTED)
                    mBluetoothGatt!!.discoverServices()
                    Log.i(TAG, "Connected to GATT server and call discoverServices.")
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
                mConnectionState = STATE_DISCOVERED
                mService = mBluetoothGatt!!.getService(uuid_service)
                mCharacteristic = mService!!.getCharacteristic(uuid_read)
                val descriptor = mCharacteristic!!.getDescriptor(uuid_config)
                //              mBluetoothGatt.requestConnectionPriority(CONNECTION_PRIORITY_HIGH);
                mBluetoothGatt!!.setCharacteristicNotification(mCharacteristic, true)
                descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                mBluetoothGatt!!.writeDescriptor(descriptor)
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
            Log.w(TAG, "onCharacteristicRead: " + status)
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
        if (mConnectionState == STATE_DISCOVERED) {
            Log.i(TAG, String.format("write:%d", `in`.size))
            if (`in` != null) {
                val Characteristic =
                    mBluetoothGatt!!.getService(uuid_service).getCharacteristic(uuid_write)
                Characteristic.setValue(`in`)
                mBluetoothGatt!!.writeCharacteristic(Characteristic)
                return true
            } else {
                return false
            }
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

        if (uuid_read == characteristic.getUuid()) {
            // For all other profiles, writes the data formatted in HEX.
            val data = characteristic.getValue()
            if (data != null && data.size > 0) {
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

    override fun onUnbind(intent: Intent?): Boolean {
        // After using a given device, you should make sure that BluetoothGatt.close() is called
        // such that resources are cleaned up properly.  In this particular example, close() is
        // invoked when the UI is disconnected from the Service.
        return super.onUnbind(intent)
    }

    private val mBinder: IBinder = LocalBinder()

    /**
     * Initializes a reference to the local Bluetooth adapter.
     *
     * @return Return true if the initialization is successful.
     */
    fun initialize(): Boolean {
        // For API level 18 and above, get a reference to BluetoothAdapter through
        // BluetoothManager.
        if (mBluetoothManager == null) {
            mBluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager?
            if (mBluetoothManager == null) {
                Log.e(TAG, "Unable to initialize BluetoothManager.")
                return false
            }
        }
        mBluetoothAdapter = mBluetoothManager!!.getAdapter()
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
     *
     * @param address The device address of the destination device.
     *
     * @return Return true if the connection is initiated successfully. The connection result
     * is reported asynchronously through the
     * `BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)`
     * callback.
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun connect(address: String): Boolean {
        if (mConnectionState < STATE_INITIALIZE) {
            Log.w(TAG, "Dose not initialized")
            return false
        }
        if (mConnectionState >= STATE_CONNECTED) {
            Log.w(TAG, "Dose not disconnect before connect")
            disconnect()
            return false
        } else {
            // Previously connected device.  Try to reconnect.
            if (mBluetoothDeviceAddress != null && address == mBluetoothDeviceAddress && mBluetoothGatt != null) {
                if (mBluetoothGatt!!.connect()) {
                    mConnectionState = STATE_CONNECTING
                    Log.d(TAG, "Try to use an existing mBluetoothGatt for connection.")
                    return true
                } else {
                    Log.d(TAG, "Fail to use an existing mBluetoothGatt for connection.")
                }
            }
            mBluetoothDeviceAddress = null
            if (mBluetoothGatt != null) {
                mBluetoothGatt!!.disconnect()
                mBluetoothGatt!!.close()
                Log.d(TAG, "mBluetoothGatt.disconnect()")
                mBluetoothGatt = null
            }
            mConnectionState = STATE_INITIALIZE

            val device = mBluetoothAdapter!!.getRemoteDevice(address)
            if (device == null) {
                Log.w(TAG, "Device not found.  Unable to connect.")
                return false
            }
            // We want to directly connect to the device, so we are setting the autoConnect
            // parameter to false.
            mBluetoothGatt = device.connectGatt(this, false, mGattCallback)
            Log.d(TAG, "Trying to create a new connection.")
            mBluetoothDeviceAddress = address
            mConnectionState = STATE_CONNECTING
            return true
        }
    }

    /**
     * Disconnects an existing connection or cancel a pending connection. The disconnection result
     * is reported asynchronously through the
     * `BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)`
     * callback.
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun disconnect() {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized")
            return
        }
        if (mConnectionState >= STATE_CONNECTED) {
            mBluetoothGatt!!.disconnect()
            mBluetoothGatt!!.close()
        }
    }

    companion object {
        private val TAG: String = BluetoothLeService::class.java.getSimpleName()
        private val STATE_INITIATE = -1
        private const val STATE_INITIALIZE = 0
        private const val STATE_DISCONNECTED = 1
        private const val STATE_CONNECTING = 2
        private const val STATE_CONNECTED = 3
        private const val STATE_DISCOVERED = 4

        const val ACTION_GATT_ERROR: String = "com.fujielectricmeter.bluetooth.le.ACTION_GATT_ERROR"
        const val ACTION_GATT_CONNECTED: String =
            "com.fujielectricmeter.bluetooth.le.ACTION_GATT_CONNECTED"
        const val ACTION_GATT_DISCONNECTED: String =
            "com.fujielectricmeter.bluetooth.le.ACTION_GATT_DISCONNECTED"
        const val ACTION_GATT_SERVICES_DISCOVERED: String =
            "com.fujielectricmeter.bluetooth.le.ACTION_GATT_SERVICES_DISCOVERED"
        const val ACTION_DATA_AVAILABLE: String =
            "com.fujielectricmeter.bluetooth.le.ACTION_DATA_AVAILABLE"
        const val EXTRA_DATA: String = "com.fujielectricmeter.bluetooth.le.EXTRA_DATA"
    }
}