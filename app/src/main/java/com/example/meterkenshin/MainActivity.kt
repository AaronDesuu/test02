package com.example.meterkenshin

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
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
import com.example.meterkenshin.ui.component.MeterKenshinApp
import com.example.meterkenshin.ui.theme.MeterKenshinTheme
import com.example.meterkenshin.ui.viewmodel.PrinterBluetoothViewModel
import com.example.meterkenshin.ui.viewmodel.FileUploadViewModel
import com.example.meterkenshin.ui.viewmodel.MeterReadingViewModel
import kotlinx.coroutines.launch
import com.example.meterkenshin.bluetooth.BluetoothManager as CustomBluetoothManager

class MainActivity : ComponentActivity() {

    private lateinit var sessionManager: SessionManager
    private val fileUploadViewModel: FileUploadViewModel by viewModels()
    private val meterReadingViewModel: MeterReadingViewModel by viewModels()
    private val printerBluetoothViewModel: PrinterBluetoothViewModel by viewModels()

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
                    // Your existing MeterKenshinApp now handles the screen tracking automatically
                    MeterKenshinApp(
                        sessionManager = sessionManager,
                        fileUploadViewModel = fileUploadViewModel,
                        meterReadingViewModel = meterReadingViewModel,
                        printerBluetoothViewModel = printerBluetoothViewModel
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
            printerBluetoothViewModel.initializeBluetoothManager(this)

            // DO NOT auto-connect here
            // Connection will happen when user:
            // 1. Uploads printer.csv with active printer
            // 2. Manually triggers connection from UI
            Log.d("MainActivity", "Bluetooth manager initialized. Waiting for printer.csv or manual connection.")
        }
    }

    override fun onResume() {
        super.onResume()
        // DO NOT auto-connect when app resumes
        // Let the user control when to connect
        Log.d("MainActivity", "MainActivity resumed. Bluetooth manager ready.")
    }

    override fun onDestroy() {
        super.onDestroy()
        // Clean up Bluetooth connection
        customBluetoothManager?.cleanup()
    }
}
