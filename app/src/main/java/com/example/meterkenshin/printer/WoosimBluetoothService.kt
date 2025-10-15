package com.example.meterkenshin.printer

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.Arrays

/**
 * Bluetooth Service using Woosim Library architecture
 * Based on woosim-samples BluetoothPrintService
 */
class WoosimBluetoothService(
    private val handler: Handler
) {
    companion object {
        private const val TAG = "WoosimBluetoothService"
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

        // Connection states
        const val STATE_NONE = 0
        const val STATE_CONNECTING = 2
        const val STATE_CONNECTED = 3

        // Message types for handler
        const val MESSAGE_STATE_CHANGE = 1
        const val MESSAGE_READ = 3
        const val MESSAGE_WRITE = 4
        const val MESSAGE_DEVICE_NAME = 5
        const val MESSAGE_TOAST = 6

        const val DEVICE_NAME = "device_name"
        const val TOAST = "toast"
    }

    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var connectThread: ConnectThread? = null
    private var connectedThread: ConnectedThread? = null
    private var state: Int = STATE_NONE

    @Synchronized
    private fun setState(newState: Int) {
        Log.d(TAG, "setState() $state -> $newState")
        state = newState
        handler.obtainMessage(MESSAGE_STATE_CHANGE, newState, -1).sendToTarget()
    }

    @Synchronized
    fun getState(): Int = state

    @Synchronized
    fun start() {
        Log.d(TAG, "start")

        connectThread?.cancel()
        connectThread = null

        connectedThread?.cancel()
        connectedThread = null

        setState(STATE_NONE)
    }

    @Synchronized
    fun connect(device: BluetoothDevice) {
        Log.d(TAG, "connect to: $device")

        if (state == STATE_CONNECTING) {
            connectThread?.cancel()
            connectThread = null
        }

        connectedThread?.cancel()
        connectedThread = null

        connectThread = ConnectThread(device)
        connectThread?.start()
        setState(STATE_CONNECTING)
    }

    @Synchronized
    fun connected(socket: BluetoothSocket, device: BluetoothDevice) {
        Log.d(TAG, "connected")

        connectThread?.cancel()
        connectThread = null

        connectedThread?.cancel()
        connectedThread = null

        connectedThread = ConnectedThread(socket)
        connectedThread?.start()

        val msg = handler.obtainMessage(MESSAGE_DEVICE_NAME)
        msg.data.putString(DEVICE_NAME, device.name)
        handler.sendMessage(msg)

        setState(STATE_CONNECTED)
    }

    @Synchronized
    fun stop() {
        Log.d(TAG, "stop")

        connectThread?.cancel()
        connectThread = null

        connectedThread?.cancel()
        connectedThread = null

        setState(STATE_NONE)
    }

    fun write(out: ByteArray) {
        val r: ConnectedThread?
        synchronized(this) {
            if (state != STATE_CONNECTED) return
            r = connectedThread
        }
        r?.write(out)
    }

    private fun connectionFailed() {
        if (state == STATE_NONE) return

        val msg = handler.obtainMessage(MESSAGE_TOAST)
        msg.data.putString(TOAST, "Unable to connect device")
        handler.sendMessage(msg)

        start()
    }

    private fun connectionLost() {
        if (state == STATE_NONE) return

        val msg = handler.obtainMessage(MESSAGE_TOAST)
        msg.data.putString(TOAST, "Device connection was lost")
        handler.sendMessage(msg)

        start()
    }

    private inner class ConnectThread(private val device: BluetoothDevice) : Thread() {
        private val socket: BluetoothSocket?

        init {
            var tmp: BluetoothSocket? = null
            try {
                tmp = device.createRfcommSocketToServiceRecord(SPP_UUID)
            } catch (e: IOException) {
                Log.e(TAG, "create() failed", e)
            }
            socket = tmp
        }

        override fun run() {
            Log.i(TAG, "BEGIN ConnectThread")
            name = "ConnectThread"

            bluetoothAdapter?.cancelDiscovery()

            try {
                socket?.connect()
            } catch (e: IOException) {
                Log.e(TAG, "Connection failed", e)
                try {
                    socket?.close()
                } catch (e2: IOException) {
                    Log.e(TAG, "unable to close() socket during connection failure", e2)
                }
                connectionFailed()
                return
            }

            synchronized(this@WoosimBluetoothService) {
                connectThread = null
            }

            socket?.let { connected(it, device) }
        }

        fun cancel() {
            try {
                socket?.close()
            } catch (e: IOException) {
                Log.e(TAG, "close() of connect socket failed", e)
            }
        }
    }

    private inner class ConnectedThread(private val socket: BluetoothSocket) : Thread() {
        private val inStream: InputStream?
        private val outStream: OutputStream?

        init {
            Log.d(TAG, "create ConnectedThread")
            var tmpIn: InputStream? = null
            var tmpOut: OutputStream? = null

            try {
                tmpIn = socket.inputStream
                tmpOut = socket.outputStream
            } catch (e: IOException) {
                Log.e(TAG, "temp sockets not created", e)
            }

            inStream = tmpIn
            outStream = tmpOut
        }

        override fun run() {
            Log.i(TAG, "BEGIN ConnectedThread")
            val buffer = ByteArray(1024)

            while (true) {
                try {
                    val bytes = inStream?.read(buffer) ?: break
                    val rcvData = Arrays.copyOf(buffer, bytes)
                    handler.obtainMessage(MESSAGE_READ, bytes, -1, rcvData).sendToTarget()
                } catch (e: IOException) {
                    Log.e(TAG, "Connection Lost", e)
                    connectionLost()
                    break
                }
            }
        }

        fun write(buffer: ByteArray) {
            try {
                outStream?.write(buffer)
                handler.obtainMessage(MESSAGE_WRITE, -1, -1, buffer).sendToTarget()
            } catch (e: IOException) {
                Log.e(TAG, "Exception during write", e)
            }
        }

        fun cancel() {
            try {
                inStream?.close()
                outStream?.close()
                socket.close()
            } catch (e: IOException) {
                Log.e(TAG, "close() of connect socket failed", e)
            }
        }
    }
}