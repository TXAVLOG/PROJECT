package com.txapp.musicplayer.volume

import android.content.Context
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.core.content.getSystemService

/**
 * Observer class to register and unregister volume change listeners
 */
class AudioVolumeObserver(private val context: Context) {
    
    private val mAudioManager: AudioManager = context.getSystemService()!!
    private var contentObserver: AudioVolumeContentObserver? = null

    fun register(audioStreamType: Int, listener: OnAudioVolumeChangedListener) {
        val handler = Handler(Looper.getMainLooper())
        // with this handler AudioVolumeContentObserver#onChange()
        //   will be executed in the main thread
        contentObserver = AudioVolumeContentObserver(
            handler,
            mAudioManager,
            audioStreamType,
            listener
        )
        context.contentResolver.registerContentObserver(
            Settings.System.CONTENT_URI,
            true,
            contentObserver!!
        )
    }

    fun unregister() {
        if (contentObserver != null) {
            context.contentResolver.unregisterContentObserver(contentObserver!!)
            contentObserver = null
        }
    }
}
