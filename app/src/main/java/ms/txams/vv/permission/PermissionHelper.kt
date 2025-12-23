package ms.txams.vv.permission

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import ms.txams.vv.R

class PermissionHelper(
    private val activity: AppCompatActivity,
    private val onPermissionResult: (String, Boolean) -> Unit = { _, _ -> }
) : DefaultLifecycleObserver {

    private val sharedPreferences: SharedPreferences = 
        activity.getSharedPreferences("permission_prefs", Context.MODE_PRIVATE)

    private lateinit var audioPermissionLauncher: ActivityResultLauncher<String>
    private var isCheckingPermissions = false

    companion object {
        private const val KEY_AUDIO_PERMISSION_DENIED = "audio_permission_denied"
        private const val KEY_AUDIO_PERMISSION_PERMANENTLY_DENIED = "audio_permission_permanently_denied"
        private const val KEY_INSTALL_PERMISSION_CHECKED = "install_permission_checked"
    }

    init {
        activity.lifecycle.addObserver(this)
        setupPermissionLaunchers()
    }

    private fun setupPermissionLaunchers() {
        audioPermissionLauncher = activity.registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            handleAudioPermissionResult(isGranted)
        }
    }

    override fun onResume(owner: LifecycleOwner) {
        super.onResume(owner)
        checkPermissionsOnResume()
    }

    override fun onDestroy(owner: LifecycleOwner) {
        super.onDestroy(owner)
        PermissionDialogManager.dismissCurrentDialog()
    }

    private fun checkPermissionsOnResume() {
        if (isCheckingPermissions) return
        
        isCheckingPermissions = true
        
        // Check audio permission
        val audioState = PermissionDialogManager.checkAudioPermissionState(activity)
        if (audioState is PermissionDialogManager.PermissionState.Granted) {
            // Permission granted, dismiss any dialogs
            PermissionDialogManager.dismissCurrentDialog()
            clearAudioPermissionDeniedState()
        }
        
        // Check install permission
        val installState = PermissionDialogManager.checkInstallPermissionState(activity)
        if (installState is PermissionDialogManager.PermissionState.Granted) {
            clearInstallPermissionCheckedState()
        }
        
        isCheckingPermissions = false
    }

    fun checkAndRequestPermissions() {
        checkAudioPermission()
        checkInstallPermission()
    }

    private fun checkAudioPermission() {
        when (val state = PermissionDialogManager.checkAudioPermissionState(activity)) {
            is PermissionDialogManager.PermissionState.Granted -> {
                clearAudioPermissionDeniedState()
            }
            is PermissionDialogManager.PermissionState.Denied -> {
                if (shouldShowAudioPermissionRationale()) {
                    showAudioPermissionRationale()
                } else {
                    // Check if it's permanently denied
                    if (isAudioPermissionPermanentlyDenied()) {
                        showAudioPermissionBlocked()
                    } else {
                        // First time denial, show rationale
                        showAudioPermissionRationale()
                    }
                }
            }
            else -> {}
        }
    }

    private fun checkInstallPermission() {
        when (val state = PermissionDialogManager.checkInstallPermissionState(activity)) {
            is PermissionDialogManager.PermissionState.InstallDisabled -> {
                if (!hasShownInstallPermissionRationale()) {
                    showInstallPermissionRationale()
                } else {
                    showInstallPermissionBlocked()
                }
            }
            else -> {}
        }
    }

    private fun shouldShowAudioPermissionRationale(): Boolean {
        return try {
            val navHostFragment = activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment)
            navHostFragment?.shouldShowRequestPermissionRationale(PermissionManager.getAudioPermission()) 
                ?: false
        } catch (e: Exception) {
            // Fallback: check with activity if fragment is not available
            try {
                activity.shouldShowRequestPermissionRationale(PermissionManager.getAudioPermission())
            } catch (ex: Exception) {
                false
            }
        }
    }

    private fun showAudioPermissionRationale() {
        PermissionDialogManager.showAudioPermissionRationale(
            activity,
            onGrant = { requestAudioPermission() },
            onSettings = { openAppSettings() },
            onCancel = { markAudioPermissionDenied() }
        )
    }

    private fun showAudioPermissionBlocked() {
        PermissionDialogManager.showAudioPermissionBlocked(
            activity,
            onSettings = { openAppSettings() },
            onCancel = { markAudioPermissionPermanentlyDenied() }
        )
    }

    private fun showInstallPermissionRationale() {
        PermissionDialogManager.showInstallPermissionRationale(
            activity,
            onSettings = { openInstallSettings() },
            onCancel = { markInstallPermissionChecked() }
        )
    }

    private fun showInstallPermissionBlocked() {
        PermissionDialogManager.showInstallPermissionBlocked(
            activity,
            onSettings = { openInstallSettings() },
            onCancel = { markInstallPermissionChecked() }
        )
    }

    private fun requestAudioPermission() {
        audioPermissionLauncher.launch(PermissionManager.getAudioPermission())
    }

    private fun handleAudioPermissionResult(isGranted: Boolean) {
        onPermissionResult(PermissionManager.getAudioPermission(), isGranted)
        
        if (!isGranted) {
            if (!shouldShowAudioPermissionRationale()) {
                // User selected "Don't ask again"
                markAudioPermissionPermanentlyDenied()
                showAudioPermissionBlocked()
            } else {
                markAudioPermissionDenied()
            }
        } else {
            clearAudioPermissionDeniedState()
        }
    }

    private fun openAppSettings() {
        PermissionDialogManager.openAppSettings(activity)
    }

    private fun openInstallSettings() {
        PermissionDialogManager.openInstallSettings(activity)
    }

    // SharedPreferences helpers
    private fun markAudioPermissionDenied() {
        sharedPreferences.edit()
            .putBoolean(KEY_AUDIO_PERMISSION_DENIED, true)
            .apply()
    }

    private fun markAudioPermissionPermanentlyDenied() {
        sharedPreferences.edit()
            .putBoolean(KEY_AUDIO_PERMISSION_PERMANENTLY_DENIED, true)
            .apply()
    }

    private fun clearAudioPermissionDeniedState() {
        sharedPreferences.edit()
            .remove(KEY_AUDIO_PERMISSION_DENIED)
            .remove(KEY_AUDIO_PERMISSION_PERMANENTLY_DENIED)
            .apply()
    }

    private fun isAudioPermissionPermanentlyDenied(): Boolean {
        return sharedPreferences.getBoolean(KEY_AUDIO_PERMISSION_PERMANENTLY_DENIED, false)
    }

    private fun markInstallPermissionChecked() {
        sharedPreferences.edit()
            .putBoolean(KEY_INSTALL_PERMISSION_CHECKED, true)
            .apply()
    }

    private fun clearInstallPermissionCheckedState() {
        sharedPreferences.edit()
            .remove(KEY_INSTALL_PERMISSION_CHECKED)
            .apply()
    }

    private fun hasShownInstallPermissionRationale(): Boolean {
        return sharedPreferences.getBoolean(KEY_INSTALL_PERMISSION_CHECKED, false)
    }

    fun forceCheckPermissions() {
        isCheckingPermissions = false
        checkPermissionsOnResume()
    }
}
