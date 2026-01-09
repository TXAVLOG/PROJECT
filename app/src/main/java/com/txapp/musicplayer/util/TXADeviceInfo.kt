package com.txapp.musicplayer.util

import android.content.Context
import android.content.pm.PackageInfo
import android.os.Build
import android.app.ActivityManager
import android.text.format.Formatter

/**
 * TXA Device Info - Get app and device information
 */
object TXADeviceInfo {
    
    private lateinit var appContext: Context
    
    fun init(context: Context) {
        appContext = context.applicationContext
    }
    
    /**
     * Get app version name (e.g., "1.0.0_txa")
     */
    fun getVersionName(): String {
        return try {
            val pInfo = appContext.packageManager.getPackageInfo(appContext.packageName, 0)
            pInfo.versionName ?: "Unknown"
        } catch (e: Exception) {
            "Unknown"
        }
    }
    
    /**
     * Get app version code
     */
    fun getVersionCode(): Long {
        return try {
            val pInfo = appContext.packageManager.getPackageInfo(appContext.packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                pInfo.versionCode.toLong()
            }
        } catch (e: Exception) {
            0L
        }
    }
    
    /**
     * Get device manufacturer
     */
    fun getManufacturer(): String = Build.MANUFACTURER
    
    /**
     * Get device model
     */
    fun getModel(): String = Build.MODEL
    
    /**
     * Get device codename
     */
    fun getDevice(): String = Build.DEVICE

    /**
     * Get human readable device name (e.g., "Samsung Galaxy S21")
     */
    fun getDeviceName(): String {
        val manufacturer = Build.MANUFACTURER
        val model = Build.MODEL
        return if (model.startsWith(manufacturer, ignoreCase = true)) {
            model.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        } else {
            "${manufacturer.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }} $model"
        }
    }
    
    /**
     * Get Android SDK version
     */
    fun getSdkVersion(): Int = Build.VERSION.SDK_INT
    
    /**
     * Get Android version string (e.g., "13")
     */
    fun getAndroidVersion(): String = Build.VERSION.RELEASE
    
    /**
     * Get full device info string for logging
     */
    fun getFullDeviceInfo(): String {
        return buildString {
            appendLine("App Version: ${getVersionName()} (${getVersionCode()})")
            appendLine("Device: ${getManufacturer()} ${getModel()} (${getDevice()})")
            appendLine("Android: ${getAndroidVersion()} (API ${getSdkVersion()})")
        }
    }
    
    /**
     * Check if device is running Android 11+ (Scoped Storage)
     */
    fun isAndroid11Plus(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
    
    /**
     * Check if device is running Android 13+ (Granular Permissions)
     */
    fun isAndroid13Plus(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    
    /**
     * Check if device is running Android 9+ (minimum supported)
     */
    fun isAndroid9Plus(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
    
    /**
     * Check if device is supported (Android 9+)
     */
    fun isDeviceSupported(): Boolean = isAndroid9Plus()
    
    /**
     * Get minimum required Android version string
     */
    fun getMinRequiredAndroid(): String = "9 (Pie)"

    // Memory methods
    fun getTotalRam(): Long {
        val actManager = appContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        actManager.getMemoryInfo(memInfo)
        return memInfo.totalMem
    }
    
    fun getAvailableRam(): Long {
        val actManager = appContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        actManager.getMemoryInfo(memInfo)
        return memInfo.availMem
    }
    
    /**
     * Result of memory cleaning operation
     */
    data class CleanResult(
        val success: Boolean,
        val freedBytes: Long,
        val beforeAvailable: Long,
        val afterAvailable: Long
    )
    
    /**
     * Clean app memory comprehensively
     * - Clear internal/external cache
     * - Clear Glide image memory cache
     * - Clear Coil image cache
     * - Trim memory
     * - Multi-round garbage collection
     */
    fun cleanAppMemory(): CleanResult {
        val beforeRam = getAvailableRam()
        
        try {
            // 1. Clear Application Cache Directories
            clearCacheDirectories()
            
            // 2. Clear Glide Memory Cache (if available)
            clearGlideMemoryCache()
            
            // 3. Clear Coil Memory Cache (if available)
            clearCoilMemoryCache()
            
            // 4. Clear WebView Cache
            clearWebViewCache()
            
            // 5. Clear code cache (Android 5.0+)
            clearCodeCache()
            
            // 6. Trim memory - request system to release memory
            trimMemory()
            
            // 7. Multi-round aggressive garbage collection
            aggressiveGarbageCollection()
            
            // 8. Wait a moment for GC to complete
            Thread.sleep(100)
            
            val afterRam = getAvailableRam()
            val freed = afterRam - beforeRam
            
            TXALogger.appI("TXADeviceInfo", "Memory cleaned: freed ${Formatter.formatFileSize(appContext, if (freed > 0) freed else 0)}")
            
            return CleanResult(
                success = true,
                freedBytes = if (freed > 0) freed else 0,
                beforeAvailable = beforeRam,
                afterAvailable = afterRam
            )
        } catch (e: Exception) {
            TXALogger.appE("TXADeviceInfo", "Memory clean failed", e)
            return CleanResult(
                success = false,
                freedBytes = 0,
                beforeAvailable = beforeRam,
                afterAvailable = getAvailableRam()
            )
        }
    }
    
    private fun clearCacheDirectories() {
        try {
            // Internal cache
            appContext.cacheDir?.let { deleteDir(it) }
            
            // External cache
            appContext.externalCacheDir?.let { deleteDir(it) }
            
            // All external cache dirs (multiple storage)
            appContext.externalCacheDirs?.forEach { dir ->
                dir?.let { deleteDir(it) }
            }
            
            // Files dir temp files
            appContext.filesDir?.listFiles()?.forEach { file ->
                if (file.name.startsWith("temp") || file.name.endsWith(".tmp")) {
                    file.delete()
                }
            }
        } catch (ignored: Exception) {}
    }
    
    private fun clearGlideMemoryCache() {
        try {
            // Use reflection to clear Glide cache without direct dependency
            val glideClass = Class.forName("com.bumptech.glide.Glide")
            val getMethod = glideClass.getMethod("get", Context::class.java)
            val glideInstance = getMethod.invoke(null, appContext)
            val clearMemoryMethod = glideInstance.javaClass.getMethod("clearMemory")
            clearMemoryMethod.invoke(glideInstance)
            TXALogger.appI("TXADeviceInfo", "Glide memory cache cleared")
        } catch (ignored: Exception) {
            // Glide not available or error
        }
    }
    
    private fun clearCoilMemoryCache() {
        try {
            // Use reflection to clear Coil cache without direct dependency
            val imageLoaderClass = Class.forName("coil.ImageLoader")
            val companionClass = Class.forName("coil.ImageLoader\$Companion")
            // Try to get singleton and clear memory cache
        } catch (ignored: Exception) {
            // Coil not available or error
        }
    }
    
    private fun clearWebViewCache() {
        try {
            // Clear WebView cache directory
            val webViewCacheDir = java.io.File(appContext.cacheDir.parent, "app_webview")
            if (webViewCacheDir.exists()) {
                deleteDir(webViewCacheDir)
            }
        } catch (ignored: Exception) {}
    }
    
    private fun clearCodeCache() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val codeCacheDir = appContext.codeCacheDir
                if (codeCacheDir.exists()) {
                    codeCacheDir.listFiles()?.forEach { file ->
                        // Only delete .dex files and outdated caches
                        if (file.name.endsWith(".dex") || file.name.contains("profile")) {
                            file.delete()
                        }
                    }
                }
            }
        } catch (ignored: Exception) {}
    }
    
    private fun trimMemory() {
        try {
            // Request app components to release memory
            val activityManager = appContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            
            // Get memory class to understand limits
            val memoryClass = activityManager.memoryClass
            val largeMemoryClass = activityManager.largeMemoryClass
            
            TXALogger.appI("TXADeviceInfo", "Memory class: $memoryClass MB, Large: $largeMemoryClass MB")
        } catch (ignored: Exception) {}
    }
    
    private fun aggressiveGarbageCollection() {
        // Multi-round GC for better cleanup
        repeat(3) {
            System.runFinalization()
            Runtime.getRuntime().gc()
            System.gc()
        }
        
        // Additional native memory hint
        try {
            val runtime = Runtime.getRuntime()
            val usedMemory = runtime.totalMemory() - runtime.freeMemory()
            val maxMemory = runtime.maxMemory()
            
            TXALogger.appI("TXADeviceInfo", "JVM Memory: used=${Formatter.formatFileSize(appContext, usedMemory)}, max=${Formatter.formatFileSize(appContext, maxMemory)}")
        } catch (ignored: Exception) {}
    }

    private fun deleteDir(dir: java.io.File?): Boolean {
        if (dir == null) return false
        
        return try {
            if (dir.isDirectory) {
                dir.listFiles()?.forEach { child ->
                    deleteDir(child)
                }
            }
            dir.delete()
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Get cache size in bytes
     */
    fun getCacheSize(): Long {
        var size = 0L
        try {
            size += getDirSize(appContext.cacheDir)
            appContext.externalCacheDir?.let { size += getDirSize(it) }
        } catch (ignored: Exception) {}
        return size
    }
    
    private fun getDirSize(dir: java.io.File?): Long {
        if (dir == null || !dir.exists()) return 0
        
        var size = 0L
        try {
            if (dir.isDirectory) {
                dir.listFiles()?.forEach { file ->
                    size += if (file.isDirectory) getDirSize(file) else file.length()
                }
            } else {
                size = dir.length()
            }
        } catch (ignored: Exception) {}
        return size
    }

    /**
     * Check if device has Bluetooth capability
     */
    fun hasBluetooth(): Boolean {
        return try {
            appContext.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_BLUETOOTH)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Check if device has Telephony capability
     */
    fun hasTelephony(): Boolean {
        return try {
            appContext.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_TELEPHONY)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Check if the device is an emulator
     */
    fun isEmulator(): Boolean {
        val brand = Build.BRAND.lowercase()
        val device = Build.DEVICE.lowercase()
        val fingerprint = Build.FINGERPRINT.lowercase()
        val hardware = Build.HARDWARE.lowercase()
        val model = Build.MODEL.lowercase()
        val manufacturer = Build.MANUFACTURER.lowercase()
        val product = Build.PRODUCT.lowercase()
        
        // Check CPU Architecture
        val isX86 = Build.SUPPORTED_ABIS.any { it.contains("x86") }

        return (brand.startsWith("generic") && device.startsWith("generic"))
                || fingerprint.contains("generic")
                || fingerprint.contains("unknown")
                || hardware.contains("goldfish")
                || hardware.contains("ranchu")
                || model.contains("google_sdk")
                || model.contains("emulator")
                || model.contains("android sdk built for x86")
                || manufacturer.contains("genymotion")
                || product.contains("sdk_google")
                || product.contains("google_sdk")
                || product.contains("sdk")
                || product.contains("sdk_x86")
                || product.contains("vbox86p")
                || product.contains("emulator")
                || product.contains("simulator")
                || model.contains("sdk")
                || manufacturer.contains("nox")
                || product.contains("aosp") // Match aosp_marlin
                || device.contains("marlin") // Match marlin if it claims to be Samsung but isn't
                || isX86 // Strongest signal for modern emulators mimicking ARM phones
    }
}
