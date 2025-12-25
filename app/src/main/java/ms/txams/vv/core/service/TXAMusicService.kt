package ms.txams.vv.core.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ms.txams.vv.R
import ms.txams.vv.core.audio.TXAAudioInjectionManager
import ms.txams.vv.core.audio.TXAAudioProcessor
import ms.txams.vv.core.data.database.dao.TXAQueueDao
import ms.txams.vv.core.data.database.dao.TXASongDao
import ms.txams.vv.core.data.database.entity.TXASongEntity
import timber.log.Timber
import javax.inject.Inject

/**
 * TXA Music Service - Clean Media3 implementation
 * Features: ExoPlayer, MediaSession, Audio Injection, Crossfade, Queue Management
 */
@UnstableApi
@AndroidEntryPoint
class TXAMusicService : MediaSessionService() {

    @Inject
    lateinit var songDao: TXASongDao

    @Inject
    lateinit var queueDao: TXAQueueDao

    @Inject
    lateinit var audioInjectionManager: TXAAudioInjectionManager

    @Inject
    lateinit var audioProcessor: TXAAudioProcessor

    // Player and Session
    private lateinit var player: ExoPlayer
    private lateinit var mediaSession: MediaSession
    private lateinit var dataSourceFactory: androidx.media3.datasource.DataSource.Factory

    // Service management
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var wakeLock: PowerManager.WakeLock? = null
    private val binder = TXAMusicBinder()

    // State flows for ViewModel
    private val _currentSong = MutableStateFlow<TXASongEntity?>(null)
    val currentSong: StateFlow<TXASongEntity?> = _currentSong.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _playbackState = MutableStateFlow(TXAPlaybackState.IDLE)
    val playbackState: StateFlow<TXAPlaybackState> = _playbackState.asStateFlow()

    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    override fun onCreate() {
        super.onCreate()
        Timber.d("TXAMusicService onCreate")
        
        initializePlayer()
        initializeMediaSession()
        initializeAudioComponents()
        initializeNotification()
        
        _isInitialized.value = true
        
        serviceScope.launch {
            restoreQueue()
        }
    }

    private fun initializePlayer() {
        player = ExoPlayer.Builder(this).build().apply {
            setAudioAttributes(
                androidx.media3.common.AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true
            )
            
            addListener(playerListener)
        }
        
        dataSourceFactory = androidx.media3.datasource.DefaultDataSource.Factory(this)
    }

    private fun initializeMediaSession() {
        mediaSession = MediaSession.Builder(this, player)
            .setCallback(mediaSessionCallback)
            .build()
    }

    private fun initializeAudioComponents() {
        audioInjectionManager.initialize()
        audioProcessor.initialize()
    }

    private fun initializeNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "TXA Music Playback",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying.value = isPlaying
            updateNotification()
            updateWakeLock(isPlaying)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            _playbackState.value = when (playbackState) {
                Player.STATE_IDLE -> TXAPlaybackState.IDLE
                Player.STATE_BUFFERING -> TXAPlaybackState.BUFFERING
                Player.STATE_READY -> TXAPlaybackState.READY
                Player.STATE_ENDED -> TXAPlaybackState.ENDED
                else -> TXAPlaybackState.IDLE
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            mediaItem?.let { item ->
                serviceScope.launch {
                    val songId = item.mediaId.toLongOrNull()
                    songId?.let { id ->
                        val song = songDao.getSongById(id)
                        _currentSong.value = song
                        queueDao.setCurrentTrack(id)
                    }
                }
            }
        }
    }

    // Media3 MediaSession automatically forwards commands to ExoPlayer
    // No need for custom callback implementation

    // Playback controls
    fun play() {
        player.play()
    }

    fun pause() {
        player.pause()
    }

    fun stop() {
        player.stop()
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    fun seekTo(position: Long) {
        player.seekTo(position)
    }

    // Song management
    fun playSong(song: TXASongEntity, injectBranding: Boolean = true) {
        serviceScope.launch {
            try {
                _currentSong.value = song
                
                val mediaSource = if (injectBranding) {
                    // Kiểm tra file branded đã tồn tại chưa
                    val brandedPath = audioInjectionManager.getBrandedFilePath(song)
                    if (brandedPath != null) {
                        Timber.d("Playing existing branded file: $brandedPath")
                        createSimpleMediaSourceFromPath(brandedPath, song.id)
                    } else {
                        // Tạo file branded mới
                        val newBrandedPath = audioInjectionManager.createBrandedAudioFile(song)
                        if (newBrandedPath != null) {
                            Timber.d("Created and playing new branded file: $newBrandedPath")
                            createSimpleMediaSourceFromPath(newBrandedPath, song.id)
                        } else {
                            Timber.w("Failed to create branded file, using original")
                            audioInjectionManager.createBrandedMediaSource(song, dataSourceFactory)
                        }
                    }
                } else {
                    createSimpleMediaSource(song)
                }
                
                player.setMediaSource(mediaSource)
                player.prepare()
                player.play()
                
                queueDao.clearQueue()
                queueDao.addToQueue(song.id, 0)
                
            } catch (e: Exception) {
                Timber.e(e, "Failed to play song: ${song.title}")
            }
        }
    }

    private fun createSimpleMediaSourceFromPath(filePath: String, songId: Long): ProgressiveMediaSource {
        val mediaItem = MediaItem.Builder()
            .setMediaId(songId.toString())
            .setUri(android.net.Uri.parse(filePath))
            .build()
        return ProgressiveMediaSource.Factory(dataSourceFactory)
            .createMediaSource(mediaItem)
    }

    fun playNext() {
        serviceScope.launch {
            try {
                val nextQueueItem = queueDao.getNextQueueItem()
                if (nextQueueItem != null) {
                    val nextSong = songDao.getSongById(nextQueueItem.songId)
                    if (nextSong != null) {
                        playSong(nextSong)
                    }
                } else {
                    handleEndOfQueue()
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to play next song")
            }
        }
    }

    fun playPrevious() {
        serviceScope.launch {
            try {
                val prevQueueItem = queueDao.getPreviousQueueItem()
                if (prevQueueItem != null) {
                    val prevSong = songDao.getSongById(prevQueueItem.songId)
                    if (prevSong != null) {
                        playSong(prevSong)
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to play previous song")
            }
        }
    }

    // Queue management
    fun addToQueue(songId: Long) {
        serviceScope.launch {
            queueDao.addToQueue(songId)
            reloadQueue()
        }
    }

    fun addToQueueNext(songId: Long) {
        serviceScope.launch {
            queueDao.addToQueueNext(songId)
            reloadQueue()
        }
    }

    fun removeFromQueue(songId: Long) {
        serviceScope.launch {
            queueDao.removeFromQueue(songId)
            reloadQueue()
        }
    }

    fun clearQueue() {
        serviceScope.launch {
            queueDao.clearQueue()
            player.clearMediaItems()
        }
    }

    fun shuffleQueue() {
        serviceScope.launch {
            queueDao.shuffleQueue()
            reloadQueue()
        }
    }

    // Playback modes
    fun setShuffleMode(enabled: Boolean) {
        serviceScope.launch {
            if (enabled) {
                shuffleQueue()
            }
        }
    }

    fun setRepeatMode(mode: RepeatMode) {
        when (mode) {
            RepeatMode.OFF -> player.repeatMode = Player.REPEAT_MODE_OFF
            RepeatMode.ALL -> player.repeatMode = Player.REPEAT_MODE_ALL
            RepeatMode.ONE -> player.repeatMode = Player.REPEAT_MODE_ONE
        }
    }

    // Private helper methods
    private fun createSimpleMediaSource(song: TXASongEntity): ProgressiveMediaSource {
        return ProgressiveMediaSource.Factory(dataSourceFactory)
            .createMediaSource(MediaItem.fromUri(android.net.Uri.parse(song.filePath)))
    }

    private fun reloadQueue() {
        serviceScope.launch {
            try {
                val queueItems = queueDao.getQueueSnapshot()
                val mediaItems = queueItems.mapNotNull { item ->
                    songDao.getSongById(item.songId)?.let { song ->
                        MediaItem.Builder()
                            .setMediaId(song.id.toString())
                            .setUri(android.net.Uri.parse(song.filePath))
                            .build()
                    }
                }
                
                player.setMediaItems(mediaItems)
                if (mediaItems.isNotEmpty()) {
                    player.prepare()
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to reload queue")
            }
        }
    }

    private fun restoreQueue() {
        serviceScope.launch {
            try {
                val queueSnapshot = queueDao.getQueueSnapshot()
                if (queueSnapshot.isNotEmpty()) {
                    reloadQueue()
                    
                    val currentSong = queueSnapshot.find { it.isCurrent }
                    currentSong?.let { item ->
                        val song = songDao.getSongById(item.songId)
                        if (song != null) {
                            _currentSong.value = song
                            player.seekTo(item.lastPlayedPosition)
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to restore queue")
            }
        }
    }

    private fun handleEndOfQueue() {
        when (player.repeatMode) {
            Player.REPEAT_MODE_ALL -> playNext()
            Player.REPEAT_MODE_ONE -> _currentSong.value?.let { playSong(it) }
            else -> stop()
        }
    }

    private fun updateNotification() {
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)
    }

    private fun createNotification(): Notification {
        val song = _currentSong.value
        
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(song?.title ?: "TXA Music")
            .setContentText(song?.artist ?: "")
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(_isPlaying.value)
            .addAction(
                if (_isPlaying.value) R.drawable.ic_pause else R.drawable.ic_play,
                if (_isPlaying.value) "Pause" else "Play",
                createPlaybackAction(if (_isPlaying.value) ACTION_PAUSE else ACTION_PLAY)
            )
            .build()
    }

    private fun createPlaybackAction(action: String): Intent {
        return Intent(this, TXAMusicService::class.java).apply {
            this.action = action
        }
    }

    private fun updateWakeLock(isPlaying: Boolean) {
        if (isPlaying) {
            if (wakeLock == null) {
                val powerManager = getSystemService(POWER_SERVICE) as PowerManager
                wakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "TXAMusicService:WakeLock"
                ).apply {
                    acquire()
                }
            }
        } else {
            wakeLock?.release()
            wakeLock = null
        }
    }

    // Service binding
    inner class TXAMusicBinder : Binder() {
        fun getService(): TXAMusicService = this@TXAMusicService
    }

    override fun onBind(intent: Intent?): IBinder? {
        return binder
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        
        serviceScope.launch {
            _currentSong.value?.let { song ->
                queueDao.updateLastPlayedPosition(song.id, player.currentPosition)
            }
        }
        
        player.release()
        mediaSession.release()
        audioInjectionManager.release()
        audioProcessor.release()
        wakeLock?.release()
        
        _isInitialized.value = false
    }

    override fun onGetSession(controllerInfo: androidx.media3.session.MediaSession.ControllerInfo): MediaSession {
        return mediaSession
    }

    companion object {
        const val ACTION_PLAY = "ms.txams.vv.action.PLAY"
        const val ACTION_PAUSE = "ms.txams.vv.action.PAUSE"
        const val ACTION_NEXT = "ms.txams.vv.action.NEXT"
        const val ACTION_PREVIOUS = "ms.txams.vv.action.PREVIOUS"
        const val ACTION_STOP = "ms.txams.vv.action.STOP"
        
        private const val NOTIFICATION_CHANNEL_ID = "txa_music_playback"
        private const val NOTIFICATION_ID = 1001
    }
}

enum class TXAPlaybackState {
    IDLE,
    BUFFERING,
    READY,
    PLAYING,
    PAUSED,
    ENDED,
    ERROR
}

enum class RepeatMode {
    OFF,
    ALL,
    ONE
}
