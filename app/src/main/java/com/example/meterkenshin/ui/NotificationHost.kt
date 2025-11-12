package com.example.meterkenshin.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.meterkenshin.ui.manager.NotificationManager

/**
 * NotificationHost - Wrapper for screens with notification support
 * Positions notifications at the bottom of the screen
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
    val notification by NotificationManager.notification.collectAsState()

    Box(modifier = modifier.fillMaxSize()) {
        // Screen content
        content()

        // Notification bar at bottom
        NotificationBar(
            notification = notification,
            onDismiss = { NotificationManager.clear() },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
        )
    }
}