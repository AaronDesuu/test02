package com.example.meterkenshin.ui.viewmodel

import android.annotation.SuppressLint
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

data class RegistrationState(
    val isRunning: Boolean = false,
    val currentStage: Int = 0,
    val message: String = "",
    val isComplete: Boolean = false,
    val error: String? = null
)

class DLMSRegistrationViewModel : ViewModel() {

    private val _registrationState = MutableStateFlow(RegistrationState())
    val registrationState: StateFlow<RegistrationState> = _registrationState.asStateFlow()

    private val _dlmsLog = MutableStateFlow("")
    val dlmsLog: StateFlow<String> = _dlmsLog.asStateFlow()

    private var dlms: DLMS? = null
    private var mBluetoothLeService: BluetoothLeService? = null

    private var mContext: Context? = null

    // FIX: Track service readiness and receiver registration
    private var mServiceBound = false
    private var mServiceActive = false
    private var mReceiverRegistered = false

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

    private val mGattUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            Log.d(TAG, "=== BROADCAST RECEIVED: $action ===")

            when (action) {
                BluetoothLeService.ACTION_GATT_CONNECTED -> {
                    Log.i(TAG, "GATT Connected")
                }
                BluetoothLeService.ACTION_GATT_DISCONNECTED -> {
                    Log.i(TAG, "GATT Disconnected")
                }
                BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED -> {
                    Log.i(TAG, "!!! GATT Services Discovered - Setting mArrived = 0 !!!")
                    mArrived = 0  // THIS IS THE KEY FLAG
                    Log.i(TAG, "mArrived is now: ${0}")
                }
                BluetoothLeService.ACTION_DATA_AVAILABLE -> {
                    val data = intent.getByteArrayExtra(BluetoothLeService.EXTRA_DATA)
                    if (data != null) {
                        mData = data
                        mArrived++
                        Log.d(TAG, "Data received: ${data.size} bytes, mArrived=$mArrived")
                    }
                }
                else -> {
                    Log.w(TAG, "Unknown action received: $action")
                }
            }
        }
    }

    private val mServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(componentName: ComponentName, service: IBinder) {
            mBluetoothLeService = (service as BluetoothLeService.LocalBinder).service
            Log.i(TAG, "Success to initialize Bluetooth service.")
            mServiceBound = true  // FIX: Mark as bound
            mServiceActive = true
        }

        override fun onServiceDisconnected(componentName: ComponentName) {
            mBluetoothLeService = null
            mServiceBound = false
            mServiceActive = false
            Log.i(TAG, "onServiceDisconnected")
        }
    }

    /**
     * FIX: Initialize must complete BEFORE registration starts
     * CRITICAL: Register receiver FIRST, then bind service
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    suspend fun initializeDLMS(context: Context, meter: Meter) {
        mContext = context
        dlms = DLMS(context)

        dlms?.Password(meter.key, 1)
        dlms?.writeAddress(meter.logical, 1)
        dlms?.writeRank(String.format("%02x", meter.rank), 1)

        // FIX: Register receiver FIRST, before any BLE operations
        if (!mReceiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(BluetoothLeService.ACTION_GATT_CONNECTED)
                addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED)
                addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED)
                addAction(BluetoothLeService.ACTION_DATA_AVAILABLE)
            }

            try {
                // CRITICAL: Use RECEIVER_EXPORTED for Android 13+
                // Service broadcasts need to reach this receiver within the same app
                context.registerReceiver(
                    mGattUpdateReceiver,
                    filter,
                    Context.RECEIVER_EXPORTED  // Allow broadcasts from our own service
                )
                mReceiverRegistered = true
                Log.i(TAG, "Broadcast receiver registered successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to register receiver: ${e.message}")
                return
            }
        }

        // Small delay to ensure receiver is ready
        delay(100)

        // Bind service and WAIT for it
        val serviceIntent = Intent(context, BluetoothLeService::class.java)
        context.bindService(serviceIntent, mServiceConnection, Context.BIND_AUTO_CREATE)

        // FIX: Wait for service to be bound (like project01 does)
        var waited = 0
        while (!mServiceBound && waited < 50) {
            delay(100)
            waited++
        }

        if (!mServiceBound) {
            appendLog("ERROR: Service binding timeout")
            return
        }

        appendLog("DLMS initialized and service bound")
    }

    /**
     * Start registration - NOW ASSUMES SERVICE IS READY
     */
    fun startRegistration(meter: Meter) {
        viewModelScope.launch {
            try {
                // FIX: Check service is actually bound
                if (dlms == null || !mServiceBound || !mServiceActive || meter.bluetoothId.isNullOrEmpty()) {
                    appendLog("ERROR: Not ready (service bound: $mServiceBound, active: $mServiceActive)")
                    return@launch
                }

                _registrationState.value = RegistrationState(isRunning = true)
                _dlmsLog.value = ""

                appendLog("Connecting to ${meter.bluetoothId}...")
                mArrived = -1  // Reset to waiting state
                Log.d(TAG, "Initial mArrived set to: $mArrived")

                if (mBluetoothLeService?.connect(meter.bluetoothId) != true) {
                    appendLog("ERROR: Failed to start connection")
                    return@launch
                }

                // Wait for SERVICES_DISCOVERED (mArrived becomes 0)
                appendLog("Waiting for service discovery...")
                var ready = false
                for (i in 0..100) {
                    delay(100)
                    Log.v(TAG, "Wait loop $i: mArrived=$mArrived")
                    if (mArrived == 0) {
                        ready = true
                        Log.i(TAG, "Service discovered! mArrived=${0} at iteration $i")
                        break
                    }
                }

                if (!ready) {
                    appendLog("ERROR: Services not discovered (timeout) - final mArrived=$mArrived")
                    Log.e(TAG, "Timeout: mArrived never became 0, final value=$mArrived")
                    return@launch
                }

                appendLog("Connected and ready")
                delay(500)

                // Continue with DLMS session establishment
                appendLog("Establishing DLMS session...")
                if (!establishSession()) {
                    appendLog("ERROR: Failed to establish DLMS session")
                    return@launch
                }
                appendLog("DLMS session established")

                delay(500)

                // Set clock
                appendLog("Setting clock...")
                if (performSetClock()) {
                    appendLog("Success to set clock")
                } else {
                    appendLog("ERROR: Failed to set clock")
                    return@launch
                }

                delay(500)

                // Demand reset
                appendLog("Calling demand reset...")
                if (performDemandReset()) {
                    appendLog("Success to call demand reset")
                } else {
                    appendLog("ERROR: Failed to call demand reset")
                    return@launch
                }

                delay(500)

                // Get billing count
                appendLog("Getting billing count...")
                if (performGetBillingCount()) {
                    appendLog("Billing count retrieved")
                } else {
                    appendLog("ERROR: Failed to get billing count")
                    return@launch
                }

                delay(500)

                // Get billing data
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

    private suspend fun establishSession(): Boolean {
        mStep = 0
        var sessionEstablished = false
        var timeout = 0

        while (!sessionEstablished && timeout < 100) {
            when (mStep) {
                0 -> {
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
                    val res = IntArray(2)
                    val challengeRequest = dlms?.Challenge(res, mData)
                    if (res[0] != 0 && challengeRequest != null) {
                        mTimer = 0
                        mArrived = 0
                        mBluetoothLeService?.write(challengeRequest)
                        mStep++
                        Log.i(TAG, "Challenge: ${challengeRequest.size}")
                    } else {
                        Log.e(TAG, "Failed challenge")
                        return false
                    }
                }
                6 -> {
                    val res = IntArray(2)
                    dlms?.Confirm(res, mData)
                    if (res[0] != 0) {
                        mStep = 0
                        sessionEstablished = true
                        Log.i(TAG, "Session established")
                    } else {
                        Log.e(TAG, "Failed confirm")
                        return false
                    }
                }
            }

            if (mStep % 2 == 1) {
                mTimer = 0
                while (mArrived == 0 && mTimer < 300) {
                    delay(10)
                    mTimer++
                }
                if (mArrived == 0) {
                    Log.e(TAG, "Timeout waiting for response at step $mStep")
                    return false
                }
                mStep++
            }

            timeout++
            delay(10)
        }

        return sessionEstablished
    }

    private suspend fun performSetClock(): Boolean {
        mDataIndex = 0
        mSel = 0
        mParameter = StringBuilder()

        val sec = dlms?.CurrentDatetimeSec()?.plus(1) ?: return false
        val rawDatetime = dlms?.SecToRawDatetime(sec) ?: return false

        mParameter.append("090c").append(rawDatetime)

        return accessData(1, DLMS.IST_DATETIME_NOW, 2, false)
    }

    private suspend fun performDemandReset(): Boolean {
        mDataIndex = 0
        mParameter = StringBuilder("120001")
        // FIX: Use IST_DEMAND_RESET (88) instead of 1
        return accessData(2, DLMS.IST_DEMAND_RESET, 1, false)
    }

    private suspend fun performGetBillingCount(): Boolean {
        mDataIndex = 0
        mSel = 0
        mParameter = StringBuilder()
        // Get billing count (attribute 7, not 2)
        return accessData(0, DLMS.IST_BILLING_PARAMS, 7, false)
    }

    private suspend fun performGetBillingData(): Boolean {
        mDataIndex = 0
        mSel = 2

        // Get the billing count from mReceive (should be from previous step)
        val billingCount = if (mReceive != null && mReceive!!.size > 1) {
            try {
                mReceive!![1].toInt()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse billing count: ${e.message}")
                1 // Default to 1
            }
        } else {
            1
        }

        // Build CounterParameter with the count
        mParameter = StringBuilder(
            String.format("020406%08x06%08x120001120000", billingCount, billingCount)
        )

        return accessData(0, DLMS.IST_BILLING_PARAMS, 2, true)
    }

    private suspend fun accessData(mode: Int, index: Int, attr: Int, modeling: Boolean): Boolean {
        mStep = 0
        var timeout = 0

        while (mStep < 2 && timeout < 100) {
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
                    }
                }
                1 -> {
                    mTimer = 0
                    while (mArrived == 0 && mTimer < 300) {
                        delay(10)
                        mTimer++
                    }

                    if (mArrived == 0) {
                        Log.e(TAG, "Timeout at access step 1")
                        return false
                    }

                    val res = IntArray(2)
                    mReceive = dlms?.DataRes(res, mData, modeling)

                    if (mReceive == null || mReceive!!.isEmpty()) {
                        Log.e(TAG, "ERROR: mReceive is null or empty")
                        mStep = 0
                        return false
                    }

                    if (res[1] < 0) {
                        Log.e(TAG, "DataRes error: ${res[1]}")
                        return false
                    }

                    if (mode > 0 && mReceive!!.size > 1) {
                        if (mReceive!![1] != "success (0)") {
                            Log.e(TAG, "Operation failed: ${mReceive!![1]}")
                            return false
                        }
                    }

                    mStep = 0
                    return true
                }
            }
            timeout++
            delay(10)
        }

        return false
    }

    fun appendLog(message: String) {
        _dlmsLog.value += "${message}\n"
        Log.d(TAG, message)
    }

    fun clearLog() {
        _dlmsLog.value = ""
    }

    fun cleanup(context: Context) {
        try {
            if (mReceiverRegistered) {
                context.unregisterReceiver(mGattUpdateReceiver)
                mReceiverRegistered = false
                Log.i(TAG, "Receiver unregistered")
            }
            mBluetoothLeService?.disconnect()
            if (mServiceBound) {
                context.unbindService(mServiceConnection)
                mServiceBound = false
                Log.i(TAG, "Service unbound")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Cleanup error: ${e.message}")
        }
    }

    override fun onCleared() {
        super.onCleared()
        mContext?.let { cleanup(it) }
    }
}