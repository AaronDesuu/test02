package com.example.meterkenshin.data.parser

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.io.BufferedReader

/**
 * Parser for printer.csv file to extract Bluetooth configuration
 * Expected CSV format:
 * Activate,Bluetooth ID
 * 1,1C:B8:57:50:01:D9
 */
class PrinterCsvParser(private val context: Context) {

    companion object {
        private const val TAG = "PrinterCsvParser"
        private const val PRINTER_CSV_FILENAME = "printer.csv"
        private const val APP_FILES_FOLDER = "app_files"

        // CSV column indices (based on your example)
        private const val ACTIVATE_COLUMN = 0
        private const val BLUETOOTH_ID_COLUMN = 1

        // Expected headers
        private const val ACTIVATE_HEADER = "Activate"
        private const val BLUETOOTH_ID_HEADER = "Bluetooth ID"
    }

    /**
     * Data class representing printer configuration
     */
    data class PrinterConfig(
        val isActive: Boolean,
        val bluetoothMacAddress: String
    )

    /**
     * Parse the printer.csv file and return printer configuration
     */
    fun parsePrinterConfig(): PrinterConfig? {
        try {
            val printerFile = getPrinterCsvFile()
            if (!printerFile.exists()) {
                Log.w(TAG, "Printer CSV file not found: ${printerFile.absolutePath}")
                return null
            }

            return parseCsvFile(printerFile)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing printer CSV", e)
            return null
        }
    }

    /**
     * Get the printer MAC address from the CSV file
     */
    fun getPrinterMacAddress(): String? {
        val config = parsePrinterConfig()
        return if (config?.isActive == true) {
            config.bluetoothMacAddress
        } else {
            Log.w(TAG, "Printer is not active or configuration not found")
            null
        }
    }

    /**
     * Check if printer.csv file exists
     */
    fun isPrinterCsvAvailable(): Boolean {
        return getPrinterCsvFile().exists()
    }

    /**
     * Get the printer CSV file from app storage
     */
    private fun getPrinterCsvFile(): File {
        val appFilesDir = File(context.filesDir, APP_FILES_FOLDER)
        return File(appFilesDir, PRINTER_CSV_FILENAME)
    }

    /**
     * Parse the CSV file and extract printer configuration
     */
    private fun parseCsvFile(file: File): PrinterConfig? {
        var config: PrinterConfig? = null

        try {
            BufferedReader(InputStreamReader(FileInputStream(file), "UTF-8")).use { reader ->
                var lineNumber = 0
                var activateIndex = -1
                var bluetoothIdIndex = -1

                reader.forEachLine { line ->
                    lineNumber++

                    if (lineNumber == 1) {
                        // Parse header to determine column indices
                        val headers = line.split(",").map { it.trim() }
                        activateIndex = headers.indexOfFirst {
                            it.equals(ACTIVATE_HEADER, ignoreCase = true)
                        }
                        bluetoothIdIndex = headers.indexOfFirst {
                            it.equals(BLUETOOTH_ID_HEADER, ignoreCase = true)
                        }

                        if (activateIndex == -1 || bluetoothIdIndex == -1) {
                            Log.e(TAG, "Required headers not found. Expected: '$ACTIVATE_HEADER', '$BLUETOOTH_ID_HEADER'")
                            Log.e(TAG, "Found headers: $headers")
                            return null
                        }

                        Log.d(TAG, "CSV headers parsed - Activate: $activateIndex, Bluetooth ID: $bluetoothIdIndex")
                    } else if (lineNumber == 2) {
                        // Parse data row (assuming first data row contains the printer config)
                        val values = line.split(",").map { it.trim() }

                        if (values.size > maxOf(activateIndex, bluetoothIdIndex)) {
                            val isActive = values[activateIndex].equals("1") ||
                                    values[activateIndex].equals("true", ignoreCase = true)
                            val macAddress = values[bluetoothIdIndex]

                            // Validate MAC address format
                            if (isValidMacAddress(macAddress)) {
                                config = PrinterConfig(isActive, macAddress)
                                Log.d(TAG, "Printer config parsed - Active: $isActive, MAC: $macAddress")
                            } else {
                                Log.e(TAG, "Invalid MAC address format: $macAddress")
                            }
                        } else {
                            Log.e(TAG, "Insufficient data in CSV row: $line")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading CSV file", e)
        }

        return config
    }

    /**
     * Validate MAC address format (XX:XX:XX:XX:XX:XX)
     */
    private fun isValidMacAddress(macAddress: String): Boolean {
        val macPattern = Regex("^([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})$")
        return macPattern.matches(macAddress)
    }

    /**
     * Get all printer configurations (if CSV contains multiple printers)
     */
    fun getAllPrinterConfigs(): List<PrinterConfig> {
        val configs = mutableListOf<PrinterConfig>()

        try {
            val printerFile = getPrinterCsvFile()
            if (!printerFile.exists()) {
                Log.w(TAG, "Printer CSV file not found")
                return configs
            }

            BufferedReader(InputStreamReader(FileInputStream(printerFile), "UTF-8")).use { reader ->
                var lineNumber = 0
                var activateIndex = -1
                var bluetoothIdIndex = -1

                reader.forEachLine { line ->
                    lineNumber++

                    if (lineNumber == 1) {
                        // Parse headers
                        val headers = line.split(",").map { it.trim() }
                        activateIndex = headers.indexOfFirst {
                            it.equals(ACTIVATE_HEADER, ignoreCase = true)
                        }
                        bluetoothIdIndex = headers.indexOfFirst {
                            it.equals(BLUETOOTH_ID_HEADER, ignoreCase = true)
                        }
                    } else {
                        // Parse data rows
                        val values = line.split(",").map { it.trim() }

                        if (activateIndex != -1 && bluetoothIdIndex != -1 &&
                            values.size > maxOf(activateIndex, bluetoothIdIndex)) {

                            val isActive = values[activateIndex].equals("1") ||
                                    values[activateIndex].equals("true", ignoreCase = true)
                            val macAddress = values[bluetoothIdIndex]

                            if (isValidMacAddress(macAddress)) {
                                configs.add(PrinterConfig(isActive, macAddress))
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading all printer configs", e)
        }

        return configs
    }

    /**
     * Get the first active printer's MAC address
     */
    fun getActivePrinterMacAddress(): String? {
        val configs = getAllPrinterConfigs()
        return configs.firstOrNull { it.isActive }?.bluetoothMacAddress
    }

    /**
     * Log printer configuration for debugging
     */
    fun logPrinterConfig() {
        val config = parsePrinterConfig()
        if (config != null) {
            Log.d(TAG, "Printer Configuration:")
            Log.d(TAG, "  Active: ${config.isActive}")
            Log.d(TAG, "  MAC Address: ${config.bluetoothMacAddress}")
        } else {
            Log.d(TAG, "No printer configuration found")
        }
    }
}