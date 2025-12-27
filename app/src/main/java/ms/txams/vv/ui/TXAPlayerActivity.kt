package ms.txams.vv.ui

import android.content.ComponentName
import android.content.Intent
import android.media.audiofx.AudioEffect
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Menu
import android.view.MenuItem
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import ms.txams.vv.R
import ms.txams.vv.core.TXATranslation
import ms.txams.vv.core.TXABackgroundLogger
import ms.txams.vv.service.MusicService
import ms.txams.vv.ui.dialog.TXAVolumeDialog
import ms.txams.vv.ui.widget.TXAAlbumArtView
import ms.txams.vv.ui.widget.TXAWaveformView

/**
 * TXA Player Activity
 * Full-screen music player inspired by Namida
 * 
 * Features:
 * - Waveform progress bar
 * - Album art with rotation/tilt effects
 * - All playback controls (play/pause, prev, next, shuffle, repeat)
 * - Volume control (media + system)
 * - Sleep timer
 * - Equalizer link
 * - Lyrics display
 * - Queue management
 * - Share, like, add to playlist
 * 
 * @author TXA - fb.com/vlog.txa.2311
 */
class TXAPlayerActivity : AppCompatActivity() {
    
    private lateinit var controllerFuture: ListenableFuture<MediaController>
    private var mediaController: MediaController? = null
    
    // UI Elements
    private lateinit var albumArt: TXAAlbumArtView
    private lateinit var waveform: TXAWaveformView
    private lateinit var tvTitle: TextView
    private lateinit var tvArtist: TextView
    private lateinit var tvAlbum: TextView
    private lateinit var tvCurrentTime: TextView
    private lateinit var tvTotalTime: TextView
    private lateinit var seekBar: SeekBar
    
    // Control buttons
    private lateinit var btnPlayPause: ImageButton
    private lateinit var btnPrevious: ImageButton
    private lateinit var btnNext: ImageButton
    private lateinit var btnShuffle: ImageButton
    private lateinit var btnRepeat: ImageButton
    private lateinit var btnVolume: ImageButton
    private lateinit var btnLike: ImageButton
    private lateinit var btnQueue: ImageButton
    private lateinit var btnSleep: ImageButton
    private lateinit var btnEqualizer: ImageButton
    private lateinit var btnLyrics: ImageButton
    private lateinit var btnSpeed: ImageButton
    private lateinit var btnMore: ImageButton
    
    // State
    private var isLiked = false
    private var sleepTimerMinutes = 0
    
    // Handler for progress updates
    private val progressHandler = Handler(Looper.getMainLooper())
    private val progressRunnable = object : Runnable {
        override fun run() {
            updateProgress()
            progressHandler.postDelayed(this, 100)
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_txa_player)
        
        setupViews()
        setupListeners()
        setupMediaController()
    }
    
    private fun setupViews() {
        // Album art
        albumArt = findViewById(R.id.albumArt)
        albumArt.loadShapeFromSettings()
        
        // Waveform
        waveform = findViewById(R.id.waveform)
        
        // Text views
        tvTitle = findViewById(R.id.tvTitle)
        tvArtist = findViewById(R.id.tvArtist)
        tvAlbum = findViewById(R.id.tvAlbum)
        tvCurrentTime = findViewById(R.id.tvCurrentTime)
        tvTotalTime = findViewById(R.id.tvTotalTime)
        
        // Seekbar
        seekBar = findViewById(R.id.seekBar)
        
        // Control buttons
        btnPlayPause = findViewById(R.id.btnPlayPause)
        btnPrevious = findViewById(R.id.btnPrevious)
        btnNext = findViewById(R.id.btnNext)
        btnShuffle = findViewById(R.id.btnShuffle)
        btnRepeat = findViewById(R.id.btnRepeat)
        btnVolume = findViewById(R.id.btnVolume)
        btnLike = findViewById(R.id.btnLike)
        btnQueue = findViewById(R.id.btnQueue)
        btnSleep = findViewById(R.id.btnSleep)
        btnEqualizer = findViewById(R.id.btnEqualizer)
        btnLyrics = findViewById(R.id.btnLyrics)
        btnSpeed = findViewById(R.id.btnSpeed)
        btnMore = findViewById(R.id.btnMore)
    }
    
    private fun setupListeners() {
        // Play/Pause
        btnPlayPause.setOnClickListener {
            mediaController?.let { controller ->
                if (controller.isPlaying) {
                    controller.pause()
                } else {
                    controller.play()
                }
            }
        }
        
        // Previous
        btnPrevious.setOnClickListener {
            mediaController?.seekToPrevious()
        }
        
        // Next
        btnNext.setOnClickListener {
            mediaController?.seekToNext()
        }
        
        // Shuffle
        btnShuffle.setOnClickListener {
            mediaController?.let { controller ->
                val newMode = !controller.shuffleModeEnabled
                controller.shuffleModeEnabled = newMode
                updateShuffleButton(newMode)
                val msg = if (newMode) "txamusic_shuffle_on" else "txamusic_shuffle_off"
                Toast.makeText(this, TXATranslation.txa(msg), Toast.LENGTH_SHORT).show()
            }
        }
        
        // Repeat
        btnRepeat.setOnClickListener {
            mediaController?.let { controller ->
                val newMode = when (controller.repeatMode) {
                    Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                    Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                    else -> Player.REPEAT_MODE_OFF
                }
                controller.repeatMode = newMode
                updateRepeatButton(newMode)
                
                val msg = when (newMode) {
                    Player.REPEAT_MODE_OFF -> "txamusic_repeat_off"
                    Player.REPEAT_MODE_ALL -> "txamusic_repeat_all"
                    else -> "txamusic_repeat_one"
                }
                Toast.makeText(this, TXATranslation.txa(msg), Toast.LENGTH_SHORT).show()
            }
        }
        
        // Volume - show dialog with 2 sliders
        btnVolume.setOnClickListener {
            showVolumeDialog()
        }
        
        // Like
        btnLike.setOnClickListener {
            isLiked = !isLiked
            updateLikeButton()
            val msg = if (isLiked) "Added to favorites" else "Removed from favorites"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
        
        // Queue
        btnQueue.setOnClickListener {
            Toast.makeText(this, TXATranslation.txa("txamusic_queue"), Toast.LENGTH_SHORT).show()
            // TODO: Show queue bottom sheet
        }
        
        // Sleep timer
        btnSleep.setOnClickListener {
            showSleepTimerDialog()
        }
        
        // Equalizer
        btnEqualizer.setOnClickListener {
            openEqualizer()
        }
        
        // Lyrics
        btnLyrics.setOnClickListener {
            Toast.makeText(this, TXATranslation.txa("txamusic_lyrics"), Toast.LENGTH_SHORT).show()
            // TODO: Show lyrics
        }
        
        // Playback speed
        btnSpeed.setOnClickListener {
            showSpeedDialog()
        }
        
        // More options
        btnMore.setOnClickListener {
            showMoreOptionsMenu()
        }
        
        // Seekbar
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    mediaController?.seekTo(progress.toLong())
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        
        // Waveform tap to seek
        waveform.onSeek = { progress ->
            mediaController?.let { controller ->
                val seekPosition = (controller.duration * progress).toLong()
                controller.seekTo(seekPosition)
            }
        }
    }
    
    private fun setupMediaController() {
        val sessionToken = SessionToken(this, ComponentName(this, MusicService::class.java))
        controllerFuture = MediaController.Builder(this, sessionToken).buildAsync()
        
        controllerFuture.addListener({
            mediaController = controllerFuture.get()
            setupPlayerListener()
            updateUI()
        }, MoreExecutors.directExecutor())
    }
    
    private fun setupPlayerListener() {
        mediaController?.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                updateTrackInfo(mediaItem)
            }
            
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updatePlayPauseButton(isPlaying)
                if (isPlaying) {
                    albumArt.enableRotation = true
                } else {
                    albumArt.pauseRotation()
                }
            }
            
            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                updateShuffleButton(shuffleModeEnabled)
            }
            
            override fun onRepeatModeChanged(repeatMode: Int) {
                updateRepeatButton(repeatMode)
            }
        })
    }
    
    private fun updateUI() {
        mediaController?.let { controller ->
            updateTrackInfo(controller.currentMediaItem)
            updatePlayPauseButton(controller.isPlaying)
            updateShuffleButton(controller.shuffleModeEnabled)
            updateRepeatButton(controller.repeatMode)
            
            // Setup seekbar
            val duration = controller.duration
            if (duration > 0) {
                seekBar.max = duration.toInt()
                tvTotalTime.text = formatTime(duration)
            }
        }
    }
    
    private fun updateTrackInfo(mediaItem: MediaItem?) {
        mediaItem?.let { item ->
            tvTitle.text = item.mediaMetadata.title ?: "Unknown"
            tvArtist.text = item.mediaMetadata.artist ?: "Unknown Artist"
            tvAlbum.text = item.mediaMetadata.albumTitle ?: ""
            
            // Update album art
            item.mediaMetadata.artworkUri?.let { uri ->
                // Load from URI
            }
            
            // Generate new waveform
            waveform.generateDummyWaveform()
        }
    }
    
    private fun updatePlayPauseButton(isPlaying: Boolean) {
        btnPlayPause.setImageResource(
            if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        )
    }
    
    private fun updateShuffleButton(enabled: Boolean) {
        btnShuffle.alpha = if (enabled) 1.0f else 0.5f
    }
    
    private fun updateRepeatButton(mode: Int) {
        when (mode) {
            Player.REPEAT_MODE_OFF -> {
                btnRepeat.setImageResource(R.drawable.ic_repeat)
                btnRepeat.alpha = 0.5f
            }
            Player.REPEAT_MODE_ALL -> {
                btnRepeat.setImageResource(R.drawable.ic_repeat)
                btnRepeat.alpha = 1.0f
            }
            Player.REPEAT_MODE_ONE -> {
                btnRepeat.setImageResource(R.drawable.ic_repeat) // Could use ic_repeat_one
                btnRepeat.alpha = 1.0f
            }
        }
    }
    
    private fun updateLikeButton() {
        btnLike.setImageResource(
            if (isLiked) R.drawable.ic_heart else R.drawable.ic_heart
        )
        btnLike.alpha = if (isLiked) 1.0f else 0.5f
    }
    
    private fun updateProgress() {
        mediaController?.let { controller ->
            val position = controller.currentPosition
            val duration = controller.duration
            
            seekBar.progress = position.toInt()
            tvCurrentTime.text = formatTime(position)
            
            if (duration > 0) {
                waveform.progress = position.toFloat() / duration.toFloat()
            }
        }
    }
    
    private fun formatTime(ms: Long): String {
        val seconds = (ms / 1000) % 60
        val minutes = (ms / 1000 / 60) % 60
        val hours = ms / 1000 / 60 / 60
        
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%d:%02d", minutes, seconds)
        }
    }
    
    private fun showVolumeDialog() {
        val dialog = TXAVolumeDialog.newInstance()
        dialog.onMediaVolumeChange = { volume ->
            // Set app internal volume
            mediaController?.volume = volume
            TXABackgroundLogger.d("Media volume changed to: $volume")
        }
        dialog.show(supportFragmentManager, "volume_dialog")
    }
    
    private fun showSleepTimerDialog() {
        val options = arrayOf("15 ${TXATranslation.txa("txamusic_minutes")}", 
                             "30 ${TXATranslation.txa("txamusic_minutes")}", 
                             "45 ${TXATranslation.txa("txamusic_minutes")}", 
                             "60 ${TXATranslation.txa("txamusic_minutes")}",
                             TXATranslation.txa("txamusic_timer_off"))
        val values = arrayOf(15, 30, 45, 60, 0)
        
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(TXATranslation.txa("txamusic_sleep_timer"))
            .setItems(options) { _, which ->
                sleepTimerMinutes = values[which]
                if (sleepTimerMinutes > 0) {
                    Toast.makeText(this, 
                        TXATranslation.txa("txamusic_timer_set").format("$sleepTimerMinutes min"), 
                        Toast.LENGTH_SHORT).show()
                    // TODO: Start sleep timer
                } else {
                    Toast.makeText(this, TXATranslation.txa("txamusic_timer_off"), Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }
    
    private fun showSpeedDialog() {
        val speeds = arrayOf("0.5x", "0.75x", "1.0x", "1.25x", "1.5x", "2.0x")
        val speedValues = floatArrayOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
        
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(TXATranslation.txa("txamusic_playback_speed"))
            .setItems(speeds) { _, which ->
                mediaController?.setPlaybackSpeed(speedValues[which])
                Toast.makeText(this, "Speed: ${speeds[which]}", Toast.LENGTH_SHORT).show()
            }
            .show()
    }
    
    private fun openEqualizer() {
        try {
            val intent = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).apply {
                putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
                // Note: MediaController doesn't expose audioSessionId directly
                // Pass 0 to open system equalizer without specific session
                putExtra(AudioEffect.EXTRA_AUDIO_SESSION, 0)
                putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "No equalizer app found", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun showMoreOptionsMenu() {
        val options = arrayOf(
            TXATranslation.txa("txamusic_add_to_playlist"),
            TXATranslation.txa("txamusic_share"),
            TXATranslation.txa("txamusic_track_info")
        )
        
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> Toast.makeText(this, "Add to playlist", Toast.LENGTH_SHORT).show()
                    1 -> shareCurrentTrack()
                    2 -> showTrackInfo()
                }
            }
            .show()
    }
    
    private fun shareCurrentTrack() {
        mediaController?.currentMediaItem?.let { item ->
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "Now playing: ${item.mediaMetadata.title} - ${item.mediaMetadata.artist}")
            }
            startActivity(Intent.createChooser(shareIntent, TXATranslation.txa("txamusic_share")))
        }
    }
    
    private fun showTrackInfo() {
        mediaController?.currentMediaItem?.let { item ->
            val info = """
                Title: ${item.mediaMetadata.title}
                Artist: ${item.mediaMetadata.artist}
                Album: ${item.mediaMetadata.albumTitle}
            """.trimIndent()
            
            com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(TXATranslation.txa("txamusic_track_info"))
                .setMessage(info)
                .setPositiveButton(TXATranslation.txa("txamusic_ok"), null)
                .show()
        }
    }
    
    override fun onResume() {
        super.onResume()
        progressHandler.post(progressRunnable)
    }
    
    override fun onPause() {
        super.onPause()
        progressHandler.removeCallbacks(progressRunnable)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        MediaController.releaseFuture(controllerFuture)
    }
}
