package com.example.meterkenshin.dlms

import android.annotation.SuppressLint
import android.util.Log
import kotlinx.coroutines.delay

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