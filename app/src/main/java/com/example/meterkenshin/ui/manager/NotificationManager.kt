package com.example.meterkenshin.ui.manager

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Universal Notification Manager
 * Singleton to manage app-wide notification pop-ups
 * Replaces Toast messages with consistent notification bars
 */
object NotificationManager {

    private val _notification = MutableStateFlow<Notification?>(null)
    val notification: StateFlow<Notification?> = _notification.asStateFlow()

    /**
     * Show success notification
     */
    fun showSuccess(message: String, duration: NotificationDuration = NotificationDuration.SHORT) {
        _notification.value = Notification(
            message = message,
            type = NotificationType.SUCCESS,
            duration = duration
        )
    }

    /**
     * Show error notification
     */
    fun showError(message: String, duration: NotificationDuration = NotificationDuration.LONG) {
        _notification.value = Notification(
            message = message,
            type = NotificationType.ERROR,
            duration = duration
        )
    }

    /**
     * Show info notification
     */
    fun showInfo(message: String, duration: NotificationDuration = NotificationDuration.SHORT) {
        _notification.value = Notification(
            message = message,
            type = NotificationType.INFO,
            duration = duration
        )
    }

    /**
     * Show warning notification
     */
    fun showWarning(message: String, duration: NotificationDuration = NotificationDuration.MEDIUM) {
        _notification.value = Notification(
            message = message,
            type = NotificationType.WARNING,
            duration = duration
        )
    }

    /**
     * Clear current notification
     */
    fun clear() {
        _notification.value = null
    }
}

/**
 * Notification data class
 */
data class Notification(
    val message: String,
    val type: NotificationType,
    val duration: NotificationDuration
)

/**
 * Notification types with visual styling
 */
enum class NotificationType {
    SUCCESS,    // Green
    ERROR,      // Red
    INFO,       // Blue
    WARNING     // Orange/Yellow
}

/**
 * Notification duration
 */
enum class NotificationDuration(val milliseconds: Long) {
    SHORT(2000L),
    MEDIUM(3500L),
    LONG(5000L)
}