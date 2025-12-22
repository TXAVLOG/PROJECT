package ms.txams.vv.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import ms.txams.vv.TXAApp
import ms.txams.vv.core.TXATranslation
import ms.txams.vv.core.TXALog
import ms.txams.vv.databinding.ActivityTxaSplashBinding
import ms.txams.vv.update.TXAInstall
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class TXASplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTxaSplashBinding
    private val PERMISSION_REQUEST_CODE = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTxaSplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        checkAndRequestPermissions()
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf<String>()

        // Storage permissions for Android 9
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }

        if (permissions.isNotEmpty()) {
            updateStatus(TXATranslation.txa("txamusic_splash_requesting_permissions"))
            ActivityCompat.requestPermissions(
                this,
                permissions.toTypedArray(),
                PERMISSION_REQUEST_CODE
            )
        } else {
            checkInstallPermission()
        }
    }

    private fun checkInstallPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!TXAInstall.canInstallPackages(this)) {
                updateStatus(TXATranslation.txa("txamusic_permission_install_message"))
                // For now, just continue - user can grant later when needed
                lifecycleScope.launch {
                    delay(2000)
                    startLanguageSync()
                }
            } else {
                startLanguageSync()
            }
        } else {
            startLanguageSync()
        }
    }

    private fun startLanguageSync() {
        lifecycleScope.launch {
            updateStatus(TXATranslation.txa("txamusic_splash_checking_language"))
            
            val locale = TXAApp.getLocale(this@TXASplashActivity)
            TXALog.i("SplashActivity", "Starting language sync for locale: $locale")
            
            updateStatus(TXATranslation.txa("txamusic_splash_downloading_language"))
            
            val result = TXATranslation.syncIfNewer(this@TXASplashActivity, locale)
            
            when (result) {
                is TXATranslation.SyncResult.Success -> {
                    TXALog.i("SplashActivity", "Language sync successful for locale: $locale")
                    updateStatus(TXATranslation.txa("txamusic_splash_language_updated"))
                }
                is TXATranslation.SyncResult.CachedUsed -> {
                    TXALog.i("SplashActivity", "Using cached translations for locale: $locale")
                    updateStatus(TXATranslation.txa("txamusic_splash_initializing"))
                }
                is TXATranslation.SyncResult.Failed -> {
                    TXALog.e("SplashActivity", "Language sync failed for locale: $locale")
                    TXALog.e("SplashActivity", "Error message: ${result.message}")
                    updateStatus(TXATranslation.txa("txamusic_splash_language_failed"))
                }
            }
            
            delay(500)
            navigateToSettings()
        }
    }

    private fun updateStatus(message: String) {
        binding.tvStatus.text = message
    }

    private fun navigateToSettings() {
        val intent = Intent(this, TXASettingsActivity::class.java)
        startActivity(intent)
        finish()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        if (requestCode == PERMISSION_REQUEST_CODE) {
            val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            
            if (allGranted) {
                checkInstallPermission()
            } else {
                // Show message and continue anyway
                updateStatus(TXATranslation.txa("txamusic_permission_denied"))
                lifecycleScope.launch {
                    delay(2000)
                    checkInstallPermission()
                }
            }
        }
    }
}
