package com.example.meterkenshin.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.meterkenshin.ui.manager.AppPreferences
import com.example.meterkenshin.ui.manager.NotificationManager

/**
 * NotificationHost - Wrapper for screens with notification support
 * Positions notifications at the top or bottom of the screen based on user preference
 *
 * Usage:
 * ```
 * NotificationHost {
 *     // Your screen content here
 * }
 * ```
 */
@Composable
fun NotificationHost(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val notification by NotificationManager.notification.collectAsState()
    val fromTop = remember { AppPreferences.isNotificationFromTop(context) }

    Box(modifier = modifier.fillMaxSize()) {
        // Screen content
        content()

        // Notification bar at top or bottom based on preference
        NotificationBar(
            notification = notification,
            onDismiss = { NotificationManager.clear() },
            fromTop = fromTop,
            modifier = Modifier
                .align(if (fromTop) Alignment.TopCenter else Alignment.BottomCenter)
                .padding(
                    top = if (fromTop) 56.dp else 0.dp,
                    bottom = if (fromTop) 0.dp else 16.dp,
                    start = 0.dp,
                    end = 0.dp
                )
        )
    }
}