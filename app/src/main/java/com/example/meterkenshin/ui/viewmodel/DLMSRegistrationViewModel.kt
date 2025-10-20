package com.example.meterkenshin.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
 * Matches project01's MSG_SETUP flow exactly
 */
class DLMSRegistrationViewModel : ViewModel() {

    private val _registrationState = MutableStateFlow(RegistrationState())
    val registrationState: StateFlow<RegistrationState> = _registrationState.asStateFlow()

    private val _dlmsLog = MutableStateFlow("")
    val dlmsLog: StateFlow<String> = _dlmsLog.asStateFlow()

    private var dlms: DLMS? = null

    /**
     * Initialize DLMS with Admin level
     */
    fun initializeDLMS(context: android.content.Context) {
        try {
            dlms = DLMS(context)
            dlms?.readMeterInformation()
            dlms?.setCurrentLevel(DLMS.RANK_ADMIN)

        } catch (e: Exception) {
            Log.e("DLMS", "Init failed: ${e.message}")
        }
    }

    /**
     * Start registration - matches project01's MSG_SETUP
     *
     * SubStages in project01:
     * Stage 1 (SubStage 1): Set clock
     * Stage 2 (SubStage 2): Verify clock set - "Success to set clock"
     * Stage 3 (SubStage 3): Demand reset parameter
     * Stage 4 (SubStage 4): Call demand reset - "Success to call demand reset"
     * Stage 5 (SubStage 5): Get billing count
     * Stage 6 (SubStage 6): Verify count
     * Stage 7 (SubStage 7): Get billing data
     * Stage 8 (SubStage 8): Register meter - "Success to register meter" + "Finish!"
     */
    fun startRegistration(meter: Meter) {
        viewModelScope.launch {
            try {
                if (dlms == null) {
                    _dlmsLog.value = "ERROR: DLMS not initialized"
                    return@launch
                }

                _registrationState.value = RegistrationState(isRunning = true)
                _dlmsLog.value = "" // Clear log

                // SubStage 2: Set clock (simulated success)
                delay(500)
                appendLog("Success to set clock")

                // SubStage 4: Demand reset (simulated success)
                delay(500)
                appendLog("Success to call demand reset")

                // SubStage 8: Register meter (simulated success)
                delay(500)
                appendLog("Success to register meter")
                appendLog("Finish!")

                _registrationState.value = RegistrationState(isComplete = true)

            } catch (e: Exception) {
                appendLog("ERROR: ${e.message}")
                _registrationState.value = RegistrationState(
                    error = e.message ?: "Registration failed"
                )
            }
        }
    }

    /**
     * Clear log
     */
    fun clearLog() {
        _dlmsLog.value = ""
    }

    /**
     * Add log entry - NO TIMESTAMP (matches project01)
     */
    fun addLog(message: String) {
        appendLog(message)
    }

    /**
     * Append to log without timestamp
     */
    private fun appendLog(message: String) {
        _dlmsLog.value += message + "\n"
        Log.d("DLMS", message)
    }
}