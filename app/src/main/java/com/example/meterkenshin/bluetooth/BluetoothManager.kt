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
 * Enhanced with direct MAC address connection capability
 */
class BluetoothManager(private val context: Context) {

    companion object {
        private const val TAG = "BluetoothManager"
        private const val WOOSIM_DEVICE_PREFIX = "F5" // Woosim devices contain "F5" in their name
        private const val SCAN_DURATION = 10000L // 10 seconds scan duration
        private const val AUTO_CONNECT_RETRY_COUNT = 3
        private const val AUTO_CONNECT_RETRY_DELAY = 2000L // 2 seconds
        private const val WOOSIM_MAC_ADDRESS = "1C:B8:57:50:01:D9" // Hardcoded MAC address
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
    private val printerCsvParser = PrinterCsvParser(context)
    private val handler = Handler(Looper.getMainLooper())

    // LiveData for observing states
    private val _connectionState = MutableLiveData<ConnectionState>()
    val connectionState: LiveData<ConnectionState> = _connectionState

    private val _connectedDevice = MutableLiveData<BluetoothDevice?>()
    val connectedDevice: LiveData<BluetoothDevice?> = _connectedDevice

    private val _statusMessage = MutableLiveData<String>()
    val statusMessage: LiveData<String> = _statusMessage

    private val _isBluetoothEnabled = MutableLiveData<Boolean>()
    val isBluetoothEnabled: LiveData<Boolean> = _isBluetoothEnabled

    // Scan-related variables
    private var isScanning = false
    private var autoConnectAttempts = 0
    private val discoveredDevices = mutableSetOf<BluetoothDevice>()

    init {
        updateBluetoothEnabledState()
        initializePrintService()

        // Initial status
        _connectionState.value = ConnectionState.DISCONNECTED
        updateStatus("Bluetooth Manager initialized")
    }

    private fun updateBluetoothEnabledState() {
        _isBluetoothEnabled.value = bluetoothAdapter?.isEnabled == true
    }

    private fun updateStatus(message: String) {
        Log.d(TAG, message)
        _statusMessage.value = message
    }

    /**
     * Check if Bluetooth is supported on this device
     */
    fun isBluetoothSupported(): Boolean {
        return bluetoothAdapter != null
    }

    /**
     * Check if Bluetooth is enabled
     */
    fun isBluetoothEnabled(): Boolean {
        updateBluetoothEnabledState()
        return bluetoothAdapter?.isEnabled == true
    }

    /**
     * Check if device is currently connected
     */
    fun isConnected(): Boolean {
        return _connectionState.value == ConnectionState.CONNECTED
    }

    /**
     * Check if required Bluetooth permissions are granted
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
     * Connect to the Woosim printer using the hardcoded MAC address
     */
    suspend fun connectToSpecificDevice(macAddress: String = WOOSIM_MAC_ADDRESS) {
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

        Log.d(TAG, "Attempting to connect to device: $macAddress")
        updateStatus("Connecting to Woosim printer...")

        try {
            val device = bluetoothAdapter?.getRemoteDevice(macAddress)
            if (device != null) {
                connectToDevice(device)
            } else {
                updateStatus("Could not get device for MAC address: $macAddress")
                _connectionState.value = ConnectionState.ERROR
            }
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Invalid MAC address format: $macAddress", e)
            updateStatus("Invalid MAC address format: $macAddress")
            _connectionState.value = ConnectionState.ERROR
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception: Missing Bluetooth permissions", e)
            updateStatus("Missing Bluetooth permissions")
            _connectionState.value = ConnectionState.ERROR
        }
    }

    /**
     * Start automatic connection process to Woosim printer
     * First tries to connect using MAC address from printer.csv
     * Falls back to scanning if CSV not available or connection fails
     */
    fun startAutoConnect() {
        if (!isBluetoothEnabled()) {
            updateStatus("Bluetooth is not enabled")
            _connectionState.value = ConnectionState.ERROR
            return
        }

        Log.d(TAG, "Starting auto-connect process")
        autoConnectAttempts = 0

        // First, try to connect using hardcoded MAC address
        if (connectDirectToWoosim()) {
            return
        }

        // If direct connection fails, try CSV configuration
        if (connectUsingCsvConfiguration()) {
            return
        }

        // If CSV method fails, fall back to scanning
        updateStatus("Falling back to device scanning")
        attemptAutoConnect()
    }

    /**
     * Try to connect directly using the hardcoded Woosim MAC address
     */
    private fun connectDirectToWoosim(): Boolean {
        if (!hasBluetoothPermissions()) {
            updateStatus("Missing Bluetooth permissions")
            return false
        }

        Log.d(TAG, "Attempting direct connection to Woosim: $WOOSIM_MAC_ADDRESS")
        updateStatus("Connecting to Woosim printer: $WOOSIM_MAC_ADDRESS")

        return try {
            val device = bluetoothAdapter?.getRemoteDevice(WOOSIM_MAC_ADDRESS)
            if (device != null) {
                connectToDevice(device)
                true
            } else {
                Log.e(TAG, "Could not get device for MAC address: $WOOSIM_MAC_ADDRESS")
                updateStatus("Failed to get Woosim device")
                false
            }
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Invalid MAC address format: $WOOSIM_MAC_ADDRESS", e)
            updateStatus("Invalid Woosim MAC address")
            false
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception: Missing Bluetooth permissions", e)
            updateStatus("Missing Bluetooth permissions")
            false
        }
    }

    /**
     * Try to connect using MAC address from printer.csv file
     */
    private fun connectUsingCsvConfiguration(): Boolean {
        if (!printerCsvParser.isPrinterCsvAvailable()) {
            Log.d(TAG, "Printer CSV file not found")
            updateStatus("Printer CSV file not uploaded")
            return false
        }

        val macAddress = printerCsvParser.getActivePrinterMacAddress()
        if (macAddress == null) {
            Log.d(TAG, "No active printer found in CSV or invalid MAC address")
            updateStatus("No active printer configured in CSV")
            return false
        }

        Log.d(TAG, "Found printer MAC address in CSV: $macAddress")
        updateStatus("Connecting to configured printer: $macAddress")

        // Get device by MAC address and connect
        return connectToDeviceByMacAddress(macAddress)
    }

    /**
     * Connect to a device using its MAC address
     */
    private fun connectToDeviceByMacAddress(macAddress: String): Boolean {
        if (!hasBluetoothPermissions()) {
            updateStatus("Missing Bluetooth permissions")
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
                updateStatus("Invalid MAC address: $macAddress")
                false
            }
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Invalid MAC address format: $macAddress", e)
            updateStatus("Invalid MAC address format: $macAddress")
            false
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception: Missing Bluetooth permissions", e)
            updateStatus("Missing Bluetooth permissions")
            false
        }
    }

    /**
     * Attempt to connect to a previously paired Woosim device or scan for new ones
     * This is now used as a fallback method when direct and CSV connection fails
     */
    private fun attemptAutoConnect() {
        if (autoConnectAttempts >= AUTO_CONNECT_RETRY_COUNT) {
            updateStatus("Auto-connect failed after $AUTO_CONNECT_RETRY_COUNT attempts")
            _connectionState.value = ConnectionState.ERROR
            return
        }

        autoConnectAttempts++
        updateStatus("Scanning attempt $autoConnectAttempts of $AUTO_CONNECT_RETRY_COUNT")

        // Try to connect to paired devices first
        if (connectToPairedWoosimDevice()) {
            return
        }

        // If no paired device found, scan for new devices
        startScan()
    }

    /**
     * Try to connect to an already paired Woosim device
     */
    private fun connectToPairedWoosimDevice(): Boolean {
        if (!hasBluetoothPermissions()) {
            updateStatus("Missing Bluetooth permissions")
            return false
        }

        return try {
            val pairedDevices = bluetoothAdapter?.bondedDevices
            val woosimDevice = pairedDevices?.find { device ->
                try {
                    device.name?.contains(WOOSIM_DEVICE_PREFIX, ignoreCase = true) == true ||
                            device.address == WOOSIM_MAC_ADDRESS
                } catch (e: SecurityException) {
                    Log.w(TAG, "Cannot access device name due to permissions", e)
                    device.address == WOOSIM_MAC_ADDRESS
                }
            }

            if (woosimDevice != null) {
                try {
                    Log.d(TAG, "Found paired Woosim device: ${woosimDevice.name} (${woosimDevice.address})")
                } catch (e: SecurityException) {
                    Log.d(TAG, "Found paired Woosim device: (${woosimDevice.address})")
                }
                connectToDevice(woosimDevice)
                true
            } else {
                Log.d(TAG, "No paired Woosim device found")
                false
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception while accessing paired devices", e)
            updateStatus("Missing Bluetooth permissions")
            false
        }
    }

    /**
     * Start scanning for Bluetooth devices
     */
    fun startScan() {
        if (!hasBluetoothPermissions()) {
            updateStatus("Missing Bluetooth permissions")
            return
        }

        if (isScanning) {
            Log.d(TAG, "Already scanning")
            return
        }

        discoveredDevices.clear()
        isScanning = true
        updateStatus("Scanning for Woosim devices...")

        try {
            // Start discovery
            bluetoothAdapter?.startDiscovery()

            // Stop scanning after duration
            handler.postDelayed({
                stopScan()
                handleScanResults()
            }, SCAN_DURATION)
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception during scan start", e)
            updateStatus("Missing Bluetooth scan permissions")
            isScanning = false
        }
    }

    /**
     * Stop scanning for devices
     */
    private fun stopScan() {
        if (isScanning) {
            try {
                bluetoothAdapter?.cancelDiscovery()
                isScanning = false
                Log.d(TAG, "Bluetooth scanning stopped")
            } catch (e: SecurityException) {
                Log.w(TAG, "Security exception during scan stop", e)
                isScanning = false
            }
        }
    }

    /**
     * Handle scan results
     */
    private fun handleScanResults() {
        val woosimDevices = discoveredDevices.filter { device ->
            try {
                device.name?.contains(WOOSIM_DEVICE_PREFIX, ignoreCase = true) == true ||
                        device.address == WOOSIM_MAC_ADDRESS
            } catch (e: SecurityException) {
                Log.w(TAG, "Cannot access device name during filtering", e)
                device.address == WOOSIM_MAC_ADDRESS
            }
        }

        Log.d(TAG, "Found ${woosimDevices.size} Woosim devices")

        if (woosimDevices.isNotEmpty()) {
            // Connect to the first discovered device
            val device = woosimDevices.first()
            connectToDevice(device)
        } else {
            // Retry auto-connect after delay
            updateStatus("No Woosim devices found. Retrying...")
            handler.postDelayed({
                attemptAutoConnect()
            }, AUTO_CONNECT_RETRY_DELAY)
        }
    }

    /**
     * Connect to a specific Bluetooth device
     */
    fun connectToDevice(device: BluetoothDevice) {
        if (!hasBluetoothPermissions()) {
            updateStatus("Missing Bluetooth permissions")
            return
        }

        try {
            val deviceName = try {
                device.name ?: "Unknown"
            } catch (e: SecurityException) {
                "Unknown"
            }

            Log.d(TAG, "Connecting to device: $deviceName (${device.address})")
            _connectionState.value = ConnectionState.CONNECTING
            updateStatus("Connecting to $deviceName...")

            printService?.connect(device)
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception during device connection", e)
            updateStatus("Missing Bluetooth permissions")
            _connectionState.value = ConnectionState.ERROR
        }
    }

    /**
     * Disconnect from current device
     */
    fun disconnect() {
        Log.d(TAG, "Disconnecting from device")
        printService?.disconnect()
        _connectedDevice.value = null
        _connectionState.value = ConnectionState.DISCONNECTED
        updateStatus("Disconnected")
    }

    /**
     * Write data to connected printer
     */
    fun write(data: ByteArray): Boolean {
        return try {
            if (printService?.getState() == BluetoothPrintService.STATE_CONNECTED) {
                printService?.write(data)
                true
            } else {
                Log.w(TAG, "Cannot write: printer not connected")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error writing data to printer", e)
            false
        }
    }

    /**
     * Get the print service instance
     */
    fun getPrintService(): BluetoothPrintService? {
        return printService
    }

    /**
     * Handle connection state changes from BluetoothPrintService
     */
    private fun handleConnectionStateChange(
        state: Int,
        device: BluetoothDevice?,
        message: String?
    ) {
        // Ensure UI updates happen on main thread
        handler.post {
            when (state) {
                BluetoothPrintService.STATE_CONNECTING -> {
                    _connectionState.value = ConnectionState.CONNECTING
                    updateStatus("Connecting...")
                }
                BluetoothPrintService.STATE_CONNECTED -> {
                    _connectionState.value = ConnectionState.CONNECTED
                    _connectedDevice.value = device
                    autoConnectAttempts = 0 // Reset retry count on successful connection

                    // Safely get device info
                    val deviceInfo = try {
                        device?.name ?: device?.address ?: "Unknown"
                    } catch (e: SecurityException) {
                        device?.address ?: "Unknown Device"
                    }

                    updateStatus("Connected to $deviceInfo")
                    Log.d(TAG, "Successfully connected to Woosim printer")
                }
                BluetoothPrintService.STATE_NONE -> {
                    _connectionState.value = ConnectionState.DISCONNECTED
                    _connectedDevice.value = null
                    updateStatus("Disconnected")

                    // If we were trying to auto-connect, retry with fallback methods
                    if (autoConnectAttempts < AUTO_CONNECT_RETRY_COUNT) {
                        Log.d(TAG, "Connection failed, attempting retry...")
                        handler.postDelayed({
                            attemptAutoConnect()
                        }, AUTO_CONNECT_RETRY_DELAY)
                    } else {
                        Log.d(TAG, "Max retry attempts reached")
                        updateStatus("Failed to connect after $AUTO_CONNECT_RETRY_COUNT attempts")
                    }
                }
                else -> {
                    _connectionState.value = ConnectionState.ERROR
                    updateStatus(message ?: "Connection error")

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
    }

    /**
     * Ensure correct device is connected if CSV configuration is available
     */
    fun ensureCorrectDeviceConnected() {
        val currentDevice = _connectedDevice.value

        // First priority: hardcoded MAC address
        if (currentDevice?.address == WOOSIM_MAC_ADDRESS) {
            Log.d(TAG, "Already connected to correct Woosim device")
            return
        }

        // Second priority: CSV configuration
        val configuredMac = printerCsvParser.getActivePrinterMacAddress()
        if (configuredMac != null && currentDevice?.address == configuredMac) {
            if (configuredMac == WOOSIM_MAC_ADDRESS) {
                Log.d(TAG, "Connected to correct printer from CSV configuration")
                return
            } else {
                Log.d(TAG, "Current device doesn't match hardcoded MAC, reconnecting")
                disconnect()
            }
        }

        // Wait a moment for disconnection to complete, then connect to Woosim
        handler.postDelayed({
            connectDirectToWoosim()
        }, 1000)
    }

    /**
     * Get printer configuration information for display
     */
    fun getPrinterConfigInfo(): String {
        val hardcodedInfo = "Hardcoded MAC: $WOOSIM_MAC_ADDRESS"

        return if (printerCsvParser.isPrinterCsvAvailable()) {
            val config = printerCsvParser.parsePrinterConfig()
            if (config != null) {
                "$hardcodedInfo\nCSV: Active=${config.isActive}, MAC=${config.bluetoothMacAddress}"
            } else {
                "$hardcodedInfo\nCSV: Invalid configuration"
            }
        } else {
            "$hardcodedInfo\nCSV: Not uploaded"
        }
    }

    /**
     * Check if printer CSV configuration is available and valid
     */
    fun isPrinterConfigurationValid(): Boolean {
        return printerCsvParser.getActivePrinterMacAddress() != null || WOOSIM_MAC_ADDRESS.isNotEmpty()
    }

    /**
     * Get the configured printer MAC address (priority: hardcoded, then CSV)
     */
    fun getConfiguredPrinterMacAddress(): String? {
        return if (WOOSIM_MAC_ADDRESS.isNotEmpty()) {
            WOOSIM_MAC_ADDRESS
        } else {
            printerCsvParser.getActivePrinterMacAddress()
        }
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