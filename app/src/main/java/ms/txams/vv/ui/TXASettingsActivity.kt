package ms.txams.vv.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
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
import ms.txams.vv.update.TXAUpdateManager
import ms.txams.vv.update.TXAUpdateWorker
import ms.txams.vv.core.TXAApp
import ms.txams.vv.core.TXAFormat
import ms.txams.vv.update.UpdateCheckResult
import ms.txams.vv.update.TXAUpdatePhase
import ms.txams.vv.databinding.TxaDialogDownloadProgressBinding
import ms.txams.vv.databinding.TxaDialogUpdateChangelogBinding
import android.webkit.WebView
import android.webkit.WebViewClient
import android.app.AlertDialog
import android.view.LayoutInflater

/**
 * TXA Settings Activity
 * 
 * Features:
 * 1. Language Selector
 * 2. Check Update & Background Update Toggle
 * 3. Theme Selector (Dark/Light/System)
 * 4. App Info
 * 5. Logs Management
 * 
 * @author TXA - fb.com/vlog.txa.2311 - txavlog7@gmail.com
 */
@AndroidEntryPoint
class TXASettingsActivity : BaseActivity() {
    
    private lateinit var binding: TxaActivitySettingsBinding
    
    private val defaultLocales = listOf(
        LocaleItem("en", "English"),
        LocaleItem("vi", "Tiếng Việt")
    )
    
    // Theme constants
    private val THEME_SYSTEM = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
    private val THEME_LIGHT = AppCompatDelegate.MODE_NIGHT_NO
    private val THEME_DARK = AppCompatDelegate.MODE_NIGHT_YES

    private val requestRoleLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) {
        updateDefaultPlayerUI()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = TxaActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        TXALogger.appI("TXASettingsActivity opened")
        
        setupToolbar()
        setupAppInfo()
        setupLanguageSection()
        setupFontSection()
        setupAppearanceSection()
        setupUpdateSection()
        setupDefaultPlayerSection()
        setupLogsSection()
        setupAboutSection()
    }

    override fun onResume() {
        super.onResume()
        updateDefaultPlayerUI()
    }
    
    private fun setupToolbar() {
        binding.toolbar.title = TXATranslation.txa("txamusic_settings_title")
        binding.toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }
    
    private fun setupAppInfo() {
        binding.tvAppInfoTitle.text = TXATranslation.txa("txamusic_settings_app_info")
        binding.tvVersionTitle.text = TXATranslation.txa("txamusic_settings_version")
        binding.tvAppSetIdTitle.text = TXATranslation.txa("txamusic_settings_app_set_id")
        
        binding.tvVersion.text = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
        binding.tvAppSetId.text = packageName
    }
    
    private fun setupAppearanceSection() {
        binding.tvAppearanceTitle.text = TXATranslation.txa("txamusic_settings_appearance")
        binding.tvThemeTitle.text = TXATranslation.txa("txamusic_settings_theme_mode")

        val currentTheme = getSavedThemeMode()
        binding.tvCurrentTheme.text = when (currentTheme) {
            THEME_LIGHT -> TXATranslation.txa("txamusic_theme_light")
            THEME_DARK -> TXATranslation.txa("txamusic_theme_dark")
            else -> TXATranslation.txa("txamusic_theme_system")
        }
        
        binding.layoutTheme.setOnClickListener {
            showThemeSelectionDialog()
        }
    }
    
    private fun showThemeSelectionDialog() {
        val themes = arrayOf(
            TXATranslation.txa("txamusic_theme_system"),
            TXATranslation.txa("txamusic_theme_light"),
            TXATranslation.txa("txamusic_theme_dark")
        )
        
        val currentTheme = getSavedThemeMode()
        val checkedItem = when (currentTheme) {
            THEME_LIGHT -> 1
            THEME_DARK -> 2
            else -> 0
        }
        
        MaterialAlertDialogBuilder(this)
            .setTitle(TXATranslation.txa("txamusic_theme_title"))
            .setSingleChoiceItems(themes, checkedItem) { dialog, which ->
                val selectedTheme = when (which) {
                    1 -> THEME_LIGHT
                    2 -> THEME_DARK
                    else -> THEME_SYSTEM
                }
                
                saveThemeMode(selectedTheme)
                AppCompatDelegate.setDefaultNightMode(selectedTheme)
                dialog.dismiss()
            }
            .setNegativeButton(TXATranslation.txa("txamusic_action_cancel"), null)
            .show()
    }
    
    private fun getSavedThemeMode(): Int {
        return getSharedPreferences("txa_prefs", MODE_PRIVATE)
            .getInt("theme_mode", THEME_SYSTEM)
    }

    private fun saveThemeMode(mode: Int) {
        getSharedPreferences("txa_prefs", MODE_PRIVATE)
            .edit()
            .putInt("theme_mode", mode)
            .apply()
    }
    
    // --- FONT SECTION ---

    data class FontItem(val name: String, val resId: Int)

    private val availableFonts = listOf(
        FontItem("Soyuz Grotesk", R.font.soyuz_grotesk),
        FontItem("Outfit", R.font.outfit),
        FontItem("Montserrat", R.font.montserrat),
        FontItem("Inter", R.font.inter)
    )

    private fun setupFontSection() {
        binding.tvFontSectionTitle.text = TXATranslation.txa("txamusic_settings_font")
        binding.tvChangeFont.text = TXATranslation.txa("txamusic_settings_change_font")

        val currentFontName = getSavedFontName()
        binding.tvCurrentFont.text = currentFontName
        
        binding.layoutChangeFont.setOnClickListener {
            showFontSelectionDialog()
        }
    }

    private fun showFontSelectionDialog() {
        val currentFontName = getSavedFontName()
        val currentIndex = availableFonts.indexOfFirst { it.name == currentFontName }.coerceAtLeast(0)

        val adapter = object : android.widget.ArrayAdapter<FontItem>(
            this,
            android.R.layout.select_dialog_singlechoice,
            availableFonts
        ) {
            override fun getView(position: Int, convertView: android.view.View?, parent: android.view.ViewGroup): android.view.View {
                val view = super.getView(position, convertView, parent) as android.widget.TextView
                val font = getItem(position)
                if (font != null) {
                    view.text = font.name
                    try {
                        view.typeface = androidx.core.content.res.ResourcesCompat.getFont(this@TXASettingsActivity, font.resId)
                    } catch (e: Exception) {
                        // Use default typeface if font fails to load
                        view.typeface = android.graphics.Typeface.DEFAULT
                    }
                }
                return view
            }
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(TXATranslation.txa("txamusic_settings_change_font"))
            .setSingleChoiceItems(adapter, currentIndex) { dialog, which ->
                handleFontSelection(availableFonts[which])
                dialog.dismiss()
            }
            .setNegativeButton(TXATranslation.txa("txamusic_action_cancel"), null)
            .show()
    }

    private fun handleFontSelection(font: FontItem) {
        saveFontSelection(font.name)
        binding.tvCurrentFont.text = font.name
        
        MaterialAlertDialogBuilder(this)
            .setTitle(TXATranslation.txa("txamusic_font_apply_title"))
            .setMessage(TXATranslation.txa("txamusic_font_apply_message"))
            .setPositiveButton(TXATranslation.txa("txamusic_font_restart_now")) { _, _ ->
                restartApp()
            }
            .setNegativeButton(TXATranslation.txa("txamusic_font_later"), null)
            .show()
    }

    private fun getSavedFontName(): String {
        val locale = TXATranslation.getCurrentLocale()
        return getSharedPreferences("txa_prefs", MODE_PRIVATE)
            .getString("font_selection_$locale", availableFonts[0].name) ?: availableFonts[0].name
    }

    private fun saveFontSelection(fontName: String) {
        val locale = TXATranslation.getCurrentLocale()
        getSharedPreferences("txa_prefs", MODE_PRIVATE)
            .edit()
            .putString("font_selection_$locale", fontName)
            .apply()
        
        TXAApp.clearFontCache()
    }

    private fun setupLanguageSection() {
        binding.tvLanguageTitle.text = TXATranslation.txa("txamusic_settings_language")
        binding.tvChangeLanguageTitle.text = TXATranslation.txa("txamusic_settings_change_language")

        val currentLocale = TXATranslation.getCurrentLocale()
        val currentLangName = defaultLocales.find { it.code == currentLocale }?.name ?: currentLocale
        binding.tvCurrentLanguage.text = currentLangName
        
        binding.layoutChangeLanguage.setOnClickListener {
            showLanguagePickerDialog()
        }
    }
    
    private fun showLanguagePickerDialog() {
        lifecycleScope.launch {
            // Get available locales
            val locales = withContext(Dispatchers.IO) {
                try {
                    TXATranslation.getAvailableLocales()
                } catch (e: Exception) {
                    null
                }
            } ?: defaultLocales.map { it.code }
            
            val localeItems = locales.map { code ->
                defaultLocales.find { it.code == code } ?: LocaleItem(code, code.uppercase())
            }
            
            showLocaleSelectionDialog(localeItems)
        }
    }
    
    private fun showLocaleSelectionDialog(locales: List<LocaleItem>) {
        val currentLocale = TXATranslation.getCurrentLocale()
        val localeNames = locales.map { it.name }.toTypedArray()
        val currentIndex = locales.indexOfFirst { it.code == currentLocale }.coerceAtLeast(0)
        
        MaterialAlertDialogBuilder(this)
            .setTitle(TXATranslation.txa("txamusic_settings_change_language"))
            .setSingleChoiceItems(localeNames, currentIndex) { dialog, which ->
                handleLocaleSelection(locales[which])
                dialog.dismiss()
            }
            .setNegativeButton(TXATranslation.txa("txamusic_action_cancel"), null)
            .show()
    }
    
    private fun handleLocaleSelection(locale: LocaleItem) {
        lifecycleScope.launch {
            try {
                saveLocaleTag(locale.code)
                TXATranslation.init(applicationContext, locale.code)
                TXATranslation.forceSync(locale.code)
                restartApp()
            } catch (e: Exception) {
                Toast.makeText(this@TXASettingsActivity, TXATranslation.txa("txamusic_error_lang_change"), Toast.LENGTH_SHORT).show()
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
        binding.tvUpdateTitle.text = TXATranslation.txa("txamusic_settings_update")
        binding.btnCheckUpdate.text = TXATranslation.txa("txamusic_settings_check_update")
        
        binding.btnCheckUpdate.setOnClickListener {
            checkForUpdate()
        }
    }
    
    private fun checkForUpdate() {
        val loadingDialog = MaterialAlertDialogBuilder(this)
            .setTitle(TXATranslation.txa("txamusic_update_checking"))
            .setMessage(TXATranslation.txa("txamusic_msg_please_wait"))
            .setCancelable(false)
            .create()
        
        loadingDialog.show()
        
        lifecycleScope.launch {
            try {
                // Use TXAUpdateManager
                when (val result = TXAUpdateManager.checkForUpdate(this@TXASettingsActivity)) {
                    is UpdateCheckResult.UpdateAvailable -> {
                         loadingDialog.dismiss()
                         showUpdateDialog(result.updateInfo)
                    }
                    is UpdateCheckResult.NoUpdate -> {
                        loadingDialog.dismiss()
                        MaterialAlertDialogBuilder(this@TXASettingsActivity)
                            .setTitle(TXATranslation.txa("txamusic_update_not_available"))
                            .setMessage(TXATranslation.txa("txamusic_update_latest"))
                            .setPositiveButton(TXATranslation.txa("txamusic_action_ok"), null)
                            .show()
                    }
                    is UpdateCheckResult.Error -> {
                        loadingDialog.dismiss()
                        Toast.makeText(this@TXASettingsActivity, TXATranslation.txa("txamusic_error_prefix").format(result.message), Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                loadingDialog.dismiss()
                TXALogger.appE("Update check failed", e)
                Toast.makeText(this@TXASettingsActivity, TXATranslation.txa("txamusic_error_update_check_failed"), Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun showUpdateDialog(updateInfo: ms.txams.vv.update.UpdateInfo) {
        val dialogBinding = TxaDialogUpdateChangelogBinding.inflate(layoutInflater)
        
        val dialog = AlertDialog.Builder(this, com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog)
            .setView(dialogBinding.root)
            .create()
        
        // Setup header with translations
        dialogBinding.tvUpdateTitle.text = TXATranslation.txa("txamusic_update_available")
        dialogBinding.tvUpdateVersion.text = TXATranslation.txa("txamusic_update_version_label").format(updateInfo.versionName)
        dialogBinding.tvUpdateSize.text = "${TXATranslation.txa("txamusic_update_size")}: ${TXAFormat.formatBytes(updateInfo.downloadSizeBytes)}"
        dialogBinding.tvUpdateDate.text = TXATranslation.txa("txamusic_update_release_date").format(updateInfo.releaseDate)
        
        // Setup buttons with translations
        dialogBinding.btnCancel.text = TXATranslation.txa("txamusic_action_cancel")
        dialogBinding.btnUpdate.text = TXATranslation.txa("txamusic_action_update")
        
        // Setup WebView for changelog
        dialogBinding.webViewChangelog.apply {
            settings.javaScriptEnabled = false
            settings.domStorageEnabled = true
            settings.builtInZoomControls = false
            settings.displayZoomControls = false
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    dialogBinding.progressChangelog.visibility = android.view.View.GONE
                }
            }
            
            // Load changelog HTML content
            val changelogHtml = buildString {
                append("<!DOCTYPE html><html><head>")
                append("<meta charset='UTF-8'>")
                append("<meta name='viewport' content='width=device-width, initial-scale=1.0'>")
                append("<style>")
                append("body { font-family: -apple-system, BlinkMacSystemFont, sans-serif; padding: 16px; margin: 0; color: #E0E0E0; background: transparent; line-height: 1.6; }")
                append("h1, h2, h3 { color: #BB86FC; margin-top: 0; }")
                append("ul { padding-left: 20px; }")
                append("li { margin-bottom: 8px; }")
                append(".emoji { font-size: 1.1em; }")
                append("</style></head><body>")
                append(updateInfo.changelog)
                append("</body></html>")
            }
            loadDataWithBaseURL(null, changelogHtml, "text/html", "UTF-8", null)
        }
        
        dialogBinding.btnCancel.setOnClickListener {
            dialog.dismiss()
        }
        
        dialogBinding.btnUpdate.setOnClickListener {
            dialog.dismiss()
            startUpdateDownload(updateInfo)
        }
        
        dialog.show()
    }
    
    private fun startUpdateDownload(updateInfo: ms.txams.vv.update.UpdateInfo) {
        val dialogBinding = TxaDialogDownloadProgressBinding.inflate(layoutInflater)
        
        val dialog = AlertDialog.Builder(this, com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog)
            .setView(dialogBinding.root)
            .setCancelable(false)
            .create()
        
        // Setup initial UI with translations
        dialogBinding.tvDownloadTitle.text = TXATranslation.txa("txamusic_update_downloading")
        dialogBinding.tvDownloadStatus.text = TXATranslation.txa("txamusic_download_connecting")
        dialogBinding.tvProgressPercent.text = "0%"
        dialogBinding.tvDownloadSizeInfo.text = "0 KB / 0 KB"
        dialogBinding.tvDownloadSpeed.text = "0 KB/s"
        dialogBinding.tvDownloadEta.text = "--:--"
        
        dialog.show()
        
        lifecycleScope.launch {
            TXAUpdateManager.downloadUpdate(this@TXASettingsActivity, updateInfo)
                .collect { phase ->
                    when (phase) {
                        is TXAUpdatePhase.Starting -> {
                            dialogBinding.tvDownloadStatus.text = TXATranslation.txa("txamusic_download_starting")
                        }
                        is TXAUpdatePhase.Resolving -> {
                            dialogBinding.tvDownloadStatus.text = TXATranslation.txa("txamusic_download_resolving")
                        }
                        is TXAUpdatePhase.Connecting -> {
                            dialogBinding.tvDownloadStatus.text = TXATranslation.txa("txamusic_download_connecting")
                        }
                        is TXAUpdatePhase.Downloading -> {
                            val percent = phase.progressPercent
                            dialogBinding.tvProgressPercent.text = "${TXAFormat.formatTwoDigits(percent)}%"
                            dialogBinding.progressBar.progress = percent
                            dialogBinding.cpDownloadProgress.progress = percent
                            dialogBinding.tvDownloadSizeInfo.text = TXAFormat.formatProgressDetail(phase.downloadedBytes, phase.totalBytes)
                            dialogBinding.tvDownloadSpeed.text = TXAFormat.formatSpeed(phase.speed.toLong())
                            dialogBinding.tvDownloadEta.text = TXAFormat.formatTimeRemaining(phase.etaSeconds * 1000L)
                            dialogBinding.tvDownloadStatus.text = TXATranslation.txa("txamusic_download_downloading")
                        }
                        is TXAUpdatePhase.Retrying -> {
                            dialogBinding.tvDownloadStatus.text = "${TXATranslation.txa("txamusic_download_retrying")} (${phase.attempt}/${phase.maxAttempts})"
                        }
                        is TXAUpdatePhase.Validating -> {
                            dialogBinding.tvDownloadStatus.text = TXATranslation.txa("txamusic_download_validating")
                            dialogBinding.progressBar.isIndeterminate = true
                        }
                        is TXAUpdatePhase.ChecksumMismatch -> {
                            dialog.dismiss()
                            // Show integrity check failed dialog
                            MaterialAlertDialogBuilder(this@TXASettingsActivity)
                                .setTitle(TXATranslation.txa("txamusic_msg_error"))
                                .setMessage(TXATranslation.txa("txamusic_integrity_check_failed"))
                                .setPositiveButton(TXATranslation.txa("txamusic_action_ok"), null)
                                .show()
                        }
                        is TXAUpdatePhase.ReadyToInstall -> {
                            dialog.dismiss()
                            TXAUpdateManager.installUpdate(this@TXASettingsActivity)
                        }
                        is TXAUpdatePhase.Error -> {
                            dialog.dismiss()
                            Toast.makeText(this@TXASettingsActivity, "${TXATranslation.txa("txamusic_download_error")}: ${phase.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
        }
    }

    private fun setupDefaultPlayerSection() {
        binding.tvDefaultPlayerTitle.text = TXATranslation.txa("txamusic_settings_default_player")
        binding.btnSetDefault.text = TXATranslation.txa("txamusic_settings_set_default")
        
        binding.btnSetDefault.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val roleManager = getSystemService(android.app.role.RoleManager::class.java)
                if (roleManager != null && roleManager.isRoleAvailable("android.app.role.MUSIC")) {
                    val intent = roleManager.createRequestRoleIntent("android.app.role.MUSIC")
                    requestRoleLauncher.launch(intent)
                } else {
                    openDefaultAppsSettings()
                }
            } else {
                openDefaultAppsSettings()
            }
        }
        
        updateDefaultPlayerUI()
    }

    private fun openDefaultAppsSettings() {
        try {
            val intent = Intent(android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
            startActivity(intent)
        } catch (e: Exception) {
            try {
                val intent = Intent(android.provider.Settings.ACTION_SETTINGS)
                startActivity(intent)
            } catch (e2: Exception) {
                Toast.makeText(this, "Could not open settings", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateDefaultPlayerUI() {
        val isDefault = isDefaultMusicPlayer()
        if (isDefault) {
            binding.tvDefaultStatus.text = TXATranslation.txa("txamusic_settings_status_default")
            binding.tvDefaultStatus.setTextColor(getColor(R.color.txa_primary))
            binding.btnSetDefault.visibility = android.view.View.GONE
        } else {
            binding.tvDefaultStatus.text = TXATranslation.txa("txamusic_settings_status_not_default")
            binding.tvDefaultStatus.setTextColor(getColor(R.color.txa_on_surface_variant))
            binding.btnSetDefault.visibility = android.view.View.VISIBLE
        }
    }

    private fun isDefaultMusicPlayer(): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(android.net.Uri.parse("file:///sdcard/dummy.mp3"), "audio/mp3")
            }
            val resolveInfo = packageManager.resolveActivity(intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
            resolveInfo?.activityInfo?.packageName == packageName
        } catch (e: Exception) {
            false
        }
    }

    private fun setupLogsSection() {
        binding.tvDeveloperTitle.text = TXATranslation.txa("txamusic_settings_developer")
        binding.btnViewLogs.text = TXATranslation.txa("txamusic_settings_view_logs")
        binding.btnClearLogs.text = TXATranslation.txa("txamusic_settings_clear_logs")

        binding.btnViewLogs.setOnClickListener { showLogsDialog() }
        binding.btnClearLogs.setOnClickListener {
            TXALogger.clearAllLogs()
            Toast.makeText(this, TXATranslation.txa("txamusic_msg_logs_cleared"), Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun setupAboutSection() {
        binding.tvAboutAppTitle.text = TXATranslation.txa("txamusic_app_name")
        binding.tvAboutBuildBy.text = TXATranslation.txa("txamusic_settings_build_by").format("TXA")
    }
    
    private fun showLogsDialog() {
        lifecycleScope.launch(Dispatchers.IO) {
            val logSummary = TXALogger.getAllLogFiles().joinToString("\n") { 
                "${it.name} (${it.length() / 1024} KB)" 
            }
            
            withContext(Dispatchers.Main) {
                MaterialAlertDialogBuilder(this@TXASettingsActivity)
                    .setTitle(TXATranslation.txa("txamusic_logs_title"))
                    .setMessage(logSummary.ifEmpty { TXATranslation.txa("txamusic_logs_empty") })
                    .setPositiveButton(TXATranslation.txa("txamusic_action_close"), null)
                    .show()
            }
        }
    }
    
    data class LocaleItem(val code: String, val name: String)
}
