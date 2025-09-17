package com.example.meterkenshin.ui.viewmodel

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import android.view.WindowManager
import androidx.annotation.RequiresPermission
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.meterkenshin.dlms.DLMS
import com.example.meterkenshin.R
import com.example.meterkenshin.bluetooth.BluetoothLeService
import com.example.meterkenshin.bluetooth.DefaultDeviceList
import com.example.meterkenshin.model.Meter
import com.example.meterkenshin.model.MeterStatus
import com.example.meterkenshin.model.MeterType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Enhanced ViewModel for Meter Reading functionality with DLMS integration
 * Handles meter data loading, BLE connection, DLMS communication, and meter data access
 */
class MeterReadingViewModel : ViewModel() {

    companion object {
        private const val TAG = "MeterReadingViewModel"
        private const val APP_FILES_FOLDER = "app_files"
        private const val TIMEOUT = 100

        // DLMS Message Constants
        private const val MSG_ADMIN = 0
        private const val MSG_READER = 1
        private const val MSG_CHANGE_THRESH = 2

        // DLMS Object Constants (add these if missing from your DLMS class)
        private const val IST_ENERGY_A_PLUS = 1
        private const val IST_DEMAND_A_PLUS = 2
        private const val IST_BILLING_PARAMS = 3
        private const val IST_DEMAND_RESET = 4
        private const val IST_DETECT = 5
    }

    // BluetoothLeService constants (should match your BluetoothLeService)
    // If these don't exist in your BluetoothLeService, you need to add them
    object BluetoothLeServiceConstants {
        const val ACTION_GATT_CONNECTED = "com.example.meterkenshin.bluetooth.ACTION_GATT_CONNECTED"
        const val ACTION_GATT_DISCONNECTED = "com.example.meterkenshin.bluetooth.ACTION_GATT_DISCONNECTED"
        const val ACTION_GATT_SERVICES_DISCOVERED = "com.example.meterkenshin.bluetooth.ACTION_GATT_SERVICES_DISCOVERED"
        const val ACTION_DATA_AVAILABLE = "com.example.meterkenshin.bluetooth.ACTION_DATA_AVAILABLE"
        const val ACTION_GATT_ERROR = "com.example.meterkenshin.bluetooth.ACTION_GATT_ERROR"
        const val EXTRA_DATA = "com.example.meterkenshin.bluetooth.EXTRA_DATA"
    }

    // State management
    private val _uiState = MutableStateFlow(MeterReadingUiState())
    val uiState: StateFlow<MeterReadingUiState> = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // BLE and DLMS communication states
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _dlmsSessionState = MutableStateFlow(DLMSSessionState.IDLE)
    val dlmsSessionState: StateFlow<DLMSSessionState> = _dlmsSessionState.asStateFlow()

    private val _meterDataState = MutableStateFlow(MeterDataState())
    val meterDataState: StateFlow<MeterDataState> = _meterDataState.asStateFlow()

    // DLMS communication variables (ported from MainActivity)
    private var mCurrentMessage = 0
    private var mStage = 0
    private var mSubStage = 0
    private var mStep = 0
    private var mProgressing = false
    private var mTimer = 0
    private var mRetry = 0
    private var mConnected = 0
    private var mServiceActive = false
    private var mConnect: String? = null
    private var mPosition = -1
    private var mTotal = 0
    private var mDataIndex = 0
    private var mkeep = false
    private var mCount = 0
    private var mArrived = 0
    private var mData = ByteArray(0)
    private var mReceive: ArrayList<String>? = null
    private var mSel: Byte = 0
    private var mParameter = StringBuilder()

    // BLE Service
    private var mBluetoothLeService: BluetoothLeService? = null
    private var mAddress: String? = null

    // DLMS instance
    private var d: DLMS? = null

    // Device list (would be injected or managed separately)
    private var mDeviceList: DeviceList? = null

    // Context for broadcast receiver
    private var mContext: Context? = null

    // BroadcastReceiver for GATT events (copied from project01 MainActivity)
    private val mGattUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            when (action) {
                BluetoothLeServiceConstants.ACTION_GATT_CONNECTED -> {
                    mConnected = 1
                    Log.i(TAG, "ACTION_GATT_CONNECTED")
                }
                BluetoothLeServiceConstants.ACTION_GATT_DISCONNECTED -> {
                    Log.i(TAG, "ACTION_GATT_DISCONNECTED")
                    mConnected = -1
                    if (mStage != 0) {
                        Log.i(TAG, "Restart gatt service")
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

    // Service connection for BLE (copied from project01 MainActivity)
    private val mServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(componentName: ComponentName, service: IBinder) {
            mBluetoothLeService = (service as BluetoothLeService.LocalBinder).service
            if (mBluetoothLeService?.initialize() != true) {
                Log.i(TAG, "Fail to initialize of Bluetooth service.")
                mServiceActive = false
            } else {
                Log.i(TAG, "Success to initialize of Bluetooth service.")
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
     * Initialize the ViewModel with DLMS and register broadcast receiver
     * Based on project01 MainActivity pattern
     */
    fun initialize(context: Context) {
        mContext = context

        // Initialize device list (like project01)
        mDeviceList = DefaultDeviceList()

        // DON'T register receiver or bind service here - this should be done in Activity/Composable
        // The ViewModel shouldn't directly register receivers due to context lifecycle issues
    }

    /**
     * Start BLE operations - should be called from Activity/Composable
     * This separates the initialization from the BLE operations
     */
    fun startBLEOperations(context: Context) {
        mContext = context

        // Register broadcast receiver for GATT events (critical for BLE to work!)
        val intentFilter = makeGattUpdateIntentFilter()

        // Register receiver with RECEIVER_NOT_EXPORTED flag (required for targetSdk 33+)
        context.registerReceiver(
            mGattUpdateReceiver,
            intentFilter,
            Context.RECEIVER_NOT_EXPORTED
        )

        // Bind BLE service (like project01)
        val gattServiceIntent = Intent(context, BluetoothLeService::class.java)
        context.bindService(gattServiceIntent, mServiceConnection, Context.BIND_AUTO_CREATE)

        Log.i(TAG, "BLE operations started - receiver registered and service bound")
    }

    /**
     * Stop BLE operations - should be called from Activity/Composable onDestroy
     */
    fun stopBLEOperations(context: Context) {
        try {
            // Unregister broadcast receiver
            context.unregisterReceiver(mGattUpdateReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering broadcast receiver", e)
        }

        try {
            // Unbind service
            context.unbindService(mServiceConnection)
        } catch (e: Exception) {
            Log.e(TAG, "Error unbinding service", e)
        }

        mServiceActive = false
        Log.i(TAG, "BLE operations stopped")
    }

    /**
     * Create IntentFilter like project01 MainActivity makeGattUpdateIntentFilter()
     */
    private fun makeGattUpdateIntentFilter(): IntentFilter {
        val intentFilter = IntentFilter()
        intentFilter.addAction(BluetoothLeServiceConstants.ACTION_GATT_ERROR)
        intentFilter.addAction(BluetoothLeServiceConstants.ACTION_GATT_CONNECTED)
        intentFilter.addAction(BluetoothLeServiceConstants.ACTION_GATT_DISCONNECTED)
        intentFilter.addAction(BluetoothLeServiceConstants.ACTION_GATT_SERVICES_DISCOVERED)
        intentFilter.addAction(BluetoothLeServiceConstants.ACTION_DATA_AVAILABLE)
        return intentFilter
    }

    /**
     * Load meters from CSV file and convert to MeterModel objects
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
                        Log.d(TAG, "Successfully loaded ${result.meters.size} meters")
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

    /**
     * Connect to meter using Bluetooth ID and start DLMS communication
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun connectToMeter(context: Context, meter: Meter) {
        if (meter.bluetoothId.isNullOrEmpty()) {
            updateErrorMessage(context.getString(R.string.ble_meter_no_bluetooth_id))
            return
        }

        viewModelScope.launch {
            try {
                _connectionState.value = ConnectionState.CONNECTING
                mAddress = meter.bluetoothId

                // Start the connection process
                val result = fragmentMessage(MSG_ADMIN)
                if (result > 0) {
                    _connectionState.value = ConnectionState.CONNECTED
                    updateMeterStatus(meter, MeterStatus.ACTIVE)
                } else {
                    _connectionState.value = ConnectionState.FAILED
                    updateErrorMessage(context.getString(R.string.ble_connection_failed))
                }
            } catch (e: Exception) {
                _connectionState.value = ConnectionState.FAILED
                updateErrorMessage("Connection error: ${e.message}")
                Log.e(TAG, "Error connecting to meter", e)
            }
        }
    }

    /**
     * Disconnect from current meter
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun disconnectFromMeter(force: Boolean = false) {
        viewModelScope.launch {
            try {
                Disconnect(force)
                _connectionState.value = ConnectionState.DISCONNECTED
                _dlmsSessionState.value = DLMSSessionState.IDLE
                mAddress = null
            } catch (e: Exception) {
                Log.e(TAG, "Error disconnecting from meter", e)
            }
        }
    }

    /**
     * Main fragment message handler (ported from MainActivity)
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun fragmentMessage(message: Int): Int {
        var ret = 0

        Log.i(TAG, String.format("Message now:%d, new: %d, Stage:%d", mCurrentMessage, message, mStage))
        mCurrentMessage = message

        if (message <= 0) {
            if (message == 0) {
                mCurrentMessage = -1
            } else {
                return ret
            }
        }

        if (mProgressing) {
            Log.i(TAG, String.format("Waiting Process:%b", mProgressing))
            return 1
        }

        mProgressing = true

        if (mTimer > TIMEOUT) {
            mTimer = 0
            Log.i(TAG, "fragmentMessage - Timeout")
            ret = -50
            Disconnect(true)
        } else {
            when (mStage) {
                0 -> {
                    ret = Connection(message)
                    if (ret == 2) {
                        mStage++
                        _connectionState.value = ConnectionState.CONNECTED
                    }
                    if (ret < 0) {
                        Disconnect(true)
                    }
                }
                1 -> {
                    ret = sessionEstablish(message)
                    if (ret == 2) {
                        mStage++
                        mSubStage = 0
                        _dlmsSessionState.value = DLMSSessionState.ESTABLISHED
                    }
                    if (ret < 0) {
                        Disconnect(true)
                    }
                }
                2 -> {
                    ret = handleDataAccess(message)
                    if (ret == 0) {
                        ret = 2
                        mStage++
                    } else {
                        if (ret < 0) {
                            Disconnect(true)
                        }
                    }
                }
                3 -> {
                    ret = sessionRelease()
                    if (ret <= 0) {
                        Disconnect(true)
                    }
                }
            }
        }

        mProgressing = false
        return ret
    }

    /**
     * Handle data access stage
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun handleDataAccess(message: Int): Int {
        var ret: Int

        when (mSubStage) {
            0 -> {
                mTotal = 0
                mDataIndex = 0
                mkeep = false
                mSubStage++
                ret = 1
            }
            1, 3, 5, 7, 9, 11 -> {
                ret = Parameter(message)
                if (ret == 3) {
                    ret = 1
                    mSubStage++
                }
            }
            2, 4, 6, 8, 10, 12 -> {
                ret = Access(message)
                if (ret <= 0) {
                    if (ret < 0) {
                        Log.i(TAG, "Detect error...")
                    }
                    mSubStage = 0
                    mDataIndex = 0
                } else {
                    if (ret > 2) {
                        if (ret == 3) {
                            mSubStage = 1
                        } else {
                            mSubStage++
                        }
                    }
                }
            }
            else -> {
                mSubStage = 0
                ret = 0
            }
        }

        Log.i(TAG, String.format("handleDataAccess - result :%d", ret))
        return ret
    }

    /**
     * Connection establishment (ported from MainActivity)
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun Connection(message: Int): Int {
        var ret = 0

        when (mStep) {
            0 -> {
                d?.setCurrentLevel(getLevel())
                mRetry = 0
                mConnect = null
                mPosition = Position(mAddress)
                if (mDeviceList?.Rssi(mPosition) ?: -300 > -200) {
                    mConnect = mAddress
                    Log.i(TAG, "Try to connect $mAddress")
                }
                if (mConnect != null) {
                    mStep++
                    ret = 1
                } else {
                    ret = -99
                }
            }
            1 -> {
                if (!mServiceActive) {
                    // Bind BLE service
                    // This would need context - could be passed or injected
                    mTimer = 0
                }
                mStep++
                ret = 1
            }
            2 -> {
                if (!mServiceActive) {
                    mTimer++
                } else {
                    Log.i(TAG, " mServiceActive true")
                    mStep++
                    mTimer = 0
                }
                ret = 1
            }
            3 -> {
                if (mBluetoothLeService?.connect(mConnect!!) == true) {
                    mConnected = 0
                    Log.i(TAG, "BLE service connecting...")
                    mStep++
                    mTimer = 0
                    ret = 1
                } else {
                    Log.i(TAG, "Fail to connect service")
                    if (mRetry < 3) {
                        mRetry++
                    } else {
                        ret = -98
                    }
                }
            }
            4 -> {
                ret = 1
                when (mConnected) {
                    0, 1 -> mTimer++
                    -1 -> {
                        if (mRetry < 3) {
                            mRetry++
                            Log.i(TAG, "Retry connect service")
                            mStep = 3
                        } else {
                            mStep++
                        }
                    }
                    2 -> mStep++
                }
            }
            5 -> {
                if (mConnected == 2) {
                    mStep++
                    ret = 2
                } else {
                    if (mConnected == -1) {
                        ret = -97
                    } else {
                        Log.i(TAG, "Reject to connect service...")
                        ret = -96
                    }
                }
            }
        }

        if (ret != 1) {
            mStep = 0
        }
        return ret
    }

    /**
     * Session establishment (ported from MainActivity)
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun sessionEstablish(message: Int): Int {
        var send: ByteArray? = null
        var ret: Int
        val res = IntArray(2)
        ret = 0 // 0:fail, 1:processing, 2:established

        when (mStep) {
            0 -> {
                _dlmsSessionState.value = DLMSSessionState.OPENING
                send = d?.Open()
                if (send != null) {
                    mStep++
                    mTimer = 0
                    mArrived = 0
                    mBluetoothLeService?.write(send)
                    ret = 1
                    Log.i(TAG, String.format("Open:%d", send.size))
                } else {
                    ret = -95 // DLMS not available
                }
            }
            2 -> {
                _dlmsSessionState.value = DLMSSessionState.ESTABLISHING
                send = d?.Session(res, mData)
                if (send != null && res[0] != 0) {
                    mStep++
                    mTimer = 0
                    mArrived = 0
                    mBluetoothLeService?.write(send)
                    ret = 1
                    Log.i(TAG, String.format("Session:%d", send.size))
                } else {
                    ret = -89
                    Log.i(TAG, "Fail to connect HDLC.")
                }
            }
            4 -> {
                send = d?.Challenge(res, mData)
                if (send != null && res[0] != 0) {
                    mStep++
                    mTimer = 0
                    mBluetoothLeService?.write(send)
                    ret = 1
                    Log.i(TAG, String.format("Challenge:%d", send.size))
                } else {
                    // Challenge not required
                    val rank = d?.Rank() ?: DLMS.RANK_PUBLIC
                    if (rank == DLMS.RANK_POWER || rank == DLMS.RANK_READER || rank == DLMS.RANK_PUBLIC) {
                        ret = 2
                    } else {
                        ret = -88
                    }
                }
            }
            6 -> {
                send = d?.Confirm(res, mData)
                if (send != null && res[0] != 0) {
                    val rank = d?.Rank() ?: DLMS.RANK_PUBLIC
                    if (rank == DLMS.RANK_ADMIN || rank == DLMS.RANK_SUPER) {
                        ret = 2
                        Log.i(TAG, "Confirm")
                    }
                } else {
                    ret = -86
                }
            }
            1, 3, 5 -> { // received
                mTimer++
                if (mArrived > 0) {
                    mStep++
                    Log.i(TAG, String.format("DataArrived-session:%d", mData.size))
                }
                ret = 1
            }
        }

        if (ret != 1) {
            mStep = 0
        }
        return ret
    }

    /**
     * Session release (ported from MainActivity)
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun sessionRelease(): Int {
        var send: ByteArray? = null
        var ret: Int
        val res = IntArray(2)
        ret = 0 // 0:fail, 1:processing, 2:finished

        when (mStep) {
            0 -> {
                _dlmsSessionState.value = DLMSSessionState.RELEASING
                Log.i(TAG, "Release session...")
                send = d?.Release()
                if (send != null) {
                    mStep++
                    mTimer = 0
                    mArrived = 0
                    mBluetoothLeService?.write(send)
                    ret = 1
                    Log.i(TAG, String.format("Release:%d", send.size))
                } else {
                    ret = -95 // DLMS not available
                }
            }
            2 -> {
                send = d?.Close(res, mData)
                if (send != null && res[0] != 0) {
                    Log.i(TAG, "Close connecting...")
                    mStep++
                    mTimer = 0
                    mArrived = 0
                    mBluetoothLeService?.write(send)
                    ret = 1
                    Log.i(TAG, String.format("Close:%d", send.size))
                }
            }
            4 -> {
                d?.Finish(res, mData)
                if (res[0] != 0) {
                    Log.i(TAG, "sessionRelease - Finish")
                } else {
                    Log.i(TAG, "Fail to finish.")
                }
                _dlmsSessionState.value = DLMSSessionState.IDLE
            }
            1, 3 -> {
                mTimer++
                if (mArrived > 0) {
                    mStep++
                    Log.i(TAG, String.format("mArrived1:%d", mData.size))
                }
                ret = 1
            }
        }

        if (ret != 1) {
            mStep = 0
        }
        return ret
    }

    /**
     * Access meter data (ported from MainActivity)
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun AccessData(mode: Int, index: Int, attr: Int, modeling: Boolean): Int {
        var send: ByteArray? = null
        var ret = 0
        var feedback: Int

        when (mStep) {
            0 -> {
                mCount++
                when (mode) {
                    0 -> {
                        Log.i(TAG, String.format("Getting index:%d, attr:%d", index, attr))
                        send = d?.getReq(index, attr.toByte(), mSel, mParameter.toString(), mDataIndex.toByte())                    }
                    1 -> {
                        Log.i(TAG, String.format("Setting index:%d, attr:%d", index, attr))
                        send = d?.getReq(index, attr.toByte(), mSel, mParameter.toString(), mDataIndex.toByte())                    }
                    2 -> {
                        Log.i(TAG, String.format("Calling index:%d, attr:%d", index, attr))
                        send = d?.getReq(index, attr.toByte(), mSel, mParameter.toString(), mDataIndex.toByte())                    }
                }
                if (send != null) {
                    mTimer = 0
                    mArrived = 0
                    mBluetoothLeService?.write(send)
                    mStep++
                    ret = 1
                } else {
                    ret = -95 // DLMS not available
                }
            }
            2 -> {
                val res = IntArray(2)
                res[0] = 0
                res[1] = 0
                mReceive = d?.DataRes(res, mData, modeling)
                if (res[1] < 0) {
                    // Handle data arrived with error
                    ret = res[1]
                } else {
                    ret = res[0]
                    // Process received data
                    processMeterData(mReceive)
                }
            }
            1 -> {
                mTimer++
                if (mArrived > 0 && mTimer > 5) {
                    mStep++
                    Log.i(TAG, String.format("DataArrived-access:%d", mData.size))
                }
                ret = 1
            }
        }

        if (ret != 1) {
            mStep = 0
        }
        return ret
    }

    /**
     * Parameter handling (implementation based on MainActivity)
     */
    private fun Parameter(message: Int): Int {
        var ret = 0

        // Clear mParameter at the beginning for most cases
        mParameter.setLength(0)
        mSel = 0 // Default selector

        when (mCurrentMessage) {
            MSG_ADMIN -> {
                when (mSubStage) {
                    1, 3, 5 -> {
                        ret = 3
                    }
                    7 -> {
                        mSel = 2
                        ret = 3
                    }
                    else -> ret = 0
                }
            }
            MSG_READER -> {
                when (mSubStage) {
                    1 -> {
                        mParameter.append("120001")
                        ret = 3
                    }
                    3 -> {
                        ret = 3
                    }
                    5 -> {
                        mSel = 2
                        ret = 3
                    }
                    else -> ret = 0
                }
            }
            MSG_CHANGE_THRESH -> {
                mParameter.append("01010204128001120032110c1101")
                ret = 3
            }
            else -> {
                Log.w(TAG, "Parameter: Unhandled message_id: $mCurrentMessage")
                ret = 0
            }
        }

        if (ret != 1) {
            mStep = 0
        }
        return ret
    }

    /**
     * Access handling (simplified implementation based on MainActivity)
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun Access(message: Int): Int {
        var ret: Int

        when (mCurrentMessage) {
            MSG_ADMIN -> {
                when (mSubStage) {
                    2 -> {
                        ret = AccessData(0, IST_ENERGY_A_PLUS, 2, false)
                        if (ret == 0) {
                            ret = 5
                        }
                    }
                    4 -> {
                        ret = AccessData(0, IST_DEMAND_A_PLUS, 2, false)
                        if (ret == 0) {
                            ret = 5
                        }
                    }
                    6 -> {
                        ret = AccessData(0, IST_BILLING_PARAMS, 7, false)
                        if (ret == 0) {
                            ret = 5
                        }
                    }
                    8 -> {
                        ret = AccessData(0, IST_BILLING_PARAMS, 2, false)
                    }
                    else -> ret = 0
                }
            }
            MSG_READER -> {
                when (mSubStage) {
                    2 -> {
                        ret = AccessData(2, IST_DEMAND_RESET, 1, false)
                        if (ret == 0) {
                            ret = 5
                        }
                    }
                    4 -> {
                        ret = AccessData(0, IST_BILLING_PARAMS, 7, false)
                        if (ret == 0) {
                            ret = 5
                        }
                    }
                    6 -> {
                        ret = AccessData(0, IST_BILLING_PARAMS, 2, false)
                    }
                    else -> ret = 0
                }
            }
            MSG_CHANGE_THRESH -> {
                mDataIndex = 8
                ret = AccessData(1, IST_DETECT, 2, false)
            }
            else -> {
                Log.i(TAG, "Not implemented function...")
                ret = 0
            }
        }

        if (ret == 0) {
            Log.i(TAG, "Access - Finish")
        }
        return ret
    }

    /**
     * Disconnect from BLE service
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun Disconnect(force: Boolean) {
        try {
            mBluetoothLeService?.disconnect()
            if (mServiceActive) {
                // Unbind service would need context
                mServiceActive = false
            }

            // Reset all states
            mStage = 0
            mSubStage = 0
            mStep = 0
            mProgressing = false
            mTimer = 0
            mConnected = 0

            _connectionState.value = ConnectionState.DISCONNECTED
            _dlmsSessionState.value = DLMSSessionState.IDLE

            Log.i(TAG, "Disconnected from meter")
        } catch (e: Exception) {
            Log.e(TAG, "Error during disconnect", e)
        }
    }

    /**
     * Process received meter data and update UI state
     */
    private fun processMeterData(data: ArrayList<String>?) {
        data?.let { dataList ->
            try {
                // Parse meter data based on DLMS response
                // The ArrayList contains formatted meter data strings
                if (dataList.isNotEmpty()) {
                    Log.i(TAG, "Received meter data with ${dataList.size} items")

                    // Process the data strings - format depends on DLMS response structure
                    var energyValue: Double? = null
                    var voltageValue: Double? = null
                    var currentValue: Double? = null

                    // Parse data based on your specific DLMS response format
                    // This is a simplified example - adjust based on actual data format
                    for (dataItem in dataList) {
                        Log.d(TAG, "Data item: $dataItem")

                        // Example parsing - adjust based on your data format
                        if (dataItem.contains("Energy") || dataItem.matches(Regex("\\d+\\.\\d+"))) {
                            energyValue = dataItem.toDoubleOrNull() ?: energyValue
                        }
                        // Add more parsing logic as needed
                    }

                    _meterDataState.value = MeterDataState(
                        energy = energyValue,
                        voltage = voltageValue,
                        current = currentValue,
                        lastUpdated = System.currentTimeMillis(),
                        isValid = true
                    )

                    Log.i(TAG, "Meter data processed successfully: Energy=$energyValue")
                } else {
                    Log.w(TAG, "Received empty meter data")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing meter data", e)
                _meterDataState.value = _meterDataState.value.copy(
                    error = "Error processing data: ${e.message}",
                    isValid = false
                )
            }
        }
    }

    // Helper methods
    private fun getLevel(): Int = 1 // Default level

    private fun Position(address: String?): Int {
        // Return position in device list or -1 if not found
        return mDeviceList?.findPosition(address) ?: -1
    }

    /**
     * Update meter status in the list
     */
    private fun updateMeterStatus(meter: Meter, newStatus: MeterStatus) {
        val currentMeters = _uiState.value.allMeters.toMutableList()
        val index = currentMeters.indexOfFirst { it.id == meter.id }
        if (index != -1) {
            currentMeters[index] = meter.copy(status = newStatus)
            _uiState.value = _uiState.value.copy(
                allMeters = currentMeters,
                filteredMeters = filterMeters(currentMeters, _searchQuery.value)
            )
        }
    }

    private fun updateErrorMessage(message: String) {
        _uiState.value = _uiState.value.copy(errorMessage = message)
    }

    /**
     * Search functionality
     */
    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        val filteredMeters = filterMeters(_uiState.value.allMeters, query)
        _uiState.value = _uiState.value.copy(filteredMeters = filteredMeters)
    }

    private fun filterMeters(meters: List<Meter>, query: String): List<Meter> {
        if (query.isBlank()) return meters

        val lowerQuery = query.lowercase()
        return meters.filter { meter ->
            meter.serialNumber.lowercase().contains(lowerQuery) ||
                    meter.location.lowercase().contains(lowerQuery) ||
                    meter.type.displayName.lowercase().contains(lowerQuery) ||
                    meter.status.displayName.lowercase().contains(lowerQuery) ||
                    meter.bluetoothId?.lowercase()?.contains(lowerQuery) == true
        }
    }

    /**
     * Load meter data from CSV file and parse to MeterModel objects
     */
    private suspend fun loadMeterDataFromFile(
        context: Context,
        fileName: String
    ): MeterLoadResult {
        return try {
            val externalFilesDir = context.getExternalFilesDir(null)
            val appFilesDir = File(externalFilesDir, APP_FILES_FOLDER)
            val meterFile = File(appFilesDir, fileName)

            if (!meterFile.exists()) {
                return MeterLoadResult.Error("Meter file not found: ${meterFile.absolutePath}")
            }

            Log.d(TAG, "Loading meter data from: ${meterFile.absolutePath}")

            val meters = mutableListOf<Meter>()
            val reader = BufferedReader(FileReader(meterFile))

            reader.use { bufferedReader ->
                var isFirstLine = true
                var lineNumber = 0

                bufferedReader.forEachLine { line ->
                    lineNumber++

                    if (isFirstLine) {
                        // Skip header line - expected format: UID,Activate,Serial NO.,Bluetooth ID,Fixed date,Imp [kWh],Exp [kWh],ImpMaxDemand [kW],ExpMaxDemand [kW],MinVolt [V],Alert,Read date
                        isFirstLine = false
                        Log.d(TAG, "CSV Header: $line")
                        return@forEachLine
                    }

                    if (line.isBlank()) return@forEachLine

                    try {
                        val columns = line.split(",").map { it.trim().removeSurrounding("\"") }

                        if (columns.size >= 12) {
                            val uid = columns[0]
                            val activate = columns[1].toIntOrNull() ?: 0
                            val serialNumber = columns[2]
                            val bluetoothId = columns[3].takeIf { it.isNotBlank() }
                            val fixedDate = columns[4]
                            val impKWh = columns[5].toDoubleOrNull()
                            val expKWh = columns[6].toDoubleOrNull()
                            val impMaxDemandKW = columns[7].toDoubleOrNull()
                            val expMaxDemandKW = columns[8].toDoubleOrNull()
                            val minVoltV = columns[9].toDoubleOrNull()
                            val alert = columns[10].toDoubleOrNull()
                            val readDate = columns[11]

                            // Create meter object using existing Meter model structure
                            val meter = Meter(
                                id = uid,
                                serialNumber = serialNumber,
                                location = "Location $lineNumber", // Keep existing location field
                                type = MeterType.SINGLE_PHASE, // Keep existing type field
                                status = if (activate == 1) MeterStatus.ACTIVE else MeterStatus.OFFLINE,
                                installationDate = parseDate(fixedDate) ?: Date(), // Use fixedDate as installation date
                                lastMaintenanceDate = parseDate(readDate), // Use readDate as last maintenance
                                coordinates = null,
                                // CSV specific fields
                                activate = activate,
                                bluetoothId = bluetoothId,
                                fixedDate = parseDate(fixedDate),
                                impKWh = impKWh,
                                expKWh = expKWh,
                                impMaxDemandKW = impMaxDemandKW,
                                expMaxDemandKW = expMaxDemandKW,
                                minVoltV = minVoltV,
                                alert = alert
                            )

                            meters.add(meter)
                            Log.d(TAG, "Parsed meter: $serialNumber, BT: $bluetoothId, Status: ${meter.status}")
                        } else {
                            Log.w(TAG, "Line $lineNumber has insufficient columns: ${columns.size}")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing line $lineNumber: $line", e)
                    }
                }
            }

            Log.i(TAG, "Successfully loaded ${meters.size} meters from CSV")
            MeterLoadResult.Success(meters)

        } catch (e: Exception) {
            Log.e(TAG, "Error loading meter data", e)
            MeterLoadResult.Error("Failed to load meter data: ${e.message}")
        }
    }

    /**
     * Parse date string to Date object
     */
    private fun parseDate(dateString: String): Date? {
        if (dateString.isBlank()) return null

        return try {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            dateFormat.parse(dateString)
        } catch (e: Exception) {
            try {
                val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                dateFormat.parse(dateString)
            } catch (e2: Exception) {
                Log.w(TAG, "Unable to parse date: $dateString")
                null
            }
        }
    }

    /**
     * Get meter by ID
     */
    fun getMeterById(id: String): Meter? {
        return _uiState.value.allMeters.find { it.id == id }
    }

    /**
     * Get meters by status
     */
    fun getMetersByStatus(status: MeterStatus): List<Meter> {
        return _uiState.value.allMeters.filter { it.status == status }
    }

    /**
     * Get meters by type
     */
    fun getMetersByType(type: MeterType): List<Meter> {
        return _uiState.value.allMeters.filter { it.type == type }
    }

    /**
     * Get meters with Bluetooth capability
     */
    fun getBluetoothEnabledMeters(): List<Meter> {
        return _uiState.value.allMeters.filter { it.bluetoothId != null }
    }

    /**
     * Get meters with energy readings
     */
    fun getMetersWithEnergyReadings(): List<Meter> {
        return _uiState.value.allMeters.filter { it.impKWh != null || it.expKWh != null }
    }

    /**
     * Get meters with alerts
     */
    fun getMetersWithAlerts(): List<Meter> {
        return _uiState.value.allMeters.filter { it.alert != null && it.alert!! > 0 }
    }

    /**
     * Connect to multiple meters sequentially
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun connectToMetersSequentially(context: Context, meters: List<Meter>) {
        viewModelScope.launch {
            for (meter in meters) {
                if (meter.bluetoothId != null) {
                    Log.i(TAG, "Connecting to meter: ${meter.serialNumber}")
                    connectToMeter(context, meter)

                    // Wait for connection to complete or timeout
                    var attempts = 0
                    while (_connectionState.value == ConnectionState.CONNECTING && attempts < 30) {
                        delay(1000)
                        attempts++
                    }

                    if (_connectionState.value == ConnectionState.CONNECTED) {
                        // Read meter data
                        delay(2000) // Wait for DLMS session

                        if (_dlmsSessionState.value == DLMSSessionState.ESTABLISHED) {
                            // Meter is connected and session established
                            // Update status to active
                            updateMeterStatus(meter, MeterStatus.ACTIVE)
                            Log.i(TAG, "Successfully activated meter: ${meter.serialNumber}")
                        }

                        // Disconnect before connecting to next meter
                        disconnectFromMeter()
                        delay(1000)
                    } else {
                        Log.w(TAG, "Failed to connect to meter: ${meter.serialNumber}")
                    }
                }
            }
        }
    }

    /**
     * Auto-connect to available Bluetooth meters
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun autoConnectToAvailableMeters(context: Context) {
        val bluetoothMeters = getBluetoothEnabledMeters()
        if (bluetoothMeters.isNotEmpty()) {
            connectToMetersSequentially(context, bluetoothMeters)
        } else {
            updateErrorMessage(context.getString(R.string.ble_auto_connect_no_bluetooth_meters))
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun onCleared() {
        super.onCleared()
        // Clean up resources - but don't unregister receiver here
        // That should be done in Activity/Composable lifecycle
        try {
            if (_connectionState.value == ConnectionState.CONNECTED) {
                disconnectFromMeter(true)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup", e)
        }
    }

    // Dummy DeviceList interface for compilation
    interface DeviceList {
        fun Rssi(position: Int): Int
        fun findPosition(address: String?): Int
    }
}

/**
 * Enhanced UI State for Meter Reading screen
 */
data class MeterReadingUiState(
    val isLoading: Boolean = false,
    val allMeters: List<Meter> = emptyList(),
    val filteredMeters: List<Meter> = emptyList(),
    val errorMessage: String? = null
)

/**
 * Connection state for BLE communication
 */
enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    FAILED
}

/**
 * DLMS session state
 */
enum class DLMSSessionState {
    IDLE,
    OPENING,
    ESTABLISHING,
    ESTABLISHED,
    RELEASING
}

/**
 * Meter data state
 */
data class MeterDataState(
    val energy: Double? = null,
    val voltage: Double? = null,
    val current: Double? = null,
    val lastUpdated: Long? = null,
    val isValid: Boolean = false,
    val error: String? = null
)

/**
 * Sealed class for meter loading results
 */
sealed class MeterLoadResult {
    data class Success(val meters: List<Meter>) : MeterLoadResult()
    data class Error(val message: String) : MeterLoadResult()
}