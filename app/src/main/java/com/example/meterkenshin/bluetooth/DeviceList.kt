package com.example.meterkenshin.bluetooth

import android.bluetooth.BluetoothDevice
import com.example.meterkenshin.ui.viewmodel.MeterReadingViewModel

/**
 * Interface for managing discovered BLE devices
 * This interface should be implemented by your existing device management system
 */
interface DeviceList {
    /**
     * Get RSSI (signal strength) for device at given position
     * @param position Position in device list
     * @return RSSI value in dBm, or -300 if not found
     */
    fun Rssi(position: Int): Int

    /**
     * Find position of device by Bluetooth address
     * @param address Bluetooth MAC address
     * @return Position in list, or -1 if not found
     */
    fun findPosition(address: String?): Int

    /**
     * Get device at specific position
     * @param position Position in device list
     * @return BluetoothDevice or null if not found
     */
    fun getDevice(position: Int): BluetoothDevice?

    /**
     * Add or update device in list
     * @param device Bluetooth device to add
     * @param rssi Signal strength
     */
    fun addDevice(device: BluetoothDevice, rssi: Int)

    /**
     * Clear all devices from list
     */
    fun clear()

    /**
     * Get total number of devices
     */
    fun size(): Int

    /**
     * Get all devices as list
     */
    fun getAllDevices(): List<BluetoothDevice>
}

/**
 * Default implementation of DeviceList interface
 * You can replace this with your existing implementation from project01
 */
class DefaultDeviceList : DeviceList, MeterReadingViewModel.DeviceList {

    data class DeviceInfo(
        val device: BluetoothDevice,
        var rssi: Int,
        var lastSeen: Long = System.currentTimeMillis()
    )

    private val devices = mutableListOf<DeviceInfo>()

    override fun Rssi(position: Int): Int {
        return if (position in 0 until devices.size) {
            devices[position].rssi
        } else {
            -300 // Return very low RSSI if not found
        }
    }

    override fun findPosition(address: String?): Int {
        if (address == null) return -1

        return devices.indexOfFirst { it.device.address == address }
    }

    override fun getDevice(position: Int): BluetoothDevice? {
        return if (position in 0 until devices.size) {
            devices[position].device
        } else {
            null
        }
    }

    override fun addDevice(device: BluetoothDevice, rssi: Int) {
        val existingIndex = devices.indexOfFirst { it.device.address == device.address }

        if (existingIndex != -1) {
            // Update existing device
            devices[existingIndex] = devices[existingIndex].copy(
                rssi = rssi,
                lastSeen = System.currentTimeMillis()
            )
        } else {
            // Add new device
            devices.add(DeviceInfo(device, rssi))
        }
    }

    override fun clear() {
        devices.clear()
    }

    override fun size(): Int {
        return devices.size
    }

    override fun getAllDevices(): List<BluetoothDevice> {
        return devices.map { it.device }
    }

    /**
     * Get device info by address
     */
    fun getDeviceInfo(address: String): DeviceInfo? {
        return devices.find { it.device.address == address }
    }

    /**
     * Sort devices by RSSI (strongest signal first)
     */
    fun sortBySignalStrength() {
        devices.sortByDescending { it.rssi }
    }
}