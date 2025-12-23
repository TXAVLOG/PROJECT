package ms.txams.vv.core

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/**
 * TXA App utility class - minimal implementation for compilation
 */
object TXAApp {
    
    fun isCompatible(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    }
    
    fun getAppVersion(context: Context): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "1.0.0"
        } catch (e: PackageManager.NameNotFoundException) {
            "1.0.0"
        }
    }
}
