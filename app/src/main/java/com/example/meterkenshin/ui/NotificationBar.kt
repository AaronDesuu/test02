package com.example.meterkenshin.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.meterkenshin.ui.manager.Notification
import com.example.meterkenshin.ui.manager.NotificationType
import kotlinx.coroutines.delay

/**
 * Universal Notification Bar Component
 * Displays app-wide notifications at top or bottom of screen based on fromTop parameter
 * Usage: Add NotificationBar() to your screen's root Box/Scaffold
 */
@Composable
fun NotificationBar(
    modifier: Modifier = Modifier,
    notification: Notification?,
    onDismiss: () -> Unit,
    fromTop: Boolean = false,
) {
    var visible by remember(notification) { mutableStateOf(notification != null) }

    // Auto-dismiss after duration
    LaunchedEffect(notification) {
        if (notification != null) {
            visible = true
            delay(notification.duration.milliseconds)
            visible = false
            delay(300) // Wait for animation to complete
            onDismiss()
        }
    }

    AnimatedVisibility(
        visible = visible && notification != null,
        enter = slideInVertically(
            initialOffsetY = { if (fromTop) -it else it },
            animationSpec = tween(300)
        ) + fadeIn(animationSpec = tween(300)),
        exit = slideOutVertically(
            targetOffsetY = { if (fromTop) -it else it },
            animationSpec = tween(300)
        ) + fadeOut(animationSpec = tween(300)),
        modifier = modifier
            .fillMaxWidth()
            .zIndex(999f)
    ) {
        notification?.let {
            NotificationContent(notification = it)
        }
    }
}

/**
 * Notification content with icon and message
 */
@Composable
private fun NotificationContent(notification: Notification) {
    val (backgroundColor, iconColor, icon) = getNotificationStyle(notification.type)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(12.dp),
        shadowElevation = 8.dp
    ) {
        Box(
            modifier = Modifier
                .background(backgroundColor)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = notification.type.name,
                    tint = iconColor,
                    modifier = Modifier.padding(end = 12.dp)
                )

                Text(
                    text = notification.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * Get notification styling based on type
 */
@Composable
private fun getNotificationStyle(type: NotificationType): Triple<Color, Color, ImageVector> {
    return when (type) {
        NotificationType.SUCCESS -> Triple(
            Color(0xFF388E3C), // Green
            Color.White,
            Icons.Default.CheckCircle
        )
        NotificationType.ERROR -> Triple(
            Color(0xFFD32F2F), // Red
            Color.White,
            Icons.Default.Error
        )
        NotificationType.INFO -> Triple(
            Color(0xFF1976D2), // Blue
            Color.White,
            Icons.Default.Info
        )
        NotificationType.WARNING -> Triple(
            Color(0xFFF57C00), // Orange
            Color.White,
            Icons.Default.Warning
        )
    }
}