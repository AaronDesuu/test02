package com.example.meterkenshin.ui.viewmodel

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.meterkenshin.bluetooth.BluetoothLeService
import com.example.meterkenshin.dlms.DLMS
import com.example.meterkenshin.model.Meter
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Registration state
 */
data class RegistrationState(
    val isRunning: Boolean = false,
    val currentStage: Int = 0,
    val message: String = "",
    val isComplete: Boolean = false,
    val error: String? = null
)

/**
 * ViewModel for DLMS Registration
 * Matches project01's MSG_SETUP flow with Bluetooth integration
 * FIXED: Added bounds checking like project01
 */
class DLMSRegistrationViewModel : ViewModel() {

    private val _registrationState = MutableStateFlow(RegistrationState())
    val registrationState: StateFlow<RegistrationState> = _registrationState.asStateFlow()

    private val _dlmsLog = MutableStateFlow("")
    val dlmsLog: StateFlow<String> = _dlmsLog.asStateFlow()

    private var dlms: DLMS? = null
    private var mBluetoothLeService: BluetoothLeService? = null
    private var mContext: Context? = null
    private var mServiceActive = false

    // State management matching project01
    private var mStep = 0
    private var mTimer = 0
    private var mArrived = 0
    private var mData: ByteArray = ByteArray(0)
    private var mSel: Byte = 0
    private var mDataIndex: Byte = 0
    private var mParameter = StringBuilder()
    private var mReceive: ArrayList<String>? = null

    companion object {
        private const val TAG = "DLMSRegistration"
    }

    /**
     * Broadcast receiver for GATT events
     */
    private val mGattUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothLeService.ACTION_GATT_CONNECTED -> {
                    Log.i(TAG, "GATT Connected")
                }
                BluetoothLeService.ACTION_GATT_DISCONNECTED -> {
                    Log.i(TAG, "GATT Disconnected")
                }
                BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED -> {
                    Log.i(TAG, "GATT Services Discovered - READY")
                    mArrived = 0  // Set ready flag
                }
                BluetoothLeService.ACTION_DATA_AVAILABLE -> {
                    val data = intent.getByteArrayExtra(BluetoothLeService.EXTRA_DATA)
                    if (data != null) {
                        mData = data
                        mArrived++
                        Log.d(TAG, "Data: ${data.size} bytes")
                    }
                }
            }
        }
    }
    /**
     * Service connection for Bluetooth LE
     */
    private val mServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(componentName: ComponentName, service: IBinder) {
            mBluetoothLeService = (service as BluetoothLeService.LocalBinder).service
            if (mBluetoothLeService == null) {
                Log.e(TAG, "Failed to initialize Bluetooth service")
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
     * Initialize DLMS with Admin level and bind Bluetooth service
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun initializeDLMS(context: Context, meter: Meter) {
        mContext = context
        dlms = DLMS(context)

        // Set DLMS credentials
        dlms?.Password(meter.key, 1)
        dlms?.writeAddress(meter.logical, 1)
        dlms?.writeRank(String.format("%02x", meter.rank), 1)

        // Register broadcast receiver with proper flag
        val filter = IntentFilter().apply {
            addAction(BluetoothLeService.ACTION_GATT_CONNECTED)
            addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED)
            addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED)
            addAction(BluetoothLeService.ACTION_DATA_AVAILABLE)
        }

        context.registerReceiver(
            mGattUpdateReceiver,
            filter,
            Context.RECEIVER_NOT_EXPORTED  // Add this flag
        )

        // Bind BluetoothLeService
        val serviceIntent = Intent(context, BluetoothLeService::class.java)
        context.bindService(serviceIntent, mServiceConnection, Context.BIND_AUTO_CREATE)

        appendLog("DLMS initialized")
    }

    /**
     * Start registration - NOW WITH SESSION ESTABLISHMENT
     */
    fun startRegistration(context: Context, meter: Meter) {
        viewModelScope.launch {
            try {
                if (dlms == null || !mServiceActive || meter.bluetoothId.isNullOrEmpty()) {
                    appendLog("ERROR: Not ready")
                    return@launch
                }

                _registrationState.value = RegistrationState(isRunning = true)
                _dlmsLog.value = ""

                // Connect to meter
                appendLog("Connecting to ${meter.bluetoothId}...")
                mArrived = -1  // Reset flag

                if (mBluetoothLeService?.connect(meter.bluetoothId) != true) {
                    appendLog("ERROR: Failed to connect")
                    return@launch
                }

                // Wait for GATT_SERVICES_DISCOVERED (mArrived becomes 0)
                var ready = false
                for (i in 0..100) {
                    delay(100)
                    if (mArrived == 0) {
                        ready = true
                        break
                    }
                }

                if (!ready) {
                    appendLog("ERROR: Services not discovered")
                    return@launch
                }

                appendLog("Connected and ready")
                delay(500)

                // Now start DLMS session
                appendLog("Establishing DLMS session...")
                if (!establishSession()) {
                    appendLog("ERROR: Failed to establish DLMS session")
                    return@launch
                }
                appendLog("DLMS session established")

                delay(500)

                // STEP 2: Set clock
                appendLog("Setting clock...")
                if (performSetClock()) {
                    appendLog("Success to set clock")
                } else {
                    appendLog("ERROR: Failed to set clock")
                    return@launch
                }

                delay(500)

                // STEP 3: Demand reset
                appendLog("Calling demand reset...")
                if (performDemandReset()) {
                    appendLog("Success to call demand reset")
                } else {
                    appendLog("ERROR: Failed to call demand reset")
                    return@launch
                }

                delay(500)

                // STEP 4: Get billing count
                appendLog("Getting billing count...")
                if (performGetBillingCount()) {
                    appendLog("Billing count retrieved")
                } else {
                    appendLog("ERROR: Failed to get billing count")
                    return@launch
                }

                delay(500)

                // STEP 5: Get billing data
                appendLog("Getting billing data...")
                if (performGetBillingData()) {
                    appendLog("Success to register meter")
                    appendLog("Finish!")
                    _registrationState.value = RegistrationState(isComplete = true)
                } else {
                    appendLog("ERROR: Failed to get billing data")
                }

            } catch (e: Exception) {
                appendLog("ERROR: ${e.message}")
                _registrationState.value = RegistrationState(
                    error = e.message ?: "Registration failed"
                )
            }
        }
    }

    /**
     * Establish DLMS session (Open + Session + Challenge + Confirm)
     * Matches project01's sessionEstablish()
     */
    private suspend fun establishSession(): Boolean {
        mStep = 0
        var sessionEstablished = false
        var timeout = 0

        while (!sessionEstablished && timeout < 100) {
            when (mStep) {
                0 -> {
                    // Send Open request
                    val openRequest = dlms?.Open()
                    if (openRequest != null) {
                        mTimer = 0
                        mArrived = 0
                        mBluetoothLeService?.write(openRequest)
                        mStep++
                        Log.i(TAG, "Open: ${openRequest.size}")
                    }
                }
                2 -> {
                    // Process Session response
                    val res = IntArray(2)
                    val sessionRequest = dlms?.Session(res, mData)
                    if (res[0] != 0 && sessionRequest != null) {
                        mTimer = 0
                        mArrived = 0
                        mBluetoothLeService?.write(sessionRequest)
                        mStep++
                        Log.i(TAG, "Session: ${sessionRequest.size}")
                    } else {
                        Log.e(TAG, "Failed to connect HDLC")
                        return false
                    }
                }
                4 -> {
                    // Process Challenge response
                    val res = IntArray(2)
                    val challengeRequest = dlms?.Challenge(res, mData)
                    if (res[0] != 0) {
                        if (challengeRequest != null) {
                            mTimer = 0
                            mBluetoothLeService?.write(challengeRequest)
                            mStep++
                            Log.i(TAG, "Challenge: ${challengeRequest.size}")
                        } else {
                            // No challenge needed
                            sessionEstablished = true
                            return true
                        }
                    } else {
                        Log.e(TAG, "Challenge failed")
                        return false
                    }
                }
                6 -> {
                    // Process Confirm response
                    val res = IntArray(2)
                    dlms?.Confirm(res, mData)
                    if (res[0] != 0) {
                        sessionEstablished = true
                        return true
                    } else {
                        Log.e(TAG, "Confirm failed")
                        return false
                    }
                }
                1, 3, 5 -> {
                    // Waiting for response
                    mTimer++
                    if (mArrived > 0 && mTimer > 5) {
                        mStep++
                        Log.i(TAG, "DataArrived-session:${mData.size}")
                    }
                }
            }

            delay(100)
            timeout++
        }

        return false // Timeout
    }

    /**
     * Perform Set Clock (SubStage 1-2)
     * Matches: AccessData(1, DLMS.IST_DATETIME_NOW, 2, false)
     */
    private suspend fun performSetClock(): Boolean {
        mStep = 0
        mSel = 0
        mParameter.clear()
        mParameter.append("0007e50a160f0a00ffff8000") // Example datetime

        return accessData(1, DLMS.IST_DATETIME_NOW, 2, false)
    }

    /**
     * Perform Demand Reset (SubStage 3-4)
     * Matches: AccessData(2, DLMS.IST_DEMAND_RESET, 1, false)
     */
    private suspend fun performDemandReset(): Boolean {
        mStep = 0
        mSel = 0
        mParameter.clear()
        mParameter.append("120001")

        return accessData(2, DLMS.IST_DEMAND_RESET, 1, false)
    }

    /**
     * Perform Get Billing Count (SubStage 5-6)
     * Matches: AccessData(0, DLMS.IST_BILLING_PARAMS, 7, false)
     */
    private suspend fun performGetBillingCount(): Boolean {
        mStep = 0
        mSel = 0
        mParameter.clear()

        return accessData(0, DLMS.IST_BILLING_PARAMS, 7, false)
    }

    /**
     * Perform Get Billing Data (SubStage 7-8)
     * Matches: AccessData(0, DLMS.IST_BILLING_PARAMS, 2, false)
     */
    private suspend fun performGetBillingData(): Boolean {
        mStep = 0
        mSel = 2
        mParameter.clear()

        return accessData(0, DLMS.IST_BILLING_PARAMS, 2, false)
    }

    /**
     * Access Data - matches project01's AccessData method
     * FIXED: Added bounds checking like project01
     * @param mode 0=GET, 1=SET, 2=ACTION
     * @param index Object index
     * @param attr Attribute
     * @param modeling Modeling flag
     */
    private suspend fun accessData(mode: Int, index: Int, attr: Int, modeling: Boolean): Boolean {
        var ret = 0

        when (mStep) {
            0 -> {
                val send = when (mode) {
                    0 -> {
                        Log.i(TAG, "Getting index:$index, attr:$attr")
                        dlms?.getReq(index, attr.toByte(), mSel, mParameter.toString(), mDataIndex)
                    }
                    1 -> {
                        Log.i(TAG, "Setting index:$index, attr:$attr")
                        dlms?.setReq(index, attr.toByte(), mSel, mParameter.toString(), mDataIndex)
                    }
                    2 -> {
                        Log.i(TAG, "Calling index:$index, attr:$attr")
                        dlms?.actReq(index, attr.toByte(), mParameter.toString(), mDataIndex)
                    }
                    else -> null
                }

                if (send != null) {
                    mTimer = 0
                    mArrived = 0
                    mBluetoothLeService?.write(send)
                    mStep++
                    ret = 1
                }
            }
            2 -> {
                val res = IntArray(2)
                res[0] = 0
                res[1] = 0
                mReceive = dlms?.DataRes(res, mData, modeling)

                // FIX: Add bounds checking like project01 does
                if (mReceive == null || mReceive!!.isEmpty()) {
                    Log.e(TAG, "ERROR: mReceive is null or empty")
                    mStep = 0
                    return false
                }

                if (res[1] < 0) {
                    ret = res[1]
                    mStep = 0
                    return false
                } else {
                    ret = res[0]
                    mStep = 0

                    // FIX: Validate response data based on mode (matching project01 pattern)
                    // For SET/ACTION operations (mode 1,2), check if response indicates success
                    if (mode > 0) {
                        // Check bounds before accessing index 1
                        if (mReceive!!.size > 1) {
                            // Check for "success (0)" response like project01 does
                            if (!mReceive!![1].equals("success (0)")) {
                                Log.e(TAG, "Operation failed: ${mReceive!![1]}")
                                return false
                            }
                        } else {
                            Log.e(TAG, "ERROR: Index 1 out of bounds for length ${mReceive!!.size}")
                            return false
                        }
                    }

                    return ret == 0 // Success if ret == 0
                }
            }
            1 -> {
                // Wait for data
                mTimer++
                if (mArrived > 0 && mTimer > 5) {
                    mStep++
                    Log.i(TAG, "DataArrived-access:${mData.size}")
                }
                ret = 1
            }
        }

        if (ret != 1) {
            mStep = 0
        }

        // Wait for completion
        var timeout = 0
        while (mStep != 0 && timeout < 100) {
            delay(100)
            timeout++
        }

        return false // Timeout
    }

    /**
     * Clear log
     */
    fun clearLog() {
        _dlmsLog.value = ""
    }

    /**
     * Add log entry
     */
    fun addLog(message: String) {
        appendLog(message)
    }

    /**
     * Append to log
     */
    private fun appendLog(message: String) {
        _dlmsLog.value += message + "\n"
        Log.d(TAG, message)
    }

    override fun onCleared() {
        super.onCleared()
        try {
            mContext?.unregisterReceiver(mGattUpdateReceiver)
            mContext?.unbindService(mServiceConnection)
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup: ${e.message}")
        }
    }
}