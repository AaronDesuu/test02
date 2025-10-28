package com.example.meterkenshin.dlms

import android.annotation.SuppressLint
import android.util.Log
import kotlinx.coroutines.delay

@Suppress("KotlinConstantConditions")
@SuppressLint("MissingPermission")
class DLMSSessionManager(
    private val dlmsInit: DLMSInit
) {
    companion object {
        private const val TAG = "DLMSSessionManager"
        private const val MAX_TIMEOUT = 100
        private const val RESPONSE_TIMEOUT = 300
    }

    private var mStep = 0
    private var mTimer = 0

    suspend fun establishSession(): Boolean {
        mStep = 0
        var sessionEstablished = false
        var timeout = 0

        while (!sessionEstablished && timeout < MAX_TIMEOUT) {
            when (mStep) {
                0 -> handleOpen()
                2 -> if (!handleSession()) return false
                4 -> if (!handleChallenge()) return false
                6 -> {
                    sessionEstablished = handleConfirm()
                    if (!sessionEstablished) return false
                }
                1, 3, 5 -> if (!waitForResponse()) return false
            }
            timeout++
            delay(10)
        }

        return sessionEstablished
    }

    /**
     * Release DLMS session using 3-step closing sequence:
     * Step 0: Send Release request (RLRQ)
     * Step 2: Send Close frame
     * Step 4: Send Finish and validate
     */
    suspend fun releaseSession(): Boolean {
        mStep = 0
        var sessionReleased = false
        var timeout = 0

        Log.i(TAG, "Starting session release sequence...")

        while (!sessionReleased && timeout < MAX_TIMEOUT) {
            when (mStep) {
                0 -> if (!handleRelease()) return false
                2 -> if (!handleClose()) return false
                4 -> {
                    sessionReleased = handleFinish()
                    if (!sessionReleased) return false
                }
                1, 3 -> if (!waitForResponse()) return false
            }
            timeout++
            delay(10)
        }

        return sessionReleased
    }

    private fun handleOpen() {
        dlmsInit.dlms?.Open()?.let { openRequest ->
            mTimer = 0
            dlmsInit.mArrived = 0
            dlmsInit.bluetoothLeService?.write(openRequest)
            mStep++
            Log.i(TAG, "Open: ${openRequest.size}")
        }
    }

    private fun handleSession(): Boolean {
        val res = IntArray(2)
        val sessionRequest = dlmsInit.dlms?.Session(res, dlmsInit.mData)
        return if (res[0] != 0 && sessionRequest != null) {
            mTimer = 0
            dlmsInit.mArrived = 0
            dlmsInit.bluetoothLeService?.write(sessionRequest)
            mStep++
            Log.i(TAG, "Session: ${sessionRequest.size}")
            true
        } else {
            Log.e(TAG, "Failed to connect HDLC")
            false
        }
    }

    private fun handleChallenge(): Boolean {
        val res = IntArray(2)
        val challengeRequest = dlmsInit.dlms?.Challenge(res, dlmsInit.mData)
        return if (res[0] != 0 && challengeRequest != null) {
            mTimer = 0
            dlmsInit.mArrived = 0
            dlmsInit.bluetoothLeService?.write(challengeRequest)
            mStep++
            Log.i(TAG, "Challenge: ${challengeRequest.size}")
            true
        } else {
            Log.e(TAG, "Failed challenge")
            false
        }
    }

    private fun handleConfirm(): Boolean {
        val res = IntArray(2)
        dlmsInit.dlms?.Confirm(res, dlmsInit.mData)
        return if (res[0] != 0) {
            Log.i(TAG, "Session established!")
            true
        } else {
            Log.e(TAG, "Failed confirm")
            false
        }
    }

    /**
     * Step 1: Send Release request (RLRQ frame)
     */
    private fun handleRelease(): Boolean {
        val releaseRequest = dlmsInit.dlms?.Release()
        return if (releaseRequest != null) {
            mTimer = 0
            dlmsInit.mArrived = 0
            dlmsInit.bluetoothLeService?.write(releaseRequest)
            mStep++
            Log.i(TAG, "Release: ${releaseRequest.size}")
            true
        } else {
            Log.e(TAG, "Failed to create Release request")
            false
        }
    }

    /**
     * Step 2: Send Close frame (validates release response first)
     */
    private fun handleClose(): Boolean {
        val res = IntArray(2)
        val closeRequest = dlmsInit.dlms?.Close(res, dlmsInit.mData)
        return if (res[0] != 0 && closeRequest != null) {
            mTimer = 0
            dlmsInit.mArrived = 0
            dlmsInit.bluetoothLeService?.write(closeRequest)
            mStep++
            Log.i(TAG, "Close: ${closeRequest.size}")
            true
        } else {
            Log.e(TAG, "Failed to create Close request (res[0]=${res[0]}, res[1]=${res[1]})")
            false
        }
    }

    /**
     * Step 3: Finish - validate final response
     */
    private fun handleFinish(): Boolean {
        val res = IntArray(2)
        dlmsInit.dlms?.Finish(res, dlmsInit.mData)
        return if (res[0] != 0) {
            Log.i(TAG, "Session released successfully")
            true
        } else {
            Log.e(TAG, "Failed to finish session release (res[0]=${res[0]}, res[1]=${res[1]})")
            false
        }
    }

    private suspend fun waitForResponse(): Boolean {
        mTimer = 0
        while (dlmsInit.mArrived == 0 && mTimer < RESPONSE_TIMEOUT) {
            delay(10)
            mTimer++
        }
        return if (dlmsInit.mArrived == 0) {
            Log.e(TAG, "Timeout at step $mStep")
            false
        } else {
            mStep++
            true
        }
    }
}