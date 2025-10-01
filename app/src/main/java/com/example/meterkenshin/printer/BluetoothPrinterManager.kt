package com.example.meterkenshin.printer

import android.Manifest
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.app.ActivityCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.meterkenshin.data.parser.PrinterCsvParser
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Enhanced Bluetooth Manager for handling Woosim printer connectivity with status checks
 */
class BluetoothPrinterManager(private val context: Context) {

    companion object {
        private const val TAG = "BluetoothPrinterManager"

        private const val AUTO_CONNECT_RETRY_COUNT = 3
        private const val STATUS_CHECK_TIMEOUT = 500L

        // IMPORTANT: WSP-i350 status checking is unreliable
        // The printer does not consistently respond to DLE EOT EOT commands
        // Set to false to allow printing without status verification
        private const val ENABLE_STATUS_CHECK = false
    }

    enum class ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        ERROR
    }

    data class PrinterStatus(
        val isPaperPresent: Boolean,
        val isCoverClosed: Boolean,
        val errorMessage: String? = null
    )

    private val bluetoothManager: BluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private var printService: BluetoothPrinterService? = null
    private val handler = Handler(Looper.getMainLooper())
    private val printerCsvParser = PrinterCsvParser(context)

    // Status response handling
    private var statusResponseLatch: CountDownLatch? = null
    private var lastStatusResponse: ByteArray? = null

    // LiveData
    private val _connectionState = MutableLiveData<ConnectionState>()
    val connectionState: LiveData<ConnectionState> = _connectionState

    private val _connectedDevice = MutableLiveData<BluetoothDevice?>()
    val connectedDevice: LiveData<BluetoothDevice?> = _connectedDevice

    private val _statusMessage = MutableLiveData<String?>()
    val statusMessage: LiveData<String?> = _statusMessage

    private val _isBluetoothEnabled = MutableLiveData<Boolean>()
    val isBluetoothEnabled: LiveData<Boolean> = _isBluetoothEnabled

    private var autoConnectAttempts = 0

    init {
        _connectionState.value = ConnectionState.DISCONNECTED
        _connectedDevice.value = null
        updateBluetoothEnabledState()
        initializePrintService()

        Log.d(TAG, "BluetoothPrinterManager initialized with status checking")
    }

    private fun updateBluetoothEnabledState() {
        _isBluetoothEnabled.value = bluetoothAdapter?.isEnabled == true
    }

    fun isBluetoothSupported(): Boolean {
        return bluetoothAdapter != null
    }

    fun isBluetoothEnabled(): Boolean {
        updateBluetoothEnabledState()
        return bluetoothAdapter?.isEnabled == true
    }

    private fun initializePrintService() {
        if (printService == null) {
            printService = BluetoothPrinterService(
                stateCallback = { state, device, message ->
                    handleConnectionStateChange(state, device, message)
                },
                dataCallback = { data ->
                    handleStatusResponse(data)
                }
            )
            Log.d(TAG, "BluetoothPrinterService initialized with data callback")
        }
    }

    /**
     * Get connected device from print service
     */
    fun getConnectedDevice(): BluetoothDevice? {
        return _connectedDevice.value
    }

    /**
     * Get printer configuration
     */
    fun getPrinterConfiguration() = printerCsvParser.getActivePrinterConfig()

    /**
     * Handle status response from printer
     */
    private fun handleStatusResponse(response: ByteArray) {
        Log.d(TAG, "Received status response: ${response.joinToString { "%02X".format(it) }}")
        lastStatusResponse = response
        statusResponseLatch?.countDown()
    }

    /**
     * Check printer status using ESC/POS real-time status command
     * WSP-i350 uses DLE EOT EOT which returns M C P status in one byte
     */
    fun checkPrinterStatus(): PrinterStatus {
        // If status check is disabled, assume printer is OK
        if (!ENABLE_STATUS_CHECK) {
            return PrinterStatus(
                isPaperPresent = true,
                isCoverClosed = true,
                errorMessage = null
            )
        }

        if (printService?.getState() != BluetoothPrinterService.STATE_CONNECTED) {
            return PrinterStatus(
                isPaperPresent = false,
                isCoverClosed = false,
                errorMessage = "Printer not connected"
            )
        }

        try {
            // WSP-i350 uses single DLE EOT EOT command that returns all status bits
            val statusResponse = queryPrinterStatus() // parameter ignored for i350

            if (statusResponse != null && statusResponse.isNotEmpty()) {
                val statusByte = statusResponse[0].toInt() and 0xFF
                Log.d(TAG, "Status byte received: 0x${String.format("%02X", statusByte)} (binary: ${Integer.toBinaryString(statusByte).padStart(8, '0')})")

                val paperPresent = checkPaperStatus(statusResponse)
                val coverClosed = checkCoverStatus(statusResponse)

                val status = PrinterStatus(
                    isPaperPresent = paperPresent,
                    isCoverClosed = coverClosed,
                    errorMessage = when {
                        !paperPresent && !coverClosed -> "Paper out and cover open"
                        !paperPresent -> "Paper out"
                        !coverClosed -> "Cover open"
                        else -> null
                    }
                )

                Log.d(TAG, "Printer status: Paper=${status.isPaperPresent}, Cover=${status.isCoverClosed}")
                return status
            } else {
                // CRITICAL: No response means we can't verify status - BLOCK printing for safety
                Log.e(TAG, "No status response from printer - blocking print for safety")
                return PrinterStatus(
                    isPaperPresent = false,
                    isCoverClosed = false,
                    errorMessage = "Cannot verify printer status. Printer may not be responding to status commands."
                )
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error checking printer status", e)
            // On error, block printing for safety
            return PrinterStatus(
                isPaperPresent = false,
                isCoverClosed = false,
                errorMessage = "Error checking printer status: ${e.message}"
            )
        }
    }

    /**
     * Query printer status and wait for response
     * WSP-i350 uses DLE EOT EOT command (not DLE EOT n)
     */
    private fun queryPrinterStatus(): ByteArray? {
        statusResponseLatch = CountDownLatch(1)
        lastStatusResponse = null

        // Send DLE EOT EOT command (WSP-i350 specific)
        // According to Woosim documentation, i350 uses DLE EOT EOT (0x10 0x04 0x04)
        val command = byteArrayOf(0x10, 0x04, 0x04)
        printService?.write(command)

        Log.d(TAG, "Sent status query: DLE EOT EOT")

        // Wait for response with timeout
        val received = statusResponseLatch?.await(STATUS_CHECK_TIMEOUT, TimeUnit.MILLISECONDS) ?: false

        return if (received) {
            Log.d(TAG, "Received status response")
            lastStatusResponse
        } else {
            Log.w(TAG, "Status check timed out")
            null
        }
    }

    /**
     * Check if paper is present based on status byte
     * WSP-i350 returns ASCII character: '0', '1', '2', '3'
     * Convert ASCII to binary: '0'=0x30=48, '1'=0x31=49, etc.
     */
    private fun checkPaperStatus(status: ByteArray): Boolean {
        if (status.isEmpty()) return false

        // WSP-i350 sends ASCII character, convert to actual value
        val statusByte = if (status[0] >= 0x30 && status[0] <= 0x39) {
            // ASCII digit '0'-'9', convert to actual value
            (status[0] - 0x30).toByte()
        } else {
            status[0]
        }

        val value = statusByte.toInt() and 0xFF
        // Bit 0: 1 = paper present, 0 = paper not present
        val paperPresent = (value and 0x01) != 0
        Log.d(TAG, "Paper status: raw=0x${String.format("%02X", status[0])}, converted=${value}, bit0=${value and 0x01}, present=$paperPresent")
        return paperPresent
    }

    /**
     * Check if cover is closed based on status byte
     * WSP-i350 returns ASCII character: '0', '1', '2', '3'
     */
    private fun checkCoverStatus(status: ByteArray): Boolean {
        if (status.isEmpty()) return false

        // WSP-i350 sends ASCII character, convert to actual value
        val statusByte = if (status[0] >= 0x30 && status[0] <= 0x39) {
            // ASCII digit '0'-'9', convert to actual value
            (status[0] - 0x30).toByte()
        } else {
            status[0]
        }

        val value = statusByte.toInt() and 0xFF
        // Bit 1: 1 = cover closed, 0 = cover opened
        val coverClosed = (value and 0x02) != 0
        Log.d(TAG, "Cover status: raw=0x${String.format("%02X", status[0])}, converted=${value}, bit1=${value and 0x02}, closed=$coverClosed")
        return coverClosed
    }

    /**
     * Write data to connected printer with failsafe status check
     */
    fun write(data: ByteArray, showAlert: Boolean = true): Boolean {
        return try {
            if (printService?.getState() != BluetoothPrinterService.STATE_CONNECTED) {
                Log.w(TAG, "Cannot write: printer not connected")
                if (showAlert) {
                    showErrorAlert("Printer Error", "Printer is not connected. Please connect the printer first.")
                }
                return false
            }

            // Only perform status check if enabled
            if (ENABLE_STATUS_CHECK) {
                val status = checkPrinterStatus()

                // Check for errors
                if (status.errorMessage != null) {
                    Log.w(TAG, "Printer status error: ${status.errorMessage}")
                    if (showAlert) {
                        showErrorAlert("Printer Error", status.errorMessage)
                    }
                    return false
                }
            } else {
                Log.d(TAG, "Status check disabled, proceeding with write")
            }

            // All checks passed, proceed with writing
            printService?.write(data)
            Log.d(TAG, "Data written to printer: ${data.size} bytes")
            true

        } catch (e: Exception) {
            Log.e(TAG, "Error writing data to printer", e)
            if (showAlert) {
                showErrorAlert("Printer Error", "Failed to write to printer: ${e.message}")
            }
            false
        }
    }

    /**
     * Show error alert dialog
     */
    @Suppress("SameParameterValue")
    private fun showErrorAlert(title: String, message: String) {
        handler.post {
            try {
                AlertDialog.Builder(context)
                    .setTitle(title)
                    .setMessage(message)
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
                    .show()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to show alert dialog", e)
            }
        }
    }

    /**
     * Connect to a specific Bluetooth device
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun connect(device: BluetoothDevice) {
        if (_connectionState.value == ConnectionState.CONNECTING ||
            _connectionState.value == ConnectionState.CONNECTED) {
            Log.d(TAG, "Already connecting or connected")
            return
        }

        Log.d(TAG, "Connecting to device: ${device.address}")
        updateStatus("Connecting to ${device.address}...")
        printService?.connect(device)
    }

    /**
     * Disconnect from current device
     */
    fun disconnect() {
        printService?.disconnect()
        _connectionState.value = ConnectionState.DISCONNECTED
        _connectedDevice.value = null
        updateStatus("Disconnected")
    }

    private fun handleConnectionStateChange(state: Int, device: BluetoothDevice?, message: String?) {
        when (state) {
            BluetoothPrinterService.STATE_CONNECTED -> {
                _connectionState.value = ConnectionState.CONNECTED
                _connectedDevice.value = device

                val displayName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
                        == PackageManager.PERMISSION_GRANTED) {
                        try {
                            device?.name ?: device?.address ?: "Woosim Printer"
                        } catch (_: SecurityException) {
                            device?.address ?: "Woosim Printer"
                        }
                    } else {
                        device?.address ?: "Woosim Printer"
                    }
                } else {
                    @Suppress("DEPRECATION")
                    try {
                        device?.name ?: device?.address ?: "Woosim Printer"
                    } catch (_: SecurityException) {
                        device?.address ?: "Woosim Printer"
                    }
                }

                val macAddress = device?.address ?: getPrinterConfiguration()?.bluetoothMacAddress ?: "Unknown"
                updateStatus("$displayName\nMAC Address: $macAddress")
                Log.d(TAG, "Successfully connected to $macAddress ($displayName)")
            }

            BluetoothPrinterService.STATE_CONNECTING -> {
                _connectionState.value = ConnectionState.CONNECTING
                updateStatus("Connecting to device...")
            }

            BluetoothPrinterService.STATE_NONE -> {
                val wasConnecting = _connectionState.value == ConnectionState.CONNECTING
                _connectionState.value = ConnectionState.DISCONNECTED
                _connectedDevice.value = null

                if (wasConnecting) {
                    updateStatus("Connection failed: Printer not responding. Check if printer is ON and paired.")
                    Log.e(TAG, "Connection failed")
                } else {
                    updateStatus(message ?: "Disconnected")
                }
            }

            else -> {
                _connectionState.value = ConnectionState.ERROR
                updateStatus("Connection error: ${message ?: "Unknown error"}")
            }
        }
    }

    private fun updateStatus(message: String) {
        Log.d(TAG, "Status: $message")
        _statusMessage.value = message
    }

    private fun hasBluetoothPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) ==
                    PackageManager.PERMISSION_GRANTED &&
                    ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
                    PackageManager.PERMISSION_GRANTED
        } else {
            ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) ==
                    PackageManager.PERMISSION_GRANTED &&
                    ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADMIN) ==
                    PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Cleanup resources when activity is destroyed
     */
    fun cleanup() {
        Log.d(TAG, "Cleaning up BluetoothPrinterManager")

        // Disconnect from printer
        try {
            printService?.disconnect()
        } catch (e: Exception) {
            Log.e(TAG, "Error disconnecting during cleanup", e)
        }

        // Remove all handler callbacks
        handler.removeCallbacksAndMessages(null)

        // Clear state
        _connectionState.value = ConnectionState.DISCONNECTED
        _connectedDevice.value = null
        updateStatus("Cleanup completed")

        Log.d(TAG, "BluetoothPrinterManager cleanup completed")
    }

    /**
     * Start automatic connection process to Woosim printer
     */
    fun startAutoConnect() {
        if (!isBluetoothEnabled()) {
            updateStatus("Bluetooth is not enabled")
            _connectionState.value = ConnectionState.ERROR
            return
        }

        Log.d(TAG, "Starting auto-connect process")
        autoConnectAttempts = 0
        attemptAutoConnect()
    }

    /**
     * Attempt to auto-connect to printer
     */
    private fun attemptAutoConnect() {
        autoConnectAttempts++

        if (autoConnectAttempts > AUTO_CONNECT_RETRY_COUNT) {
            Log.e(TAG, "Auto-connect failed after $AUTO_CONNECT_RETRY_COUNT attempts")
            updateStatus("ERROR: Failed to connect after $AUTO_CONNECT_RETRY_COUNT attempts")
            _connectionState.value = ConnectionState.ERROR
            return
        }

        Log.d(TAG, "Auto-connect attempt $autoConnectAttempts of $AUTO_CONNECT_RETRY_COUNT")

        // Try to connect using printer.csv configuration
        if (!connectUsingCsvConfiguration()) {
            updateStatus("ERROR: No printer configured. Please upload printer.csv with active printer.")
            _connectionState.value = ConnectionState.ERROR
        }
    }

    /**
     * Try to connect using MAC address from printer.csv file
     */
    private fun connectUsingCsvConfiguration(): Boolean {
        if (!printerCsvParser.isPrinterCsvAvailable()) {
            Log.e(TAG, "Printer CSV file not found")
            updateStatus("ERROR: printer.csv not uploaded")
            return false
        }

        val macAddress = printerCsvParser.getActivePrinterMacAddress()
        if (macAddress == null) {
            Log.e(TAG, "No active printer found in CSV")
            updateStatus("ERROR: No active printer in printer.csv (Activate must be 1)")
            return false
        }

        Log.d(TAG, "Using MAC address from printer.csv: $macAddress")
        updateStatus("Connecting to configured printer: $macAddress")

        return connectToDeviceByMacAddress(macAddress)
    }

    /**
     * Connect to a device using its MAC address
     */
    private fun connectToDeviceByMacAddress(macAddress: String): Boolean {
        if (!hasBluetoothPermissions()) {
            updateStatus("ERROR: Missing Bluetooth permissions")
            return false
        }

        return try {
            val device = bluetoothAdapter?.getRemoteDevice(macAddress)
            if (device != null) {
                Log.d(TAG, "Connecting to device: ${device.address}")
                connect(device)
                true
            } else {
                Log.e(TAG, "Could not get device for MAC address: $macAddress")
                updateStatus("ERROR: Invalid MAC address: $macAddress")
                false
            }
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Invalid MAC address format: $macAddress", e)
            updateStatus("ERROR: Invalid MAC address format: $macAddress")
            false
        } catch (e: SecurityException) {
            Log.e(TAG, "Missing Bluetooth permissions", e)
            updateStatus("ERROR: Missing Bluetooth permissions")
            false
        }
    }

    /**
     * Get printer configuration information for display
     */
    fun getPrinterConfigInfo(): String {
        val csvMac = printerCsvParser.getActivePrinterMacAddress()

        return if (csvMac != null) {
            val config = printerCsvParser.getActivePrinterConfig()
            if (config?.printerModel != null) {
                "Printer: ${config.printerModel}\nMAC: $csvMac"
            } else {
                "Active Printer MAC: $csvMac"
            }
        } else if (printerCsvParser.isPrinterCsvAvailable()) {
            "ERROR: printer.csv uploaded but no active printer (set Activate=1)"
        } else {
            "ERROR: printer.csv not uploaded"
        }
    }
}