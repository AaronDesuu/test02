package com.example.meterkenshin.data


data class RegistrationState(
    val isRunning: Boolean = false,
    val currentStage: Int = 0,
    val message: String = "",
    val isComplete: Boolean = false,
    val error: String? = null
)
