package com.txapp.musicplayer.appshortcuts

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import com.txapp.musicplayer.service.CheckUpdateShortcutService
import com.txapp.musicplayer.ui.MainActivity

class AppShortcutLauncherActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val shortcutType = intent.getLongExtra(KEY_SHORTCUT_TYPE, SHORTCUT_TYPE_NONE)
        
        // Handle Check Update shortcut separately - it doesn't open MainActivity
        if (shortcutType == SHORTCUT_TYPE_CHECK_UPDATE) {
            handleCheckUpdate()
            finish()
            return
        }
        
        val mainIntent = Intent(this, MainActivity::class.java).apply {
            when (shortcutType) {
                SHORTCUT_TYPE_SHUFFLE_ALL -> {
                    action = MainActivity.ACTION_APP_SHORTCUT_SHUFFLE
                }
                SHORTCUT_TYPE_TOP_TRACKS -> {
                    action = MainActivity.ACTION_APP_SHORTCUT_TOP_TRACKS
                }
                SHORTCUT_TYPE_LAST_ADDED -> {
                    action = MainActivity.ACTION_APP_SHORTCUT_LAST_ADDED
                }
            }
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        
        startActivity(mainIntent)
        finish()
    }
    
    private fun handleCheckUpdate() {
        val serviceIntent = Intent(this, CheckUpdateShortcutService::class.java).apply {
            action = CheckUpdateShortcutService.ACTION_CHECK_UPDATE
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    companion object {
        const val KEY_SHORTCUT_TYPE = "com.txapp.musicplayer.appshortcuts.ShortcutType"
        const val SHORTCUT_TYPE_SHUFFLE_ALL = 0L
        const val SHORTCUT_TYPE_TOP_TRACKS = 1L
        const val SHORTCUT_TYPE_LAST_ADDED = 2L
        const val SHORTCUT_TYPE_CHECK_UPDATE = 3L
        const val SHORTCUT_TYPE_NONE = 4L
    }
}
