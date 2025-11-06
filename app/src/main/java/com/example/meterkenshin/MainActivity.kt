package com.example.meterkenshin

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import com.example.meterkenshin.ui.notification.NotificationManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.example.meterkenshin.manager.SessionManager
import com.example.meterkenshin.permissions.BluetoothPermissionHandler
import com.example.meterkenshin.ui.theme.MeterKenshinTheme
import com.example.meterkenshin.ui.viewmodel.PrinterBluetoothViewModel
import com.example.meterkenshin.ui.viewmodel.FileUploadViewModel
import com.example.meterkenshin.ui.viewmodel.MeterReadingViewModel
import kotlinx.coroutines.launch
import com.example.meterkenshin.printer.BluetoothPrinterManager as CustomBluetoothManager

class MainActivity : ComponentActivity() {

    private lateinit var sessionManager: SessionManager
    private val fileUploadViewModel: FileUploadViewModel by viewModels()
    private val meterReadingViewModel: MeterReadingViewModel by viewModels()
    private val printerBluetoothViewModel: PrinterBluetoothViewModel by viewModels()

    private var bluetoothAdapter: BluetoothAdapter? = null
    private lateinit var bluetoothPermissionHandler: BluetoothPermissionHandler
    private var bluetoothPrinterManager: CustomBluetoothManager? = null

    // Track if BLE operations are started
    private var bleOperationsStarted = false

    // Activity result launcher for Bluetooth enable request
    private val enableBluetoothLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            NotificationManager.showSuccess("Bluetooth enabled")
            initializeBluetoothConnection()
            // Start BLE scanning after Bluetooth enabled
            startBLEScanningIfLoggedIn()
        }
    }

    // Permission request launcher
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            initializeBluetoothConnection()
            // Start BLE scanning after permissions granted
            startBLEScanningIfLoggedIn()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize session manager
        sessionManager = SessionManager.getInstance(this)

        // Initialize Bluetooth
        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
        bluetoothPermissionHandler = BluetoothPermissionHandler(this)

        // Initialize MeterReadingViewModel
        meterReadingViewModel.initialize(this)

        // Check and request permissions
        checkAndRequestPermissions()

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
                        printerBluetoothViewModel = printerBluetoothViewModel
                    )
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    override fun onResume() {
        super.onResume()
        // Check if user is logged in and start BLE operations
        if (sessionManager.isLoggedIn()) {
            startBLEOperationsIfNeeded()
            startBLEScanningIfLoggedIn()
            meterReadingViewModel.startPeriodicScanning()
        }
    }

    @SuppressLint("MissingPermission")
    override fun onPause() {
        super.onPause()
        // Stop BLE scanning when app goes to background
        meterReadingViewModel.stopBLEScanning()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Stop BLE operations when activity destroyed
        if (bleOperationsStarted) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) ==
                        android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        meterReadingViewModel.stopBLEOperations(this)
                    }
                } else {
                    meterReadingViewModel.stopBLEOperations(this)
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Error stopping BLE operations", e)
            }
        }
        bluetoothPrinterManager?.cleanup()
    }

    // Start BLE operations (register receiver, bind service)
    private fun startBLEOperationsIfNeeded() {
        if (!bleOperationsStarted && bluetoothPermissionHandler.hasAllPermissions()) {
            try {
                meterReadingViewModel.startBLEOperations(this)
                bleOperationsStarted = true
                Log.i("MainActivity", "BLE operations started")
            } catch (e: Exception) {
                Log.e("MainActivity", "Error starting BLE operations", e)
            }
        }
    }

    // Start BLE scanning if user is logged in and has permissions
    @SuppressLint("MissingPermission")
    private fun startBLEScanningIfLoggedIn() {
        if (sessionManager.isLoggedIn() && bluetoothPermissionHandler.hasAllPermissions()) {
            lifecycleScope.launch {
                try {
                    // Small delay to ensure everything is initialized
                    kotlinx.coroutines.delay(500)

                    Log.i("MainActivity", "Starting automatic BLE scan after login")
                    meterReadingViewModel.startBLEScanning()

                    NotificationManager.showInfo("Scanning for nearby meters...")
                } catch (e: Exception) {
                    Log.e("MainActivity", "Error starting BLE scan", e)
                }
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val missingPermissions = bluetoothPermissionHandler.getMissingPermissions()
        if (missingPermissions.isNotEmpty()) {
            permissionLauncher.launch(missingPermissions.toTypedArray())
        } else {
            initializeBluetoothConnection()
        }
    }

    private fun initializeBluetoothConnection() {
        if (bluetoothAdapter == null) {
            NotificationManager.showError("Bluetooth not supported")
            return
        }

        if (bluetoothAdapter?.isEnabled == false) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            enableBluetoothLauncher.launch(enableBtIntent)
        } else {
            // Bluetooth is already enabled, initialize printer manager
            lifecycleScope.launch {
                try {
                    // Initialize custom Bluetooth printer manager
                    bluetoothPrinterManager = CustomBluetoothManager(this@MainActivity).apply {
                        // Initialize Bluetooth ViewModel with manager (correct method name)
                        printerBluetoothViewModel.initializeBluetoothManager(this)

                        Log.d("MainActivity", "Bluetooth printer manager initialized")
                    }
                } catch (e: Exception) {
                    Log.e("MainActivity", "Error initializing Bluetooth printer manager", e)
                }
            }
        }
    }
}