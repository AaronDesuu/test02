package com.example.meterkenshin.bluetooth

import android.Manifest
import android.bluetooth.BluetoothDevice
import androidx.annotation.RequiresPermission

/**
 * Device List for managing discovered BLE devices
 * Ported from project01 Java implementation
 */
class DeviceList {

    private val mScanDevice = mutableListOf<ScanDevice>()

    fun size(): Int = mScanDevice.size

    fun addDevice(device: BluetoothDevice, rssi: Int) {
        var find = false
        for (i in mScanDevice.indices) {
            val scanDevice = mScanDevice[i].Device()
            if (scanDevice != null && scanDevice.address == device.address) {
                mScanDevice[i].Rssi(rssi)
                find = true
                break
            }
        }
        if (!find) {
            mScanDevice.add(ScanDevice(device, rssi))
        }
    }

    fun Deactivate() {
        for (i in mScanDevice.indices) {
            mScanDevice[i].Rssi(-200)
        }
    }

    fun Device(position: Int): BluetoothDevice? {
        return if (position in 0 until mScanDevice.size) {
            mScanDevice[position].Device()
        } else {
            null
        }
    }

    fun Rssi(position: Int): Int {
        return if (position >= 0) {
            if (position < mScanDevice.size) {
                mScanDevice[position].Rssi()
            } else {
                -200
            }
        } else {
            -200
        }
    }

    fun Position(address: String?): Int {
        if (address == null) return -1
        var ret = -1
        for (i in mScanDevice.indices) {
            if (address == mScanDevice[i].Address()) {
                ret = i
                break
            }
        }
        return ret
    }

    fun Address(position: Int): String? {
        return if (position in 0 until mScanDevice.size) {
            mScanDevice[position].Address()
        } else {
            null
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun Name(position: Int): String? {
        return if (position in 0 until mScanDevice.size) {
            mScanDevice[position].Name()
        } else {
            null
        }
    }

    fun clear() {
        mScanDevice.clear()
    }

    fun Activate(position: Int): Boolean {
        return if (position in 0 until mScanDevice.size) {
            mScanDevice[position].Activate()
        } else {
            false
        }
    }

    fun Check(position: Int): Boolean {
        return if (position in 0 until mScanDevice.size) {
            mScanDevice[position].Check()
        } else {
            false
        }
    }

    fun Done(position: Int) {
        if (position in 0 until mScanDevice.size) {
            mScanDevice[position].Check(true)
        }
    }

    /**
     * Helper method for MeterReadingViewModel compatibility
     */
    fun findPosition(address: String?): Int = Position(address)

    /**
     * Inner class for holding device scan information
     */
    inner class ScanDevice {
        private var mDev: BluetoothDevice? = null
        private var mRssi: Int = -200
        private var mChk: Boolean = false

        constructor() {
            mDev = null
            mChk = false
            mRssi = -200
        }

        constructor(dev: BluetoothDevice, rssi: Int) {
            mDev = dev
            mChk = false
            mRssi = rssi
        }

        fun Device(): BluetoothDevice? = mDev

        fun Address(): String? = mDev?.address

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        fun Name(): String? = mDev?.name

        fun Rssi(): Int = mRssi

        fun Check(): Boolean = mChk

        fun Activate(): Boolean = Rssi() != -200

        fun Device(dev: BluetoothDevice?) {
            mDev = dev
        }

        fun Rssi(rssi: Int) {
            mRssi = rssi
        }

        fun Check(chk: Boolean) {
            mChk = chk
        }
    }
}

/**
 * Alias for compatibility with DefaultDeviceList
 */
typealias DefaultDeviceList = DeviceList