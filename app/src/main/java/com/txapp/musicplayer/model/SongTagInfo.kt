package com.txapp.musicplayer.model

import android.graphics.Bitmap

/**
 * Data class representing song tags to be updated
 */
data class SongTagInfo(
    val songId: Long,
    val filePaths: List<String>,
    val title: String,
    val artist: String,
    val album: String,
    val albumArtist: String? = null,
    val composer: String? = null,
    val year: String? = null,
    val trackNumber: String? = null,
    val artwork: Bitmap? = null,
    val deleteArtwork: Boolean = false
)
