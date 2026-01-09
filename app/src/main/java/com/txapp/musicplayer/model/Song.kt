package com.txapp.musicplayer.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "songs")
data class Song(
    @PrimaryKey val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val data: String, // file path
    val duration: Long,
    val albumId: Long,
    val artistId: Long = -1,
    val trackNumber: Int = 0,
    val year: Int = 0,
    val composer: String? = null,
    val albumArtist: String? = null,
    val dateAdded: Long = System.currentTimeMillis(),
    val dateModified: Long = System.currentTimeMillis(),
    val playCount: Int = 0,
    val isFavorite: Boolean = false,
    val lastPlayed: Long = 0,
    val isManual: Boolean = false // Added manually by user
) {
    companion object {
        val emptySong = Song(
            id = -1,
            title = "",
            artist = "",
            album = "",
            data = "",
            duration = 0,
            albumId = -1
        )
    }
}
