package com.txapp.musicplayer.util

import java.io.DataOutputStream
import java.io.File
import java.io.IOException

/**
 * TXA Su Helper - Utility for Root operations
 * Use only as a fallback for critical operations like logging when standard methods fail.
 */
object TXASuHelper {
    private const val TAG = "TXASuHelper"

    private var hasRootPermission: Boolean? = null

    /**
     * Check if the device binaries suggest it is rooted (without requesting permission)
     */
    fun isRooted(): Boolean {
        // 1. Check build tags
        val buildTags = android.os.Build.TAGS
        if (buildTags != null && buildTags.contains("test-keys")) return true

        // 2. Check common paths
        val paths = arrayOf(
            "/system/app/Superuser.apk",
            "/sbin/su",
            "/system/bin/su",
            "/system/xbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/su"
        )
        for (path in paths) {
            if (File(path).exists()) return true
        }

        // 3. Try execution check without su (sometimes su is in PATH)
        try {
            val process = Runtime.getRuntime().exec(arrayOf("which", "su"))
            if (process.waitFor() == 0) return true
        } catch (ignored: Exception) {}

        return false
    }

    /**
     * Try to create directories using root if normal mkdirs() fails
     * Only attempts root if the device is confirmed rooted and permission is granted.
     */
    fun mkdirs(directory: File): Boolean {
        if (directory.exists()) return true
        
        // 1. Try standard way
        try {
            if (directory.mkdirs()) return true
        } catch (e: Exception) {
            TXALogger.e(TAG, "Standard mkdirs exception for ${directory.absolutePath}", e)
        }
        
        // 2. If failed, try root ONLY if already verified or possible
        if (hasRootPermission == true || (hasRootPermission == null && isRooted())) {
            val path = directory.absolutePath
            TXALogger.w(TAG, "Standard mkdirs failed for $path, trying root command...")
            val success = runAsRoot("mkdir -p '$path' && chmod 777 '$path'")
            if (success) hasRootPermission = true
            return success && directory.exists()
        }
        
        return false
    }

    /**
     * Run a shell command as root
     */
    fun runAsRoot(command: String): Boolean {
        var process: Process? = null
        var os: DataOutputStream? = null
        return try {
            process = Runtime.getRuntime().exec("su")
            os = DataOutputStream(process.outputStream)
            os.writeBytes("$command\n")
            os.writeBytes("exit\n")
            os.flush()
            val exitValue = process.waitFor()
            exitValue == 0
        } catch (e: Exception) {
            TXALogger.e(TAG, "Error running as root: $command", e)
            false
        } finally {
            try { os?.close() } catch (ignored: Exception) {}
            try { process?.destroy() } catch (ignored: Exception) {}
        }
    }
    
    /**
     * Verify if we actually have root access (triggers the SU prompt)
     * Saves the result in hasRootPermission.
     */
    fun verifyRoot(): Boolean {
        if (hasRootPermission != null) return hasRootPermission!!
        
        TXALogger.i(TAG, "Verifying Root Access (triggering prompt)...")
        val success = runAsRoot("id")
        hasRootPermission = success
        return success
    }
    
    fun getRootStatus(): Boolean? = hasRootPermission

    /**
     * BOOST PERFORMANCE: Set app priority (nice value)
     * -20 is highest priority, 19 is lowest.
     */
    fun boostAppPriority(packageName: String): Boolean {
        TXALogger.i(TAG, "Root: Boosting priority for $packageName")
        // Find PID and renice
        val command = "ps -A | grep '$packageName' | awk '{print \$2}' | xargs -r renice -n -20 -p"
        return runAsRoot(command)
    }

    /**
     * AUDIO EXCLUSIVE: Kill other common music players to prevent interference
     */
    fun clearCompetingAudioApps(): Boolean {
        TXALogger.w(TAG, "Root: Clearing competing audio apps")
        val appsToKill = listOf(
            "com.spotify.music",
            "com.google.android.apps.youtube.music", 
            "com.apple.android.music",
            "deezer.android.app", 
            "com.soundcloud.android"
        )
        return appsToKill.all { runAsRoot("pkill -9 '$it'") }
    }

    /**
     * DEEP SCAN: Force system media scanner using root
     */
    fun deepRescanMedia(path: String = "/sdcard"): Boolean {
        TXALogger.i(TAG, "Root: Deep media rescan for $path")
        // Broadcast for versions that still support it, or use 'content' command
        return runAsRoot("am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d 'file://$path'") ||
                runAsRoot("content call --uri content://media --method scan_volume --arg external")
    }
    
    /**
     * SYSTEM TWEAK: Set high priority audio flag in global settings (experimental)
     */
    fun enableHighPriorityAudio(): Boolean {
        return runAsRoot("settings put global high_priority_audio 1") &&
               runAsRoot("settings put global audio_low_latency_mode 1")
    }

    /**
     * POWER OPTIMIZATION: Whitelist app from battery optimizations (Doze) via Root
     */
    fun whitelistAppFromBatteryOptimizations(packageName: String): Boolean {
        TXALogger.i(TAG, "Root: Whitelisting $packageName from battery optimizations")
        return runAsRoot("dumpsys deviceidle whitelist +$packageName")
    }

    /**
     * SYSTEM MEDIA: List audio files in system directories
     */
    fun getSystemAudioFiles(): List<String> {
        val result = mutableListOf<String>()
        val paths = listOf("/system/media/audio/ringtones", "/system/media/audio/notifications", "/system/media/audio/alarms")
        paths.forEach { path ->
            // Use shell to find files as standard File API might be restricted
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "find '$path' -type f"))
            process.inputStream.bufferedReader().useLines { lines ->
                result.addAll(lines)
            }
        }
        return result
    }
}
