package com.example.meterkenshin.ui.manager

import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import com.example.meterkenshin.model.Meter
import com.example.meterkenshin.model.MeterStatus
import com.example.meterkenshin.model.MeterType
import com.example.meterkenshin.utils.UserFileManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

object MeterExportManager {
    private const val TAG = "MeterExportManager"

    // Multiple date formats to support different CSV formats
    private val dateFormats = listOf(
        SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault()),  // 2025/12/17 17:55:54 (primary format)
        SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()),           // 2025/12/17
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()),           // 2025-01-15
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())   // 2025-01-15 17:55:54
    )
    // Output format matches the primary input format
    private val outputDateFormat = SimpleDateFormat("yyyy/MM/dd  HH:mm:ss", Locale.getDefault())
    private val fileTimestampFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())

    /**
     * Get the most recent meter CSV file for the current user
     * Looks for files matching patterns:
     * 1. YYYYMM_meter.csv (dated files)
     * 2. meter.csv (default uploaded file)
     */
    suspend fun getMostRecentMeterFile(
        context: Context,
        sessionManager: SessionManager
    ): File? = withContext(Dispatchers.IO) {
        try {
            val userDir = UserFileManager.getAppFilesDir(context, sessionManager)

            // First, check for dated meter files (YYYYMM_meter.csv)
            val datedMeterFiles = userDir.listFiles { file ->
                file.name.matches(Regex("\\d{6}_meter\\.csv"))
            }

            // If dated files exist, use the most recent one
            val mostRecentDated = datedMeterFiles?.maxByOrNull { it.name }
            if (mostRecentDated != null && mostRecentDated.exists()) {
                Log.d(TAG, "Found dated meter file: ${mostRecentDated.name}")
                return@withContext mostRecentDated
            }

            // Otherwise, check for the default meter.csv file
            val defaultMeterFile = File(userDir, "meter.csv")
            if (defaultMeterFile.exists()) {
                Log.d(TAG, "Found default meter file: meter.csv")
                return@withContext defaultMeterFile
            }

            Log.w(TAG, "No meter files found in ${userDir.absolutePath}")
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error finding most recent meter file", e)
            null
        }
    }

    /**
     * Load registered meters from the most recent meter CSV file
     * Only includes meters that have:
     * - fixedDate is not null (scheduled for billing)
     * - activate = 1 (active)
     * - valid serialNumber
     */
    suspend fun getRegisteredMeters(
        context: Context,
        sessionManager: SessionManager
    ): List<Meter> = withContext(Dispatchers.IO) {
        try {
            val meterFile = getMostRecentMeterFile(context, sessionManager)
            if (meterFile == null || !meterFile.exists()) {
                Log.w(TAG, "No meter file found")
                return@withContext emptyList()
            }

            Log.d(TAG, "Loading meters from: ${meterFile.name}")

            val meters = mutableListOf<Meter>()
            val allMeters = mutableListOf<Meter>()
            var metersWithoutFixedDate = 0
            var inactiveMeters = 0

            meterFile.bufferedReader().use { reader ->
                val lines = reader.readLines()
                if (lines.isEmpty()) {
                    Log.w(TAG, "Meter file is empty")
                    return@withContext emptyList()
                }

                Log.d(TAG, "CSV Header: ${lines[0]}")

                // Skip header line
                for (i in 1 until lines.size) {
                    try {
                        val meter = parseMeterCSVLine(lines[i])
                        if (meter != null) {
                            allMeters.add(meter)

                            // Track why meters are filtered out
                            when {
                                meter.fixedDate == null -> {
                                    metersWithoutFixedDate++
                                    Log.d(TAG, "Meter ${meter.serialNumber} skipped: no fixedDate")
                                }
                                meter.activate != 1 -> {
                                    inactiveMeters++
                                    Log.d(TAG, "Meter ${meter.serialNumber} skipped: inactive (activate=${meter.activate})")
                                }
                                else -> {
                                    meters.add(meter)
                                    Log.d(TAG, "Meter ${meter.serialNumber} added: fixedDate=${meter.fixedDate}")
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing meter line $i: ${lines[i]}", e)
                    }
                }
            }

            Log.i(TAG, """
                Meter Export Summary:
                - Total meters in CSV: ${allMeters.size}
                - Meters with fixedDate: ${meters.size}
                - Meters without fixedDate: $metersWithoutFixedDate
                - Inactive meters: $inactiveMeters
                - File: ${meterFile.name}
            """.trimIndent())

            meters
        } catch (e: Exception) {
            Log.e(TAG, "Error loading registered meters", e)
            emptyList()
        }
    }

    /**
     * Parse a single CSV line into a Meter object
     * CSV format: uid,activate,serialNumber,bluetoothId,fixedDate,impKWh,expKWh,
     *             impMaxDemandKW,expMaxDemandKW,minVoltV,alert,readDate,
     *             billingPrintDate,lastCommunication
     */
    private fun parseMeterCSVLine(line: String): Meter? {
        try {
            val parts = line.split(",")
            if (parts.size < 14) {
                Log.w(TAG, "Invalid CSV line - not enough columns: $line")
                return null
            }

            return Meter(
                uid = parts[0].trim().toIntOrNull() ?: 0,
                activate = parts[1].trim().toIntOrNull() ?: 0,
                serialNumber = parts[2].trim(),
                bluetoothId = parts[3].trim().takeIf { it.isNotEmpty() },
                fixedDate = parseDate(parts[4].trim()),
                impKWh = parts[5].trim().toDoubleOrNull(),
                expKWh = parts[6].trim().toDoubleOrNull(),
                impMaxDemandKW = parts[7].trim().toDoubleOrNull(),
                expMaxDemandKW = parts[8].trim().toDoubleOrNull(),
                minVoltV = parts[9].trim().toDoubleOrNull(),
                alert = parts[10].trim().toDoubleOrNull(),
                readDate = parseDate(parts[11].trim()),
                billingPrintDate = parseDate(parts[12].trim()),
                lastCommunication = parseDate(parts[13].trim()),
                status = MeterStatus.ACTIVE,
                location = "",
                type = MeterType.Type01,
                installationDate = null
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing CSV line: $line", e)
            return null
        }
    }

    private fun parseDate(dateString: String): Date? {
        if (dateString.isEmpty() || dateString == "null") return null

        // Try each date format until one works
        for (format in dateFormats) {
            try {
                val date = format.parse(dateString)
                if (date != null) {
                    Log.d(TAG, "Successfully parsed date '$dateString' using format ${format.toPattern()}")
                    return date
                }
            } catch (e: Exception) {
                // Continue to next format
            }
        }

        Log.w(TAG, "Could not parse date: '$dateString'")
        return null
    }

    /**
     * Export registered meters to CSV file
     * Creates a timestamped folder (meters_export_YYYYMMDD_HHMMSS)
     * and exports as meter.csv inside it
     * Resets state fields (readDate, billingPrintDate, lastCommunication) to null
     * so imported meters appear as "Not Inspected"
     */
    suspend fun exportMetersToCSV(
        meters: List<Meter>,
        context: Context
    ): File = withContext(Dispatchers.IO) {
        val timestamp = fileTimestampFormat.format(Date())
        val folderName = "meters_export_$timestamp"

        // Export to Download/kenshinApp/meters_export_YYYYMMDD_HHMMSS/ directory
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val kenshinAppDir = File(downloadsDir, "kenshinApp")
        val exportFolder = File(kenshinAppDir, folderName)

        if (!exportFolder.exists()) {
            exportFolder.mkdirs()
        }

        val exportFile = File(exportFolder, "meter.csv")

        // Generate CSV content with reset state
        val csvContent = generateMeterCSV(meters)
        exportFile.writeText(csvContent)

        Log.d(TAG, "Exported ${meters.size} meters to ${exportFile.absolutePath}")

        // Notify MediaStore so the file and folder appear in File Manager
        notifyMediaStore(context, exportFolder)
        notifyMediaStore(context, exportFile)

        exportFile
    }

    /**
     * Notify Android's MediaStore about the new file or folder
     * This makes the file/folder visible in File Manager apps
     */
    private suspend fun notifyMediaStore(context: Context, file: File) = suspendCoroutine { continuation ->
        try {
            val mimeType = if (file.isDirectory) null else "text/csv"

            MediaScannerConnection.scanFile(
                context,
                arrayOf(file.absolutePath),
                arrayOf(mimeType)
            ) { path, uri ->
                Log.d(TAG, "Media scan completed for: $path, URI: $uri")
                continuation.resume(Unit)
            }

            // Also send broadcast for older Android versions
            val mediaScanIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
            mediaScanIntent.data = Uri.fromFile(file)
            context.sendBroadcast(mediaScanIntent)

            Log.d(TAG, "MediaStore notified about: ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Error notifying MediaStore", e)
            continuation.resume(Unit)
        }
    }

    /**
     * Generate CSV content from meter list
     * Resets state fields to null for "Not Inspected" status
     */
    private fun generateMeterCSV(meters: List<Meter>): String {
        val builder = StringBuilder()

        // CSV Header
        builder.appendLine(
            "uid,activate,serialNumber,bluetoothId,fixedDate,impKWh,expKWh," +
            "impMaxDemandKW,expMaxDemandKW,minVoltV,alert,readDate," +
            "billingPrintDate,lastCommunication"
        )

        // CSV Rows - reset state fields to make meters "Not Inspected"
        meters.forEach { meter ->
            builder.appendLine(
                "${meter.uid}," +
                "${meter.activate}," +
                "${meter.serialNumber}," +
                "${meter.bluetoothId ?: ""}," +
                "${formatDate(meter.fixedDate)}," +
                "${meter.impKWh ?: ""}," +
                "${meter.expKWh ?: ""}," +
                "${meter.impMaxDemandKW ?: ""}," +
                "${meter.expMaxDemandKW ?: ""}," +
                "${meter.minVoltV ?: ""}," +
                "${meter.alert ?: ""}," +
                "," +  // readDate = null (Not Inspected)
                "," +  // billingPrintDate = null (Not Printed)
                ""     // lastCommunication = null (No Communication)
            )
        }

        return builder.toString()
    }

    private fun formatDate(date: Date?): String {
        return date?.let { outputDateFormat.format(it) } ?: ""
    }

    /**
     * Share meter CSV file via Android Share Sheet
     */
    fun shareMeterCSV(context: Context, file: File) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Meter Export - ${file.name}")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            context.startActivity(Intent.createChooser(shareIntent, "Share Meter CSV"))
            Log.d(TAG, "Shared meter CSV: ${file.name}")
        } catch (e: Exception) {
            Log.e(TAG, "Error sharing meter CSV", e)
            throw e
        }
    }
}
