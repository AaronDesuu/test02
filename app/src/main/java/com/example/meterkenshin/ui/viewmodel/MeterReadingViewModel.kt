package com.example.meterkenshin.ui.viewmodel

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
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
import com.example.meterkenshin.ui.manager.SessionManager
import com.example.meterkenshin.utils.FilterUtils
import com.example.meterkenshin.utils.UserFileManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Suppress("PrivatePropertyName")
class MeterReadingViewModel : ViewModel() {
    companion object {
        private const val TAG = "MeterReadingViewModel"
    }

    // Modern BLE Scanner
    private val bluetoothLeScanner: BluetoothLeScanner?
        get() = mBluetoothAdapter?.bluetoothLeScanner

    // Scan settings - LOW_POWER mode minimizes interference with Bluetooth Classic (printer)
    private val scanSettings: ScanSettings by lazy {
        ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
            .setReportDelay(0)
            .build()
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

    // Track last RSSI update time for throttling (MAC -> timestamp in millis)
    private val _lastRssiUpdate = mutableMapOf<String, Long>()
    private val RSSI_UPDATE_INTERVAL_MS = 2000L // 2 seconds

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
    val discoveredDevices: StateFlow<Map<String, Int>> = _discoveredDevices.asStateFlow()
    private val _scannedInCurrentCycle = mutableSetOf<String>() // Track which devices already scanned

    private val _selectionMode = MutableStateFlow(false)
    val selectionMode: StateFlow<Boolean> = _selectionMode.asStateFlow()

    private val _selectedMeters = MutableStateFlow<Set<Int>>(emptySet())
    val selectedMeters: StateFlow<Set<Int>> = _selectedMeters.asStateFlow()

    fun toggleSelectionMode() {
        _selectionMode.value = !_selectionMode.value
        if (!_selectionMode.value) {
            _selectedMeters.value = emptySet()
        }
    }

    fun toggleMeterSelection(meterUid: Int) {
        _selectedMeters.update { current ->
            if (current.contains(meterUid)) {
                current - meterUid
            } else {
                current + meterUid
            }
        }
    }

    fun selectAllMeters() {
        _selectedMeters.value = _uiState.value.filteredMeters
            .filter { it.activate == 1 }  // Only select registered meters
            .map { it.uid }
            .toSet()
    }

    fun clearSelection() {
        _selectedMeters.value = emptySet()
        _selectionMode.value = false
    }

    /**
     * Build hardware scan filters from known meter MAC addresses
     * Hardware filtering is more efficient and reduces radio interference
     */
    private fun buildScanFilters(): List<ScanFilter> {
        return _uiState.value.allMeters
            .mapNotNull { it.bluetoothId }
            .filter { it.isNotBlank() }
            .map { mac ->
                ScanFilter.Builder()
                    .setDeviceAddress(mac.uppercase())
                    .build()
            }
    }

    // Modern ScanCallback - hardware pre-filtered, only receives known meter devices
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val rssi = result.rssi
            val deviceMac = device.address.uppercase()
            val currentTime = System.currentTimeMillis()

            val isFirstDiscovery = !_scannedInCurrentCycle.contains(deviceMac)

            // Throttle RSSI updates to every 2 seconds (except for first discovery)
            val lastUpdate = _lastRssiUpdate[deviceMac] ?: 0L
            val shouldUpdateRssi = isFirstDiscovery || (currentTime - lastUpdate >= RSSI_UPDATE_INTERVAL_MS)

            if (shouldUpdateRssi) {
                // Update RSSI for real-time signal strength display
                val currentDevices = _discoveredDevices.value.toMutableMap()
                val previousRssi = currentDevices[deviceMac]
                currentDevices[deviceMac] = rssi
                _discoveredDevices.value = currentDevices
                _nearbyMeterCount.value = currentDevices.size
                _lastRssiUpdate[deviceMac] = currentTime

                // Update device list with latest RSSI
                mDeviceList?.addDevice(device, rssi)

                // Only log and do expensive operations on first discovery or significant RSSI change
                if (isFirstDiscovery) {
                    Log.i(TAG, "Meter discovered: $deviceMac, RSSI: $rssi dBm")
                    _scannedInCurrentCycle.add(deviceMac)
                    updateMeterNearbyStatus(deviceMac, rssi)
                    updateMeterLastCommunication(deviceMac)
                } else if (previousRssi != null && kotlin.math.abs(rssi - previousRssi) >= 5) {
                    // Log significant RSSI changes (5+ dBm difference)
                    Log.d(TAG, "RSSI update: $deviceMac, $previousRssi -> $rssi dBm")
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "BLE scan failed with error code: $errorCode")
            mScanning = false
            _isScanning.value = false
            when (errorCode) {
                SCAN_FAILED_ALREADY_STARTED -> Log.w(TAG, "Scan already started")
                SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> Log.e(TAG, "App registration failed")
                SCAN_FAILED_FEATURE_UNSUPPORTED -> Log.e(TAG, "BLE scan not supported")
                SCAN_FAILED_INTERNAL_ERROR -> Log.e(TAG, "Internal error")
                SCAN_FAILED_OUT_OF_HARDWARE_RESOURCES -> {
                    Log.e(TAG, "Out of BLE hardware resources - too many concurrent scans")
                    updateErrorMessage("Bluetooth hardware busy. Please wait and try again.")
                }

                SCAN_FAILED_SCANNING_TOO_FREQUENTLY -> {
                    Log.w(TAG, "Scanning too frequently - Android BLE rate limit hit")
                    updateErrorMessage("Scanning paused to prevent battery drain. Please wait a moment before scanning again.")
                }
            }
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
     * Start BLE scanning for nearby meters using modern BluetoothLeScanner API
     * Uses hardware filtering and LOW_POWER mode to minimize interference with printer
     */
    @RequiresPermission(allOf = [
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.ACCESS_FINE_LOCATION
    ])
    fun startBLEScanning() {
        if (bluetoothLeScanner == null) {
            updateErrorMessage("Bluetooth scanner not available")
            return
        }

        if (_uiState.value.allMeters.isEmpty()) {
            updateErrorMessage("Load meter.csv first before scanning")
            return
        }

        if (mScanning) {
            Log.d(TAG, "BLE scan already running")
            return
        }

        try {
            // Build hardware filters from known meter MAC addresses
            val filters = buildScanFilters()
            if (filters.isEmpty()) {
                Log.w(TAG, "No meter MAC addresses to scan for")
                updateErrorMessage("No meters with Bluetooth IDs found")
                return
            }

            // Clear tracking for new scan session
            _scannedInCurrentCycle.clear()
            _lastRssiUpdate.clear()
            mDeviceList?.Deactivate()

            // Start scanning with hardware filters and low-power settings
            bluetoothLeScanner?.startScan(filters, scanSettings, scanCallback)
            mScanning = true
            _isScanning.value = true

            Log.i(TAG, "BLE scan started with ${filters.size} hardware filters (LOW_POWER mode)")

        } catch (e: Exception) {
            Log.e(TAG, "Error starting BLE scan", e)
            updateErrorMessage("Failed to start scanning: ${e.message}")
            mScanning = false
            _isScanning.value = false
        }
    }

    /**
     * Stop BLE scanning
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun stopBLEScanning() {
        if (!mScanning) return

        try {
            bluetoothLeScanner?.stopScan(scanCallback)
            mScanning = false
            _isScanning.value = false

            // Update offline status for meters not discovered
            updateOfflineMeters()

            Log.i(TAG, "BLE scan stopped - found ${_nearbyMeterCount.value} nearby meters")

        } catch (e: Exception) {
            Log.e(TAG, "Error stopping BLE scan", e)
            mScanning = false
            _isScanning.value = false
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

            _uiState.value = _uiState.value.copy(allMeters = currentMeters)
            reapplyCurrentState()

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
            _uiState.value = _uiState.value.copy(allMeters = currentMeters)
            reapplyCurrentState()
        }
    }

    // Track the last loaded file to prevent unnecessary reloads
    private var lastLoadedFileName: String? = null
    private var lastLoadedFileTimestamp: Long = 0

    /**
     * Load meters from CSV file
     * ✅ FIXED: Prevents unnecessary reloads of the same file
     */
    fun loadMeters(context: Context, fileName: String, forceReload: Boolean = false) {
        viewModelScope.launch {
            // ✅ FIXED: Check if we're trying to reload the same file
            val sessionManager = SessionManager.getInstance(context)
            val meterFile = UserFileManager.getMeterFile(context, sessionManager, fileName)

            // Skip reload if same file and not modified (unless force reload requested)
            if (!forceReload &&
                meterFile.exists() &&
                fileName == lastLoadedFileName &&
                meterFile.lastModified() == lastLoadedFileTimestamp &&
                _uiState.value.allMeters.isNotEmpty()) {
                Log.d(TAG, "Skipping reload - file unchanged: $fileName")
                return@launch
            }

            if (forceReload) {
                Log.i(TAG, "Force reloading meters from: $fileName")
            }

            lastLoadedFileName = fileName
            lastLoadedFileTimestamp = if (meterFile.exists()) meterFile.lastModified() else 0

            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

            try {
                when (val result = loadMeterDataFromFile(context, fileName)) {
                    is MeterLoadResult.Success -> {
                        // ✅ FIXED: Add small delay to ensure data is fully composed before showing
                        // This prevents the flash of "Not Inspected" status while UI is initializing
                        delay(150)

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

    fun reloadMeters(context: Context, forceReload: Boolean = false) {
        val currentYearMonth = SimpleDateFormat("yyyyMM", Locale.getDefault()).format(Date())
        val currentMeterFile = "${currentYearMonth}_meter.csv"
        val fallbackFile = "meter.csv"

        // Check user-specific directory for meter file
        val sessionManager = SessionManager.getInstance(context)
        val currentFile = UserFileManager.getMeterFile(context, sessionManager, currentMeterFile)

        val fileToLoad = if (currentFile.exists()) {
            currentMeterFile
        } else {
            fallbackFile
        }

        loadMeters(context, fileToLoad, forceReload)
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

    private fun loadMeterDataFromFile(context: Context, fileName: String): MeterLoadResult {
        return try {
            // Get user-specific app files directory
            val sessionManager = SessionManager.getInstance(context)
            val meterFile = UserFileManager.getMeterFile(context, sessionManager, fileName)

            if (!meterFile.exists()) {
                return MeterLoadResult.Error("Meter file not found")
            }

            // First, check if CSV needs billingPrintDate column and add it if missing
            ensureBillingPrintDateColumn(meterFile)
            ensureLastCommunicationColumn(meterFile)

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
                                uid = columns[0].toIntOrNull() ?: 0,
                                activate = columns[1].toIntOrNull() ?: 0,
                                serialNumber = columns[2],
                                bluetoothId = columns[3].takeIf { it.isNotBlank() },
                                fixedDate = parseDate(columns[4]),
                                impKWh = columns[5].toDoubleOrNull(),
                                expKWh = columns[6].toDoubleOrNull(),
                                impMaxDemandKW = columns[7].toDoubleOrNull(),
                                expMaxDemandKW = columns[8].toDoubleOrNull(),
                                minVoltV = columns[9].toDoubleOrNull(),
                                alert = columns[10].toDoubleOrNull(),
                                readDate = parseDate(columns[11]),
                                billingPrintDate = if (columns.size >= 13) parseDate(columns[12]) else null,
                                lastCommunication = if (columns.size >= 14) parseDate(columns[13]) else null,

                                location = "Location $lineNumber",
                                type = MeterType.Type01,
                                status = if (columns[1].toIntOrNull() == 1) MeterStatus.ACTIVE else MeterStatus.OFFLINE,
                                installationDate = null,
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

    /**
     * Reapply current filters and sorting after allMeters is updated
     * Preserves user's active filter and sort state during BLE scan updates
     */
    private fun reapplyCurrentState() {
        val currentFiltered = _uiState.value.filteredMeters
        val currentAll = _uiState.value.allMeters

        // Determine which filter is active by checking filtered vs all meters
        val filtered = when {
            // Check if a specific filter is applied by comparing sizes
            currentFiltered.size < currentAll.size -> {
                // Preserve filter by matching serial numbers from previous filtered list
                val filteredSerials = currentFiltered.map { it.serialNumber }.toSet()
                val refiltered = currentAll.filter { it.serialNumber in filteredSerials }

                // If search query exists, apply it too
                if (_searchQuery.value.isNotBlank()) {
                    filterMeters(refiltered, _searchQuery.value)
                } else {
                    refiltered
                }
            }
            // Only search query applied
            _searchQuery.value.isNotBlank() -> filterMeters(currentAll, _searchQuery.value)
            // No filters
            else -> currentAll
        }

        // Reapply sorting
        val sorted = when (_sortConfig.value.field) {
            SortField.SERIAL_NUMBER -> {
                if (_sortConfig.value.order == SortOrder.ASCENDING)
                    filtered.sortedBy { it.serialNumber }
                else
                    filtered.sortedByDescending { it.serialNumber }
            }
            SortField.LOCATION -> {
                if (_sortConfig.value.order == SortOrder.ASCENDING)
                    filtered.sortedBy { it.location }
                else
                    filtered.sortedByDescending { it.location }
            }
            SortField.LAST_MAINTENANCE_DATE -> {
                if (_sortConfig.value.order == SortOrder.ASCENDING)
                    filtered.sortedWith(compareBy(nullsLast()) { it.readDate })
                else
                    filtered.sortedWith(compareByDescending(nullsLast()) { it.readDate })
            }
        }

        _uiState.update { it.copy(filteredMeters = sorted) }
    }

    private fun parseDate(dateString: String): Date? {
        if (dateString.isBlank()) return null
        val formats = listOf(
            "yyyy/MM/dd HH:mm:ss",
            "yyyy/MM/dd",
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
     * Ensure the CSV file has billingPrintDate column (column 13)
     * If not present, add it to the header and all rows with empty values
     */
    private fun ensureBillingPrintDateColumn(file: File) {
        try {
            val lines = file.readLines().toMutableList()
            if (lines.isEmpty()) return

            // Check header (first line)
            val headerColumns = lines[0].split(',')

            // If header already has 13+ columns, assume billingPrintDate exists
            if (headerColumns.size >= 13) return

            Log.d(TAG, "Adding billingPrintDate column to CSV (column was missing)")

            // Add billingPrintDate to header
            lines[0] = lines[0] + ",billingPrintDate"

            // Add empty billingPrintDate to all data rows
            for (i in 1 until lines.size) {
                lines[i] = lines[i] + ","
            }

            // Write back to file
            file.writeText(lines.joinToString("\n"))
            Log.d(TAG, "Successfully added billingPrintDate column to meter CSV")

        } catch (e: Exception) {
            Log.e(TAG, "Error adding billingPrintDate column: ${e.message}", e)
        }
    }

    /**
     * Ensure the CSV file has lastCommunication column (column 14)
     * If not present, add it to the header and all rows with empty values
     */
    private fun ensureLastCommunicationColumn(file: File) {
        try {
            val lines = file.readLines().toMutableList()
            if (lines.isEmpty()) return

            // Check header (first line)
            val headerColumns = lines[0].split(',')

            // If header already has 14+ columns, assume lastCommunication exists
            if (headerColumns.size >= 14) return

            Log.d(TAG, "Adding lastCommunication column to CSV (column was missing)")

            // Add lastCommunication to header
            lines[0] = lines[0] + ",lastCommunication"

            // Add empty lastCommunication to all data rows
            for (i in 1 until lines.size) {
                lines[i] = lines[i] + ","
            }

            // Write back to file
            file.writeText(lines.joinToString("\n"))
            Log.d(TAG, "Successfully added lastCommunication column to meter CSV")

        } catch (e: Exception) {
            Log.e(TAG, "Error adding lastCommunication column: ${e.message}", e)
        }
    }

    /**
     * Update lastCommunication timestamp when meter discovered via BLE
     */
    private fun updateMeterLastCommunication(bluetoothMac: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = mContext ?: return@launch
                val sessionManager = SessionManager.getInstance(context)
                val yearMonth = SimpleDateFormat("yyyyMM", Locale.getDefault()).format(Date())
                val filename = "${yearMonth}_meter.csv"
                val meterFile = UserFileManager.getMeterFile(context, sessionManager, filename)

                if (!meterFile.exists()) return@launch

                val lines = meterFile.readLines().toMutableList()
                if (lines.isEmpty()) return@launch

                // Ensure column exists
                if (!lines[0].contains("lastCommunication")) {
                    lines[0] = lines[0] + ",lastCommunication"
                    for (i in 1 until lines.size) {
                        lines[i] = lines[i] + ","
                    }
                }

                val lastCommIndex = lines[0].split(',').indexOf("lastCommunication")
                val bluetoothIdIndex = 3
                val currentTimestamp = SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault()).format(Date())

                var wasUpdated = false

                // Find meter by Bluetooth MAC and update
                for (i in 1 until lines.size) {
                    val columns = lines[i].split(',').toMutableList()
                    val csvMac = columns.getOrNull(bluetoothIdIndex)
                        ?.trim()
                        ?.removeSurrounding("\"")
                        ?.uppercase()

                    if (csvMac == bluetoothMac) {
                        while (columns.size <= lastCommIndex) {
                            columns.add("")
                        }
                        columns[lastCommIndex] = currentTimestamp
                        lines[i] = columns.joinToString(",")
                        wasUpdated = true
                        break
                    }
                }

                if (wasUpdated) {
                    meterFile.writeText(lines.joinToString("\n"))
                    // Update UI on main thread
                    withContext(Dispatchers.Main) {
                        val updatedMeters = loadMeterDataFromFile(context, filename)
                        if (updatedMeters is MeterLoadResult.Success) {
                            _uiState.update { it.copy(allMeters = updatedMeters.meters) }
                            reapplyCurrentState()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error updating lastCommunication", e)
            }
        }
    }

    fun filterNotInspected() {
        val filtered = FilterUtils.filterNotInspected(_uiState.value.allMeters)
        _uiState.update { it.copy(filteredMeters = filtered) }
    }

    fun filterInspected() {
        val filtered = FilterUtils.filterInspected(_uiState.value.allMeters)
        _uiState.update { it.copy(filteredMeters = filtered) }
    }

    fun filterBillingNotPrinted() {
        val filtered = FilterUtils.filterBillingNotPrinted(_uiState.value.allMeters)
        _uiState.update { it.copy(filteredMeters = filtered) }
    }

    fun filterBillingPrinted() {
        val filtered = FilterUtils.filterBillingPrinted(_uiState.value.allMeters)
        _uiState.update { it.copy(filteredMeters = filtered) }
    }

    fun filterOnline() {
        val filtered = FilterUtils.filterOnline(_uiState.value.allMeters, this)
        _uiState.update { it.copy(filteredMeters = filtered) }
    }

    fun filterOffline() {
        val filtered = FilterUtils.filterOffline(_uiState.value.allMeters, this)
        _uiState.update { it.copy(filteredMeters = filtered) }
    }

    fun filterByLocation(location: String) {
        val filtered = FilterUtils.filterByLocation(_uiState.value.allMeters, location)
        _uiState.update { it.copy(filteredMeters = filtered) }
    }

    fun getAllLocations(): List<String> {
        return FilterUtils.getAllLocations(_uiState.value.allMeters)
    }

    fun clearFilters() {
        _uiState.update { it.copy(filteredMeters = it.allMeters) }
    }

    fun clearMeters() {
        _uiState.value = MeterReadingUiState(
            isLoading = false,
            allMeters = emptyList(),
            filteredMeters = emptyList(),
            errorMessage = null
        )
        // ✅ FIXED: Reset tracking so next load will execute
        lastLoadedFileName = null
        lastLoadedFileTimestamp = 0

        // ✅ FIXED: Clear BLE scan results (nearby meters and discovered devices)
        _discoveredDevices.value = emptyMap()
        _nearbyMeterCount.value = 0
        _scannedInCurrentCycle.clear()

        Log.i(TAG, "Cleared meters and BLE scan results")
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