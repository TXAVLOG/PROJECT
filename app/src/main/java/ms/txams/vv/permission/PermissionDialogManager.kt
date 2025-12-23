package ms.txams.vv.permission

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import ms.txams.vv.R
import ms.txams.vv.core.TXATranslation

class PermissionDialogManager {

    sealed class PermissionState {
        object Granted : PermissionState()
        object Denied : PermissionState()
        object PermanentlyDenied : PermissionState()
        object InstallDisabled : PermissionState()
    }

    companion object {
        private var currentDialog: AlertDialog? = null

        fun dismissCurrentDialog() {
            currentDialog?.dismiss()
            currentDialog = null
        }

        fun showAudioPermissionRationale(
            context: Context,
            onGrant: () -> Unit,
            onSettings: () -> Unit,
            onCancel: () -> Unit = {}
        ) {
            dismissCurrentDialog()
            
            currentDialog = MaterialAlertDialogBuilder(context)
                .setTitle(TXATranslation.txa("txamusic_permission_required_title"))
                .setMessage(TXATranslation.txa("txamusic_permission_audio_rationale"))
                .setPositiveButton(TXATranslation.txa("txamusic_action_grant")) { _, _ ->
                    onGrant()
                }
                .setNegativeButton(TXATranslation.txa("txamusic_action_later")) { _, _ ->
                    onCancel()
                }
                .setCancelable(false)
                .show()
        }

        fun showAudioPermissionBlocked(
            context: Context,
            onSettings: () -> Unit,
            onCancel: () -> Unit = {}
        ) {
            dismissCurrentDialog()
            
            currentDialog = MaterialAlertDialogBuilder(context)
                .setTitle(TXATranslation.txa("txamusic_permission_blocked_title"))
                .setMessage(TXATranslation.txa("txamusic_permission_audio_blocked"))
                .setPositiveButton(TXATranslation.txa("txamusic_action_open_settings")) { _, _ ->
                    onSettings()
                }
                .setNegativeButton(TXATranslation.txa("txamusic_action_cancel")) { _, _ ->
                    onCancel()
                }
                .setCancelable(false)
                .show()
        }

        fun showInstallPermissionRationale(
            context: Context,
            onSettings: () -> Unit,
            onCancel: () -> Unit = {}
        ) {
            dismissCurrentDialog()
            
            currentDialog = MaterialAlertDialogBuilder(context)
                .setTitle(TXATranslation.txa("txamusic_permission_install_blocked_title"))
                .setMessage(TXATranslation.txa("txamusic_permission_install_rationale"))
                .setPositiveButton(TXATranslation.txa("txamusic_action_open_settings")) { _, _ ->
                    onSettings()
                }
                .setNegativeButton(TXATranslation.txa("txamusic_action_cancel")) { _, _ ->
                    onCancel()
                }
                .setCancelable(false)
                .show()
        }

        fun showInstallPermissionBlocked(
            context: Context,
            onSettings: () -> Unit,
            onCancel: () -> Unit = {}
        ) {
            dismissCurrentDialog()
            
            currentDialog = MaterialAlertDialogBuilder(context)
                .setTitle(TXATranslation.txa("txamusic_permission_install_blocked_title"))
                .setMessage(TXATranslation.txa("txamusic_permission_install_blocked"))
                .setPositiveButton(TXATranslation.txa("txamusic_action_open_settings")) { _, _ ->
                    onSettings()
                }
                .setNegativeButton(TXATranslation.txa("txamusic_action_cancel")) { _, _ ->
                    onCancel()
                }
                .setCancelable(false)
                .show()
        }

        fun openAppSettings(context: Context) {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }

        fun openInstallSettings(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } else {
                openAppSettings(context)
            }
        }

        fun checkAudioPermissionState(context: Context): PermissionState {
            return if (PermissionManager.hasAudioPermission(context)) {
                PermissionState.Granted
            } else {
                // Check if permanently denied by checking if rationale should be shown
                PermissionState.Denied // Will be updated to PermanentlyDenied after request
            }
        }

        fun checkInstallPermissionState(context: Context): PermissionState {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (context.packageManager.canRequestPackageInstalls()) {
                    PermissionState.Granted
                } else {
                    PermissionState.InstallDisabled
                }
            } else {
                PermissionState.Granted // Not required for older versions
            }
        }
    }
}
