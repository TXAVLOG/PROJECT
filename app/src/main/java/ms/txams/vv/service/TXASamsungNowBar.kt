package ms.txams.vv.service

import android.os.Build
import ms.txams.vv.core.TXALogger

/**
 * TXA Samsung Now Bar Helper
 * 
 * Detect Samsung device và OneUI version để tối ưu notification.
 * 
 * Samsung Now Bar (OneUI 7+):
 * - Pill-shaped widget trên lock screen và AOD
 * - Tự động hiển thị từ MediaStyle notifications
 * - Album art, title, artist được hiển thị
 * 
 * Cách hoạt động:
 * 1. MusicService tạo MediaStyle notification với MediaSession
 * 2. Samsung tự động convert thành Now Bar trên OneUI 7+
 * 3. Không cần thêm dependency hay permission đặc biệt
 * 
 * @author TXA - fb.com/vlog.txa.2311 - txavlog7@gmail.com
 */
object TXASamsungNowBar {
    
    /**
     * Check if device is Samsung
     */
    fun isSamsungDevice(): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase()
        return manufacturer.contains("samsung")
    }
    
    /**
     * Check if device is Samsung Galaxy
     */
    fun isGalaxyDevice(): Boolean {
        if (!isSamsungDevice()) return false
        val model = Build.MODEL.lowercase()
        return model.contains("sm-") || model.contains("galaxy")
    }
    
    /**
     * Check if device supports Now Bar
     * OneUI 7 = Android 15 (API 35)
     * OneUI 6 = Android 14 (API 34) - basic support
     */
    fun supportsNowBar(): Boolean {
        if (!isSamsungDevice()) return false
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE // API 34
    }
    
    /**
     * Check if device has full Now Bar support (OneUI 7+)
     */
    fun hasFullNowBarSupport(): Boolean {
        if (!isSamsungDevice()) return false
        return Build.VERSION.SDK_INT >= 35 // Android 15
    }
    
    /**
     * Get OneUI version estimate
     */
    fun getOneUIVersion(): Int {
        if (!isSamsungDevice()) return 0
        
        return when {
            Build.VERSION.SDK_INT >= 35 -> 7 // Android 15
            Build.VERSION.SDK_INT >= 34 -> 6 // Android 14
            Build.VERSION.SDK_INT >= 33 -> 5 // Android 13
            Build.VERSION.SDK_INT >= 31 -> 4 // Android 12
            Build.VERSION.SDK_INT >= 30 -> 3 // Android 11
            else -> 2
        }
    }
    
    /**
     * Get OneUI version as string
     */
    fun getOneUIVersionString(): String {
        val version = getOneUIVersion()
        return if (version > 0) "OneUI $version" else "N/A"
    }
    
    /**
     * Get display info for Now Bar status
     */
    fun getNowBarStatus(): String {
        return when {
            !isSamsungDevice() -> "Not Samsung device"
            hasFullNowBarSupport() -> "Full Now Bar (OneUI 7+)"
            supportsNowBar() -> "Basic Now Bar (OneUI 6)"
            else -> "Not supported"
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
     * Get Android version
     */
    fun getAndroidVersion(): String = Build.VERSION.RELEASE
    
    /**
     * Get API level
     */
    fun getApiLevel(): Int = Build.VERSION.SDK_INT
    
    /**
     * Log device info for debugging
     */
    fun logDeviceInfo() {
        TXALogger.appI(buildString {
            appendLine("=== TXA Device Info ===")
            appendLine("Manufacturer: ${getManufacturer()}")
            appendLine("Model: ${getModel()}")
            appendLine("Android: ${getAndroidVersion()} (API ${getApiLevel()})")
            appendLine("Samsung: ${isSamsungDevice()}")
            appendLine("Galaxy: ${isGalaxyDevice()}")
            if (isSamsungDevice()) {
                appendLine("OneUI: ${getOneUIVersionString()}")
                appendLine("Now Bar: ${getNowBarStatus()}")
            }
            appendLine("=======================")
        })
    }
    
    /**
     * Get device info as Map (for analytics)
     */
    fun getDeviceInfoMap(): Map<String, Any> {
        return mapOf(
            "manufacturer" to getManufacturer(),
            "model" to getModel(),
            "android_version" to getAndroidVersion(),
            "api_level" to getApiLevel(),
            "is_samsung" to isSamsungDevice(),
            "is_galaxy" to isGalaxyDevice(),
            "oneui_version" to getOneUIVersion(),
            "supports_now_bar" to supportsNowBar(),
            "now_bar_status" to getNowBarStatus()
        )
    }
}
