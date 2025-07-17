package com.example.meterkenshin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.example.meterkenshin.manager.SessionManager
import com.example.meterkenshin.ui.screen.HomeScreen
import com.example.meterkenshin.ui.screen.LoginScreen
import com.example.meterkenshin.ui.screen.FileUploadScreen
import com.example.meterkenshin.ui.screen.ReceiptScreen
import com.example.meterkenshin.ui.screen.MeterReadingScreen
import com.example.meterkenshin.ui.screen.MeterDetailScreen
import com.example.meterkenshin.ui.screen.Meter
import com.example.meterkenshin.ui.theme.MeterKenshinTheme
import com.example.meterkenshin.ui.viewmodel.FileUploadViewModel
import com.example.meterkenshin.ui.viewmodel.MeterReadingViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var sessionManager: SessionManager
    private val fileUploadViewModel: FileUploadViewModel by viewModels()
    private val meterReadingViewModel: MeterReadingViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // Install splash screen
        val splashScreen = installSplashScreen()

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize session manager
        sessionManager = SessionManager.getInstance(this)

        // Initialize file checking on app start (only if logged in)
        lifecycleScope.launch {
            if (sessionManager.isLoggedIn()) {
                fileUploadViewModel.checkExistingFiles(this@MainActivity)
            }
        }

        setContent {
            MeterKenshinTheme {
                MeterKenshinApp(
                    sessionManager = sessionManager,
                    fileUploadViewModel = fileUploadViewModel,
                    meterReadingViewModel = meterReadingViewModel
                )
            }
        }
    }
}

@Composable
fun MeterKenshinApp(
    sessionManager: SessionManager,
    fileUploadViewModel: FileUploadViewModel,
    meterReadingViewModel: MeterReadingViewModel
) {
    val context = LocalContext.current
    var isLoggedIn by remember { mutableStateOf(sessionManager.isLoggedIn()) }
    var currentScreen by remember { mutableStateOf("home") }
    var selectedMeter by remember { mutableStateOf<Meter?>(null) } // Add state for selected meter

    // Check login state on app start
    LaunchedEffect(Unit) {
        isLoggedIn = sessionManager.isLoggedIn()
        if (isLoggedIn) {
            currentScreen = "home"
        } else {
            currentScreen = "login"
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        when {
            !isLoggedIn || currentScreen == "login" -> {
                LoginScreen(
                    sessionManager = sessionManager,
                    onLoginSuccess = {
                        isLoggedIn = true
                        currentScreen = "home"

                        // Initialize file checking after successful login
                        fileUploadViewModel.checkExistingFiles(context)
                    }
                )
            }
            currentScreen == "home" -> {
                HomeScreen(
                    sessionManager = sessionManager,
                    onLogout = {
                        isLoggedIn = false
                        currentScreen = "login"
                    },
                    onNavigateToFileUpload = {
                        currentScreen = "file_upload"
                    },
                    onNavigateToReceiptTemplate = {
                        currentScreen = "receipt"
                    },
                    onNavigateToMeterReading = {
                        currentScreen = "meter_reading"
                    },
                    fileUploadViewModel = fileUploadViewModel,
                    meterReadingViewModel = meterReadingViewModel
                )
            }
            currentScreen == "file_upload" -> {
                FileUploadScreen(
                    onBackPressed = {
                        currentScreen = "home"
                    }
                )
            }
            currentScreen == "receipt" -> {
                ReceiptScreen(
                    fileUploadViewModel = fileUploadViewModel,
                    onBackPressed = {
                        currentScreen = "home"
                    },
                    onNavigateToFileUpload = {
                        currentScreen = "file_upload"
                    }
                )
            }
            currentScreen == "meter_reading" -> {
                MeterReadingScreen(
                    fileUploadViewModel = fileUploadViewModel,
                    meterReadingViewModel = meterReadingViewModel,
                    onBackPressed = {
                        currentScreen = "home"
                    },
                    onNavigateToFileUpload = {
                        currentScreen = "file_upload"
                    },
                    onNavigateToMeterDetail = { meter -> // Add navigation to meter detail
                        selectedMeter = meter
                        currentScreen = "meter_detail"
                    }
                )
            }
            currentScreen == "meter_detail" && selectedMeter != null -> { // Add meter detail screen
                MeterDetailScreen(
                    meter = selectedMeter!!,
                    onBackPressed = {
                        selectedMeter = null
                        currentScreen = "meter_reading"
                    },
                    onRegistration = {
                        // TODO: Implement DLMS Registration function
                        // This will be connected to DLMS communication later
                    },
                    onReadData = {
                        // TODO: Implement DLMS Read Data function
                        // This will be connected to DLMS communication later
                    },
                    onLoadProfile = {
                        // TODO: Implement DLMS Load Profile function
                        // This will be connected to DLMS communication later
                    },
                    onEventLog = {
                        // TODO: Implement DLMS Event Log function
                        // This will be connected to DLMS communication later
                    },
                    onBillingData = {
                        // TODO: Implement DLMS Billing Data function
                        // This will be connected to DLMS communication later
                    },
                    onSetClock = {
                        // TODO: Implement DLMS Set Clock function
                        // This will be connected to DLMS communication later
                    }
                )
            }
        }
    }
}