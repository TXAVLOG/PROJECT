package com.txapp.musicplayer.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket

object TXANetworkHelper {
    enum class NetworkStatus {
        NONE, 
        WIFI_CONNECTED, 
        WIFI_NO_INTERNET, 
        CELLULAR_CONNECTED, 
        CELLULAR_NO_INTERNET
    }

    /**
     * Comprehensive network check
     */
    @Suppress("DEPRECATION")
    fun getNetworkStatus(context: Context): NetworkStatus {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val activeNetwork = cm.activeNetwork ?: return NetworkStatus.NONE
            val caps = cm.getNetworkCapabilities(activeNetwork) ?: return NetworkStatus.NONE
            
            val hasTransportWifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
            val hasTransportCellular = caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
            
            // Initial capability check
            // NET_CAPABILITY_VALIDATED means the system has confirmed internet access (walled garden check passed)
            val isValidated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            
            // If validated, we are good
            if (isValidated) {
                return if (hasTransportWifi) NetworkStatus.WIFI_CONNECTED else NetworkStatus.CELLULAR_CONNECTED
            }
            
            // If not validated, we might still have internet (older devices, custom DNS, etc) or really no internet.
            // We can do a quick socket check if it's critical, or trust the lack of VALIDATED.
            // Requirement says "dùng wifi mà k có kết nối" -> typically implies VALIDATED check failed.
            
            if (hasTransportWifi) {
                return if (isRealInternetAvailable()) NetworkStatus.WIFI_CONNECTED else NetworkStatus.WIFI_NO_INTERNET
            }
            
            if (hasTransportCellular) {
                 return if (isRealInternetAvailable()) NetworkStatus.CELLULAR_CONNECTED else NetworkStatus.CELLULAR_NO_INTERNET
            }
            
            return NetworkStatus.NONE
            
        } else {
            // Legacy approach for pre-M
            // Legacy approach for pre-M
            @Suppress("DEPRECATION")
            val activeNetworkInfo = cm.activeNetworkInfo
            @Suppress("DEPRECATION")
            if (activeNetworkInfo != null && activeNetworkInfo.isConnected) {
                return when (activeNetworkInfo.type) {
                    ConnectivityManager.TYPE_WIFI -> {
                        if (isRealInternetAvailable()) NetworkStatus.WIFI_CONNECTED else NetworkStatus.WIFI_NO_INTERNET
                    }
                    ConnectivityManager.TYPE_MOBILE -> {
                         if (isRealInternetAvailable()) NetworkStatus.CELLULAR_CONNECTED else NetworkStatus.CELLULAR_NO_INTERNET
                    }
                    else -> NetworkStatus.NONE
                }
            }
            return NetworkStatus.NONE
        }
    }
    
    // Check real internet by pinging Google DNS
    private fun isRealInternetAvailable(): Boolean {
        return try {
            val timeoutMs = 1500
            val socket = Socket()
            val socketAddress = InetSocketAddress("8.8.8.8", 53)
            socket.connect(socketAddress, timeoutMs)
            socket.close()
            true
        } catch (e: IOException) {
            false
        }
    }
    
    fun isWifi(context: Context): Boolean {
         val status = getNetworkStatus(context)
         return status == NetworkStatus.WIFI_CONNECTED || status == NetworkStatus.WIFI_NO_INTERNET
    }
}
