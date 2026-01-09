package com.txapp.musicplayer.media

import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer

@UnstableApi
@androidx.annotation.experimental.Experimental
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
annotation class ExperimentalApi

@OptIn(UnstableApi::class)
@ExperimentalApi
class CompositionPlayer(private val player: ExoPlayer) {

    // Helper to handle COMMAND_SET_AUDIO_ATTRIBUTES
    fun setAudioAttributes(audioAttributes: AudioAttributes, handleAudioFocus: Boolean) {
        if (player.isCommandAvailable(Player.COMMAND_SET_AUDIO_ATTRIBUTES)) {
            player.setAudioAttributes(audioAttributes, handleAudioFocus)
        }
    }

    // Example of other methods delegating to player...
}
