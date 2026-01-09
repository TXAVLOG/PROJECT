package com.txapp.musicplayer

import android.app.Application
import androidx.room.Room
import com.txapp.musicplayer.data.MusicDatabase
import com.txapp.musicplayer.network.ArtistImageService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MusicApplication : Application() {

    lateinit var database: MusicDatabase

    override fun onCreate() {
        super.onCreate()

        // 1. Init Crash Handler FIRST to catch everything else
        com.txapp.musicplayer.util.TXACrashHandler.init(this)

        // 2. Init Logger
        com.txapp.musicplayer.util.TXALogger.init(this)

        // Init Device Info
        com.txapp.musicplayer.util.TXADeviceInfo.init(this)

        // Init Preferences
        com.txapp.musicplayer.util.TXAPreferences.init(this)

        // Init Translation
        CoroutineScope(Dispatchers.Main).launch {
            com.txapp.musicplayer.util.TXATranslation.init(this@MusicApplication)
        }
        
        // Init Artist Image Service (Deezer API)
        ArtistImageService.init(this)

        database = Room.databaseBuilder(
            applicationContext,
            MusicDatabase::class.java,
            "music_database"
        ).fallbackToDestructiveMigration()
         .build()
    }
}

