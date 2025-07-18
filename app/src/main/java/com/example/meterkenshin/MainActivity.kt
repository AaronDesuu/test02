package com.example.meterkenshin

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.example.meterkenshin.manager.SessionManager
import com.example.meterkenshin.permissions.BluetoothPermissionHandler
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

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var isBluetoothPermissionRequested = false

    // Bluetooth permission launcher
    private val bluetoothPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            showToast("Bluetooth permissions granted")
            enableBluetoothIfNeeded()
        } else {
            showToast("Bluetooth permissions are required for printer functionality")
        }
    }

    // Bluetooth enable launcher
    private val bluetoothEnableLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            showToast("Bluetooth enabled successfully")
        } else {
            showToast("Bluetooth is required for printer functionality")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Install splash screen
        val splashScreen = installSplashScreen()

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize session manager
        sessionManager = SessionManager.getInstance(this)

        // Initialize Bluetooth
        initializeBluetooth()

        // Check and request permissions
        checkBluetoothPermissions()

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

    private fun initializeBluetooth() {
        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        if (bluetoothAdapter == null) {
            showToast("Bluetooth is not supported on this device")
            return
        }
    }

    private fun checkBluetoothPermissions() {
        if (isBluetoothPermissionRequested) return

        val missingPermissions = BluetoothPermissionHandler.getMissingBluetoothPermissions(this)

        if (missingPermissions.isNotEmpty()) {
            isBluetoothPermissionRequested = true
            bluetoothPermissionLauncher.launch(missingPermissions.toTypedArray())
        } else {
            // Permissions already granted, check if Bluetooth is enabled
            enableBluetoothIfNeeded()
        }
    }

    private fun enableBluetoothIfNeeded() {
        bluetoothAdapter?.let { adapter ->
            if (!adapter.isEnabled) {
                val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                bluetoothEnableLauncher.launch(enableBtIntent)
            }
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onResume() {
        super.onResume()
        // Re-check permissions when returning to the app
        if (!isBluetoothPermissionRequested) {
            checkBluetoothPermissions()
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
    var selectedMeter by remember { mutableStateOf<Meter?>(null) }

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
                    onNavigateToMeterDetail = { meter ->
                        selectedMeter = meter
                        currentScreen = "meter_detail"
                    }
                )
            }
            currentScreen == "meter_detail" && selectedMeter != null -> {
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