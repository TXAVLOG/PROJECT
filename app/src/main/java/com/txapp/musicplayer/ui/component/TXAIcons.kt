package com.txapp.musicplayer.ui.component

// cms

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*

/**
 * Custom Icon mapping to resolve AutoMirrored warnings and library path issues
 */
object TXAIcons {
    // AutoMirrored Icons (Directional)
    val QueueMusic = Icons.AutoMirrored.Outlined.QueueMusic
    val QueueMusicFilled = Icons.AutoMirrored.Filled.QueueMusic
    val PlaylistPlay = Icons.AutoMirrored.Outlined.PlaylistPlay
    val PlaylistAdd = Icons.AutoMirrored.Filled.PlaylistAdd
    val List = Icons.AutoMirrored.Filled.List
    val VolumeUp = Icons.AutoMirrored.Outlined.VolumeUp
    val VolumeDown = Icons.AutoMirrored.Outlined.VolumeDown
    val VolumeOff = Icons.AutoMirrored.Outlined.VolumeOff
    val KeyboardArrowLeft = Icons.AutoMirrored.Filled.KeyboardArrowLeft
    val KeyboardArrowRight = Icons.AutoMirrored.Filled.KeyboardArrowRight
    val ArrowBack = Icons.AutoMirrored.Filled.ArrowBack
    // Reverted to Filled because AutoMirrored was failing for these specific icons in this environment
    val Shuffle = Icons.Filled.Shuffle
    val SkipPrevious = Icons.Filled.SkipPrevious
    val SkipNext = Icons.Filled.SkipNext
    
    // Standard Icons (Symmetric or Universal)
    val Lyrics = Icons.Outlined.MusicNote
    val KeyboardArrowDown = Icons.Filled.KeyboardArrowDown
    val ExpandMore = Icons.Filled.ExpandMore
    val Close = Icons.Filled.Close
    val PlayArrow = Icons.Filled.PlayArrow 
    val Pause = Icons.Filled.Pause
    val Repeat = Icons.Filled.Repeat
    val RepeatOne = Icons.Filled.RepeatOne
    val Favorite = Icons.Filled.Favorite
    val FavoriteBorder = Icons.Filled.FavoriteBorder
    val PauseCircleFilled = Icons.Filled.PauseCircleFilled
    val PlayCircleFilled = Icons.Filled.PlayCircleFilled
    val MusicNote = Icons.Filled.MusicNote
}
