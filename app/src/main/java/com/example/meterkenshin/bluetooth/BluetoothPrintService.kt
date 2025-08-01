package com.example.meterkenshin.bluetooth

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.RequiresPermission
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.*

/**
 * Bluetooth Print Service for managing connection and communication with Woosim printer
 * Fixed threading issues and improved error handling
 */
class BluetoothPrintService(
    private val handler: Handler,
    private val stateCallback: ((Int, BluetoothDevice?, String?) -> Unit)? = null
) {

    companion object {
        private const val TAG = "BluetoothPrintService"
        private const val D = true

        // SPP UUID for Bluetooth Serial communication
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

        // Connection states
        const val STATE_NONE = 0       // Doing nothing
        const val STATE_LISTEN = 1     // Listening for incoming connections
        const val STATE_CONNECTING = 2 // Initiating an outgoing connection
        const val STATE_CONNECTED = 3  // Connected to a remote device
    }

    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var connectThread: ConnectThread? = null
    private var connectedThread: ConnectedThread? = null
    private var currentState: Int = STATE_NONE

    // Handler for main thread operations
    private val mainHandler = Handler(Looper.getMainLooper())

    init {
        if (D) Log.d(TAG, "BluetoothPrintService initialized")
    }

    /**
     * Set the current state of the connection (thread-safe)
     */
    @Synchronized
    private fun setState(state: Int) {
        if (D) Log.d(TAG, "setState() $currentState -> $state")
        currentState = state

        // Ensure state callback is called on main thread
        mainHandler.post {
            stateCallback?.invoke(state, null, null)
        }
    }

    /**
     * Return the current connection state
     */
    @Synchronized
    fun getState(): Int = currentState

    /**
     * Start the print service
     */
    @Synchronized
    fun start() {
        if (D) Log.d(TAG, "start")

        // Cancel any thread attempting to make a connection
        connectThread?.cancel()
        connectThread = null

        // Cancel any thread currently running a connection
        connectedThread?.cancel()
        connectedThread = null

        setState(STATE_LISTEN)
    }

    /**
     * Start the ConnectThread to initiate a connection to a remote device
     */
    @Synchronized
    fun connect(device: BluetoothDevice) {
        if (D) Log.d(TAG, "connect to: $device")

        // Cancel any thread attempting to make a connection
        if (currentState == STATE_CONNECTING) {
            connectThread?.cancel()
            connectThread = null
        }

        // Cancel any thread currently running a connection
        connectedThread?.cancel()
        connectedThread = null

        // Start the thread to connect with the given device
        connectThread = ConnectThread(device)
        connectThread?.start()
        setState(STATE_CONNECTING)
    }

    /**
     * Start the ConnectedThread to begin managing a Bluetooth connection
     */
    @Synchronized
    private fun connected(socket: BluetoothSocket, device: BluetoothDevice) {
        if (D) Log.d(TAG, "connected")

        // Cancel the thread that completed the connection
        connectThread?.cancel()
        connectThread = null

        // Cancel any thread currently running a connection
        connectedThread?.cancel()
        connectedThread = null

        // Start the thread to manage the connection and perform transmissions
        connectedThread = ConnectedThread(socket)
        connectedThread?.start()

        // Send the connected device info back on main thread
        mainHandler.post {
            stateCallback?.invoke(STATE_CONNECTED, device, "Connected")
        }
        setState(STATE_CONNECTED)
    }

    /**
     * Stop all threads
     */
    @Synchronized
    fun stop() {
        if (D) Log.d(TAG, "stop")

        connectThread?.cancel()
        connectThread = null

        connectedThread?.cancel()
        connectedThread = null

        setState(STATE_NONE)
    }

    /**
     * Disconnect from current device
     */
    fun disconnect() {
        stop()
    }

    /**
     * Write to the ConnectedThread in an unsynchronized manner
     */
    fun write(out: ByteArray) {
        val thread = connectedThread
        if (thread != null) {
            thread.write(out)
        } else {
            Log.w(TAG, "Cannot write: not connected")
        }
    }

    /**
     * Indicate that the connection attempt failed and notify the UI Activity (thread-safe)
     */
    private fun connectionFailed() {
        Log.e(TAG, "Connection attempt failed")
        setState(STATE_NONE)

        // Notify on main thread
        mainHandler.post {
            stateCallback?.invoke(STATE_NONE, null, "Connection failed")
        }
    }

    /**
     * Indicate that the connection was lost and notify the UI Activity (thread-safe)
     */
    private fun connectionLost() {
        Log.e(TAG, "Connection lost")
        setState(STATE_NONE)

        // Notify on main thread
        mainHandler.post {
            stateCallback?.invoke(STATE_NONE, null, "Connection lost")
        }
    }

    /**
     * This thread runs while attempting to make an outgoing connection with a device
     */
    private inner class ConnectThread(private val device: BluetoothDevice) : Thread() {
        private val socket: BluetoothSocket?

        init {
            var tmp: BluetoothSocket? = null

            try {
                // Create a secure RFCOMM socket
                @Suppress("MissingPermission")
                tmp = device.createRfcommSocketToServiceRecord(SPP_UUID)
            } catch (e: IOException) {
                Log.e(TAG, "Socket create() failed", e)
            } catch (e: SecurityException) {
                Log.e(TAG, "Missing Bluetooth permissions", e)
            }
            socket = tmp
        }

        override fun run() {
            Log.i(TAG, "BEGIN ConnectThread")
            name = "ConnectThread"

            try {
                // Always cancel discovery because it will slow down the connection
                bluetoothAdapter?.cancelDiscovery()
            } catch (e: SecurityException) {
                Log.w(TAG, "Cannot cancel discovery due to permissions", e)
            }

            try {
                // Connect the device through the socket. This will block
                // until it succeeds or throws an exception
                socket?.connect()
                Log.d(TAG, "Socket connected successfully")
            } catch (e: IOException) {
                Log.e(TAG, "Unable to connect() socket", e)

                // Close the socket
                try {
                    socket?.close()
                } catch (e2: IOException) {
                    Log.e(TAG, "unable to close() socket during connection failure", e2)
                }
                connectionFailed()
                return
            } catch (e: SecurityException) {
                Log.e(TAG, "Security exception during connection", e)
                connectionFailed()
                return
            }

            // Reset the ConnectThread because we're done
            synchronized(this@BluetoothPrintService) {
                connectThread = null
            }

            // Start the connected thread
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

    /**
     * This thread runs during a connection with a remote device
     */
    private inner class ConnectedThread(private val socket: BluetoothSocket) : Thread() {
        private val inputStream: InputStream?
        private val outputStream: OutputStream?

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

            inputStream = tmpIn
            outputStream = tmpOut
        }

        override fun run() {
            Log.i(TAG, "BEGIN ConnectedThread")
            val buffer = ByteArray(1024)
            var bytes: Int

            // Keep listening to the InputStream until an exception occurs
            while (currentState == STATE_CONNECTED) {
                try {
                    // Read from the InputStream
                    bytes = inputStream?.read(buffer) ?: -1
                    if (bytes > 0) {
                        // Send the obtained bytes to the UI Activity
                        val readBuffer = buffer.copyOf(bytes)
                        mainHandler.post {
                            // Handle received data if needed
                            // For now, just log it
                            Log.d(TAG, "Received ${bytes} bytes")
                        }
                    }
                } catch (e: IOException) {
                    Log.d(TAG, "disconnected", e)
                    connectionLost()
                    break
                }
            }
        }

        /**
         * Write to the connected OutStream
         */
        fun write(buffer: ByteArray) {
            try {
                outputStream?.write(buffer)
                outputStream?.flush()
                Log.d(TAG, "Wrote ${buffer.size} bytes")
            } catch (e: IOException) {
                Log.e(TAG, "Exception during write", e)
                connectionLost()
            }
        }

        fun cancel() {
            try {
                socket.close()
            } catch (e: IOException) {
                Log.e(TAG, "close() of connect socket failed", e)
            }
        }
    }
}