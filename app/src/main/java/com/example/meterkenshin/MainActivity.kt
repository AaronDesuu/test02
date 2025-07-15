package com.example.meterkenshin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.example.meterkenshin.manager.SessionManager
import com.example.meterkenshin.ui.screen.HomeScreen
import com.example.meterkenshin.ui.screen.LoginScreen
import com.example.meterkenshin.ui.theme.MeterKenshinTheme

class MainActivity : ComponentActivity() {

    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        // Install splash screen
        val splashScreen = installSplashScreen()

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize session manager
        sessionManager = SessionManager.getInstance(this)

        setContent {
            MeterKenshinTheme {
                MeterKenshinApp(sessionManager = sessionManager)
            }
        }
    }
}

@Composable
fun MeterKenshinApp(sessionManager: SessionManager) {
    var isLoggedIn by remember { mutableStateOf(sessionManager.isLoggedIn()) }
    var currentScreen by remember { mutableStateOf("home") } // Add screen state

    // Check session validity periodically
    LaunchedEffect(key1 = isLoggedIn) {
        if (isLoggedIn && !sessionManager.isLoggedIn()) {
            isLoggedIn = false
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        if (isLoggedIn) {
            when (currentScreen) {
                "home" -> HomeScreen(
                    sessionManager = sessionManager,
                    onLogout = {
                        isLoggedIn = false
                        currentScreen = "home"
                    },
                )
            }
        } else {
            LoginScreen(
                sessionManager = sessionManager,
                onLoginSuccess = {
                    isLoggedIn = true
                    currentScreen = "home"
                }
            )
        }
    }
}