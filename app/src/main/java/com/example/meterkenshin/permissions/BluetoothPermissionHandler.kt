package com.example.meterkenshin.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Handles Bluetooth permissions for different Android API levels
 * Manages the transition from legacy to new Bluetooth permissions
 */
class BluetoothPermissionHandler(private val context: Context) {

    companion object {
        private const val TAG = "BluetoothPermissionHandler"
    }

    /**
     * Get the required Bluetooth permissions based on Android version
     */
    fun getRequiredPermissions(): List<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+ (API 31+) requires new Bluetooth permissions
            listOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION // Still needed for device discovery
            )
        } else {
            // Android 11 and below use legacy permissions
            listOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        }
    }

    /**
     * Check if all required Bluetooth permissions are granted
     */
    fun hasAllPermissions(): Boolean {
        val requiredPermissions = getRequiredPermissions()
        return requiredPermissions.all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Check if specific permission is granted
     */
    fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Check if BLUETOOTH_SCAN permission is granted (for Android 12+)
     */
    fun hasBluetoothScanPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            hasPermission(Manifest.permission.BLUETOOTH_SCAN)
        } else {
            hasPermission(Manifest.permission.BLUETOOTH) &&
                    hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    /**
     * Check if BLUETOOTH_CONNECT permission is granted (for Android 12+)
     */
    fun hasBluetoothConnectPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            hasPermission(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            hasPermission(Manifest.permission.BLUETOOTH)
        }
    }

    /**
     * Check if location permission is granted (required for Bluetooth scanning)
     */
    fun hasLocationPermission(): Boolean {
        return hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) ||
                hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
    }

    /**
     * Get missing permissions that need to be requested
     */
    fun getMissingPermissions(): List<String> {
        val requiredPermissions = getRequiredPermissions()
        return requiredPermissions.filter { permission ->
            !hasPermission(permission)
        }
    }

    /**
     * Get a human-readable description of what permissions are needed
     */
    fun getPermissionExplanation(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            "This app needs Bluetooth and Location permissions to connect to the Woosim printer. " +
                    "Bluetooth permissions allow the app to scan for and connect to nearby printers. " +
                    "Location permission is required for Bluetooth device discovery."
        } else {
            "This app needs Bluetooth and Location permissions to connect to the Woosim printer. " +
                    "These permissions allow the app to manage Bluetooth connections and discover nearby devices."
        }
    }

    /**
     * Check if the app should show rationale for requesting permissions
     */
    fun shouldShowRequestPermissionRationale(activity: android.app.Activity): Boolean {
        val missingPermissions = getMissingPermissions()
        return missingPermissions.any { permission ->
            androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
        }
    }
}