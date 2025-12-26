package ms.txams.vv.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import ms.txams.vv.core.TXALogger

/**
 * TXA Permission Manager
 * Handles complex permission flow for "All Files Access" and legacy storage
 */
object TXAPermissionManager {

    /**
     * Check if "All Files Access" is granted
     */
    fun hasAllFilesAccess(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            context.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Request "All Files Access"
     * Opens system settings on Android 11+, or triggers standard request on older versions
     */
    fun requestAllFilesAccess(activity: Activity, requestCode: Int) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:${activity.packageName}")
                }
                activity.startActivityForResult(intent, requestCode)
            } else {
                activity.requestPermissions(
                    arrayOf(
                        android.Manifest.permission.READ_EXTERNAL_STORAGE,
                        android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                    ),
                    requestCode
                )
            }
        } catch (e: Exception) {
            TXALogger.appE("Failed to request all files access", e)
            // Fallback for some ROMs where ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION might fail
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    activity.startActivityForResult(intent, requestCode)
                } catch (e2: Exception) {
                    TXALogger.appE("Total fail requesting permissions", e2)
                }
            }
        }
    }
}
