package com.example.meterkenshin.data.manager

import android.content.Context
import android.util.Log
import com.example.meterkenshin.model.Meter
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

/**
 * Manages meter registration data persistence
 * Stores registration data in CSV files (registration.csv, meter.csv, YYYYMM_meter.csv)
 */
class RegistrationDataManager(private val context: Context) {

    companion object {
        private const val TAG = "RegistrationDataMgr"
        private const val REGISTRATION_CSV = "registration.csv"
        private const val METER_CSV = "meter.csv"
    }

    private val externalFilesDir: File? = context.getExternalFilesDir(null)

    data class RegistrationData(
        val serialId: String,
        val fixedDate: String,
        val impValue: Float,
        val impMax: Float,
        val powerFactor: Float,
        val status: String,
        val readDate: String
    )

    /**
     * Save meter registration data after successful DLMS registration
     */
    fun saveRegistrationData(meter: Meter, billingData: List<String>): Boolean {
        if (externalFilesDir == null) {
            Log.e(TAG, "External storage not available")
            return false
        }

        try {
            if (billingData.size < 10) {
                Log.e(TAG, "Insufficient billing data: size=${billingData.size}")
                return false
            }

            val registrationData = RegistrationData(
                serialId = meter.serialNumber,
                fixedDate = billingData[1],
                impValue = billingData[2].toFloatOrNull()?.div(1000f) ?: 0f,
                impMax = billingData[6].toFloatOrNull()?.div(1000f) ?: 0f,
                powerFactor = billingData[8].toFloatOrNull()?.div(100f) ?: 0f,
                status = billingData[9],
                readDate = billingData[0]
            )

            saveToRegistrationCsv(registrationData)
            updateMeterCsv(meter.serialNumber, registrationData)
            saveHistoricalData(registrationData)

            Log.i(TAG, "Registration data saved for ${meter.serialNumber}")
            return true

        } catch (e: Exception) {
            Log.e(TAG, "Failed to save registration data", e)
            return false
        }
    }

    private fun saveToRegistrationCsv(data: RegistrationData) {
        val file = File(externalFilesDir, REGISTRATION_CSV)

        val exists = file.exists()
        BufferedWriter(FileWriter(file, true)).use { writer ->
            if (!exists) {
                writer.write("SerialID,FixedDate,ImpValue,ImpMax,PowerFactor,Status,ReadDate\n")
            }

            writer.write("${data.serialId},${data.fixedDate}," +
                    "${String.format("%.3f", data.impValue)}," +
                    "${String.format("%.3f", data.impMax)}," +
                    "${String.format("%.3f", data.powerFactor)}," +
                    "${data.status},${data.readDate}\n")
        }
    }

    private fun updateMeterCsv(serialId: String, data: RegistrationData) {
        val file = File(externalFilesDir, METER_CSV)

        if (!file.exists()) {
            Log.w(TAG, "meter.csv not found")
            return
        }

        val lines = file.readLines().toMutableList()
        if (lines.isEmpty()) return

        val updated = lines.mapIndexed { index, line ->
            if (index == 0) {
                line
            } else {
                val cols = line.split(",")
                if (cols.size > 1 && cols[1] == serialId) {
                    "1,$serialId,${data.fixedDate},${String.format("%.3f", data.impValue)}," +
                            "${String.format("%.3f", data.impMax)},${data.readDate}"
                } else {
                    line
                }
            }
        }

        file.writeText(updated.joinToString("\n"))
    }

    private fun saveHistoricalData(data: RegistrationData) {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.MONTH, -1)
        val monthFormat = SimpleDateFormat("yyyyMM", Locale.getDefault())
        val monthStr = monthFormat.format(calendar.time)

        val historicalFile = File(externalFilesDir, "${monthStr}_meter.csv")

        val exists = historicalFile.exists()
        BufferedWriter(FileWriter(historicalFile, true)).use { writer ->
            if (!exists) {
                writer.write("SerialID,FixedDate,ImpValue\n")
            }
            writer.write("${data.serialId},${data.fixedDate}," +
                    "${String.format("%.3f", data.impValue)}\n")
        }
    }

    fun loadRegistrationData(serialId: String): RegistrationData? {
        val file = File(externalFilesDir, REGISTRATION_CSV)

        if (!file.exists()) return null

        try {
            file.readLines().forEach { line ->
                val cols = line.split(",")
                if (cols.size >= 7 && cols[0] == serialId) {
                    return RegistrationData(
                        serialId = cols[0],
                        fixedDate = cols[1],
                        impValue = cols[2].toFloat(),
                        impMax = cols[3].toFloat(),
                        powerFactor = cols[4].toFloat(),
                        status = cols[5],
                        readDate = cols[6]
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading registration data", e)
        }

        return null
    }

    fun isMeterRegistered(serialId: String): Boolean {
        return loadRegistrationData(serialId) != null
    }

    fun getAllRegisteredMeters(): List<RegistrationData> {
        val file = File(externalFilesDir, REGISTRATION_CSV)
        val results = mutableListOf<RegistrationData>()

        if (!file.exists()) return results

        try {
            file.readLines().drop(1).forEach { line ->
                val cols = line.split(",")
                if (cols.size >= 7) {
                    results.add(RegistrationData(
                        serialId = cols[0],
                        fixedDate = cols[1],
                        impValue = cols[2].toFloat(),
                        impMax = cols[3].toFloat(),
                        powerFactor = cols[4].toFloat(),
                        status = cols[5],
                        readDate = cols[6]
                    ))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading all registration data", e)
        }

        return results
    }
}