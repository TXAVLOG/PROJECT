package ms.txams.vv.ui.nowplaying

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.SeekBar
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import ms.txams.vv.R
import ms.txams.vv.core.data.database.entity.TXASongEntity
import ms.txams.vv.core.service.TXAMusicService
import ms.txams.vv.core.service.RepeatMode
import ms.txams.vv.core.txa
import ms.txams.vv.databinding.ActivityTxaNowPlayingBinding
import ms.txams.vv.ui.nowplaying.viewmodel.TXANowPlayingViewModel
import timber.log.Timber
import javax.inject.Inject

/**
 * TXA Now Playing Activity - One UI 8 design với glassmorphism effects
 * Features: Full-screen playback, shared elements, audio controls, lyrics integration
 */
@AndroidEntryPoint
class TXANowPlayingActivity : AppCompatActivity() {

    @Inject
    lateinit var translation: ms.txams.vv.core.TXATranslation

    private lateinit var binding: ActivityTxaNowPlayingBinding
    private val viewModel: TXANowPlayingViewModel by viewModels()

    // Service connection
    private var musicService: TXAMusicService? = null
    private var isServiceBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as TXAMusicService.TXAMusicBinder
            musicService = binder.getService()
            isServiceBound = true
            
            // Start observing service state
            observeServiceState()
            
            Timber.d("TXAMusicService connected in NowPlaying")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            musicService = null
            isServiceBound = false
            Timber.d("TXAMusicService disconnected in NowPlaying")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Enable edge-to-edge display for One UI 8
        enableEdgeToEdge()
        
        binding = ActivityTxaNowPlayingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupUI()
        observeViewModel()
        connectToService()
        
        // Apply One UI 8 design system
        applyOneUIDesign()
    }

    private fun enableEdgeToEdge() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let { controller ->
                controller.hide(WindowInsets.Type.systemBars())
                controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                )
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        }
    }

    private fun setupUI() {
        // Setup status bar spacer
        setupStatusBarSpacer()
        
        // Setup click listeners
        setupClickListeners()
        
        // Setup progress bar
        setupProgressBar()
        
        // Initialize UI state
        updateUIState(null, false)
    }

    private fun setupStatusBarSpacer() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.decorView.setOnApplyWindowInsetsListener { view, insets ->
                val statusBarHeight = insets.getInsets(WindowInsets.Type.statusBars()).top
                binding.statusBarSpacer.layoutParams.height = statusBarHeight
                view.requestLayout()
                insets
            }
        }
    }

    private fun setupClickListeners() {
        // Navigation
        binding.backButton.setOnClickListener {
            finishWithAnimation()
        }

        binding.menuButton.setOnClickListener {
            showOptionsMenu()
        }

        // Playback controls
        binding.playPauseButton.setOnClickListener {
            togglePlayback()
        }

        binding.previousButton.setOnClickListener {
            playPrevious()
        }

        binding.nextButton.setOnClickListener {
            playNext()
        }

        // Playback modes
        binding.shuffleButton.setOnClickListener {
            toggleShuffle()
        }

        binding.repeatButton.setOnClickListener {
            toggleRepeat()
        }

        // Action buttons
        binding.favoriteButton.setOnClickListener {
            toggleFavorite()
        }

        binding.lyricsButton.setOnClickListener {
            openLyrics()
        }

        binding.queueButton.setOnClickListener {
            openQueue()
        }

        binding.audioEffectsButton.setOnClickListener {
            openAudioEffects()
        }

        // Album art click for shared element transition
        binding.albumArt.setOnClickListener {
            // Handle album art interaction
        }
    }

    private fun setupProgressBar() {
        binding.progressBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    seekToPosition(progress)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                // User started seeking
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                // User stopped seeking
            }
        })
    }

    private fun observeViewModel() {
        // Separate lifecycle-aware collectors to prevent memory leaks
        lifecycleScope.launch {
            viewModel.currentSong.collect { song ->
                updateUIState(song, viewModel.isPlaying.value)
            }
        }
        
        lifecycleScope.launch {
            viewModel.isPlaying.collect { isPlaying ->
                updatePlaybackState(isPlaying)
            }
        }
        
        lifecycleScope.launch {
            viewModel.playbackProgress.collect { progress ->
                updateProgress(progress)
            }
        }
        
        lifecycleScope.launch {
            viewModel.shuffleMode.collect { enabled ->
                updateShuffleState(enabled)
            }
        }
        
        lifecycleScope.launch {
            viewModel.repeatMode.collect { mode ->
                updateRepeatState(mode)
            }
        }
    }

    private fun connectToService() {
        try {
            if (!isServiceBound) {
                val intent = Intent(this, TXAMusicService::class.java)
                bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
                Timber.d("Attempting to connect to TXAMusicService from NowPlaying")
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to bind to music service from NowPlaying")
        }
    }

    private fun disconnectFromService() {
        try {
            if (isServiceBound) {
                unbindService(serviceConnection)
                musicService = null
                isServiceBound = false
                Timber.d("Disconnected from TXAMusicService in NowPlaying")
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to disconnect from music service in NowPlaying")
        }
    }

    private fun observeServiceState() {
        // Separate lifecycle-aware collectors to prevent memory leaks
        lifecycleScope.launch {
            musicService?.let { service ->
                service.currentSong.collect { song ->
                    viewModel.updateCurrentSong(song)
                }
            }
        }
        
        lifecycleScope.launch {
            musicService?.let { service ->
                service.isPlaying.collect { isPlaying ->
                    viewModel.updatePlayingState(isPlaying)
                }
            }
        }
        
        lifecycleScope.launch {
            musicService?.let { service ->
                service.playbackState.collect { state ->
                    viewModel.updatePlaybackState(state)
                }
            }
        }
    }

    private fun updateUIState(song: TXASongEntity?, isPlaying: Boolean) {
        binding.apply {
            song?.let {
                songTitle.text = it.title
                artistName.text = it.artist
                
                // Load album art
                loadAlbumArt(it.albumArtPath)
                
                // Update favorite button
                updateFavoriteButton(it.favorite)
                
                // Show content
                songInfoContainer.visibility = View.VISIBLE
                playbackControls.visibility = View.VISIBLE
                actionButtons.visibility = View.VISIBLE
                
            } ?: run {
                // Hide content when no song
                songInfoContainer.visibility = View.GONE
                playbackControls.visibility = View.GONE
                actionButtons.visibility = View.GONE
            }
        }
    }

    private fun loadAlbumArt(albumArtPath: String?) {
        // Implementation for loading album art with Glide or similar
        // Apply glassmorphism effect to album art
    }

    private fun updatePlaybackState(isPlaying: Boolean) {
        binding.playPauseButton.apply {
            val iconRes = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
            setImageResource(iconRes)
            
            // Animate button state change
            animate()
                .scaleX(0.8f)
                .scaleY(0.8f)
                .setDuration(100)
                .withEndAction {
                    animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(100)
                        .start()
                }
                .start()
        }
    }

    private fun updateProgress(progress: Float) {
        binding.progressBar.progress = (progress * 1000).toInt()
        
        // Update time labels
        val currentPos = (progress * viewModel.duration.value).toLong()
        binding.currentTime.text = formatTime(currentPos)
        binding.totalTime.text = formatTime(viewModel.duration.value)
    }

    private fun updateShuffleState(enabled: Boolean) {
        binding.shuffleButton.apply {
            setColorFilter(
                if (enabled) getColor(R.color.txa_primary) else getColor(R.color.txa_text_secondary_white)
            )
        }
    }

    private fun updateRepeatState(mode: RepeatMode) {
        binding.repeatButton.apply {
            when (mode) {
                RepeatMode.OFF -> {
                    setImageResource(R.drawable.ic_repeat)
                    setColorFilter(getColor(R.color.txa_text_secondary_white))
                }
                RepeatMode.ALL -> {
                    setImageResource(R.drawable.ic_repeat)
                    setColorFilter(getColor(R.color.txa_primary))
                }
                RepeatMode.ONE -> {
                    setImageResource(R.drawable.ic_repeat_one)
                    setColorFilter(getColor(R.color.txa_primary))
                }
            }
        }
    }

    private fun updateFavoriteButton(isFavorite: Boolean) {
        binding.favoriteButton.apply {
            setImageResource(
                if (isFavorite) R.drawable.ic_favorite else R.drawable.ic_favorite_border
            )
            setColorFilter(
                if (isFavorite) getColor(R.color.txa_error) else getColor(android.R.color.white)
            )
        }
    }

    // Playback control methods
    private fun togglePlayback() {
        try {
            musicService?.let { service ->
                if (viewModel.isPlaying.value) {
                    service.pause()
                } else {
                    service.play()
                }
            } ?: run {
                showError("Music service not available")
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to toggle playback")
            showError("Failed to control playback")
        }
    }

    private fun playPrevious() {
        try {
            musicService?.playPrevious() ?: run {
                showError("Music service not available")
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to play previous")
            showError("Failed to play previous track")
        }
    }

    private fun playNext() {
        try {
            musicService?.playNext() ?: run {
                showError("Music service not available")
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to play next")
            showError("Failed to play next track")
        }
    }

    private fun seekToPosition(progress: Int) {
        try {
            val position = (progress / 1000f * viewModel.duration.value).toLong()
            musicService?.seekTo(position) ?: run {
                showError("Music service not available")
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to seek to position")
            showError("Failed to seek")
        }
    }

    private fun toggleShuffle() {
        try {
            musicService?.setShuffleMode(!viewModel.shuffleMode.value) ?: run {
                showError("Music service not available")
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to toggle shuffle")
            showError("Failed to toggle shuffle")
        }
    }

    private fun toggleRepeat() {
        try {
            val currentMode = viewModel.repeatMode.value
            val newMode = when (currentMode) {
                RepeatMode.OFF -> RepeatMode.ALL
                RepeatMode.ALL -> RepeatMode.ONE
                RepeatMode.ONE -> RepeatMode.OFF
            }
            musicService?.setRepeatMode(newMode) ?: run {
                showError("Music service not available")
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to toggle repeat")
            showError("Failed to toggle repeat")
        }
    }

    private fun toggleFavorite() {
        viewModel.currentSong.value?.let { song ->
            viewModel.toggleFavorite(song.id)
        }
    }

    // Action methods
    private fun showOptionsMenu() {
        // Show options menu with One UI 8 design
    }

    private fun openLyrics() {
        val intent = Intent(this, TXALyricsActivity::class.java)
        startActivity(intent)
        overridePendingTransition(R.anim.slide_up, R.anim.fade_out)
    }

    private fun openQueue() {
        val intent = Intent(this, TXAQueueActivity::class.java)
        startActivity(intent)
        overridePendingTransition(R.anim.slide_up, R.anim.fade_out)
    }

    private fun openAudioEffects() {
        val intent = Intent(this, TXAAudioEffectsActivity::class.java)
        startActivity(intent)
        overridePendingTransition(R.anim.slide_up, R.anim.fade_out)
    }

    private fun applyOneUIDesign() {
        // Apply One UI 8 design system
        window.apply {
            statusBarColor = android.graphics.Color.TRANSPARENT
            navigationBarColor = android.graphics.Color.TRANSPARENT
        }
        
        // Apply glassmorphism effects
        applyGlassmorphismEffects()
    }

    private fun applyGlassmorphismEffects() {
        // Apply blur and transparency effects to UI elements
        // One UI 8 glassmorphism implementation
    }

    private fun formatTime(milliseconds: Long): String {
        val seconds = (milliseconds / 1000) % 60
        val minutes = (milliseconds / (1000 * 60)) % 60
        val hours = milliseconds / (1000 * 60 * 60)
        
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%d:%02d", minutes, seconds)
        }
    }

    private fun finishWithAnimation() {
        finish()
        overridePendingTransition(R.anim.fade_in, R.anim.slide_down)
    }

    private fun showError(message: String) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Error")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showSuccess(message: String) {
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show()
    }

    override fun onBackPressed() {
        finishWithAnimation()
    }

    override fun onStart() {
        super.onStart()
        // Connect to service when activity becomes visible
        connectToService()
    }

    override fun onStop() {
        super.onStop()
        // Disconnect from service when activity is no longer visible
        disconnectFromService()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Clean up any remaining resources
        if (isServiceBound) {
            unbindService(serviceConnection)
            isServiceBound = false
        }
    }

    companion object {
        private const val TAG = "TXANowPlayingActivity"
        
        fun createIntent(context: Context): Intent {
            return Intent(context, TXANowPlayingActivity::class.java)
        }
    }
}
