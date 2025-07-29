package com.example.meterkenshin

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.example.meterkenshin.manager.SessionManager
import com.example.meterkenshin.permissions.BluetoothPermissionHandler
import com.example.meterkenshin.ui.screen.FileUploadScreen
import com.example.meterkenshin.ui.screen.HomeScreen
import com.example.meterkenshin.ui.screen.LoginScreen
import com.example.meterkenshin.ui.screen.Meter
import com.example.meterkenshin.ui.screen.MeterDetailScreen
import com.example.meterkenshin.ui.screen.MeterReadingScreen
import com.example.meterkenshin.ui.screen.ReceiptScreen
import com.example.meterkenshin.ui.theme.MeterKenshinTheme
import com.example.meterkenshin.ui.viewmodel.BluetoothViewModel
import com.example.meterkenshin.ui.viewmodel.FileUploadViewModel
import com.example.meterkenshin.ui.viewmodel.MeterReadingViewModel
import kotlinx.coroutines.launch
import com.example.meterkenshin.bluetooth.BluetoothManager as CustomBluetoothManager

class MainActivity : ComponentActivity() {

    private lateinit var sessionManager: SessionManager
    private val fileUploadViewModel: FileUploadViewModel by viewModels()
    private val meterReadingViewModel: MeterReadingViewModel by viewModels()
    private val bluetoothViewModel: BluetoothViewModel by viewModels()

    private var bluetoothAdapter: BluetoothAdapter? = null
    private lateinit var bluetoothPermissionHandler: BluetoothPermissionHandler
    private var customBluetoothManager: CustomBluetoothManager? = null

    // Activity result launcher for Bluetooth enable request
    private val enableBluetoothLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            Toast.makeText(this, "Bluetooth enabled", Toast.LENGTH_SHORT).show()
            initializeBluetoothConnection()
        }
    }

    // Permission request launcher
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            initializeBluetoothConnection()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Install splash screen
        installSplashScreen()

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize session manager using getInstance (preserve original logic)
        sessionManager = SessionManager.getInstance(this)

        // Initialize Bluetooth components (new addition)
        initializeBluetoothComponents()

        setContent {
            MeterKenshinTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MeterKenshinApp(
                        sessionManager = sessionManager,
                        fileUploadViewModel = fileUploadViewModel,
                        meterReadingViewModel = meterReadingViewModel,
                        bluetoothViewModel = bluetoothViewModel
                    )
                }
            }
        }

        // Initialize Bluetooth after UI is set (to not block UI)
        lifecycleScope.launch {
            requestBluetoothPermissions()
        }
    }

    private fun initializeBluetoothComponents() {
        // Initialize Bluetooth adapter
        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        // Initialize permission handler
        bluetoothPermissionHandler = BluetoothPermissionHandler(this)

        // Check if Bluetooth is supported
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth is not supported on this device", Toast.LENGTH_LONG).show()
            return
        }
    }

    private fun requestBluetoothPermissions() {
        val missingPermissions = bluetoothPermissionHandler.getMissingPermissions()

        if (missingPermissions.isNotEmpty()) {
            // Request missing permissions
            permissionLauncher.launch(missingPermissions.toTypedArray())
        } else {
            // All permissions granted, check if Bluetooth is enabled
            checkBluetoothEnabled()
        }
    }

    private fun checkBluetoothEnabled() {
        bluetoothAdapter?.let { adapter ->
            if (!adapter.isEnabled) {
                // Request to enable Bluetooth
                val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                enableBluetoothLauncher.launch(enableBtIntent)
            } else {
                // Bluetooth is enabled, initialize connection
                initializeBluetoothConnection()
            }
        }
    }

    private fun initializeBluetoothConnection() {
        // Check if all permissions are granted
        if (!bluetoothPermissionHandler.hasAllPermissions()) {
            return
        }

        // Initialize custom Bluetooth manager
        customBluetoothManager = CustomBluetoothManager(this).apply {
            // Initialize Bluetooth ViewModel with manager
            bluetoothViewModel.initializeBluetoothManager(this)

            // Start automatic connection using hardcoded MAC address
            lifecycleScope.launch {
                // Use the specific MAC address: 1C:B8:57:50:01:D9
                connectToSpecificDevice("1C:B8:57:50:01:D9")
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Check Bluetooth connection when app resumes (non-blocking)
        customBluetoothManager?.let { manager ->
            if (bluetoothPermissionHandler.hasAllPermissions() &&
                bluetoothAdapter?.isEnabled == true &&
                !manager.isConnected()) {

                lifecycleScope.launch {
                    manager.connectToSpecificDevice("1C:B8:57:50:01:D9")
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Clean up Bluetooth connection
        customBluetoothManager?.cleanup()
    }
}

@Composable
fun MeterKenshinApp(
    sessionManager: SessionManager,
    fileUploadViewModel: FileUploadViewModel,
    meterReadingViewModel: MeterReadingViewModel,
    bluetoothViewModel: BluetoothViewModel
) {
    val context = LocalContext.current

    // Use the original session checking logic
    var isLoggedIn by remember { mutableStateOf(sessionManager.isLoggedIn()) }
    var currentScreen by remember { mutableStateOf("home") }
    var selectedMeter by remember { mutableStateOf<Meter?>(null) }

    // Check login state immediately on app start (preserve original logic)
    LaunchedEffect(Unit) {
        isLoggedIn = sessionManager.isLoggedIn()
        if (isLoggedIn) {
            currentScreen = "home"
            // Initialize file checking after login check (preserve original behavior)
            fileUploadViewModel.checkExistingFiles(context)
        } else {
            currentScreen = "login"
        }
    }

    when {
        !isLoggedIn || currentScreen == "login" -> {
            LoginScreen(
                sessionManager = sessionManager,
                onLoginSuccess = {
                    isLoggedIn = true
                    currentScreen = "home"
                    // Initialize file checking after successful login (preserve original)
                    fileUploadViewModel.checkExistingFiles(context)
                }
            )
        }
        currentScreen == "home" -> {
            HomeScreen(
                sessionManager = sessionManager,
                onLogout = {
                    sessionManager.logout()
                    isLoggedIn = false
                    currentScreen = "login"
                },
                onNavigateToFileUpload = { currentScreen = "file_upload" },
                onNavigateToReceiptTemplate = { currentScreen = "receipt" }, // ADD THIS LINE
                onNavigateToMeterReading = { currentScreen = "meter_reading" },
                fileUploadViewModel = fileUploadViewModel,
                meterReadingViewModel = meterReadingViewModel,
                bluetoothViewModel = bluetoothViewModel
            )
        }
        currentScreen == "file_upload" -> {
            FileUploadScreen(
                viewModel = fileUploadViewModel,
                onUploadComplete = {
                    // Refresh file data after upload
                    fileUploadViewModel.checkExistingFiles(context)
                    currentScreen = "home"
                },
                onBackPressed = { currentScreen = "home" }
            )
        }
        currentScreen == "meter_reading" -> {
            MeterReadingScreen(
                fileUploadViewModel = fileUploadViewModel,
                meterReadingViewModel = meterReadingViewModel,
                onBackPressed = { currentScreen = "home" },
                onNavigateToFileUpload = { currentScreen = "file_upload" },
                onNavigateToMeterDetail = { meter ->
                    selectedMeter = meter
                    currentScreen = "meter_detail"
                }
            )
        }
        currentScreen == "meter_detail" -> {
            selectedMeter?.let { meter ->
                MeterDetailScreen(
                    meter = meter,
                    onBackPressed = { currentScreen = "meter_reading" }
                )
            }
        }
        currentScreen == "receipt" -> {
            ReceiptScreen(
                fileUploadViewModel = fileUploadViewModel,
                bluetoothViewModel = bluetoothViewModel,
                onBackPressed = { currentScreen = "home" }
            )
        }
    }
}