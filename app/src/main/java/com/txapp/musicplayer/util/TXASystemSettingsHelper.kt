package com.txapp.musicplayer.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * TXASystemSettingsHelper - Utility for modifying system settings using WRITE_SETTINGS permission.
 * Includes features like setting ringtones, adjusting brightness, and volume control.
 */
object TXASystemSettingsHelper {
    private const val TAG = "TXASystemSettingsHelper"

    /**
     * Check if we have permission to write system settings
     */
    fun canWriteSettings(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.System.canWrite(context)
        } else {
            true // Prior to M, permission was granted at install time
        }
    }

    /**
     * Request the WRITE_SETTINGS permission from the user
     */
    fun requestWriteSettingsPermission(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }



    /**
     * Set system brightness level (0-255)
     */
    fun setBrightness(context: Context, brightness: Int): Boolean {
        if (!canWriteSettings(context)) return false
        
        return try {
            // Ensure manual brightness mode
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            )
            // Set brightness
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                brightness.coerceIn(0, 255)
            )
            TXALogger.d(TAG, "Brightness set to $brightness")
            true
        } catch (e: Exception) {
            TXALogger.e(TAG, "Error setting brightness", e)
            false
        }
    }

    /**
     * Toggle System Auto-Rotation
     */
    fun setAutoRotation(context: Context, enabled: Boolean): Boolean {
        if (!canWriteSettings(context)) return false
        return try {
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.ACCELEROMETER_ROTATION,
                if (enabled) 1 else 0
            )
            true
        } catch (e: Exception) {
            false
        }
    }
}
