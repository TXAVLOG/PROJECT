package com.txapp.musicplayer.util

import com.txapp.musicplayer.model.Artist

object MusicUtil {

    fun isArtistNameUnknown(artistName: String?): Boolean {
        if (artistName.isNullOrEmpty()) {
            return false
        }
        if (artistName == Artist.UNKNOWN_ARTIST_DISPLAY_NAME) {
            return true
        }
        val tempName = artistName.trim { it <= ' ' }.lowercase()
        return tempName == "unknown" || tempName == "<unknown>"
    }

    fun isVariousArtists(artistName: String?): Boolean {
        if (artistName.isNullOrEmpty()) {
            return false
        }
        if (artistName == Artist.VARIOUS_ARTISTS_DISPLAY_NAME) {
            return true
        }
        return false
    }

    fun getAlbumArtUri(albumId: Long): android.net.Uri {
        return android.net.Uri.parse("content://media/external/audio/albumart/$albumId")
    }

    fun getSongFileUri(songId: Long): android.net.Uri {
        return android.content.ContentUris.withAppendedId(
            android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            songId
        )
    }
}