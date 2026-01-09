package com.txapp.musicplayer.ui

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.RingtoneManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.txapp.musicplayer.service.MusicService
import com.txapp.musicplayer.ui.component.*
import com.txapp.musicplayer.util.*
import com.txapp.musicplayer.volume.AudioVolumeObserver
import com.txapp.musicplayer.volume.OnAudioVolumeChangedListener
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext

class NowPlayingActivity : ComponentActivity(), OnAudioVolumeChangedListener {

    private lateinit var mediaControllerFuture: ListenableFuture<MediaController>
    private val mediaController: MediaController?
        get() = try {
            if (::mediaControllerFuture.isInitialized && mediaControllerFuture.isDone) {
                mediaControllerFuture.get()
            } else null
        } catch (e: Exception) {
            null
        }

    private var audioVolumeObserver: AudioVolumeObserver? = null

    // UI State for Compose
    private val uiState = mutableStateOf(NowPlayingState())
    private var showQueueSheet by mutableStateOf(false)
    private var showSleepTimerDialog by mutableStateOf(false)
    private var showLyricsDialog by mutableStateOf(false)
    private var showPlaybackSpeedDialog by mutableStateOf(false)
    private var showAddToPlaylistSheet by mutableStateOf(false)
    private var showTagEditorSheet by mutableStateOf(false)
    private var isDriveModeActive by mutableStateOf(false)
    private var currentStyle by mutableStateOf("adaptive")
    private var startLyricsInEditMode by mutableStateOf(false)

    // Store pending updates for retry after permission grant
    private var pendingTagUpdate: TagEditData? = null
    private var pendingLyricsUpdate: String? = null

    private val writeRequestLauncher =
        registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult()) { result ->
            if (result.resultCode == android.app.Activity.RESULT_OK) {
                // Permission granted, retry the update
                pendingTagUpdate?.let { data ->
                    val songId = uiState.value.songId
                    if (songId != -1L) {
                        saveTagUpdate(songId, data)
                    }
                }
                pendingLyricsUpdate?.let { lyrics ->
                    val mediaUri = uiState.value.mediaUri
                    if (mediaUri.isNotEmpty()) {
                        saveLyricsUpdate(mediaUri, lyrics)
                    }
                }
            }
            pendingTagUpdate = null
            pendingLyricsUpdate = null
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Use Compose for everything
        val composeView = ComposeView(this).apply {
            setContent {
                val state by uiState
                val style by remember { derivedStateOf { currentStyle } }

                NowPlayingContent(
                    style = if (isDriveModeActive) "drive" else style,
                    state = state,
                    onPlayPause = { togglePlayPause() },
                    onNext = { mediaController?.seekToNext() },
                    onPrevious = { mediaController?.seekToPrevious() },
                    onSeek = { progress -> seekTo(progress) },
                    onToggleFavorite = { toggleFavorite() },
                    onToggleShuffle = { toggleShuffle() },
                    onToggleRepeat = { toggleRepeat() },
                    onShowQueue = { fetchQueueAndShow() },
                    onClose = { if (isDriveModeActive) isDriveModeActive = false else finish() },
                    onShowSleepTimer = { showSleepTimerDialog = true },
                    onShowLyrics = { inEditMode -> 
                        startLyricsInEditMode = inEditMode
                        showLyricsDialog = true 
                    },
                    onShowPlaybackSpeed = { showPlaybackSpeedDialog = true },
                    onAddToPlaylist = { showAddToPlaylistSheet = true },
                    onEditTag = { showTagEditorSheet = true },
                    onSetRingtone = { setAsRingtone() },
                    onDriveMode = { isDriveModeActive = !isDriveModeActive },
                    onPageChanged = { index -> seekToQueueIndex(index) },
                    onArtistClick = {
                        val intent = Intent(this@NowPlayingActivity, MainActivity::class.java).apply {
                            action = "ACTION_VIEW_ARTIST"
                            putExtra("artist_name", state.artist)
                            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        }
                        startActivity(intent)
                        finish()
                    }
                )

                // Tag Editor Sheet
                if (showTagEditorSheet) {
                    val songId = uiState.value.songId
                    var song by remember { mutableStateOf<com.txapp.musicplayer.model.Song?>(null) }
                    var songLoadAttempted by remember { mutableStateOf(false) }

                    // Fetch full song data from database for editor
                    LaunchedEffect(songId) {
                        songLoadAttempted = false
                        if (songId != -1L) {
                            val repository: com.txapp.musicplayer.data.MusicRepository = GlobalContext.get().get()
                            song = repository.getSongById(songId)
                        }
                        songLoadAttempted = true
                    }

                    // Show error if song cannot be loaded
                    LaunchedEffect(songLoadAttempted, song) {
                        if (songLoadAttempted && song == null) {
                            TXAToast.show(this@NowPlayingActivity, "txamusic_error_song_not_loaded".txa())
                            showTagEditorSheet = false
                        }
                    }

                    song?.let { s ->
                        TXATagEditorSheet(
                            song = s,
                            onDismiss = { showTagEditorSheet = false },
                            onSave = { data ->
                                saveTagUpdate(s.id, data)
                                showTagEditorSheet = false
                            }
                        )
                    }
                }

                // Queue Bottom Sheet
                if (showQueueSheet) {
                    val controller = mediaController
                    if (controller != null) {
                        val count = controller.mediaItemCount
                        val items = (0 until count).map { i ->
                            val item = controller.getMediaItemAt(i)
                            QueueItem(
                                index = i,
                                title = (item.mediaMetadata.title ?: "Unknown").toString(),
                                artist = (item.mediaMetadata.artist ?: "Unknown").toString(),
                                albumId = item.mediaMetadata.extras?.getLong("album_id") ?: 0L,
                                mediaUri = item.localConfiguration?.uri?.toString() ?: "",
                                isCurrent = i == controller.currentMediaItemIndex
                            )
                        }
                        QueueBottomSheet(
                            items = items,
                            currentIndex = controller.currentMediaItemIndex,
                            onItemClick = { index ->
                                controller.seekToDefaultPosition(index)
                                controller.play()
                                showQueueSheet = false
                            },
                            onDismiss = { showQueueSheet = false }
                        )
                    }
                }

                // Sleep Timer Dialog
                if (showSleepTimerDialog) {
                    SleepTimerDialog(
                        onDismiss = { showSleepTimerDialog = false },
                        onSetTimer = { minutes ->
                            // Timer is set in the dialog itself
                            showSleepTimerDialog = false
                        }
                    )
                }

                // Playback Speed Dialog
                if (showPlaybackSpeedDialog) {
                    PlaybackSpeedDialog(
                        onDismiss = { showPlaybackSpeedDialog = false },
                        onSpeedChanged = { speed ->
                            mediaController?.setPlaybackSpeed(speed)
                        }
                    )
                }

                // Add To Playlist Sheet
                if (showAddToPlaylistSheet) {
                    // Placeholder - need repository access for playlists
                    // For now just close it
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = { showAddToPlaylistSheet = false },
                        title = { androidx.compose.material3.Text("txamusic_add_to_playlist".txa()) },
                        text = { androidx.compose.material3.Text("txamusic_add_to_playlist_desc".txa()) },
                        confirmButton = {
                            androidx.compose.material3.TextButton(onClick = { showAddToPlaylistSheet = false }) {
                                androidx.compose.material3.Text("txamusic_btn_ok".txa())
                            }
                        }
                    )
                }

                // Lyrics Dialog
                if (showLyricsDialog) {
                    var lyricsVersion by remember { mutableIntStateOf(0) }
                    val currentLyrics = remember(state.lyrics, lyricsVersion) {
                        state.lyrics?.let { lrcContent ->
                            LyricsUtil.parseLrc(lrcContent)
                        } ?: emptyList()
                    }

                    LyricsDialog(
                        songTitle = state.title,
                        artistName = state.artist,
                        songPath = try {
                            android.net.Uri.parse(state.mediaUri).path ?: ""
                        } catch (e: Exception) {
                            ""
                        },
                        lyrics = currentLyrics,
                        currentPosition = state.position,
                        isPlaying = state.isPlaying,
                        onSeek = { position ->
                            val duration = mediaController?.duration ?: 0
                            if (duration > 0) {
                                val progress = (position * 1000f) / duration
                                seekTo(progress)
                            }
                        },
                        onDismiss = { showLyricsDialog = false },
                        onLyricsUpdated = {
                            lyricsVersion++
                            updateState() // Refresh lyrics in player immediately
                        },
                        onSearchLyrics = {
                            // Open Google search for lyrics
                            val url = LyricsUtil.buildSearchUrl(state.title, state.artist)
                            try {
                                com.txapp.musicplayer.util.TXAToast.info(this@NowPlayingActivity, "txamusic_lyrics_searching".txa())
                                startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)))
                            } catch (e: Exception) {
                                com.txapp.musicplayer.util.TXAToast.error(this@NowPlayingActivity, "txamusic_browser_not_found".txa())
                            }
                        },
                        onPermissionRequest = { intent, content ->
                            pendingLyricsUpdate = content
                            writeRequestLauncher.launch(
                                androidx.activity.result.IntentSenderRequest.Builder(intent).build()
                            )
                        },
                        startInEditMode = startLyricsInEditMode,
                        songDuration = state.duration
                    )
                }
            }
        }

        setContentView(composeView)

        currentStyle = TXAPreferences.getNowPlayingUI()
        initializeController()
        setupVolumeControl()
        startProgressUpdate()

        // Observe style changes
        lifecycleScope.launch {
            // Since there's no Flow for now playing UI in TXAPreferences yet, we refresh on resume or we could add one
            // For now, it's set on onCreate.
        }
    }

    private fun initializeController() {
        val sessionToken = SessionToken(this, android.content.ComponentName(this, MusicService::class.java))
        mediaControllerFuture = MediaController.Builder(this, sessionToken).buildAsync()
        mediaControllerFuture.addListener({
            val controller = mediaController ?: return@addListener
            controller.addListener(object : Player.Listener {
                override fun onMediaMetadataChanged(mediaMetadata: androidx.media3.common.MediaMetadata) {
                    updateState()
                }

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    updateState()
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    updateState()
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    updateState()
                }

                override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                    updateState()
                }

                override fun onRepeatModeChanged(repeatMode: Int) {
                    updateState()
                }
            })
            updateState()
        }, MoreExecutors.directExecutor())
    }

    private fun updateState() {
        val controller = mediaController ?: return
        val currentItem = controller.currentMediaItem ?: return
        val metadata = currentItem.mediaMetadata

        val albumId = metadata.extras?.getLong("album_id") ?: -1L
        val mediaUri = currentItem.localConfiguration?.uri?.toString() ?: ""

        val title = (metadata.title ?: "Unknown").toString()
        val artist = (metadata.artist ?: "Unknown").toString()

        // 1. Update basic info first
        uiState.value = uiState.value.copy(
            songId = currentItem.mediaId.toLongOrNull() ?: -1L,
            title = title,
            artist = artist,
            albumId = albumId,
            mediaUri = mediaUri,
            isPlaying = controller.isPlaying,
            isFavorite = metadata.extras?.getBoolean("is_favorite") ?: false,
            shuffleMode = controller.shuffleModeEnabled,
            repeatMode = when (controller.repeatMode) {
                Player.REPEAT_MODE_ALL -> 1
                Player.REPEAT_MODE_ONE -> 2
                else -> 0
            },
            duration = controller.duration.coerceAtLeast(0),
            position = controller.currentPosition.coerceAtLeast(0),
            lyrics = LyricsUtil.getRawLyrics(try {
                android.net.Uri.parse(mediaUri).path ?: ""
            } catch (e: Exception) {
                ""
            }, title, artist)
        )

        // 2. Clear old web art and fetch new if missing local
        if (albumId == -1L || TXADeviceInfo.isEmulator()) {
            fetchWebArt(title, artist)
        } else {
            uiState.value = uiState.value.copy(webArtUrl = "")
        }

        // 3. Build queue items for AlbumCoverPager
        val queueItems = buildQueueItems(controller)

        // 4. Update state with queue
        uiState.value = uiState.value.copy(
            queueItems = queueItems,
            currentQueueIndex = controller.currentMediaItemIndex
        )

        // 5. Then load colors asynchronously
        loadAlbumArtColors(albumId, mediaUri)
    }

    private fun buildQueueItems(controller: MediaController): List<AlbumCoverItem> {
        val count = controller.mediaItemCount
        return (0 until count).map { i ->
            val item = controller.getMediaItemAt(i)
            AlbumCoverItem(
                index = i,
                albumId = item.mediaMetadata.extras?.getLong("album_id") ?: -1L,
                mediaUri = item.localConfiguration?.uri?.toString() ?: "",
                webArtUrl = "" // Will be fetched separately if needed
            )
        }
    }

    private fun seekToQueueIndex(index: Int) {
        val controller = mediaController ?: return
        if (index != controller.currentMediaItemIndex && index in 0 until controller.mediaItemCount) {
            controller.seekToDefaultPosition(index)
            controller.play()
        }
    }

    private fun fetchWebArt(title: String, artist: String) {
        if (title == "Unknown") return

        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val song = com.txapp.musicplayer.model.Song(
                id = 0, title = title, artist = artist, album = "", data = "", duration = 0, albumId = -1
            )
            val url = TXAAlbumArtFetcher.fetchAlbumArtUrl(song)
            if (url != null) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    uiState.value = uiState.value.copy(webArtUrl = url)
                }
            }
        }
    }

    private var colorJob: kotlinx.coroutines.Job? = null
    private var lastAlbumId = -2L
    private var lastMediaUri = ""

    private fun loadAlbumArtColors(albumId: Long, mediaUri: String = "") {
        if (albumId == lastAlbumId && mediaUri == lastMediaUri) return
        lastAlbumId = albumId
        lastMediaUri = mediaUri

        // List of beautiful fallback colors
        val fallbackColors = listOf(
            0xFF00D269.toInt(), // Green
            0xFF6366F1.toInt(), // Indigo
            0xFFF59E0B.toInt(), // Amber
            0xFFEC4899.toInt(), // Pink
            0xFF8B5CF6.toInt(), // Purple
            0xFF14B8A6.toInt(), // Teal
            0xFFF97316.toInt(), // Orange
            0xFF3B82F6.toInt()  // Blue
        )

        val webUrl = uiState.value.webArtUrl

        val model: Any = when {
            webUrl.isNotEmpty() -> webUrl
            albumId != -1L -> android.content.ContentUris.withAppendedId(
                android.net.Uri.parse("content://media/external/audio/albumart"),
                albumId
            )

            mediaUri.isNotEmpty() -> android.net.Uri.parse(mediaUri)
            else -> return // Nothing to load
        }

        colorJob?.cancel()
        colorJob = lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                // Resize for color extraction to save memory
                val bitmap = com.bumptech.glide.Glide.with(applicationContext)
                    .asBitmap()
                    .load(model)
                    .centerCrop()
                    .submit(120, 120) // Smaller for palette is enough
                    .get()

                if (bitmap != null) {
                    androidx.palette.graphics.Palette.from(bitmap).generate { palette ->
                        val color = palette?.getVibrantColor(
                            palette.getMutedColor(fallbackColors.random())
                        ) ?: fallbackColors.random()
                        uiState.value = uiState.value.copy(vibrantColor = androidx.compose.ui.graphics.Color(color))
                    }
                }
            } catch (e: Exception) {
                // Use random fallback color on error
                val randomColor = fallbackColors.random()
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    uiState.value = uiState.value.copy(vibrantColor = androidx.compose.ui.graphics.Color(randomColor))
                }
            }
        }
    }

    private fun startProgressUpdate() {
        lifecycleScope.launch {
            while (true) {
                mediaController?.let { controller ->
                    if (controller.isPlaying) {
                        val duration = controller.duration
                        val position = controller.currentPosition
                        if (duration > 0) {
                            val progress = (position * 1000f) / duration
                            val remainingSleep = TXASleepTimerManager.getRemainingTime(applicationContext)
                            uiState.value = uiState.value.copy(
                                progress = progress,
                                position = position,
                                duration = duration,
                                sleepTimerRemainingMs = if (remainingSleep > 0) remainingSleep else null
                            )
                        }
                    }
                }
                delay(1000)
            }
        }
    }

    private fun togglePlayPause() {
        val controller = mediaController ?: return
        if (controller.isPlaying) controller.pause() else controller.play()
    }

    private fun seekTo(progress: Float) {
        val controller = mediaController ?: return
        val duration = controller.duration
        if (duration > 0) {
            val position = (progress * duration / 1000).toLong()
            controller.seekTo(position)
            uiState.value = uiState.value.copy(position = position, progress = progress)
        }
    }

    private fun toggleFavorite() {
        val controller = mediaController ?: return
        val mediaId = controller.currentMediaItem?.mediaId?.toLongOrNull() ?: return

        val intent = Intent(this, MusicService::class.java).apply {
            action = MusicService.ACTION_TOGGLE_FAVORITE
            putExtra(MusicService.EXTRA_SONG_ID, mediaId)
        }
        startService(intent)
        // Optimistic update
        val newFavorite = !uiState.value.isFavorite
        uiState.value = uiState.value.copy(isFavorite = newFavorite)

        TXAToast.show(
            this,
            if (newFavorite) "txamusic_action_add_to_favorites".txa() else "txamusic_action_remove_from_favorites".txa()
        )
    }

    private fun toggleShuffle() {
        val controller = mediaController ?: return
        val newShuffle = !controller.shuffleModeEnabled
        controller.shuffleModeEnabled = newShuffle

        TXAToast.show(
            this,
            if (newShuffle) "txamusic_shuffle_on".txa() else "txamusic_shuffle_off".txa()
        )
    }

    private fun toggleRepeat() {
        val controller = mediaController ?: return
        controller.repeatMode = when (controller.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    private fun fetchQueueAndShow() {
        showQueueSheet = true
    }

    private fun setupVolumeControl() {
        // We can keep volume observer but we should probably implement it in Compose if needed
        // For now, system volume buttons will work normally.
    }

    override fun onAudioVolumeChanged(currentVolume: Int, maxVolume: Int) {
        // Sync volume if needed
    }

    override fun onResume() {
        super.onResume()
        currentStyle = TXAPreferences.getNowPlayingUI()
        updateState()
    }

    private fun setAsRingtone() {
        val songId = uiState.value.songId
        if (songId == -1L) {
            TXAToast.show(this, "txamusic_error_song_not_loaded".txa())
            return
        }

        lifecycleScope.launch {
            val repository: com.txapp.musicplayer.data.MusicRepository = GlobalContext.get().get()
            val song = repository.getSongById(songId)
            if (song != null) {
                if (TXARingtoneManager.setRingtone(this@NowPlayingActivity, song)) {
                    TXAToast.show(this@NowPlayingActivity, "txamusic_ringtone_set_success".txa())
                }
            } else {
                TXAToast.show(this@NowPlayingActivity, "txamusic_error_song_not_found".txa())
            }
        }
    }

    private fun saveTagUpdate(songId: Long, data: TagEditData) {
        lifecycleScope.launch {
            val repository: com.txapp.musicplayer.data.MusicRepository = GlobalContext.get().get()

            // Check for Scoped Storage write permission on Android 11+
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                val song = repository.getSongById(songId)
                if (song != null) {
                    val pendingIntent = com.txapp.musicplayer.util.TXATagWriter.createWriteRequest(
                        this@NowPlayingActivity,
                        listOf(song.data)
                    )

                    if (pendingIntent != null) {
                        // Store the data so we can retry after permission is granted
                        pendingTagUpdate = data
                        val intentSenderRequest = androidx.activity.result.IntentSenderRequest.Builder(pendingIntent)
                            .build()
                        writeRequestLauncher.launch(intentSenderRequest)
                        return@launch
                    }
                }
            }

            // Proceed with update (either legacy storage or permission already granted/not needed)
            // Need to fetch song to get current track number since editor doesn't have it
            val currentSong = repository.getSongById(songId)
            val currentTrackNumber = currentSong?.trackNumber ?: 0

            val result = repository.updateSongMetadata(
                context = this@NowPlayingActivity,
                songId = songId,
                title = data.title,
                artist = data.artist,
                album = data.album,
                albumArtist = data.albumArtist.ifEmpty { null },
                composer = data.composer.ifEmpty { null },
                year = data.year.toIntOrNull() ?: 0,
                trackNumber = currentTrackNumber,
                artwork = data.artworkBitmap
            )

            when (result) {
                is com.txapp.musicplayer.util.TXATagWriter.WriteResult.Success -> {
                    TXAToast.show(this@NowPlayingActivity, "txamusic_tag_saved".txa())
                    // Update UI state immediately
                    uiState.value = uiState.value.copy(
                        title = data.title,
                        artist = data.artist,
                    )
                    updateState()
                }
                is com.txapp.musicplayer.util.TXATagWriter.WriteResult.PermissionRequired -> {
                    pendingTagUpdate = data
                    writeRequestLauncher.launch(
                        androidx.activity.result.IntentSenderRequest.Builder(result.intent).build()
                    )
                }
                else -> {
                    TXAToast.show(this@NowPlayingActivity, "txamusic_tag_save_failed".txa())
                }
            }

        }
    }

    private fun saveLyricsUpdate(mediaUri: String, lyrics: String) {
        lifecycleScope.launch {
            val path = try {
                android.net.Uri.parse(mediaUri).path ?: ""
            } catch (e: Exception) {
                ""
            }
            if (path.isEmpty()) return@launch

            val result = LyricsUtil.saveLyrics(this@NowPlayingActivity, path, lyrics)
            when (result) {
                is LyricsUtil.SaveResult.Success -> {
                    TXAToast.success(this@NowPlayingActivity, "txamusic_lyrics_saved".txa())
                    // Refresh state to show new lyrics
                    updateState()
                }
                is LyricsUtil.SaveResult.PermissionRequired -> {
                    pendingLyricsUpdate = lyrics
                    writeRequestLauncher.launch(
                        androidx.activity.result.IntentSenderRequest.Builder(result.intent).build()
                    )
                }
                is LyricsUtil.SaveResult.Failure -> {
                    TXAToast.error(this@NowPlayingActivity, "txamusic_lyrics_save_failed".txa())
                }
            }
        }
    }


    override fun onDestroy() {
        super.onDestroy()
        if (::mediaControllerFuture.isInitialized) {
            MediaController.releaseFuture(mediaControllerFuture)
        }
    }
}

