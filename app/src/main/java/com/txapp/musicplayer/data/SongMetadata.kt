package com.txapp.musicplayer.data

/**
 * Lightweight class to hold just the user-editable metadata fields.
 * Used when upserting songs to preserve favorite status, play count, etc.
 */
data class SongMetadata(
    val id: Long,
    val data: String,
    val isFavorite: Boolean,
    val playCount: Int,
    val lastPlayed: Long
)
