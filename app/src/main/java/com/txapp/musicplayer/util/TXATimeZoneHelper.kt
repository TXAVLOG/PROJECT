package com.txapp.musicplayer.util

import android.location.Location
import java.util.TimeZone
import kotlin.math.roundToInt

/**
 * TXA TimeZone Helper
 * Centralizes TimeZone logic, prioritizing Location-based estimation over System defaults.
 */
object TXATimeZoneHelper {

    private const val TAG = "TXATimeZone"
    
    // Cache the last determined timezone to avoid recalculating constantly
    private var cachedTimeZone: TimeZone? = null
    private var isLocationBased: Boolean = false

    /**
     * Get the app's current TimeZone.
     * Priorities:
     * 1. Location-based TimeZone (if available and valid)
     * 2. System Default TimeZone (Traditional fallback)
     */
    fun getAppTimeZone(): TimeZone {
        val currentCache = cachedTimeZone
        if (currentCache != null) {
            return currentCache
        }
        
        // Fallback to traditional immediately if no cache
        val systemTZ = TimeZone.getDefault()
        TXALogger.appD(TAG, "No location-based TZ cached. Using System Default: ${systemTZ.id}")
        return systemTZ
    }

    /**
     * Update the App TimeZone based on a new Location.
     * Logs the process clearly.
     */
    fun updateFromLocation(location: Location) {
        TXALogger.appI(TAG, "==================================================")
        TXALogger.appI(TAG, "TXA TIMEZONE RESOLUTION START")
        TXALogger.appI(TAG, "==================================================")
        TXALogger.appI(TAG, "Input Location: ${location.latitude}, ${location.longitude}")

        try {
            // ESTIMATION STRATEGY:
            // Since Android doesn't natively map Lat/Long to TimeZone ID without external heavy libs,
            // we calculate the 'Theoretical GMT Offset' based on longitude (15 degrees = 1 hour).
            // This is a rough estimation but satisfies "Location-based GMT".
            
            val longitude = location.longitude
            val offsetHours = (longitude / 15.0).roundToInt()
            
            // Format into a custom GMT ID, e.g., "GMT+07:00"
            val offsetSign = if (offsetHours >= 0) "+" else "-"
            val absOffset = Math.abs(offsetHours)
            val customId = "GMT$offsetSign$absOffset:00" // Simplified for whole hours
            
            TXALogger.appI(TAG, "Calculated Theoretical Offset from Longitude ($longitude): $offsetHours hours")
            TXALogger.appI(TAG, "Generated TimeZone ID: $customId")

            // Compare with Traditional System TimeZone
            val systemTZ = TimeZone.getDefault()
            val systemID = systemTZ.id
            val systemDisplayName = systemTZ.getDisplayName(false, TimeZone.SHORT)
            
            TXALogger.appI(TAG, "Traditional System TZ: $systemID ($systemDisplayName)")

            // Logic: Is it valid?
            // Creating a TimeZone from ID
            val locationTZ = TimeZone.getTimeZone(customId)

            if (locationTZ != null) {
                cachedTimeZone = locationTZ
                isLocationBased = true
                TXALogger.appI(TAG, ">> SUCCESS: App TimeZone updated to Location-based: $customId")
                TXALogger.appI(TAG, ">> User is effectively in a UTC$offsetSign$absOffset zone according to GPS.")
            } else {
                TXALogger.appW(TAG, ">> FAILED: Could not create TimeZone from ID $customId. Falling back.")
                fallbackToSystem()
            }

        } catch (e: Exception) {
            TXALogger.appE(TAG, "Error resolving TimeZone from location", e)
            fallbackToSystem()
        } finally {
            TXALogger.appI(TAG, "==================================================")
        }
    }

    private fun fallbackToSystem() {
        cachedTimeZone = TimeZone.getDefault()
        isLocationBased = false
        TXALogger.appI(TAG, ">> FALLBACK: Using Traditional System TimeZone: ${cachedTimeZone?.id}")
    }

    /**
     * Returns true if we are currently running on a location-derived timezone
     */
    fun isUsingLocationTimeZone(): Boolean = isLocationBased
}
