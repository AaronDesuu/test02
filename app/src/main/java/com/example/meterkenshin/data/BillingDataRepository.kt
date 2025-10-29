package com.example.meterkenshin.data

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import com.example.meterkenshin.model.Billing
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Repository for managing billing data persistence using SharedPreferences
 * Handles save/load/clear operations with 30-day expiry validation
 */
class BillingDataRepository(context: Context) {

    companion object {
        private const val TAG = "BillingDataRepository"
        private const val PREFS_NAME = "BillingDataStorage"
        private const val KEY_BILLING_DATA = "saved_billing_data"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    /**
     * Load saved billing data from SharedPreferences
     * Returns null if no data exists or if data has expired
     */
    fun loadBillingData(): SavedBillingData? {
        return try {
            val json = prefs.getString(KEY_BILLING_DATA, null) ?: return null

            val type = object : TypeToken<SavedBillingData>() {}.type
            val data = gson.fromJson<SavedBillingData>(json, type)

            // Only return if still valid, otherwise clear expired data
            if (data.isValid()) {
                Log.d(TAG, "Loaded billing data (${data.daysRemaining()} days remaining)")
                data
            } else {
                Log.d(TAG, "Billing data expired, clearing...")
                clear()
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading saved billing data: ${e.message}", e)
            null
        }
    }

    /**
     * Save billing data to SharedPreferences with timestamp
     */
    @SuppressLint("UseKtx")
    fun saveBillingData(billing: Billing, rates: FloatArray): SavedBillingData {
        val savedData = SavedBillingData(
            billing = billing,
            timestamp = System.currentTimeMillis(),
            rates = rates
        )

        val json = gson.toJson(savedData)
        prefs.edit().putString(KEY_BILLING_DATA, json).apply()

        Log.d(TAG, "Billing data saved for 30 days")
        return savedData
    }

    /**
     * Clear saved billing data manually
     */
    @SuppressLint("UseKtx")
    fun clear() {
        prefs.edit().remove(KEY_BILLING_DATA).apply()
        Log.d(TAG, "Cleared saved billing data")
    }
}