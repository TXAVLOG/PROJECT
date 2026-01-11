package com.txapp.musicplayer.appshortcuts

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.os.Build
import androidx.annotation.RequiresApi
import com.txapp.musicplayer.R
import com.txapp.musicplayer.ui.MainActivity
import com.txapp.musicplayer.util.TXATranslation
import java.util.Arrays

object DynamicShortcutManager {

    private const val SHORTCUT_ID_SHUFFLE_ALL = "shuffle_all"
    private const val SHORTCUT_ID_TOP_TRACKS = "top_tracks"
    private const val SHORTCUT_ID_LAST_ADDED = "last_added"

    fun init(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            updateShortcuts(context)
        }
    }

    @RequiresApi(Build.VERSION_CODES.N_MR1)
    private fun updateShortcuts(context: Context) {
        val shortcutManager = context.getSystemService(ShortcutManager::class.java) ?: return

        val shortcuts = ArrayList<ShortcutInfo>()

        // Shuffle All
        shortcuts.add(
            createShortcut(
                context,
                SHORTCUT_ID_SHUFFLE_ALL,
                TXATranslation.txa("txamusic_shortcuts_shuffle_all"),
                R.drawable.ic_app_shortcut_shuffle_all,
                MainActivity.ACTION_APP_SHORTCUT_SHUFFLE
            )
        )

        // Top Tracks
        shortcuts.add(
            createShortcut(
                context,
                SHORTCUT_ID_TOP_TRACKS,
                TXATranslation.txa("txamusic_shortcuts_top_tracks"),
                R.drawable.ic_app_shortcut_top_tracks,
                MainActivity.ACTION_APP_SHORTCUT_TOP_TRACKS
            )
        )

        // Last Added
        shortcuts.add(
            createShortcut(
                context,
                SHORTCUT_ID_LAST_ADDED,
                TXATranslation.txa("txamusic_shortcuts_last_added"),
                R.drawable.ic_app_shortcut_last_added,
                MainActivity.ACTION_APP_SHORTCUT_LAST_ADDED
            )
        )

        try {
            shortcutManager.dynamicShortcuts = shortcuts
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @RequiresApi(Build.VERSION_CODES.N_MR1)
    private fun createShortcut(
        context: Context,
        id: String,
        label: String,
        iconResId: Int,
        action: String
    ): ShortcutInfo {
        // Create Intent pointing directly to MainActivity
        val intent = Intent(context, MainActivity::class.java).apply {
            this.action = action
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        return ShortcutInfo.Builder(context, id)
            .setShortLabel(label)
            .setLongLabel(label)
            .setIcon(AppShortcutIconGenerator.generateThemedIcon(context, iconResId))
            .setIntent(intent)
            .build()
    }
}
