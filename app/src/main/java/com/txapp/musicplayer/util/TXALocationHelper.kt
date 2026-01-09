package com.txapp.musicplayer.util

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * TXA Location Helper
 * Handles location permissions, retrieval, and logging for ADB diagnostics.
 */
object TXALocationHelper {
    
    private const val PERMISSION_REQUEST_CODE = 1001
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    fun checkPermissions(context: Context): Boolean {
        return ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED || ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun requestPermissions(activity: Activity) {
        ActivityCompat.requestPermissions(
            activity,
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ),
            PERMISSION_REQUEST_CODE
        )
    }

    @SuppressLint("MissingPermission")
    fun fetchAndLogLocation(context: Context) {
        if (!checkPermissions(context)) {
            TXALogger.appW("TXALocation", "Permissions not granted. Cannot fetch location.")
            return
        }

        try {
            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
            
            // Try last known location
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    logLocationDetailsBg(context, location)
                } else {
                    // Try current location
                    val cancellationToken = com.google.android.gms.tasks.CancellationTokenSource()
                    fusedLocationClient.getCurrentLocation(
                        Priority.PRIORITY_BALANCED_POWER_ACCURACY, 
                        cancellationToken.token
                    ).addOnSuccessListener { currentLocation ->
                        if (currentLocation != null) {
                            logLocationDetailsBg(context, currentLocation)
                        } else {
                            TXALogger.appW("TXALocation", "Location is null. Ensure GPS is on.")
                        }
                    }.addOnFailureListener { e ->
                        handleLocationError(context, e, "Failed to get current location")
                    }
                }
            }.addOnFailureListener { e ->
                handleLocationError(context, e, "Failed to get last location")
            }
        } catch (e: Exception) {
            handleLocationError(context, e, "Failed to initialize location client")
        }
    }

    private fun handleLocationError(context: Context, e: Exception, logMsg: String) {
        TXALogger.appE("TXALocation", logMsg, e)

        try {
            // Determine if it's a GMS issue or generic
            val isGmsIssue = e.message?.contains("Google Play Services", ignoreCase = true) == true ||
                             e.javaClass.name.contains("GooglePlayServices") ||
                             e.message?.contains("LocationServices.API is not available", ignoreCase = true) == true

            if (isGmsIssue) {
                 val friendlyMsg = TXATranslation.txa("txamusic_error_friendly_location_api")
                 
                 // BYPASS: Don't crash on GMS Missing. Just log warning and let app proceed.
                 TXALogger.appW("TXALocation", "Location API Unavailable: $friendlyMsg - Proceeding without location features.")
                 // Do NOT throw fatal error. User can still use music player.
            }
        } catch (ignored: Exception) {
            // Safety net
        }
    }
    
    private fun logLocationDetailsBg(context: Context, location: Location) {
        executor.execute {
            logLocationDetails(context, location)
        }
    }
    
    private fun logLocationDetails(context: Context, location: Location) {
        try {
            val geocoder = Geocoder(context, Locale.getDefault())
            
            // Handle Geocoder deprecation for API 33+
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                geocoder.getFromLocation(location.latitude, location.longitude, 1) { addresses ->
                    val addressInfo = if (addresses.isNotEmpty()) {
                        val address = addresses[0]
                        formatAddress(address)
                    } else {
                        "Address     : Unknown (No address found)"
                    }
                    printDiagnostics(location, addressInfo)
                }
            } else {
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                
                val addressInfo = if (!addresses.isNullOrEmpty()) {
                    formatAddress(addresses[0])
                } else {
                    "Address     : Unknown (Geocoder returned null)"
                }
                printDiagnostics(location, addressInfo)
            }

        } catch (e: Exception) {
            TXALogger.appE("TXALocation", "Failed to process location address", e)
            // Fallback diagnostic specific for generic failures
            printDiagnostics(location, "Address     : Unavailable (Error: ${e.message})")
        }
    }

    private fun formatAddress(address: android.location.Address): String {
        return """
                Address     : ${address.getAddressLine(0)}
                City        : ${address.locality}
                State       : ${address.adminArea}
                Country     : ${address.countryName}
                Postal Code : ${address.postalCode}
                Known Name  : ${address.featureName}
                """.trimIndent()
    }

    private fun printDiagnostics(location: Location, addressInfo: String) {
        val systemTZ = java.util.TimeZone.getDefault()
        val tzId = systemTZ.id
        val tzDisplayName = systemTZ.getDisplayName(false, java.util.TimeZone.SHORT)
        val tzOffset = systemTZ.rawOffset / (1000 * 60 * 60.0) // Float hour offset
        val tzOffsetStr = if (tzOffset >= 0) "+%.1f".format(tzOffset) else "%.1f".format(tzOffset)

        val border = "=".repeat(50)
        val msg = """
            
            $border
            TXA LOCATION DIAGNOSTICS
            $border
            [PROVIDER DATA]
            Provider    : ${location.provider}
            Coordinates : ${location.latitude}, ${location.longitude}
            Accuracy    : ${location.accuracy} m
            Altitude    : ${location.altitude} m
            Speed       : ${location.speed} m/s
            
            [TIMEZONE DATA] (Merged)
            Location Time : ${TXAFormat.formatFullTime(location.time)}
            System TZ     : $tzId ($tzDisplayName)
            Offset        : GMT$tzOffsetStr
            Epoch (ms)    : ${location.time}
            
            [ADDRESS DATA]
            $addressInfo
            
            [TIMEZONE PROCESS]
            Initiating App-wide TimeZone resolution based on this location...
            Check ADB: adb logcat -s TXATimeZone
            
            ADB Pull    : adb logcat -s TXALocation
            $border
        """.trimIndent()

        android.util.Log.i("TXALocation", msg)
        TXALogger.appI("TXALocation", "Location retrieved: ${location.latitude}, ${location.longitude} | TZ: $tzId")
        
        // Hợp nhất: Trigger update to App TimeZone
        TXATimeZoneHelper.updateFromLocation(location)
    }
}
