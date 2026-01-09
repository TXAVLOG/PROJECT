package com.txapp.musicplayer.model

data class Playlist(
    val id: Long,
    val name: String,
    val songCount: Int = 0,
    val songs: List<Song> = emptyList()
)
