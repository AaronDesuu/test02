package com.example.meterkenshin.bluetooth

import android.bluetooth.BluetoothDevice

/**
 * Device List for managing discovered BLE devices
 * Ported from project01 Java implementation
 */
@Suppress("FunctionName")
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

    fun Check(position: Int): Boolean {
        return if (position in 0 until mScanDevice.size) {
            mScanDevice[position].Check()
        } else {
            false
        }
    }


    /**
     * Inner class for holding device scan information
     */
    inner class ScanDevice {
        private var mDev: BluetoothDevice? = null
        private var mRssi: Int = -200
        private var mChk: Boolean = false

        constructor(dev: BluetoothDevice, rssi: Int) {
            mDev = dev
            mChk = false
            mRssi = rssi
        }

        fun Device(): BluetoothDevice? = mDev

        fun Check(): Boolean = mChk


        fun Rssi(rssi: Int) {
            mRssi = rssi
        }

        fun Check(chk: Boolean) {
            mChk = chk
        }
    }
}
