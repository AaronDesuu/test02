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
import com.example.meterkenshin.bluetooth.BluetoothPrintService
import com.example.meterkenshin.data.parser.PrinterCsvParser
import kotlinx.coroutines.*

/**
 * Bluetooth Manager for handling Woosim printer connectivity
 * Based on the original implementation from project01
 */
class BluetoothManager(private val context: Context) {

    companion object {
        private const val TAG = "BluetoothManager"
        private const val WOOSIM_DEVICE_PREFIX = "F5" // Woosim devices contain "F5" in their name
        private const val SCAN_DURATION = 10000L // 10 seconds scan duration
        private const val AUTO_CONNECT_RETRY_COUNT = 3
        private const val AUTO_CONNECT_RETRY_DELAY = 2000L // 2 seconds
    }

    private val bluetoothManager: BluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private var printService: BluetoothPrintService? = null
    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val printerCsvParser = PrinterCsvParser(context)

    // LiveData for observing connection state
    private val _isBluetoothEnabled = MutableLiveData<Boolean>()
    val isBluetoothEnabled: LiveData<Boolean> = _isBluetoothEnabled

    private val _connectionState = MutableLiveData<ConnectionState>()
    val connectionState: LiveData<ConnectionState> = _connectionState

    private val _connectedDevice = MutableLiveData<BluetoothDevice?>()
    val connectedDevice: LiveData<BluetoothDevice?> = _connectedDevice

    private val _statusMessage = MutableLiveData<String>()
    val statusMessage: LiveData<String> = _statusMessage

    // Track discovered devices
    private val discoveredDevices = mutableListOf<BluetoothDevice>()
    private var isScanning = false
    private var autoConnectAttempts = 0

    enum class ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        SCANNING,
        ERROR
    }

    init {
        updateBluetoothState()
    }

    /**
     * Initialize the Bluetooth manager and start auto-connection
     */
    fun initialize() {
        Log.d(TAG, "Initializing Bluetooth Manager")
        updateBluetoothState()

        if (isBluetoothSupported() && isBluetoothEnabled()) {
            initializePrintService()
            startAutoConnect()
        } else {
            updateStatus("Bluetooth not available or not enabled")
            _connectionState.value = ConnectionState.ERROR
        }
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
        return bluetoothAdapter?.isEnabled == true
    }

    /**
     * Initialize the Bluetooth Print Service
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

        // First, try to connect using MAC address from printer.csv
        if (connectUsingCsvConfiguration()) {
            return
        }

        // If CSV method fails, fall back to scanning
        updateStatus("Printer CSV not available, falling back to scanning")
        attemptAutoConnect()
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
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun connectToDeviceByMacAddress(macAddress: String): Boolean {
        if (!hasBluetoothPermissions()) {
            updateStatus("Missing Bluetooth permissions")
            return false
        }

        try {
            val device = bluetoothAdapter?.getRemoteDevice(macAddress)
            if (device != null) {
                Log.d(TAG, "Connecting to device: ${device.address}")
                connectToDevice(device)
                return true
            } else {
                Log.e(TAG, "Could not get device for MAC address: $macAddress")
                updateStatus("Invalid MAC address: $macAddress")
                return false
            }
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Invalid MAC address format: $macAddress", e)
            updateStatus("Invalid MAC address format: $macAddress")
            return false
        }
    }

    /**
     * Attempt to connect to a previously paired Woosim device or scan for new ones
     * This is now used as a fallback method when CSV connection fails
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
    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_CONNECT])
    private fun connectToPairedWoosimDevice(): Boolean {
        if (!hasBluetoothPermissions()) {
            updateStatus("Missing Bluetooth permissions")
            return false
        }

        val pairedDevices = bluetoothAdapter?.bondedDevices
        val woosimDevice = pairedDevices?.find { device ->
            device.name?.contains(WOOSIM_DEVICE_PREFIX, ignoreCase = true) == true
        }

        if (woosimDevice != null) {
            Log.d(TAG, "Found paired Woosim device: ${woosimDevice.name} (${woosimDevice.address})")
            connectToDevice(woosimDevice)
            return true
        }

        Log.d(TAG, "No paired Woosim device found")
        return false
    }

    /**
     * Start scanning for Bluetooth devices
     */
    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_SCAN])
    fun startScan() {
        if (!hasBluetoothPermissions()) {
            updateStatus("Missing Bluetooth permissions")
            return
        }

        if (isScanning) {
            Log.d(TAG, "Already scanning")
            return
        }

        Log.d(TAG, "Starting Bluetooth scan")
        _connectionState.value = ConnectionState.SCANNING
        updateStatus("Scanning for Woosim devices...")
        isScanning = true
        discoveredDevices.clear()

        // Start LE scan for Woosim devices
        bluetoothAdapter?.startLeScan { device, rssi, scanRecord ->
            onDeviceDiscovered(device, rssi)
        }

        // Stop scan after duration
        handler.postDelayed({
            stopScan()
            onScanComplete()
        }, SCAN_DURATION)
    }

    /**
     * Stop Bluetooth scanning
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun stopScan() {
        if (!isScanning) return

        Log.d(TAG, "Stopping Bluetooth scan")
        isScanning = false
        bluetoothAdapter?.stopLeScan { _, _, _ -> }
    }

    /**
     * Handle discovered Bluetooth device
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun onDeviceDiscovered(device: BluetoothDevice, rssi: Int) {
        if (!hasBluetoothPermissions()) return

        val deviceName = device.name
        if (deviceName?.contains(WOOSIM_DEVICE_PREFIX, ignoreCase = true) == true) {
            Log.d(TAG, "Discovered Woosim device: $deviceName (${device.address}) RSSI: $rssi")

            if (!discoveredDevices.any { it.address == device.address }) {
                discoveredDevices.add(device)
                updateStatus("Found Woosim device: $deviceName")
            }
        }
    }

    /**
     * Handle scan completion
     */
    private fun onScanComplete() {
        Log.d(TAG, "Scan complete. Found ${discoveredDevices.size} Woosim devices")

        if (discoveredDevices.isNotEmpty()) {
            // Connect to the first discovered device
            val device = discoveredDevices.first()
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
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun connectToDevice(device: BluetoothDevice) {
        if (!hasBluetoothPermissions()) {
            updateStatus("Missing Bluetooth permissions")
            return
        }

        Log.d(TAG, "Connecting to device: ${device.name} (${device.address})")
        _connectionState.value = ConnectionState.CONNECTING
        updateStatus("Connecting to ${device.name ?: device.address}...")

        printService?.connect(device)
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
     * Handle connection state changes from BluetoothPrintService
     */
    private fun handleConnectionStateChange(
        state: Int,
        device: BluetoothDevice?,
        message: String?
    ) {
        when (state) {
            BluetoothPrintService.STATE_CONNECTING -> {
                _connectionState.value = ConnectionState.CONNECTING
                updateStatus("Connecting...")
            }
            BluetoothPrintService.STATE_CONNECTED -> {
                _connectionState.value = ConnectionState.CONNECTED
                _connectedDevice.value = device
                autoConnectAttempts = 0 // Reset retry count on successful connection
                updateStatus("Connected to ${device?.name ?: device?.address ?: "printer"}")
                Log.d(TAG, "Successfully connected to printer: ${device?.address}")
            }
            BluetoothPrintService.STATE_NONE -> {
                _connectionState.value = ConnectionState.DISCONNECTED
                _connectedDevice.value = null
                updateStatus("Disconnected")

                // If we were trying to auto-connect and CSV method was used,
                // try fallback methods
                if (autoConnectAttempts == 0) {
                    // This was likely a CSV-based connection that failed
                    Log.d(TAG, "CSV-based connection failed, trying fallback methods")
                    handler.postDelayed({
                        attemptAutoConnect()
                    }, AUTO_CONNECT_RETRY_DELAY)
                } else if (autoConnectAttempts < AUTO_CONNECT_RETRY_COUNT) {
                    // Continue with retry attempts
                    handler.postDelayed({
                        attemptAutoConnect()
                    }, AUTO_CONNECT_RETRY_DELAY)
                }
            }
        }
    }

    /**
     * Get the current print service for printing operations
     */
    fun getPrintService(): BluetoothPrintService? = printService

    /**
     * Check if device is currently connected
     */
    fun isConnected(): Boolean {
        return printService?.getState() == BluetoothPrintService.STATE_CONNECTED
    }

    /**
     * Write data to connected device
     */
    fun write(data: ByteArray): Boolean {
        return if (isConnected()) {
            printService?.write(data)
            true
        } else {
            Log.w(TAG, "Cannot write: device not connected")
            false
        }
    }

    /**
     * Check if required Bluetooth permissions are granted
     */
    private fun hasBluetoothPermissions(): Boolean {
        val permissions = mutableListOf<String>()

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            permissions.addAll(listOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            ))
        } else {
            permissions.addAll(listOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION
            ))
        }

        return permissions.all { permission ->
            ActivityCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Update Bluetooth state
     */
    private fun updateBluetoothState() {
        _isBluetoothEnabled.value = isBluetoothEnabled()
    }

    /**
     * Update status message
     */
    private fun updateStatus(message: String) {
        Log.d(TAG, message)
        _statusMessage.value = message
    }

    /**
     * Clean up resources
     */
    fun cleanup() {
        Log.d(TAG, "Cleaning up Bluetooth Manager")
        scope.cancel()

        if (hasBluetoothPermissions() && isScanning) {
            stopScan()
        }

        printService?.stop()
        printService = null
    }

    /**
     * Refresh printer configuration from CSV and reconnect if needed
     */
    fun refreshPrinterConfiguration() {
        Log.d(TAG, "Refreshing printer configuration from CSV")

        if (isConnected()) {
            Log.d(TAG, "Already connected, checking if current device matches CSV configuration")
            val csvMacAddress = printerCsvParser.getActivePrinterMacAddress()
            val currentDeviceAddress = _connectedDevice.value?.address

            if (csvMacAddress != null && csvMacAddress.equals(currentDeviceAddress, ignoreCase = true)) {
                updateStatus("Connected to correct printer from CSV configuration")
                return
            } else {
                Log.d(TAG, "Current device doesn't match CSV, disconnecting and reconnecting")
                disconnect()
            }
        }

        // Wait a moment for disconnection to complete, then start auto-connect
        handler.postDelayed({
            startAutoConnect()
        }, 1000)
    }

    /**
     * Get printer configuration information for display
     */
    fun getPrinterConfigInfo(): String {
        return if (printerCsvParser.isPrinterCsvAvailable()) {
            val config = printerCsvParser.parsePrinterConfig()
            if (config != null) {
                "Printer CSV: Active=${config.isActive}, MAC=${config.bluetoothMacAddress}"
            } else {
                "Printer CSV: Invalid configuration"
            }
        } else {
            "Printer CSV: Not uploaded"
        }
    }

    /**
     * Check if printer CSV configuration is available and valid
     */
    fun isPrinterConfigurationValid(): Boolean {
        return printerCsvParser.getActivePrinterMacAddress() != null
    }

    /**
     * Get the configured printer MAC address from CSV
     */
    fun getConfiguredPrinterMacAddress(): String? {
        return printerCsvParser.getActivePrinterMacAddress()
    }
}