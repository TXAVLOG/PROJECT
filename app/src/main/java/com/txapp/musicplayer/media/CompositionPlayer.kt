package com.txapp.musicplayer.media

import android.content.Context
import android.os.Looper
import kotlin.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer

@UnstableApi
@RequiresOptIn(level = RequiresOptIn.Level.WARNING)
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
annotation class ExperimentalApi

/**
 * A [Player] implementation that plays compositions of media assets.
 * 
 * Inspired by androidx.media3.transformer.CompositionPlayer.
 */
@OptIn(UnstableApi::class)
@ExperimentalApi
class CompositionPlayer private constructor(player: Player) : ForwardingPlayer(player) {

    /** A builder for [CompositionPlayer] instances. */
    class Builder(private val context: Context) {
        private var looper: Looper? = null
        private var audioAttributes: AudioAttributes = AudioAttributes.DEFAULT
        private var handleAudioFocus: Boolean = true
        
        fun setLooper(looper: Looper): Builder {
            this.looper = looper
            return this
        }

        fun setAudioAttributes(audioAttributes: AudioAttributes, handleAudioFocus: Boolean): Builder {
            this.audioAttributes = audioAttributes
            this.handleAudioFocus = handleAudioFocus
            return this
        }

        private var wakeMode: Int = 0 // C.WAKE_MODE_NONE
        private var loadControl: androidx.media3.exoplayer.LoadControl? = null
        private var handleAudioBecomingNoisy: Boolean = false

        fun setWakeMode(wakeMode: Int): Builder {
            this.wakeMode = wakeMode
            return this
        }

        fun setLoadControl(loadControl: androidx.media3.exoplayer.LoadControl): Builder {
            this.loadControl = loadControl
            return this
        }

        fun setHandleAudioBecomingNoisy(handleAudioBecomingNoisy: Boolean): Builder {
            this.handleAudioBecomingNoisy = handleAudioBecomingNoisy
            return this
        }

        fun build(): CompositionPlayer {
            val playerBuilder = ExoPlayer.Builder(context)
            looper?.let { playerBuilder.setLooper(it) }
            
            playerBuilder
                .setAudioAttributes(audioAttributes, handleAudioFocus)
                .setHandleAudioBecomingNoisy(handleAudioBecomingNoisy)
                .setWakeMode(wakeMode)
            
            loadControl?.let { playerBuilder.setLoadControl(it) }

            val exoPlayer = playerBuilder.build()
                
            return CompositionPlayer(exoPlayer)
        }
    }

    override fun setAudioAttributes(audioAttributes: AudioAttributes, handleAudioFocus: Boolean) {
        if (isCommandAvailable(Player.COMMAND_SET_AUDIO_ATTRIBUTES)) {
            super.setAudioAttributes(audioAttributes, handleAudioFocus)
        }
    }
}
