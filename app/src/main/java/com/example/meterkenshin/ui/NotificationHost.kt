package com.example.meterkenshin.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.meterkenshin.ui.manager.AppPreferences
import com.example.meterkenshin.ui.manager.NotificationManager

/**
 * NotificationHost - Wrapper for screens with notification support
 * Positions notifications at the top or bottom of the screen based on user preference
 * Fixed for Android 11 with proper system bar insets handling
 * Now reactive to preference changes
 */
@Composable
fun NotificationHost(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val notification by NotificationManager.notification.collectAsState()
    val fromTop by AppPreferences.notificationFromTop.collectAsState()

    LaunchedEffect(Unit) {
        AppPreferences.initializePreferences(context)
    }

    Box(modifier = modifier.fillMaxSize()) {
        content()

        NotificationBar(
            notification = notification,
            onDismiss = { NotificationManager.clear() },
            fromTop = fromTop,
            modifier = Modifier
                .align(if (fromTop) Alignment.TopCenter else Alignment.BottomCenter)
                .then(
                    if (fromTop) {
                        Modifier.windowInsetsPadding(WindowInsets.systemBars)
                    } else {
                        Modifier
                            .windowInsetsPadding(WindowInsets.navigationBars)
                            .padding(bottom = 16.dp)
                    }
                )
        )
    }
}