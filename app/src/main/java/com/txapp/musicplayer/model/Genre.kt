package com.txapp.musicplayer.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Genre(
    val id: Long,
    val name: String,
    val songCount: Int = 0
) : Parcelable
