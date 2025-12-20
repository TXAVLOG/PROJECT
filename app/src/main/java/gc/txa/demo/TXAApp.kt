package gc.txa.demo

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import gc.txa.demo.core.TXATranslation
import gc.txa.demo.update.TXAUpdateWorker
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit

class TXAApp : Application() {

    companion object {
        private const val PREF_NAME = "txa_demo_prefs"
        private const val KEY_LOCALE = "app_locale"
        private const val DEFAULT_LOCALE = "vi"

        fun getLocale(context: Context): String {
            val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            return prefs.getString(KEY_LOCALE, DEFAULT_LOCALE) ?: DEFAULT_LOCALE
        }

        fun setLocale(context: Context, localeTag: String) {
            val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(KEY_LOCALE, localeTag).apply()
        }
    }

    override fun onCreate() {
        super.onCreate()
        
        // Apply saved locale
        applyLocale()
        
        // Clean old APK files
        cleanOldApkFiles()
        
        // Initialize WorkManager for periodic update checks
        scheduleUpdateCheck()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyLocale()
    }

    @Suppress("DEPRECATION")
    private fun applyLocale() {
        val localeTag = getLocale(this)
        val locale = Locale(localeTag)
        Locale.setDefault(locale)
        
        val config = Configuration(resources.configuration)
        config.setLocale(locale)
        resources.updateConfiguration(config, resources.displayMetrics)
    }

    private fun cleanOldApkFiles() {
        try {
            val downloadDir = File("/storage/emulated/0/Download/TXADEMO")
            if (downloadDir.exists() && downloadDir.isDirectory) {
                downloadDir.listFiles()?.forEach { file ->
                    if (file.isFile && file.name.endsWith(".apk")) {
                        val ageInDays = (System.currentTimeMillis() - file.lastModified()) / (1000 * 60 * 60 * 24)
                        if (ageInDays > 7) { // Delete APK files older than 7 days
                            file.delete()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun scheduleUpdateCheck() {
        val updateWorkRequest = PeriodicWorkRequestBuilder<TXAUpdateWorker>(
            3, TimeUnit.MINUTES
        ).build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "txa_update_check",
            ExistingPeriodicWorkPolicy.KEEP,
            updateWorkRequest
        )
    }
}
