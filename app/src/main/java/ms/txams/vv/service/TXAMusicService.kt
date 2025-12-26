package ms.txams.vv.service

import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ms.txams.vv.data.manager.TXAAudioInjectionManager
import ms.txams.vv.ui.TXAMainActivity
import javax.inject.Inject

@AndroidEntryPoint
class TXAMusicService : MediaLibraryService() {

    @Inject lateinit var audioInjectionManager: TXAAudioInjectionManager

    private var mediaSession: MediaLibrarySession? = null
    private lateinit var player: ExoPlayer
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    
    private val FADE_DURATION = 3000L
    private val CROSSFADE_TRIGGER_REMAINING = 3000L

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        
        player = ExoPlayer.Builder(this)
            .setAudioAttributes(AudioAttributes.DEFAULT, true)
            .setHandleAudioBecomingNoisy(true)
            .build()
            
        setupCrossfadeMonitor()

        val intent = Intent(this, TXAMainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaSession = MediaLibrarySession.Builder(this, player, LibrarySessionCallback())
            .setSessionActivity(pendingIntent)
            .build()
    }

    private inner class LibrarySessionCallback : MediaLibrarySession.Callback {
        @OptIn(UnstableApi::class)
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>
        ): ListenableFuture<List<MediaItem>> {
            val updatedList = mediaItems.flatMap { item ->
                val introUri = audioInjectionManager.getIntroUri()
                val introItem = MediaItem.Builder()
                    .setUri(introUri)
                    .setMediaId("intro_${item.mediaId}")
                    .setMediaMetadata(item.mediaMetadata)
                    .build()
                listOf(introItem, item)
            }
            return Futures.immediateFuture(updatedList)
        }
    }

    private fun setupCrossfadeMonitor() {
        serviceScope.launch {
            while (true) {
                if (player.isPlaying) {
                    val remaining = player.duration - player.currentPosition
                    if (remaining in 1 until CROSSFADE_TRIGGER_REMAINING) {
                        applyFadeOut(remaining)
                    } else if (player.volume < 1.0f && remaining > CROSSFADE_TRIGGER_REMAINING) {
                        player.volume = 1.0f
                    }
                }
                delay(200)
            }
        }
    }

    private fun applyFadeOut(remainingMs: Long) {
        val progress = remainingMs.toFloat() / FADE_DURATION.toFloat()
        val volume = progress.coerceIn(0f, 1f)
        player.volume = volume * volume
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        return mediaSession
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
