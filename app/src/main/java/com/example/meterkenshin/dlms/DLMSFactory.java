package com.example.meterkenshin.dlms;

import android.content.Context;

/**
 * Factory class to create DLMS instances
 * This class is in the same package as DLMS, so it can access the package-private constructor
 */
public class DLMSFactory {

    /**
     * Create a new DLMS instance
     * @param context Android context
     * @return DLMS instance or null if creation fails
     */
    public static DLMS createInstance(Context context) {
        try {
            return new DLMS(context);
        } catch (Exception e) {
            // Log the error if needed
            return null;
        }
    }

    /**
     * Check if DLMS can be instantiated
     * @return true if DLMS is available
     */
    public static boolean isAvailable() {
        return true; // Add any specific checks if needed
    }
}