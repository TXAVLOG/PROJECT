package com.txapp.musicplayer.ui

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.bumptech.glide.Glide
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.slider.Slider
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.txapp.musicplayer.R
import com.txapp.musicplayer.service.MusicService
import com.txapp.musicplayer.util.TXALogger
import kotlinx.coroutines.*

/**
 * Lockscreen Player Activity
 * Hiển thị trình phát nhạc trên màn hình khóa
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class LockScreenActivity : AppCompatActivity() {

    // Views
    private lateinit var ivBlurredBackground: ImageView
    private lateinit var ivAlbumArt: ImageView
    private lateinit var tvTitle: TextView
    private lateinit var tvArtist: TextView
    private lateinit var tvCurrentTime: TextView
    private lateinit var tvTotalTime: TextView
    private lateinit var progressSlider: Slider
    private lateinit var btnPlayPause: FloatingActionButton
    private lateinit var btnPrevious: ImageButton
    private lateinit var btnNext: ImageButton
    private lateinit var btnShuffle: ImageButton
    private lateinit var btnRepeat: ImageButton
    private lateinit var tvSwipeHint: TextView
    
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var updateJob: Job? = null
    private var isUserSeeking = false
    
    // MediaController
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    
    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            updatePlayPauseButton(isPlaying)
        }
        
        override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
            updateSongInfo()
        }
        
        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            updateShuffleState(shuffleModeEnabled)
        }
        
        override fun onRepeatModeChanged(repeatMode: Int) {
            updateRepeatState(repeatMode)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Enable show on lock screen
        setupLockScreen()
        
        setContentView(R.layout.activity_lock_screen)
        
        // Hide status bar for immersive experience
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )
        
        initViews()
        setupListeners()
        
        // Animate hint text
        tvSwipeHint.apply {
            alpha = 0f
            translationY = 50f
            animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(800)
                .setStartDelay(500)
                .start()
        }
    }
    
    @Suppress("DEPRECATION")
    private fun setupLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
        
        // Keep screen on while activity is visible
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
    
    private fun initViews() {
        ivBlurredBackground = findViewById(R.id.iv_blurred_background)
        ivAlbumArt = findViewById(R.id.iv_album_art)
        tvTitle = findViewById(R.id.tv_title)
        tvArtist = findViewById(R.id.tv_artist)
        tvCurrentTime = findViewById(R.id.tv_current_time)
        tvTotalTime = findViewById(R.id.tv_total_time)
        progressSlider = findViewById(R.id.progress_slider)
        btnPlayPause = findViewById(R.id.btn_play_pause)
        btnPrevious = findViewById(R.id.btn_previous)
        btnNext = findViewById(R.id.btn_next)
        btnShuffle = findViewById(R.id.btn_shuffle)
        btnRepeat = findViewById(R.id.btn_repeat)
        tvSwipeHint = findViewById(R.id.tv_swipe_hint)
        
        // Enable marquee for title
        tvTitle.isSelected = true
    }
    
    private fun setupListeners() {
        btnPlayPause.setOnClickListener {
            mediaController?.let { controller ->
                if (controller.isPlaying) {
                    controller.pause()
                } else {
                    controller.play()
                }
            }
        }
        
        btnPrevious.setOnClickListener {
            mediaController?.seekToPreviousMediaItem()
        }
        
        btnNext.setOnClickListener {
            mediaController?.seekToNextMediaItem()
        }
        
        btnShuffle.setOnClickListener {
            mediaController?.let { controller ->
                controller.shuffleModeEnabled = !controller.shuffleModeEnabled
            }
        }
        
        btnRepeat.setOnClickListener {
            mediaController?.let { controller ->
                val newMode = when (controller.repeatMode) {
                    Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                    Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                    else -> Player.REPEAT_MODE_OFF
                }
                controller.repeatMode = newMode
            }
        }
        
        progressSlider.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {
                isUserSeeking = true
            }
            
            override fun onStopTrackingTouch(slider: Slider) {
                isUserSeeking = false
                mediaController?.seekTo(slider.value.toLong())
            }
        })
        
        // Swipe down to dismiss
        val rootView = findViewById<View>(R.id.lock_screen_root)
        rootView.setOnTouchListener(SwipeDismissTouchListener(rootView) {
            finish()
            @Suppress("DEPRECATION")
            overridePendingTransition(0, R.anim.slide_out_down)
        })
    }
    
    override fun onStart() {
        super.onStart()
        initializeMediaController()
    }
    
    override fun onResume() {
        super.onResume()
        updateUI()
        startProgressUpdate()
    }
    
    override fun onPause() {
        super.onPause()
        stopProgressUpdate()
    }
    
    override fun onStop() {
        super.onStop()
        releaseMediaController()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
    
    private fun initializeMediaController() {
        val sessionToken = SessionToken(this, ComponentName(this, MusicService::class.java))
        controllerFuture = MediaController.Builder(this, sessionToken).buildAsync()
        controllerFuture?.addListener({
            try {
                mediaController = controllerFuture?.get()
                mediaController?.addListener(playerListener)
                updateUI()
            } catch (e: Exception) {
                TXALogger.e("LockScreen", "Error connecting to MediaController", e)
            }
        }, MoreExecutors.directExecutor())
    }
    
    private fun releaseMediaController() {
        mediaController?.removeListener(playerListener)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        mediaController = null
    }
    
    private fun updateUI() {
        val controller = mediaController ?: return
        
        updateSongInfo()
        updatePlayPauseButton(controller.isPlaying)
        updateShuffleState(controller.shuffleModeEnabled)
        updateRepeatState(controller.repeatMode)
        
        // Update duration
        val duration = controller.duration
        if (duration > 0) {
            progressSlider.valueTo = duration.toFloat()
            tvTotalTime.text = formatTime(duration)
        }
    }
    
    private fun updateSongInfo() {
        val controller = mediaController ?: return
        val metadata = controller.currentMediaItem?.mediaMetadata
        
        // Update song info
        tvTitle.text = metadata?.title ?: getString(R.string.txamusic_aod_no_music)
        tvArtist.text = metadata?.artist ?: ""
        
        // Update album art
        val artworkUri = metadata?.artworkUri
            ?: metadata?.extras?.getLong("album_id")?.let { albumId ->
                if (albumId >= 0) android.net.Uri.parse("content://media/external/audio/albumart/$albumId")
                else null
            }
        
        if (artworkUri != null && !isFinishing) {
            // Load album art
            Glide.with(this)
                .asBitmap()
                .load(artworkUri)
                .placeholder(R.drawable.ic_music_note)
                .error(R.drawable.ic_music_note)
                .into(ivAlbumArt)
            
            // Load blurred background
            Glide.with(this)
                .asBitmap()
                .load(artworkUri)
                .transform(
                    com.bumptech.glide.load.resource.bitmap.CenterCrop(),
                    jp.wasabeef.glide.transformations.BlurTransformation(25, 3)
                )
                .into(ivBlurredBackground)
        } else if (!isFinishing) {
            ivAlbumArt.setImageResource(R.drawable.ic_music_note)
            ivBlurredBackground.setImageResource(R.drawable.ic_music_note)
        }
    }
    
    private fun updatePlayPauseButton(isPlaying: Boolean) {
        btnPlayPause.setImageResource(
            if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        )
    }
    
    private fun updateShuffleState(isEnabled: Boolean) {
        val color = if (isEnabled) {
            ContextCompat.getColor(this, R.color.accent_color)
        } else {
            ContextCompat.getColor(this, android.R.color.white)
        }
        btnShuffle.setColorFilter(color)
    }
    
    private fun updateRepeatState(repeatMode: Int) {
        val (icon, color) = when (repeatMode) {
            Player.REPEAT_MODE_ONE -> Pair(
                R.drawable.ic_repeat_one,
                ContextCompat.getColor(this, R.color.accent_color)
            )
            Player.REPEAT_MODE_ALL -> Pair(
                R.drawable.ic_repeat,
                ContextCompat.getColor(this, R.color.accent_color)
            )
            else -> Pair(
                R.drawable.ic_repeat,
                ContextCompat.getColor(this, android.R.color.white)
            )
        }
        
        btnRepeat.setImageResource(icon)
        btnRepeat.setColorFilter(color)
    }
    
    private fun startProgressUpdate() {
        updateJob = scope.launch {
            while (isActive) {
                if (!isUserSeeking) {
                    val controller = mediaController
                    val position = controller?.currentPosition ?: 0L
                    val duration = controller?.duration ?: 0L
                    
                    if (duration > 0) {
                        progressSlider.value = position.toFloat().coerceIn(0f, duration.toFloat())
                        tvCurrentTime.text = formatTime(position)
                    }
                }
                delay(500)
            }
        }
    }
    
    private fun stopProgressUpdate() {
        updateJob?.cancel()
        updateJob = null
    }
    
    private fun formatTime(ms: Long): String {
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%d:%02d", minutes, seconds)
    }
    
    /**
     * Simple swipe-to-dismiss touch listener
     */
    private inner class SwipeDismissTouchListener(
        private val view: View,
        private val onDismiss: () -> Unit
    ) : View.OnTouchListener {
        
        private var startY = 0f
        private var startTranslationY = 0f
        private val threshold = 200f
        
        override fun onTouch(v: View, event: android.view.MotionEvent): Boolean {
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    startY = event.rawY
                    startTranslationY = view.translationY
                    return true
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    val deltaY = event.rawY - startY
                    if (deltaY > 0) { // Only allow swipe down
                        view.translationY = startTranslationY + deltaY
                        view.alpha = 1f - (deltaY / (view.height * 0.5f)).coerceIn(0f, 1f)
                    }
                    return true
                }
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                    val deltaY = event.rawY - startY
                    if (deltaY > threshold) {
                        onDismiss()
                    } else {
                        // Animate back
                        view.animate()
                            .translationY(0f)
                            .alpha(1f)
                            .setDuration(200)
                            .start()
                    }
                    return true
                }
            }
            return false
        }
    }
}
