package ms.txams.vv.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ms.txams.vv.BuildConfig
import ms.txams.vv.R
import ms.txams.vv.core.TXALogger
import ms.txams.vv.core.TXATranslation
import ms.txams.vv.databinding.TxaActivitySettingsBinding

/**
 * TXA Settings Activity
 * 
 * Features:
 * 1. Language Selector - Change app language
 * 2. Check Update - Check for app updates
 * 3. App Info - Version, App Set ID
 * 4. View/Clear Logs
 * 
 * @author TXA - fb.com/vlog.txa.2311 - txavlog7@gmail.com
 */
@AndroidEntryPoint
class TXASettingsActivity : AppCompatActivity() {
    
    private lateinit var binding: TxaActivitySettingsBinding
    
    // Available locales (fallback list)
    private val defaultLocales = listOf(
        LocaleItem("en", "English"),
        LocaleItem("vi", "Tiếng Việt")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = TxaActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        TXALogger.appI("TXASettingsActivity opened")
        
        setupToolbar()
        setupAppInfo()
        setupLanguageSection()
        setupUpdateSection()
        setupLogsSection()
    }
    
    private fun setupToolbar() {
        binding.toolbar.title = TXATranslation.txa("txamusic_settings_title")
        binding.toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }
    
    private fun setupAppInfo() {
        // Version
        binding.tvVersion.text = BuildConfig.VERSION_NAME
        
        // App Set ID (simplified - just show package name for now)
        binding.tvAppSetId.text = packageName.takeLast(12) + "..."
    }
    
    private fun setupLanguageSection() {
        // Show current language
        val currentLocale = TXATranslation.getCurrentLocale()
        val currentLangName = defaultLocales.find { it.code == currentLocale }?.name ?: currentLocale
        binding.tvCurrentLanguage.text = currentLangName
        
        binding.layoutChangeLanguage.setOnClickListener {
            showLanguagePickerDialog()
        }
    }
    
    private fun showLanguagePickerDialog() {
        // Show loading dialog first
        val loadingDialog = MaterialAlertDialogBuilder(this)
            .setTitle(TXATranslation.txa("txamusic_settings_change_language"))
            .setMessage(TXATranslation.txa("txamusic_msg_loading"))
            .setCancelable(false)
            .create()
        
        loadingDialog.show()
        
        lifecycleScope.launch {
            try {
                // Try to get locales from API
                val locales = withContext(Dispatchers.IO) {
                    TXATranslation.getAvailableLocales()
                } ?: defaultLocales.map { it.code }
                
                loadingDialog.dismiss()
                
                // Build locale items
                val localeItems = locales.map { code ->
                    defaultLocales.find { it.code == code } ?: LocaleItem(code, code.uppercase())
                }
                
                showLocaleSelectionDialog(localeItems)
            } catch (e: Exception) {
                loadingDialog.dismiss()
                TXALogger.appE("Failed to load locales", e)
                // Use default list
                showLocaleSelectionDialog(defaultLocales)
            }
        }
    }
    
    private fun showLocaleSelectionDialog(locales: List<LocaleItem>) {
        val currentLocale = TXATranslation.getCurrentLocale()
        val localeNames = locales.map { it.name }.toTypedArray()
        val currentIndex = locales.indexOfFirst { it.code == currentLocale }.coerceAtLeast(0)
        
        MaterialAlertDialogBuilder(this)
            .setTitle(TXATranslation.txa("txamusic_settings_change_language"))
            .setSingleChoiceItems(localeNames, currentIndex) { dialog, which ->
                val selectedLocale = locales[which]
                dialog.dismiss()
                
                if (selectedLocale.code != currentLocale) {
                    handleLocaleSelection(selectedLocale)
                }
            }
            .setNegativeButton(TXATranslation.txa("txamusic_action_cancel"), null)
            .show()
    }
    
    private fun handleLocaleSelection(locale: LocaleItem) {
        TXALogger.appI("Changing language to: ${locale.code}")
        
        lifecycleScope.launch {
            try {
                // Save locale preference
                saveLocaleTag(locale.code)
                
                // Re-init translation with new locale
                TXATranslation.init(applicationContext, locale.code)
                
                // Force sync for new locale
                TXATranslation.forceSync(locale.code)
                
                // Show success message
                Toast.makeText(
                    this@TXASettingsActivity,
                    TXATranslation.txa("txamusic_language_change_success"),
                    Toast.LENGTH_SHORT
                ).show()
                
                // Restart app to apply changes
                restartApp()
            } catch (e: Exception) {
                TXALogger.appE("Language change failed", e)
                Toast.makeText(
                    this@TXASettingsActivity,
                    TXATranslation.txa("txamusic_language_change_failed").replace("%s", e.message ?: "Unknown"),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
    
    private fun saveLocaleTag(localeCode: String) {
        getSharedPreferences("txa_prefs", MODE_PRIVATE)
            .edit()
            .putString("locale", localeCode)
            .apply()
    }
    
    private fun restartApp() {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        finishAffinity()
    }
    
    private fun setupUpdateSection() {
        binding.btnCheckUpdate.text = TXATranslation.txa("txamusic_settings_check_update")
        
        binding.btnCheckUpdate.setOnClickListener {
            checkForUpdate()
        }
    }
    
    private fun checkForUpdate() {
        TXALogger.appI("Checking for updates...")
        
        // Show loading
        val loadingDialog = MaterialAlertDialogBuilder(this)
            .setTitle(TXATranslation.txa("txamusic_update_checking"))
            .setMessage(TXATranslation.txa("txamusic_msg_please_wait"))
            .setCancelable(false)
            .create()
        
        loadingDialog.show()
        
        lifecycleScope.launch {
            try {
                // TODO: Implement TXAUpdateManager.check()
                kotlinx.coroutines.delay(2000) // Simulate API call
                
                loadingDialog.dismiss()
                
                // For now, show "No update available"
                MaterialAlertDialogBuilder(this@TXASettingsActivity)
                    .setTitle(TXATranslation.txa("txamusic_update_not_available"))
                    .setMessage("You are using the latest version: ${BuildConfig.VERSION_NAME}")
                    .setPositiveButton(TXATranslation.txa("txamusic_action_ok"), null)
                    .show()
                
            } catch (e: Exception) {
                loadingDialog.dismiss()
                TXALogger.appE("Update check failed", e)
                
                MaterialAlertDialogBuilder(this@TXASettingsActivity)
                    .setTitle(TXATranslation.txa("txamusic_msg_error"))
                    .setMessage(TXATranslation.txa("txamusic_error_update_check_failed"))
                    .setPositiveButton(TXATranslation.txa("txamusic_action_ok"), null)
                    .show()
            }
        }
    }
    
    private fun setupLogsSection() {
        binding.btnViewLogs.setOnClickListener {
            showLogsDialog()
        }
        
        binding.btnClearLogs.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("Clear Logs")
                .setMessage("Are you sure you want to clear all log files?")
                .setPositiveButton(TXATranslation.txa("txamusic_action_yes")) { _, _ ->
                    TXALogger.clearAllLogs()
                    Toast.makeText(this, "Logs cleared", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton(TXATranslation.txa("txamusic_action_no"), null)
                .show()
        }
    }
    
    private fun showLogsDialog() {
        lifecycleScope.launch(Dispatchers.IO) {
            val logFiles = TXALogger.getAllLogFiles()
            val totalSize = TXALogger.getTotalLogsSize()
            
            val logsInfo = buildString {
                appendLine("=== TXA Logs ===")
                appendLine("Total files: ${logFiles.size}")
                appendLine("Total size: ${formatBytes(totalSize)}")
                appendLine()
                appendLine("Device Info:")
                appendLine(TXALogger.getDeviceInfo())
                appendLine()
                appendLine("Log files:")
                logFiles.forEach { file ->
                    appendLine("• ${file.name} (${formatBytes(file.length())})")
                }
                
                if (logFiles.isNotEmpty()) {
                    appendLine()
                    appendLine("=== Recent Logs ===")
                    // Show last 50 lines from most recent log
                    val recentLog = logFiles.maxByOrNull { it.lastModified() }
                    recentLog?.let { file ->
                        try {
                            val lines = file.readLines().takeLast(50)
                            lines.forEach { appendLine(it) }
                        } catch (e: Exception) {
                            appendLine("Error reading log: ${e.message}")
                        }
                    }
                }
            }
            
            withContext(Dispatchers.Main) {
                MaterialAlertDialogBuilder(this@TXASettingsActivity)
                    .setTitle("Logs")
                    .setMessage(logsInfo)
                    .setPositiveButton(TXATranslation.txa("txamusic_action_close"), null)
                    .show()
            }
        }
    }
    
    private fun formatBytes(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> "${bytes / (1024 * 1024)} MB"
        }
    }
    
    data class LocaleItem(val code: String, val name: String)
}
