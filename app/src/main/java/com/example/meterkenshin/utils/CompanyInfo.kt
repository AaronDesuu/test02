package com.example.meterkenshin.utils

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.meterkenshin.ui.manager.SessionManager
import java.io.BufferedReader
import java.io.FileReader
import java.io.InputStreamReader

/**
 * Holds company/cooperative branding information for receipt printing.
 */
data class CompanyInfo(
    val companyName: String = DEFAULT_COMPANY_NAME,
    val addressLine1: String = DEFAULT_ADDRESS_LINE1,
    val addressLine2: String = DEFAULT_ADDRESS_LINE2,
    val phone: String = DEFAULT_PHONE,
    val paymentNote: String = DEFAULT_PAYMENT_NOTE,
    val disclaimer: String = DEFAULT_DISCLAIMER
) {
    companion object {
        private const val TAG = "CompanyInfo"

        const val DEFAULT_COMPANY_NAME = "Electric Philippines Inc."
        const val DEFAULT_ADDRESS_LINE1 = "H.V Dela Costa St Salcedo Village Makati 1227,"
        const val DEFAULT_ADDRESS_LINE2 = "Metro Manila Philippines"
        const val DEFAULT_PHONE = "TEL:000-000-0000"
        const val DEFAULT_PAYMENT_NOTE = "NOTE:Please pay this electric bill on or before DUE DATE otherwise,\n     we will be forced to discontinue serving your electric needs."
        const val DEFAULT_DISCLAIMER = "This is not an Official Receipt.\nPayment of this bill does not mean payment of previous delinquencies if any."

        private const val PREFS_KEY_PREFIX = "company_info_"
        private const val KEY_COMPANY_NAME = "${PREFS_KEY_PREFIX}company_name"
        private const val KEY_ADDRESS_LINE1 = "${PREFS_KEY_PREFIX}address_line1"
        private const val KEY_ADDRESS_LINE2 = "${PREFS_KEY_PREFIX}address_line2"
        private const val KEY_PHONE = "${PREFS_KEY_PREFIX}phone"
        private const val KEY_PAYMENT_NOTE = "${PREFS_KEY_PREFIX}payment_note"
        private const val KEY_DISCLAIMER = "${PREFS_KEY_PREFIX}disclaimer"
        private const val KEY_HAS_EDITS = "${PREFS_KEY_PREFIX}has_edits"

        // CSV field names (case-insensitive matching)
        private val FIELD_MAP = mapOf(
            "companyname" to "companyName",
            "addressline1" to "addressLine1",
            "addressline2" to "addressLine2",
            "phone" to "phone",
            "paymentnote" to "paymentNote",
            "disclaimer" to "disclaimer"
        )

        /**
         * Load company info with priority: SharedPreferences edits > company.csv > defaults
         */
        fun load(context: Context): CompanyInfo {
            // Priority 1: Check for in-app edits in SharedPreferences
            val edited = loadFromPrefs(context)
            if (edited != null) {
                Log.d(TAG, "Loaded company info from in-app edits")
                return edited
            }

            // Priority 2: Check for company.csv file
            val fromCsv = loadFromCsv(context)
            if (fromCsv != null) {
                Log.d(TAG, "Loaded company info from company.csv")
                return fromCsv
            }

            // Priority 3: Return defaults
            Log.d(TAG, "Using default company info")
            return CompanyInfo()
        }

        /**
         * Save company info edits to SharedPreferences (per-user)
         */
        fun saveToPrefs(context: Context, info: CompanyInfo) {
            val prefs = getPrefs(context)
            prefs.edit()
                .putString(KEY_COMPANY_NAME, info.companyName)
                .putString(KEY_ADDRESS_LINE1, info.addressLine1)
                .putString(KEY_ADDRESS_LINE2, info.addressLine2)
                .putString(KEY_PHONE, info.phone)
                .putString(KEY_PAYMENT_NOTE, info.paymentNote)
                .putString(KEY_DISCLAIMER, info.disclaimer)
                .putBoolean(KEY_HAS_EDITS, true)
                .apply()
            Log.d(TAG, "Saved company info to SharedPreferences")
        }

        /**
         * Clear in-app edits from SharedPreferences
         */
        fun clearEdits(context: Context) {
            val prefs = getPrefs(context)
            prefs.edit()
                .remove(KEY_COMPANY_NAME)
                .remove(KEY_ADDRESS_LINE1)
                .remove(KEY_ADDRESS_LINE2)
                .remove(KEY_PHONE)
                .remove(KEY_PAYMENT_NOTE)
                .remove(KEY_DISCLAIMER)
                .remove(KEY_HAS_EDITS)
                .apply()
            Log.d(TAG, "Cleared company info edits from SharedPreferences")
        }

        /**
         * Check if user has in-app edits saved
         */
        fun hasEdits(context: Context): Boolean {
            return getPrefs(context).getBoolean(KEY_HAS_EDITS, false)
        }

        /**
         * Check if company.csv exists for the current user
         */
        fun hasCsvFile(context: Context): Boolean {
            val sessionManager = SessionManager.getInstance(context)
            val companyFile = UserFileManager.getCompanyFile(context, sessionManager)
            return companyFile.exists()
        }

        /**
         * Validate a company CSV file before upload.
         */
        fun validateCompanyCsv(context: Context, uri: Uri): CompanyValidationResult {
            val errors = mutableListOf<String>()
            val warnings = mutableListOf<String>()
            val fields = mutableMapOf<String, String>()

            try {
                val reader = BufferedReader(InputStreamReader(context.contentResolver.openInputStream(uri)))
                val lines = reader.readLines().filter { it.isNotBlank() && !it.startsWith("#") }
                reader.close()

                if (lines.isEmpty()) {
                    return CompanyValidationResult(false, listOf("File is empty"))
                }

                // Validate header row
                val headerCells = lines[0].split(",").map { it.trim().lowercase() }
                if (headerCells.size < 2) {
                    errors.add("Header must have at least 2 columns (Field, Value)")
                    return CompanyValidationResult(false, errors)
                }

                if (!headerCells[0].equals("field", ignoreCase = true)) {
                    warnings.add("First header column should be 'Field', found '${lines[0].split(",")[0].trim()}'")
                }

                // Parse data rows
                val dataLines = lines.drop(1)
                if (dataLines.isEmpty()) {
                    errors.add("No data rows found (only header)")
                    return CompanyValidationResult(false, errors, warnings)
                }

                for (line in dataLines) {
                    // Split only on first comma to allow commas in values
                    val commaIndex = line.indexOf(",")
                    if (commaIndex == -1) {
                        warnings.add("Skipping line without comma: '$line'")
                        continue
                    }
                    val fieldName = line.substring(0, commaIndex).trim()
                    val fieldValue = line.substring(commaIndex + 1).trim()

                    val normalizedField = fieldName.lowercase().replace(" ", "").replace("_", "")
                    val mappedField = FIELD_MAP[normalizedField]

                    if (mappedField != null) {
                        fields[mappedField] = fieldValue
                    } else {
                        warnings.add("Unknown field: '$fieldName'")
                    }
                }

                if (!fields.containsKey("companyName")) {
                    warnings.add("Missing 'CompanyName' field - default will be used")
                }

            } catch (e: Exception) {
                return CompanyValidationResult(false, listOf("Failed to read file: ${e.message}"))
            }

            return CompanyValidationResult(
                isValid = errors.isEmpty(),
                errors = errors,
                warnings = warnings,
                fieldCount = fields.size
            )
        }

        /**
         * Parse company info from a company.csv file
         */
        fun parseFromCsv(context: Context, uri: Uri): CompanyInfo? {
            try {
                val reader = BufferedReader(InputStreamReader(context.contentResolver.openInputStream(uri)))
                val lines = reader.readLines().filter { it.isNotBlank() && !it.startsWith("#") }
                reader.close()

                if (lines.size < 2) return null

                return parseLines(lines)
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing company CSV from URI: ${e.message}", e)
                return null
            }
        }

        // --- Private helpers ---

        private fun getPrefs(context: Context): android.content.SharedPreferences {
            val sessionManager = SessionManager.getInstance(context.applicationContext)
            val username = sessionManager.getSession()?.username ?: "default"
            return context.applicationContext.getSharedPreferences(
                "MeterKenshinSettings_$username", Context.MODE_PRIVATE
            )
        }

        private fun loadFromPrefs(context: Context): CompanyInfo? {
            val prefs = getPrefs(context)
            if (!prefs.getBoolean(KEY_HAS_EDITS, false)) return null

            return CompanyInfo(
                companyName = prefs.getString(KEY_COMPANY_NAME, DEFAULT_COMPANY_NAME) ?: DEFAULT_COMPANY_NAME,
                addressLine1 = prefs.getString(KEY_ADDRESS_LINE1, DEFAULT_ADDRESS_LINE1) ?: DEFAULT_ADDRESS_LINE1,
                addressLine2 = prefs.getString(KEY_ADDRESS_LINE2, DEFAULT_ADDRESS_LINE2) ?: DEFAULT_ADDRESS_LINE2,
                phone = prefs.getString(KEY_PHONE, DEFAULT_PHONE) ?: DEFAULT_PHONE,
                paymentNote = prefs.getString(KEY_PAYMENT_NOTE, DEFAULT_PAYMENT_NOTE) ?: DEFAULT_PAYMENT_NOTE,
                disclaimer = prefs.getString(KEY_DISCLAIMER, DEFAULT_DISCLAIMER) ?: DEFAULT_DISCLAIMER
            )
        }

        private fun loadFromCsv(context: Context): CompanyInfo? {
            try {
                val sessionManager = SessionManager.getInstance(context)
                val companyFile = UserFileManager.getCompanyFile(context, sessionManager)

                if (!companyFile.exists()) return null

                val reader = BufferedReader(FileReader(companyFile))
                val lines = reader.readLines().filter { it.isNotBlank() && !it.startsWith("#") }
                reader.close()

                if (lines.size < 2) return null

                return parseLines(lines)
            } catch (e: Exception) {
                Log.e(TAG, "Error loading company CSV: ${e.message}", e)
                return null
            }
        }

        private fun parseLines(lines: List<String>): CompanyInfo {
            val fields = mutableMapOf<String, String>()

            // Skip header, parse data rows
            for (line in lines.drop(1)) {
                val commaIndex = line.indexOf(",")
                if (commaIndex == -1) continue

                val fieldName = line.substring(0, commaIndex).trim()
                val fieldValue = line.substring(commaIndex + 1).trim()

                val normalizedField = fieldName.lowercase().replace(" ", "").replace("_", "")
                val mappedField = FIELD_MAP[normalizedField]
                if (mappedField != null) {
                    fields[mappedField] = fieldValue
                }
            }

            return CompanyInfo(
                companyName = fields["companyName"] ?: DEFAULT_COMPANY_NAME,
                addressLine1 = fields["addressLine1"] ?: DEFAULT_ADDRESS_LINE1,
                addressLine2 = fields["addressLine2"] ?: DEFAULT_ADDRESS_LINE2,
                phone = fields["phone"] ?: DEFAULT_PHONE,
                paymentNote = fields["paymentNote"] ?: DEFAULT_PAYMENT_NOTE,
                disclaimer = fields["disclaimer"] ?: DEFAULT_DISCLAIMER
            )
        }
    }
}

/**
 * Validation result for company CSV files
 */
data class CompanyValidationResult(
    val isValid: Boolean,
    val errors: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
    val fieldCount: Int = 0
) {
    fun summary(): String {
        val parts = mutableListOf<String>()
        if (errors.isNotEmpty()) parts.addAll(errors)
        if (warnings.isNotEmpty()) parts.addAll(warnings)
        return parts.joinToString("\n")
    }
}
