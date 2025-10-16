package com.example.meterkenshin.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.meterkenshin.dlms.DLMS
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DLMSState(
    val isRegistering: Boolean = false,
    val isRegistered: Boolean = false,
    val registrationMessage: String = "",
    val error: String? = null
)

class DLMSViewModel(private val context: Context) : ViewModel() {

    private val _dlmsState = MutableStateFlow(DLMSState())
    val dlmsState: StateFlow<DLMSState> = _dlmsState.asStateFlow()

    private lateinit var dlms: DLMS

    init {
        dlms = DLMS(context)
    }

    fun performRegistration(bleService: Any?, bluetoothId: String) {
        viewModelScope.launch {
            try {
                _dlmsState.value = _dlmsState.value.copy(
                    isRegistering = true,
                    registrationMessage = "Starting registration...",
                    error = null
                )

                // Step 1: Open HDLC connection
                _dlmsState.value = _dlmsState.value.copy(
                    registrationMessage = "Opening connection..."
                )
                val openCmd = dlms.Open()
                // TODO: Send via BLE - sendToBLE(openCmd)
                // TODO: Receive response - val response = receiveFromBLE()

                // Step 2: Establish session (AARQ)
                _dlmsState.value = _dlmsState.value.copy(
                    registrationMessage = "Establishing session..."
                )
                val ret = intArrayOf(0, 0)
                // val sessionCmd = dlms.Session(ret, bleResponse)
                // TODO: Send via BLE
                // TODO: Receive response

                // Step 3: Authenticate (AARE)
                _dlmsState.value = _dlmsState.value.copy(
                    registrationMessage = "Authenticating..."
                )
                // val authResult = dlms.Authentication(ret, bleResponse)

                if (ret[0] == 1) {
                    // Step 4: Confirm HLS
                    _dlmsState.value = _dlmsState.value.copy(
                        registrationMessage = "Confirming HLS..."
                    )
                    // val confirmCmd = authResult
                    // TODO: Send via BLE
                    // TODO: dlms.Confirm(ret, bleResponse)

                    if (ret[0] == 1) {
                        _dlmsState.value = _dlmsState.value.copy(
                            isRegistering = false,
                            isRegistered = true,
                            registrationMessage = "Registration successful!"
                        )
                    } else {
                        throw Exception("HLS confirmation failed")
                    }
                } else {
                    throw Exception("Authentication failed")
                }

            } catch (e: Exception) {
                _dlmsState.value = _dlmsState.value.copy(
                    isRegistering = false,
                    isRegistered = false,
                    registrationMessage = "",
                    error = e.message ?: "Registration failed"
                )
            }
        }
    }

    fun resetRegistration() {
        _dlmsState.value = DLMSState()
    }
}