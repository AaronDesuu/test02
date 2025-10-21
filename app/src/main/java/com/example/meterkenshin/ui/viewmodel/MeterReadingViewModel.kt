package com.example.meterkenshin.ui.viewmodel

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.meterkenshin.bluetooth.BluetoothLeService
import com.example.meterkenshin.bluetooth.DeviceList
import com.example.meterkenshin.dlms.DLMS
import com.example.meterkenshin.model.Meter
import com.example.meterkenshin.model.MeterStatus
import com.example.meterkenshin.model.MeterType
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MeterReadingViewModel : ViewModel() {
    companion object {
        private const val TAG = "MeterReadingViewModel"
        private const val APP_FILES_FOLDER = "app_files"
        private const val SCAN_PERIOD: Long = 2000 // 2 seconds active scan
        private const val SCAN_INTERVAL: Long = 4000 // 4 seconds between scans
        private const val SCAN_COOLDOWN: Long = 500 // 500ms cooldown between stop/start
    }

    // BluetoothLeService constants
    object BluetoothLeServiceConstants {
        const val ACTION_GATT_CONNECTED = "com.example.meterkenshin.bluetooth.ACTION_GATT_CONNECTED"
        const val ACTION_GATT_DISCONNECTED = "com.example.meterkenshin.bluetooth.ACTION_GATT_DISCONNECTED"
        const val ACTION_GATT_SERVICES_DISCOVERED = "com.example.meterkenshin.bluetooth.ACTION_GATT_SERVICES_DISCOVERED"
        const val ACTION_DATA_AVAILABLE = "com.example.meterkenshin.bluetooth.ACTION_DATA_AVAILABLE"
        const val ACTION_GATT_ERROR = "com.example.meterkenshin.bluetooth.ACTION_GATT_ERROR"
        const val EXTRA_DATA = "com.example.meterkenshin.bluetooth.EXTRA_DATA"
    }

    // UI State
    private val _uiState = MutableStateFlow(MeterReadingUiState())
    val uiState: StateFlow<MeterReadingUiState> = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)

    // ✅ NEW: BLE Scanning State
    private val _isScanning = MutableStateFlow(false)

    private val _discoveredDevices = MutableStateFlow<Map<String, Int>>(emptyMap()) // MAC -> RSSI

    private val _nearbyMeterCount = MutableStateFlow(0)
    val nearbyMeterCount: StateFlow<Int> = _nearbyMeterCount.asStateFlow()

    private val _sortConfig = MutableStateFlow(SortConfig())
    val sortConfig: StateFlow<SortConfig> = _sortConfig.asStateFlow()

    fun setSortConfig(field: SortField, order: SortOrder) {
        _sortConfig.value = SortConfig(field, order)
        applySorting()
    }

    // Context and BLE
    @SuppressLint("StaticFieldLeak")
    private var mContext: Context? = null
    private var mBluetoothAdapter: BluetoothAdapter? = null
    private var mScanning = false

    private var mStage = 0
    private var mStep = 0

    private var mConnected = 0
    private var mArrived = 0
    private var mServiceActive = false
    private var mData = ByteArray(0)
    @SuppressLint("StaticFieldLeak")
    private var mBluetoothLeService: BluetoothLeService? = null
    private var d: DLMS? = null
    private var mDeviceList: DeviceList? = null
    private var scanJob: Job? = null
    val discoveredDevices: StateFlow<Map<String, Int>> = _discoveredDevices.asStateFlow()
    private val _scannedInCurrentCycle = mutableSetOf<String>() // Track which devices already scanned this cycle
    private var printerViewModel: PrinterBluetoothViewModel? = null

    fun setPrinterViewModel(viewModel: PrinterBluetoothViewModel) {
        printerViewModel = viewModel
    }

    // Modified callback - only captures first RSSI per cycle
    @Suppress("DEPRECATION")
    private val mLeScanCallback = BluetoothAdapter.LeScanCallback { device, rssi, _ ->
        val knownMacAddresses = _uiState.value.allMeters
            .mapNotNull { it.bluetoothId?.uppercase() }
            .toSet()

        val deviceMac = device.address.uppercase()

        if (knownMacAddresses.contains(deviceMac) && !_scannedInCurrentCycle.contains(deviceMac)) {
            Log.i(TAG, "First discovery this cycle: $deviceMac, RSSI: $rssi dBm")

            _scannedInCurrentCycle.add(deviceMac)
            mDeviceList?.addDevice(device, rssi)

            val currentDevices = _discoveredDevices.value.toMutableMap()
            currentDevices[deviceMac] = rssi
            _discoveredDevices.value = currentDevices
            _nearbyMeterCount.value = currentDevices.size

            updateMeterNearbyStatus(deviceMac, rssi)
        }
    }

    // BroadcastReceiver for GATT events
    private val mGattUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothLeServiceConstants.ACTION_GATT_CONNECTED -> {
                    mConnected = 1
                    Log.i(TAG, "ACTION_GATT_CONNECTED")
                }
                BluetoothLeServiceConstants.ACTION_GATT_DISCONNECTED -> {
                    Log.i(TAG, "ACTION_GATT_DISCONNECTED")
                    mConnected = -1
                    if (mStage != 0) {
                        mStage = 0
                        mStep = 0
                    }
                    _connectionState.value = ConnectionState.DISCONNECTED
                }
                BluetoothLeServiceConstants.ACTION_GATT_SERVICES_DISCOVERED -> {
                    Log.i(TAG, "ACTION_GATT_SERVICES_DISCOVERED")
                    mConnected = 2
                    mArrived = 0
                    _connectionState.value = ConnectionState.CONNECTED
                }
                BluetoothLeServiceConstants.ACTION_GATT_ERROR -> {
                    Log.i(TAG, "ACTION_GATT_ERROR")
                    _connectionState.value = ConnectionState.FAILED
                }
                BluetoothLeServiceConstants.ACTION_DATA_AVAILABLE -> {
                    Log.i(TAG, "ACTION_DATA_AVAILABLE")
                    mData = intent.getByteArrayExtra(BluetoothLeServiceConstants.EXTRA_DATA) ?: ByteArray(0)
                    mArrived++
                }
            }
        }
    }

    // Service connection for BLE
    private val mServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(componentName: ComponentName, service: IBinder) {
            mBluetoothLeService = (service as BluetoothLeService.LocalBinder).service
            if (mBluetoothLeService?.initialize() != true) {
                Log.i(TAG, "Fail to initialize Bluetooth service.")
                mServiceActive = false
            } else {
                Log.i(TAG, "Success to initialize Bluetooth service.")
                mServiceActive = true
            }
        }

        override fun onServiceDisconnected(componentName: ComponentName) {
            mBluetoothLeService = null
            mServiceActive = false
            Log.i(TAG, "onServiceDisconnected")
        }
    }

    /**
     * Initialize the ViewModel with DLMS and Bluetooth
     */
    fun initialize(context: Context) {
        mContext = context

        // Initialize DLMS with Context
        d = DLMS(context)

        // Initialize device list
        mDeviceList = DeviceList()

        // Initialize Bluetooth adapter
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        mBluetoothAdapter = bluetoothManager?.adapter

        Log.i(TAG, "MeterReadingViewModel initialized")
    }

    /**
     * ✅ NEW: Start BLE scanning for nearby meters (filtered by CSV)
     */
    @RequiresPermission(allOf = [
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.ACCESS_FINE_LOCATION
    ])
    fun startBLEScanning() {
        if (mBluetoothAdapter == null) {
            updateErrorMessage("Bluetooth adapter not available")
            return
        }

        if (_uiState.value.allMeters.isEmpty()) {
            updateErrorMessage("Load meter.csv first before scanning")
            return
        }

        if (!mScanning) {
            viewModelScope.launch {
                try {
                    // Clear previous discoveries
                    mDeviceList?.Deactivate()
                    _discoveredDevices.value = emptyMap()
                    _nearbyMeterCount.value = 0

                    // Start scanning
                    @Suppress("DEPRECATION")
                    mBluetoothAdapter?.startLeScan(mLeScanCallback)
                    mScanning = true
                    _isScanning.value = true

                    Log.i(TAG, "BLE scan started - looking for ${_uiState.value.allMeters.size} known meters")

                    // Auto-stop after SCAN_PERIOD
                    delay(SCAN_PERIOD)
                    stopBLEScanning()

                } catch (e: Exception) {
                    Log.e(TAG, "Error starting BLE scan", e)
                    updateErrorMessage("Failed to start scanning: ${e.message}")
                    _isScanning.value = false
                }
            }
        }
    }

    /**
     * ✅ NEW: Stop BLE scanning
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun stopBLEScanning() {
        if (mScanning) {
            try {
                @Suppress("DEPRECATION")
                mBluetoothAdapter?.stopLeScan(mLeScanCallback)
                mScanning = false
                _isScanning.value = false

                Log.i(TAG, "BLE scan stopped - found ${_nearbyMeterCount.value} nearby meters")

            } catch (e: Exception) {
                Log.e(TAG, "Error stopping BLE scan", e)
            }
        }
    }

    /**
     * ✅ NEW: Update meter status to show it's nearby with signal strength
     */
    private fun updateMeterNearbyStatus(bluetoothMac: String, rssi: Int) {
        val currentMeters = _uiState.value.allMeters.toMutableList()
        val index = currentMeters.indexOfFirst {
            it.bluetoothId?.uppercase() == bluetoothMac.uppercase()
        }

        if (index != -1) {
            val meter = currentMeters[index]
            // Update meter with nearby status
            currentMeters[index] = meter.copy(
                status = MeterStatus.ACTIVE,
                // You can add RSSI to Meter model if needed
            )

            _uiState.value = _uiState.value.copy(
                allMeters = currentMeters,
                filteredMeters = filterMeters(currentMeters, _searchQuery.value)
            )

            Log.d(TAG, "Updated meter ${meter.serialNumber} as nearby (RSSI: $rssi)")
        }
    }

    /**
     * ✅ NEW: Get signal strength for a specific meter
     */
    fun getMeterSignalStrength(bluetoothId: String): Int? {
        return _discoveredDevices.value[bluetoothId.uppercase()]
    }

    /**
     * ✅ NEW: Check if meter is nearby (discovered in last scan)
     */
    fun isMeterNearby(bluetoothId: String?): Boolean {
        if (bluetoothId == null) return false
        return _discoveredDevices.value.containsKey(bluetoothId.uppercase())
    }

    /**
     * Start BLE operations
     */
    fun startBLEOperations(context: Context) {
        mContext = context

        // Register broadcast receiver
        val intentFilter = IntentFilter().apply {
            addAction(BluetoothLeServiceConstants.ACTION_GATT_CONNECTED)
            addAction(BluetoothLeServiceConstants.ACTION_GATT_DISCONNECTED)
            addAction(BluetoothLeServiceConstants.ACTION_GATT_SERVICES_DISCOVERED)
            addAction(BluetoothLeServiceConstants.ACTION_DATA_AVAILABLE)
            addAction(BluetoothLeServiceConstants.ACTION_GATT_ERROR)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(mGattUpdateReceiver, intentFilter, Context.RECEIVER_NOT_EXPORTED)
        }

        // Bind BLE service
        val gattServiceIntent = Intent(context, BluetoothLeService::class.java)
        context.bindService(gattServiceIntent, mServiceConnection, Context.BIND_AUTO_CREATE)

        Log.i(TAG, "BLE operations started")
    }

    /**
     * Stop BLE operations
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun stopBLEOperations(context: Context) {
        try {
            // Stop scanning if active
            stopBLEScanning()

            // Unregister receiver
            context.unregisterReceiver(mGattUpdateReceiver)

            // Unbind service
            context.unbindService(mServiceConnection)

            mServiceActive = false
            Log.i(TAG, "BLE operations stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping BLE operations", e)
        }
    }

    /**
     * Start periodic scanning with proper backoff to avoid "scanning too frequently" warning
     */
    @RequiresPermission(allOf = [
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.ACCESS_FINE_LOCATION
    ])
    fun startPeriodicScanning() {
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            while (isActive) {
                // Clear the "already scanned" set for new cycle
                _scannedInCurrentCycle.clear()

                // ✅ Pause printer status monitoring during scan
                printerViewModel?.stopStatusMonitoring()

                // Start scan
                startBLEScanningInternal()

                // Scan for SCAN_PERIOD duration
                delay(SCAN_PERIOD)

                // Stop scan
                stopBLEScanningInternal()

                // NOW clear old discovered devices and replace with new scan results
                val newDiscoveredDevices = _discoveredDevices.value.filterKeys { mac ->
                    _scannedInCurrentCycle.contains(mac)
                }
                _discoveredDevices.value = newDiscoveredDevices
                _nearbyMeterCount.value = newDiscoveredDevices.size

                // Update offline meters based on current discovered devices
                updateOfflineMeters()

                // ✅ Resume printer status monitoring after scan
                printerViewModel?.startStatusMonitoring()

                // Wait cooldown before next cycle
                Log.d(TAG, "Waiting ${SCAN_INTERVAL}ms before next scan cycle")
                delay(SCAN_INTERVAL)
            }
        }
        Log.i(TAG, "Periodic scanning started: ${SCAN_PERIOD}ms scan every ${SCAN_INTERVAL}ms")
    }

    @RequiresPermission(allOf = [
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.ACCESS_FINE_LOCATION
    ])
    private suspend fun startBLEScanningInternal() {
        if (mBluetoothAdapter == null || _uiState.value.allMeters.isEmpty()) return

        if (!mScanning) {
            try {
                // Add small delay if previous scan just stopped
                delay(SCAN_COOLDOWN)

                @Suppress("DEPRECATION")
                mBluetoothAdapter?.startLeScan(mLeScanCallback)
                mScanning = true
                _isScanning.value = true
                Log.i(TAG, "BLE scan started")
            } catch (e: Exception) {
                Log.e(TAG, "Error starting BLE scan", e)
            }
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    private fun stopBLEScanningInternal() {
        if (mScanning) {
            try {
                @Suppress("DEPRECATION")
                mBluetoothAdapter?.stopLeScan(mLeScanCallback)
                mScanning = false
                _isScanning.value = false
                Log.i(TAG, "BLE scan stopped - found ${_scannedInCurrentCycle.size} devices this cycle")
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping BLE scan", e)
            }
        }
    }

    /**
     * Mark meters as offline if they weren't discovered in the scan cycle
     */
    private fun updateOfflineMeters() {
        val currentMeters = _uiState.value.allMeters.toMutableList()
        val discoveredMacs = _discoveredDevices.value.keys
        var updated = false

        for (i in currentMeters.indices) {
            val meter = currentMeters[i]
            val meterMac = meter.bluetoothId?.uppercase()

            if (meterMac != null) {
                if (!discoveredMacs.contains(meterMac) && meter.status == MeterStatus.ACTIVE) {
                    currentMeters[i] = meter.copy(status = MeterStatus.OFFLINE)
                    updated = true
                    Log.d(TAG, "Meter ${meter.serialNumber} went offline")
                }
            }
        }

        if (updated) {
            _uiState.value = _uiState.value.copy(
                allMeters = currentMeters,
                filteredMeters = filterMeters(currentMeters, _searchQuery.value)
            )
        }
    }

    /**
     * Load meters from CSV file
     */
    fun loadMeters(context: Context, fileName: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

            try {
                val result = loadMeterDataFromFile(context, fileName)
                when (result) {
                    is MeterLoadResult.Success -> {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            allMeters = result.meters,
                            filteredMeters = result.meters,
                            errorMessage = null
                        )
                        Log.d(TAG, "Successfully loaded ${result.meters.size} meters from CSV")

                        // Count how many have Bluetooth
                        val bluetoothMeters = result.meters.count { !it.bluetoothId.isNullOrBlank() }
                        Log.i(TAG, "$bluetoothMeters meters have Bluetooth IDs configured")
                    }
                    is MeterLoadResult.Error -> {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            errorMessage = result.message
                        )
                        Log.e(TAG, "Error loading meters: ${result.message}")
                    }
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Failed to load meter data: ${e.message}"
                )
                Log.e(TAG, "Exception loading meters", e)
            }
        }
    }

    private fun applySorting() {
        val currentMeters = _uiState.value.filteredMeters
        val sorted = when (_sortConfig.value.field) {
            SortField.SERIAL_NUMBER -> {
                if (_sortConfig.value.order == SortOrder.ASCENDING)
                    currentMeters.sortedBy { it.serialNumber }
                else
                    currentMeters.sortedByDescending { it.serialNumber }
            }
            SortField.LOCATION -> {
                if (_sortConfig.value.order == SortOrder.ASCENDING)
                    currentMeters.sortedBy { it.location }
                else
                    currentMeters.sortedByDescending { it.location }
            }
            SortField.LAST_MAINTENANCE_DATE -> {
                if (_sortConfig.value.order == SortOrder.ASCENDING)
                    currentMeters.sortedWith(compareBy(nullsLast()) { it.readDate })
                else
                    currentMeters.sortedWith(compareByDescending(nullsLast()) { it.readDate })
            }
        }
        _uiState.update { it.copy(filteredMeters = sorted) }
    }

    /**
     * Update search query and filter meters
     */
    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        val filteredMeters = filterMeters(_uiState.value.allMeters, query)
        _uiState.value = _uiState.value.copy(filteredMeters = filteredMeters)
    }

    // ... rest of private helper methods ...

    private fun loadMeterDataFromFile(context: Context, fileName: String): MeterLoadResult {
        return try {
            val externalFilesDir = context.getExternalFilesDir(null)
            val appFilesDir = File(externalFilesDir, APP_FILES_FOLDER)
            val meterFile = File(appFilesDir, fileName)

            if (!meterFile.exists()) {
                return MeterLoadResult.Error("Meter file not found")
            }

            val meters = mutableListOf<Meter>()
            val reader = BufferedReader(FileReader(meterFile))

            reader.use { bufferedReader ->
                var isFirstLine = true
                var lineNumber = 0

                bufferedReader.forEachLine { line ->
                    lineNumber++
                    if (isFirstLine) {
                        isFirstLine = false
                        return@forEachLine
                    }
                    if (line.isBlank()) return@forEachLine

                    try {
                        val columns = line.split(",").map { it.trim().removeSurrounding("\"") }
                        if (columns.size >= 12) {
                            val meter = Meter(
                                serialNumber = columns[2],
                                location = "Location $lineNumber",
                                type = MeterType.Type01,
                                status = if (columns[1].toIntOrNull() == 1) MeterStatus.ACTIVE else MeterStatus.OFFLINE,
                                installationDate = parseDate(columns[4]) ?: Date(),
                                readDate = parseDate(columns[11]),
                                bluetoothId = columns[3].takeIf { it.isNotBlank() },
                                fixedDate = parseDate(columns[4]),
                                impKWh = columns[5].toDoubleOrNull(),
                                expKWh = columns[6].toDoubleOrNull(),
                                impMaxDemandKW = columns[7].toDoubleOrNull(),
                                expMaxDemandKW = columns[8].toDoubleOrNull(),
                                minVoltV = columns[9].toDoubleOrNull(),
                                alert = columns[10].toDoubleOrNull()
                            )
                            meters.add(meter)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing line $lineNumber", e)
                    }
                }
            }

            MeterLoadResult.Success(meters)
        } catch (e: Exception) {
            MeterLoadResult.Error("Failed to load meter data: ${e.message}")
        }
    }

    private fun filterMeters(meters: List<Meter>, query: String): List<Meter> {
        if (query.isBlank()) return meters
        val lowerQuery = query.lowercase()
        return meters.filter { meter ->
            meter.serialNumber.lowercase().contains(lowerQuery) ||
                    meter.location.lowercase().contains(lowerQuery) ||
                    meter.bluetoothId?.lowercase()?.contains(lowerQuery) == true
        }
    }

    private fun parseDate(dateString: String): Date? {
        if (dateString.isBlank()) return null
        val formats = listOf(
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd",
            "dd/MM/yyyy HH:mm:ss",
            "dd/MM/yyyy"
        )
        for (format in formats) {
            try {
                return SimpleDateFormat(format, Locale.getDefault()).parse(dateString)
            } catch (_: Exception) {
                continue
            }
        }
        return null
    }

    private fun updateErrorMessage(message: String) {
        _uiState.value = _uiState.value.copy(errorMessage = message)
    }


    /**
     * Pause periodic scanning (for MeterDetailScreen)
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun pauseScanning() {
        scanJob?.cancel()
        stopBLEScanningInternal()
        Log.i(TAG, "BLE scanning paused")
    }

    /**
     * Resume periodic scanning (when leaving MeterDetailScreen)
     */
    @RequiresPermission(allOf = [
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.ACCESS_FINE_LOCATION
    ])
    fun resumeScanning() {
        startPeriodicScanning()
        Log.i(TAG, "BLE scanning resumed")
    }

    override fun onCleared() {
        super.onCleared()
        Log.i(TAG, "ViewModel cleared")
    }
}


// UI State
data class MeterReadingUiState(
    val isLoading: Boolean = false,
    val allMeters: List<Meter> = emptyList(),
    val filteredMeters: List<Meter> = emptyList(),
    val errorMessage: String? = null
)

// Connection states
enum class ConnectionState {
    DISCONNECTED,
    CONNECTED,
    FAILED
}

// Meter loading results
sealed class MeterLoadResult {
    data class Success(val meters: List<Meter>) : MeterLoadResult()
    data class Error(val message: String) : MeterLoadResult()
}