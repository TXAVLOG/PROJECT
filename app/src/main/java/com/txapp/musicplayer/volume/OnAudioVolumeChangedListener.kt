package com.txapp.musicplayer.volume

/**
 * Listener interface for audio volume changes
 */
interface OnAudioVolumeChangedListener {
    fun onAudioVolumeChanged(currentVolume: Int, maxVolume: Int)
}
