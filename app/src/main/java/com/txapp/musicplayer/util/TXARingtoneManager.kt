package com.txapp.musicplayer.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.txapp.musicplayer.R
import com.txapp.musicplayer.model.Song

object TXARingtoneManager {

    fun setRingtone(context: Context, song: Song): Boolean {
        if (!Settings.System.canWrite(context)) {
            showPermissionDialog(context)
            return false
        }

        val uri = MusicUtil.getSongFileUri(song.id)
        return try {
            Settings.System.putString(
                context.contentResolver,
                Settings.System.RINGTONE,
                uri.toString()
            )
            TXALogger.appI("TXARingtoneManager", "Set ringtone success: ${song.title}")
            true
        } catch (e: Exception) {
            TXALogger.appE("TXARingtoneManager", "Failed to set ringtone", e)
            false
        }
    }

    private fun showPermissionDialog(context: Context) {
        MaterialAlertDialogBuilder(context)
            .setTitle("txamusic_ringtone_permission_title".txa())
            .setMessage("txamusic_ringtone_permission_desc".txa())
            .setPositiveButton("txamusic_btn_grant".txa()) { _, _ ->
                val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)
                intent.data = Uri.parse("package:" + context.packageName)
                context.startActivity(intent)
            }
            .setNegativeButton("txamusic_btn_cancel".txa(), null)
            .show()
    }
}
