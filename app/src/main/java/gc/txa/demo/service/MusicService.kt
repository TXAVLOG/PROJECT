package gc.txa.demo.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.MediaSession.ControllerInfo
import gc.txa.demo.R
import gc.txa.demo.data.database.SongEntity
import gc.txa.demo.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MusicService : MediaSessionService() {
    
    companion object {
        const val ACTION_START = "gc.txa.demo.action.START"
        const val ACTION_PLAY = "gc.txa.demo.action.PLAY"
        const val ACTION_PAUSE = "gc.txa.demo.action.PAUSE"
        const val ACTION_STOP = "gc.txa.demo.action.STOP"
        const val NOTIFICATION_CHANNEL_ID = "music_playback_channel"
        const val NOTIFICATION_ID = 1001
    }
    
    private var player: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    private val _currentSong = MutableStateFlow<SongEntity?>(null)
    val currentSong: StateFlow<SongEntity?> = _currentSong
    
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying
    
    private val _playbackPosition = MutableStateFlow(0L)
    val playbackPosition: StateFlow<Long> = _playbackPosition

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        initializePlayer()
        startForeground(NOTIFICATION_ID, createNotification())
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Music Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Music playback controls"
                setShowBadge(false)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): android.app.Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Music Player")
            .setContentText("Ready to play")
            .setSmallIcon(R.drawable.ic_music_note)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    private fun initializePlayer() {
        player = ExoPlayer.Builder(this).build().apply {
            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _isPlaying.value = isPlaying
                }
                
                override fun onPlaybackStateChanged(playbackState: Int) {
                    when (playbackState) {
                        Player.STATE_READY -> {
                            _playbackPosition.value = currentPosition
                        }
                    }
                }
                
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    mediaItem?.mediaId?.let { songId ->
                        // Update current song - will be handled by bound service connection
                    }
                }
            })
        }

        mediaSession = MediaSession.Builder(this, player!!)
            .setCallback(MusicSessionCallback())
            .build()
    }

    override fun onGetSession(controllerInfo: ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        player?.run {
            if (playbackState != Player.STATE_IDLE) {
                stop()
            }
        }
        stopSelf()
    }

    override fun onDestroy() {
        releasePlayer()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun releasePlayer() {
        mediaSession?.run {
            player?.release()
            release()
        }
        mediaSession = null
        player = null
    }

    fun playSong(song: SongEntity) {
        val mediaItem = MediaItem.Builder()
            .setMediaId(song.id)
            .setUri(song.filePath)
            .setMediaMetadata(
                androidx.media3.common.MediaMetadata.Builder()
                    .setTitle(song.title)
                    .setArtist(song.artist)
                    .setAlbumTitle(song.album)
                    .setArtworkUri(song.albumArt?.let { android.net.Uri.parse(it) })
                    .build()
            )
            .build()

        player?.setMediaItem(mediaItem)
        player?.prepare()
        player?.play()
        
        _currentSong.value = song
    }

    fun pausePlayback() {
        player?.pause()
    }

    fun resumePlayback() {
        player?.play()
    }

    fun seekTo(position: Long) {
        player?.seekTo(position)
    }

    fun skipToNext() {
        player?.seekToNext()
    }

    fun skipToPrevious() {
        player?.seekToPrevious()
    }

    private inner class MusicSessionCallback : MediaSession.Callback {
        override fun onPlay() {
            resumePlayback()
        }

        override fun onPause() {
            pausePlayback()
        }

        override fun onSkipToNext() {
            skipToNext()
        }

        override fun onSkipToPrevious() {
            skipToPrevious()
        }

        override fun onSeekTo(pos: Long) {
            seekTo(pos)
        }
    }
}
