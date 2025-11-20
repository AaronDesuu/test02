package com.example.meterkenshin.data.csv

import android.content.Context
import android.util.Log
import com.example.meterkenshin.data.FileStorageManager
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.io.BufferedReader

/**
 * Parser for printer.csv file to extract Bluetooth configuration
 * Expected CSV format:
 * Activate,Bluetooth ID,printer model
 * 1,1C:B8:57:50:01:D9,WISP-i350
 */
class PrinterCsvParser(
    private val context: Context,
    private val username: String
) {

    companion object {
        private const val TAG = "PrinterCsvParser"
        private const val PRINTER_CSV_FILENAME = "printer.csv"
        private const val APP_FILES_FOLDER = "app_files"

        // Expected headers
        private const val ACTIVATE_HEADER = "Activate"
        private const val BLUETOOTH_ID_HEADER = "Bluetooth ID"
        private const val PRINTER_MODEL_HEADER = "printer model"
    }

    /**
     * Data class representing printer configuration
     */
    data class PrinterConfig(
        val isActive: Boolean,
        val bluetoothMacAddress: String,
        val printerModel: String? = null
    )

    /**
     * Check if printer.csv file exists
     */
    fun isPrinterCsvAvailable(): Boolean {
        return getPrinterCsvFile().exists()
    }

    /**
     * Get the printer CSV file from app storage
     * Checks both external and internal storage locations
     */
    private fun getPrinterCsvFile(): File {
        val fileStorageManager = FileStorageManager(context)
        val userDir = fileStorageManager.getUserStorageDirectory(username)

        // Check user-specific directory
        val userFile = File(userDir, PRINTER_CSV_FILENAME)
        if (userFile.exists()) {
            Log.d(TAG, "Found printer.csv for user $username: ${userFile.absolutePath}")
            return userFile
        }

        Log.w(TAG, "printer.csv not found for user $username")
        return userFile // Return path even if doesn't exist
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
    private fun getAllPrinterConfigs(): List<PrinterConfig> {
        val configs = mutableListOf<PrinterConfig>()

        try {
            val printerFile = getPrinterCsvFile()
            if (!printerFile.exists()) {
                Log.w(TAG, "Printer CSV file not found")
                return configs
            }

            BufferedReader(InputStreamReader(FileInputStream(printerFile), "UTF-8")).use { reader ->
                val lines = reader.readLines()

                if (lines.isEmpty()) {
                    Log.w(TAG, "CSV file is empty")
                    return configs
                }

                // Parse headers
                val headers = lines[0].split(",").map { it.trim() }
                val activateIndex = headers.indexOfFirst {
                    it.equals(ACTIVATE_HEADER, ignoreCase = true)
                }
                val bluetoothIdIndex = headers.indexOfFirst {
                    it.equals(BLUETOOTH_ID_HEADER, ignoreCase = true)
                }
                val printerModelIndex = headers.indexOfFirst {
                    it.equals(PRINTER_MODEL_HEADER, ignoreCase = true)
                }

                if (activateIndex == -1 || bluetoothIdIndex == -1) {
                    Log.e(TAG, "Required headers not found for getAllPrinterConfigs")
                    return configs
                }

                // Parse data rows (skip header)
                for (i in 1 until lines.size) {
                    val values = lines[i].split(",").map { it.trim() }

                    if (values.size > maxOf(activateIndex, bluetoothIdIndex)) {
                        val isActive = values[activateIndex] == "1" ||
                                values[activateIndex].equals("true", ignoreCase = true)
                        val macAddress = values[bluetoothIdIndex]

                        // Get printer model if available
                        val printerModel = if (printerModelIndex != -1 && values.size > printerModelIndex) {
                            values[printerModelIndex]
                        } else {
                            null
                        }

                        if (isValidMacAddress(macAddress)) {
                            configs.add(PrinterConfig(isActive, macAddress, printerModel))
                            Log.d(TAG, "Added printer config: Active=$isActive, MAC=$macAddress, Model=$printerModel")
                        } else {
                            Log.w(TAG, "Invalid MAC address in row ${i + 1}: $macAddress")
                        }
                    } else {
                        Log.w(TAG, "Insufficient data in row ${i + 1}: ${lines[i]}")
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
        val allConfigs = getAllPrinterConfigs()
        val activeConfig = allConfigs.find { it.isActive }

        return if (activeConfig != null) {
            Log.d(TAG, "Found active printer: ${activeConfig.bluetoothMacAddress} (${activeConfig.printerModel ?: "Unknown model"})")
            activeConfig.bluetoothMacAddress
        } else {
            Log.w(TAG, "No active printer found in CSV")
            null
        }
    }

    /**
     * Get the active printer configuration (including model information)
     */
    fun getActivePrinterConfig(): PrinterConfig? {
        val allConfigs = getAllPrinterConfigs()
        return allConfigs.find { it.isActive }
    }

    /**
     * Get printer configuration summary for debugging
     */
    fun getConfigurationSummary(): String {
        return try {
            val allConfigs = getAllPrinterConfigs()
            if (allConfigs.isEmpty()) {
                "No printer configurations found"
            } else {
                val activeConfig = allConfigs.find { it.isActive }
                val activeCount = allConfigs.count { it.isActive }
                val totalCount = allConfigs.size

                if (activeConfig != null) {
                    "Active: ${activeConfig.bluetoothMacAddress} (${activeConfig.printerModel ?: "Unknown"}), Total: $totalCount printer(s)"
                } else {
                    "Found $totalCount printer(s), $activeCount active"
                }
            }
        } catch (e: Exception) {
            "Error reading configuration: ${e.message}"
        }
    }
}