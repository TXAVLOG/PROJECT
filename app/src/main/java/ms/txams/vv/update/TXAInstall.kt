package ms.txams.vv.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import ms.txams.vv.core.TXALogger
import java.io.File

/**
 * TXA APK Installer
 * 
 * Features:
 * - Install APK using FileProvider (content:// URI)
 * - Save pending APK path for cleanup
 * - Cleanup pending APK after install
 * 
 * @author TXA - fb.com/vlog.txa.2311 - txavlog7@gmail.com
 */
object TXAInstall {
    
    private const val PREF_NAME = "txa_install_prefs"
    private const val KEY_PENDING_APK_PATH = "pending_apk_path"
    
    /**
     * Install APK file
     * Opens system installer with the APK
     */
    fun installApk(context: Context, apkFile: File) {
        TXALogger.downloadI("Installing APK: ${apkFile.absolutePath}")
        
        if (!apkFile.exists()) {
            TXALogger.downloadE("APK file not found: ${apkFile.absolutePath}")
            return
        }
        
        try {
            // Save pending APK path for cleanup
            savePendingApkPath(context, apkFile.absolutePath)
            
            // Get content URI via FileProvider
            val authority = "${context.packageName}.provider"
            val contentUri: Uri = FileProvider.getUriForFile(context, authority, apkFile)
            
            TXALogger.downloadD("Content URI: $contentUri")
            
            // Create install intent
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            
            context.startActivity(installIntent)
            TXALogger.downloadI("Install intent started")
            
        } catch (e: Exception) {
            TXALogger.downloadE("Install APK failed", e)
            throw e
        }
    }
    
    /**
     * Check if app has permission to install unknown apps
     */
    fun hasInstallPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true // Permission not required before Android 8.0
        }
    }
    
    /**
     * Open settings to enable install from unknown sources
     */
    fun openInstallPermissionSettings(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val intent = Intent(
                    android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")
                )
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }
        } catch (e: Exception) {
            TXALogger.downloadE("Failed to open install permission settings", e)
        }
    }
    
    /**
     * Save pending APK path to SharedPreferences
     */
    private fun savePendingApkPath(context: Context, path: String) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PENDING_APK_PATH, path)
            .apply()
        TXALogger.downloadD("Saved pending APK path: $path")
    }
    
    /**
     * Get pending APK path
     */
    fun getPendingApkPath(context: Context): String? {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(KEY_PENDING_APK_PATH, null)
    }
    
    /**
     * Clear pending APK path
     */
    fun clearPendingApkPath(context: Context) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_PENDING_APK_PATH)
            .apply()
        TXALogger.downloadD("Cleared pending APK path")
    }
    
    /**
     * Cleanup pending APK file (call on app startup after install)
     * Deletes the APK file if it exists and clears the preference
     */
    fun cleanupPendingApk(context: Context) {
        val pendingPath = getPendingApkPath(context) ?: return
        
        TXALogger.downloadI("Cleaning up pending APK: $pendingPath")
        
        try {
            val file = File(pendingPath)
            if (file.exists()) {
                val deleted = file.delete()
                TXALogger.downloadI("Pending APK deleted: $deleted")
            }
        } catch (e: Exception) {
            TXALogger.downloadE("Failed to cleanup pending APK", e)
        } finally {
            clearPendingApkPath(context)
        }
    }
    
    /**
     * Delete all APK files in updates directory
     */
    fun cleanupAllApks(context: Context) {
        try {
            val updatesDir = TXADownload.getUpdatesDir(context)
            updatesDir.listFiles()?.forEach { file ->
                if (file.name.endsWith(".apk")) {
                    file.delete()
                    TXALogger.downloadD("Deleted APK: ${file.name}")
                }
            }
            clearPendingApkPath(context)
            TXALogger.downloadI("All APKs cleaned up")
        } catch (e: Exception) {
            TXALogger.downloadE("Failed to cleanup APKs", e)
        }
    }
}
