package com.example.meterkenshin.dlms

import android.annotation.SuppressLint
import android.util.Log
import kotlinx.coroutines.delay

/**
 * DLMS Data Access Handler
 * Extracted from DLMSRegistrationViewModel for better modularity and reusability
 */
class DLMSDataAccess(private val dlmsInitializer: DLMSInit) {

    companion object {
        private const val TAG = "DLMSDataAccess"
    }

    // DLMS operation variables
    private var mStep = 0
    private var mTimer = 0
    private var mSel: Byte = 0
    private var mDataIndex: Byte = 0
    private var mParameter = StringBuilder()
    private var mReceive: ArrayList<String>? = null
    private var continueBlockTransfer = false


    fun shouldContinueBlockTransfer(): Boolean {
        return continueBlockTransfer
    }
    /**
     * Generic DLMS data access method
     */
    @SuppressLint("MissingPermission")
    suspend fun accessData(mode: Int, obj: Int, mth: Int, modeling: Boolean): Boolean {
        mStep = 0
        var timeout = 0

        while (mStep < 2 && timeout < 100) {
            when (mStep) {
                0 -> {
                    val para = mParameter.toString()
                    val data = when (mode) {
                        0 -> {
                            Log.i(TAG, "Getting index:$obj, attr:$mth")
                            dlmsInitializer.dlms?.getReq(obj, mth.toByte(), mSel, para, mDataIndex)
                        }
                        1 -> {
                            Log.i(TAG, "Setting index:$obj, attr:$mth")
                            dlmsInitializer.dlms?.setReq(obj, mth.toByte(), mSel, para, mDataIndex)
                        }
                        2 -> {
                            Log.i(TAG, "Calling index:$obj, attr:$mth")
                            dlmsInitializer.dlms?.actReq(obj, mth.toByte(), para, mDataIndex)
                        }
                        else -> null
                    }

                    if (data != null) {
                        mTimer = 0
                        dlmsInitializer.mArrived = 0
                        dlmsInitializer.bluetoothLeService?.write(data)
                        mStep++
                    }
                }
                1 -> {
                    mTimer = 0
                    while (dlmsInitializer.mArrived == 0 && mTimer < 300) {
                        delay(10)
                        mTimer++
                    }

                    if (dlmsInitializer.mArrived == 0) {
                        Log.e(TAG, "Timeout waiting for data response")
                        continueBlockTransfer = false  // ADDED: Reset on timeout
                        return false
                    }

                    val res = IntArray(2)
                    mReceive = dlmsInitializer.dlms?.DataRes(res, dlmsInitializer.mData, modeling)

                    if (mReceive == null || mReceive!!.isEmpty()) {
                        Log.e(TAG, "ERROR: mReceive is null or empty")
                        continueBlockTransfer = false  // ADDED: Reset on error
                        mStep = 0
                        return false
                    }

                    if (res[1] < 0) {
                        Log.e(TAG, "DataRes error: ${res[1]}")
                        continueBlockTransfer = false  // ADDED: Reset on error
                        return false
                    }

                    if (mode > 0 && mReceive!!.size > 1) {
                        if (mReceive!![1] != "success (0)") {
                            Log.e(TAG, "Operation failed: ${mReceive!![1]}")
                            continueBlockTransfer = false  // ADDED: Reset on operation failure
                            return false
                        }
                    }

                    // ADDED: Check block transfer status
                    // res[0] = 2 means "continue" (more blocks available)
                    // res[0] = 0 means "complete" (last block received)
                    continueBlockTransfer = (res[0] == 2)

                    if (continueBlockTransfer) {
                        Log.d(TAG, "Block transfer continues (res[0]=2, more data available)")
                    } else {
                        Log.d(TAG, "Block transfer complete (res[0]=${res[0]})")
                    }

                    mStep = 0
                    return true
                }
            }
            timeout++
            delay(10)
        }

        continueBlockTransfer = false  // ADDED: Reset on timeout
        return false
    }

    /**
     * Get the received data
     */
    fun getReceive(): ArrayList<String>? {
        return mReceive
    }

    /**
     * Set parameter for next access
     */
    fun setParameter(parameter: String) {
        mParameter = StringBuilder(parameter)
    }

    /**
     * Set selector for next access
     */
    fun setSelector(selector: Byte) {
        mSel = selector
    }

    /**
     * Set data index for next access
     */
    fun setDataIndex(dataIndex: Byte) {
        mDataIndex = dataIndex
    }

    /**
     * Reset all parameters
     */
    fun reset() {
        mStep = 0
        mTimer = 0
        mSel = 0
        mDataIndex = 0
        mParameter = StringBuilder()
        mReceive = null
    }
}