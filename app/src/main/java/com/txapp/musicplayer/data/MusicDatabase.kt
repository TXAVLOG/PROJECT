package com.txapp.musicplayer.data

import androidx.room.Database
import androidx.room.RoomDatabase
import com.txapp.musicplayer.model.Song

@Database(
    entities = [
        Song::class, 
        PlaylistEntity::class, 
        PlaylistSongCrossRef::class,
        BlackListEntity::class
    ], 
    version = 7, 
    exportSchema = false
)
abstract class MusicDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun blackListDao(): BlackListDao
}
