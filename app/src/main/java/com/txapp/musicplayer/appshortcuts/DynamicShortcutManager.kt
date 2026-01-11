package com.txapp.musicplayer.appshortcuts

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.os.bundleOf
import com.txapp.musicplayer.R
import com.txapp.musicplayer.util.TXATranslation

object DynamicShortcutManager {

    private const val ID_PREFIX = "com.txapp.musicplayer.appshortcuts.id."

    fun init(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            updateShortcuts(context)
        }
    }

    @RequiresApi(Build.VERSION_CODES.N_MR1)
    private fun updateShortcuts(context: Context) {
        val shortcutManager = context.getSystemService(ShortcutManager::class.java) ?: return

        val shortcuts = listOf(
            createShortcut(
                context,
                ID_PREFIX + "shuffle_all",
                TXATranslation.txa("txamusic_shortcuts_shuffle_all"),
                R.drawable.ic_app_shortcut_shuffle_all,
                AppShortcutLauncherActivity.SHORTCUT_TYPE_SHUFFLE_ALL
            ),
            createShortcut(
                context,
                ID_PREFIX + "top_tracks",
                TXATranslation.txa("txamusic_shortcuts_top_tracks"),
                R.drawable.ic_app_shortcut_top_tracks,
                AppShortcutLauncherActivity.SHORTCUT_TYPE_TOP_TRACKS
            ),
            createShortcut(
                context,
                ID_PREFIX + "last_added",
                TXATranslation.txa("txamusic_shortcuts_last_added"),
                R.drawable.ic_app_shortcut_last_added,
                AppShortcutLauncherActivity.SHORTCUT_TYPE_LAST_ADDED
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
        shortcutType: Long
    ): ShortcutInfo {
        // Create Intent pointing to AppShortcutLauncherActivity with ACTION_VIEW
        val intent = Intent(context, AppShortcutLauncherActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            putExtras(bundleOf(AppShortcutLauncherActivity.KEY_SHORTCUT_TYPE to shortcutType))
        }

        return ShortcutInfo.Builder(context, id)
            .setShortLabel(label)
            .setLongLabel(label)
            .setIcon(AppShortcutIconGenerator.generateThemedIcon(context, iconResId))
            .setIntent(intent)
            .build()
    }

    fun reportShortcutUsed(context: Context, shortcutId: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            context.getSystemService(ShortcutManager::class.java)?.reportShortcutUsed(shortcutId)
        }
    }
}
