package com.example.meterkenshin.bluetooth

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.app.ActivityCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.meterkenshin.data.parser.PrinterCsvParser
import kotlinx.coroutines.*

/**
 * Bluetooth Manager for handling Woosim printer connectivity
 * Enhanced to prioritize printer.csv configuration over hardcoded MAC address
 */
class BluetoothManager(private val context: Context) {

    companion object {
        private const val TAG = "BluetoothManager"
        private const val WOOSIM_DEVICE_PREFIX = "F5" // Woosim devices contain "F5" in their name
        private const val SCAN_DURATION = 10000L // 10 seconds scan duration
        private const val AUTO_CONNECT_RETRY_COUNT = 3
        private const val AUTO_CONNECT_RETRY_DELAY = 2000L // 2 seconds
    }

    enum class ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        ERROR
    }

    private val bluetoothManager: BluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private var printService: BluetoothPrintService? = null
    private val handler = Handler(Looper.getMainLooper())
    private val printerCsvParser = PrinterCsvParser(context)

    // LiveData for connection state
    private val _connectionState = MutableLiveData<ConnectionState>()
    val connectionState: LiveData<ConnectionState> = _connectionState

    // LiveData for connected device
    private val _connectedDevice = MutableLiveData<BluetoothDevice?>()
    val connectedDevice: LiveData<BluetoothDevice?> = _connectedDevice

    // LiveData for status messages
    private val _statusMessage = MutableLiveData<String?>()
    val statusMessage: LiveData<String?> = _statusMessage

    // LiveData for Bluetooth enabled state
    private val _isBluetoothEnabled = MutableLiveData<Boolean>()
    val isBluetoothEnabled: LiveData<Boolean> = _isBluetoothEnabled

    // Scanning state
    private var isScanning = false
    private var autoConnectAttempts = 0

    init {
        _connectionState.value = ConnectionState.DISCONNECTED
        _connectedDevice.value = null
        updateBluetoothEnabledState()
        initializePrintService()

        Log.d(TAG, "BluetoothManager initialized")
        Log.d(TAG, "Bluetooth enabled: ${_isBluetoothEnabled.value}")
        Log.d(TAG, "Printer CSV available: ${printerCsvParser.isPrinterCsvAvailable()}")
        Log.d(TAG, "Configuration: ${printerCsvParser.getConfigurationSummary()}")
    }

    /**
     * Update Bluetooth enabled state
     */
    private fun updateBluetoothEnabledState() {
        _isBluetoothEnabled.value = bluetoothAdapter?.isEnabled == true
    }

    /**
     * Check if Bluetooth is supported on this device
     */
    fun isBluetoothSupported(): Boolean {
        return bluetoothAdapter != null
    }

    /**
     * Check if Bluetooth is currently enabled
     */
    fun isBluetoothEnabled(): Boolean {
        updateBluetoothEnabledState()
        return bluetoothAdapter?.isEnabled == true
    }

    /**
     * Check if device is currently connected
     */
    fun isConnected(): Boolean {
        return _connectionState.value == ConnectionState.CONNECTED &&
                _connectedDevice.value != null
    }

    /**
     * Get the print service instance
     */
    fun getPrintService(): BluetoothPrintService? = printService

    /**
     * Write data to connected printer
     */
    fun write(data: ByteArray): Boolean {
        return try {
            if (printService?.getState() == BluetoothPrintService.STATE_CONNECTED) {
                printService?.write(data)
                Log.d(TAG, "Data written to printer: ${data.size} bytes")
                true
            } else {
                Log.w(TAG, "Cannot write: printer not connected")
                updateStatus("ERROR: Printer not connected")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error writing data to printer", e)
            updateStatus("ERROR: Failed to write to printer")
            false
        }
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

    /**
     * Update status message
     */
    private fun updateStatus(message: String) {
        Log.d(TAG, "Status: $message")
        _statusMessage.value = message
    }

    /**
     * Check if app has required Bluetooth permissions
     */
    private fun hasBluetoothPermissions(): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
                    PackageManager.PERMISSION_GRANTED &&
                    ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) ==
                    PackageManager.PERMISSION_GRANTED
        } else {
            ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) ==
                    PackageManager.PERMISSION_GRANTED &&
                    ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Initialize the print service
     */
    private fun initializePrintService() {
        if (printService == null) {
            printService = BluetoothPrintService(handler) { state, device, message ->
                handleConnectionStateChange(state, device, message)
            }
            Log.d(TAG, "BluetoothPrintService initialized")
        }
    }

    /**
     * Connect to the Woosim printer using configured MAC address
     * Prioritizes printer.csv over fallback hardcoded address
     */
    suspend fun connectToSpecificDevice(macAddress: String? = null) {
        if (!isBluetoothEnabled()) {
            updateStatus("Bluetooth is not enabled")
            _connectionState.value = ConnectionState.ERROR
            return
        }

        if (!hasBluetoothPermissions()) {
            updateStatus("Missing Bluetooth permissions")
            _connectionState.value = ConnectionState.ERROR
            return
        }

        // Determine which MAC address to use
        val targetMacAddress = macAddress ?: getConfiguredPrinterMacAddress()

        if (targetMacAddress == null) {
            updateStatus("No printer configured")
            _connectionState.value = ConnectionState.ERROR
            return
        }

        Log.d(TAG, "Attempting to connect to device: $targetMacAddress")
        updateStatus("Connecting to Woosim printer...")

        try {
            val device = bluetoothAdapter?.getRemoteDevice(targetMacAddress)
            if (device != null) {
                connectToDevice(device)
            } else {
                updateStatus("Could not get device for MAC address: $targetMacAddress")
                _connectionState.value = ConnectionState.ERROR
            }
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Invalid MAC address format: $targetMacAddress", e)
            updateStatus("Invalid MAC address format: $targetMacAddress")
            _connectionState.value = ConnectionState.ERROR
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception: Missing Bluetooth permissions", e)
            updateStatus("Missing Bluetooth permissions")
            _connectionState.value = ConnectionState.ERROR
        }
    }

    /**
     * Start automatic connection process to Woosim printer
     * Requires printer.csv configuration to be uploaded
     */
    fun startAutoConnect() {
        if (!isBluetoothEnabled()) {
            updateStatus("Bluetooth is not enabled")
            _connectionState.value = ConnectionState.ERROR
            return
        }

        Log.d(TAG, "Starting auto-connect process")
        autoConnectAttempts = 0

        // Try to connect using printer.csv configuration
        if (connectUsingCsvConfiguration()) {
            return
        }

        // If CSV not available or no active printer, show error
        updateStatus("ERROR: No printer configured. Please upload printer.csv with active printer.")
        _connectionState.value = ConnectionState.ERROR
        Log.e(TAG, "Cannot connect: printer.csv not found or no active printer configured")
    }

    /**
     * Try to connect using MAC address from printer.csv file
     * This is the ONLY connection method
     */
    private fun connectUsingCsvConfiguration(): Boolean {
        if (!printerCsvParser.isPrinterCsvAvailable()) {
            Log.e(TAG, "Printer CSV file not found")
            updateStatus("ERROR: printer.csv not uploaded")
            return false
        }

        val macAddress = printerCsvParser.getActivePrinterMacAddress()
        if (macAddress == null) {
            Log.e(TAG, "No active printer found in CSV or invalid MAC address")
            updateStatus("ERROR: No active printer in printer.csv (Activate must be 1)")
            return false
        }

        Log.d(TAG, "Using MAC address from printer.csv: $macAddress")
        updateStatus("Connecting to configured printer: $macAddress")

        // Get device by MAC address and connect
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
                connectToDevice(device)
                true
            } else {
                Log.e(TAG, "Could not get device for MAC address: $macAddress")
                updateStatus("ERROR: Invalid MAC address in printer.csv: $macAddress")
                false
            }
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Invalid MAC address format: $macAddress", e)
            updateStatus("ERROR: Invalid MAC address format in printer.csv: $macAddress")
            false
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception: Missing Bluetooth permissions", e)
            updateStatus("ERROR: Missing Bluetooth permissions")
            false
        }
    }

    /**
     * Attempt to connect to a previously paired Woosim device or scan for new ones
     */
    private fun attemptAutoConnect() {
        if (!hasBluetoothPermissions()) {
            updateStatus("Missing Bluetooth permissions")
            _connectionState.value = ConnectionState.ERROR
            return
        }

        try {
            autoConnectAttempts++
            Log.d(TAG, "Auto-connect attempt $autoConnectAttempts of $AUTO_CONNECT_RETRY_COUNT")

            if (autoConnectAttempts > AUTO_CONNECT_RETRY_COUNT) {
                updateStatus("Auto-connect failed after $AUTO_CONNECT_RETRY_COUNT attempts")
                _connectionState.value = ConnectionState.ERROR
                return
            }

            // Try to find paired Woosim devices
            val pairedDevices = bluetoothAdapter?.bondedDevices
            val woosimDevice = pairedDevices?.find { device ->
                device.name?.contains(WOOSIM_DEVICE_PREFIX, ignoreCase = true) == true
            }

            if (woosimDevice != null) {
                Log.d(TAG, "Found paired Woosim device: ${woosimDevice.name} (${woosimDevice.address})")
                connectToDevice(woosimDevice)
            } else {
                Log.d(TAG, "No paired Woosim devices found, starting scan")
                startScan()
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception during auto-connect", e)
            updateStatus("Missing Bluetooth permissions")
            _connectionState.value = ConnectionState.ERROR
        }
    }

    /**
     * Start scanning for Bluetooth devices
     */
    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_SCAN])
    fun startScan() {
        if (!isBluetoothEnabled()) {
            updateStatus("Bluetooth is not enabled")
            return
        }

        if (!hasBluetoothPermissions()) {
            updateStatus("Missing Bluetooth permissions")
            return
        }

        if (isScanning) {
            Log.d(TAG, "Already scanning")
            return
        }

        try {
            isScanning = true
            updateStatus("Scanning for devices...")

            bluetoothAdapter?.startDiscovery()

            // Stop scan after duration
            handler.postDelayed({
                try {
                    bluetoothAdapter?.cancelDiscovery()
                    isScanning = false
                    updateStatus("Scan completed")
                } catch (e: SecurityException) {
                    Log.e(TAG, "Error stopping discovery", e)
                }
            }, SCAN_DURATION)

        } catch (e: SecurityException) {
            Log.e(TAG, "Missing permissions for device scan", e)
            updateStatus("Missing Bluetooth permissions")
            isScanning = false
        }
    }

    /**
     * Connect to a specific Bluetooth device
     */
    private fun connectToDevice(device: BluetoothDevice) {
        if (_connectionState.value == ConnectionState.CONNECTING ||
            _connectionState.value == ConnectionState.CONNECTED
        ) {
            Log.d(TAG, "Already connecting or connected")
            return
        }

        _connectionState.value = ConnectionState.CONNECTING
        updateStatus("Connecting to ${device.name ?: device.address}...")

        printService?.connect(device)
    }

    /**
     * Handle connection state changes from BluetoothPrintService
     */
    private fun handleConnectionStateChange(
        state: Int,
        device: BluetoothDevice?,
        message: String?
    ) {
        when (state) {
            BluetoothPrintService.STATE_CONNECTED -> {
                _connectionState.value = ConnectionState.CONNECTED
                _connectedDevice.value = device

                // Get printer info from CSV for better display name
                val printerConfig = printerCsvParser.getActivePrinterConfig()
                val displayName = when {
                    printerConfig?.printerModel != null -> printerConfig.printerModel
                    else -> {
                        try {
                            device?.name ?: device?.address ?: "Woosim Printer"
                        } catch (e: SecurityException) {
                            device?.address ?: "Woosim Printer"
                        }
                    }
                }

                // Get MAC address - prioritize device object, fall back to CSV
                val macAddress = device?.address ?: printerConfig?.bluetoothMacAddress ?: "Unknown"

                updateStatus("x$displayName\nMAC Address: $macAddress")
                Log.d(TAG, "Successfully connected to $macAddress ($displayName)")
            }

            BluetoothPrintService.STATE_CONNECTING -> {
                _connectionState.value = ConnectionState.CONNECTING
                updateStatus("Connecting to device...")
            }

            BluetoothPrintService.STATE_NONE -> {
                val wasConnecting = _connectionState.value == ConnectionState.CONNECTING
                _connectionState.value = ConnectionState.DISCONNECTED
                _connectedDevice.value = null

                if (wasConnecting) {
                    // Connection failed while trying to connect
                    updateStatus("Connection failed: Printer not responding. Check if printer is ON and paired in Bluetooth settings.")
                    Log.e(TAG, "Connection failed - printer may not be paired or is not responding")
                } else {
                    updateStatus(message ?: "Disconnected")
                }

                // Retry on disconnection during auto-connect
                if (autoConnectAttempts > 0 && autoConnectAttempts < AUTO_CONNECT_RETRY_COUNT) {
                    Log.d(TAG, "Connection lost, attempting retry...")
                    handler.postDelayed({
                        attemptAutoConnect()
                    }, AUTO_CONNECT_RETRY_DELAY)
                }
            }

            else -> {
                _connectionState.value = ConnectionState.ERROR
                updateStatus("Connection error: ${message ?: "Unknown error"}. Ensure printer is paired in Bluetooth settings.")

                // Retry on error as well
                if (autoConnectAttempts < AUTO_CONNECT_RETRY_COUNT) {
                    Log.d(TAG, "Connection error, attempting retry...")
                    handler.postDelayed({
                        attemptAutoConnect()
                    }, AUTO_CONNECT_RETRY_DELAY)
                }
            }
        }
    }

    /**
     * Ensure correct device is connected based on CSV configuration
     */
    fun ensureCorrectDeviceConnected() {
        val currentDevice = _connectedDevice.value
        val configuredMac = getConfiguredPrinterMacAddress()

        if (configuredMac == null) {
            Log.w(TAG, "No printer configured")
            return
        }

        if (currentDevice?.address == configuredMac) {
            Log.d(TAG, "Already connected to correct printer: $configuredMac")
            return
        }

        Log.d(TAG, "Current device doesn't match configuration, reconnecting")
        disconnect()

        // Wait a moment for disconnection to complete, then connect to configured printer
        handler.postDelayed({
            connectToDeviceByMacAddress(configuredMac)
        }, 1000)
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

    /**
     * Check if printer CSV configuration is available and valid
     */
    fun isPrinterConfigurationValid(): Boolean {
        return printerCsvParser.getActivePrinterMacAddress() != null
    }

    /**
     * Get the configured printer MAC address from CSV only
     */
    fun getConfiguredPrinterMacAddress(): String? {
        val csvMac = printerCsvParser.getActivePrinterMacAddress()
        if (csvMac != null) {
            Log.d(TAG, "Using MAC from CSV: $csvMac")
            return csvMac
        }

        Log.e(TAG, "No printer configured in CSV")
        return null
    }

    /**
     * Cleanup resources
     */
    fun cleanup() {
        if (isScanning) {
            try {
                bluetoothAdapter?.cancelDiscovery()
            } catch (e: SecurityException) {
                Log.w(TAG, "Security exception during cleanup", e)
            }
        }

        printService?.disconnect()
        handler.removeCallbacksAndMessages(null)

        _connectionState.value = ConnectionState.DISCONNECTED
        _connectedDevice.value = null
        updateStatus("Cleanup completed")
    }
}