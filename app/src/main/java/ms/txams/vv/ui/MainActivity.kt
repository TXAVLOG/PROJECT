package ms.txams.vv.ui

import android.content.ComponentName
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.NavigationUI
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.common.util.concurrent.ListenableFuture
import ms.txams.vv.R
import ms.txams.vv.data.database.SongEntity
import ms.txams.vv.service.MusicService
import ms.txams.vv.permission.PermissionHelper
import com.google.android.material.color.DynamicColors

class MainActivity : AppCompatActivity() {

    private var mediaControllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    private lateinit var bottomNavigationView: BottomNavigationView
    private lateinit var permissionHelper: PermissionHelper

    private lateinit var bottomSheetBehavior: BottomSheetBehavior<View>
    
    // UI Components - Collapsed Now Bar
    private lateinit var ivAlbumArt: ImageView
    private lateinit var tvSongTitle: TextView
    private lateinit var tvArtistName: TextView
    private lateinit var btnPlayPause: ImageButton
    private lateinit var btnPrevious: ImageButton
    private lateinit var btnNext: ImageButton
    
    // UI Components - Expanded Player
    private lateinit var ivLargeAlbumArt: ImageView
    private lateinit var tvLargeSongTitle: TextView
    private lateinit var tvLargeArtistName: TextView
    private lateinit var seekBar: SeekBar
    private lateinit var tvCurrentTime: TextView
    private lateinit var tvTotalTime: TextView
    private lateinit var btnLargePlayPause: ImageButton
    private lateinit var btnLargePrevious: ImageButton
    private lateinit var btnLargeNext: ImageButton
    private lateinit var btnShuffle: ImageButton
    private lateinit var btnRepeat: ImageButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main_new)
        
        // Apply Material You dynamic colors
        DynamicColors.applyToActivitiesIfAvailable(this.application)
        
        // Initialize permission helper
        permissionHelper = PermissionHelper(this) { permission, granted ->
            handlePermissionResult(permission, granted)
        }
        
        setupWindowInsets()
        setupBottomNavigation()
        initViews()
        setupBottomSheet()
        setupClickListeners()
        
        // Initialize Media Controller
        initializeMediaController()
        
        // Check and request permissions
        permissionHelper.checkAndRequestPermissions()
    }

    override fun onResume() {
        super.onResume()
        // PermissionHelper will automatically check permissions in its onResume
        permissionHelper.forceCheckPermissions()
    }

    private fun handlePermissionResult(permission: String, granted: Boolean) {
        if (granted) {
            // Permission granted, you can initialize music library here
            initializeMusicLibrary()
        } else {
            // Permission denied, user will see appropriate dialog
        }
    }

    private fun initializeMusicLibrary() {
        // TODO: Initialize music library scanning and loading
        // This will be called when audio permission is granted
    }

    private fun initializeMediaController() {
        // Ensure service is started so MediaController can connect
        ContextCompat.startForegroundService(
            this,
            android.content.Intent(this, MusicService::class.java).apply {
                action = MusicService.ACTION_START
            }
        )

        val sessionToken = SessionToken(this, ComponentName(this, MusicService::class.java))
        mediaControllerFuture = MediaController.Builder(this, sessionToken).buildAsync()
        mediaControllerFuture?.addListener({
            try {
                mediaController = mediaControllerFuture?.get()
                setupMediaController()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun setupMediaController() {
        mediaController?.let { controller ->
            controller.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    updatePlayPauseButton(isPlaying)
                }

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    updateNowBarUI(mediaItem)
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    when (playbackState) {
                        Player.STATE_READY -> {
                            seekBar.max = (controller.duration / 1000).toInt()
                            tvTotalTime.text = formatDuration(controller.duration)
                        }
                    }
                }
            })
        }
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.nav_host_fragment)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun setupBottomNavigation() {
        bottomNavigationView = findViewById(R.id.bottom_navigation)
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        
        NavigationUI.setupWithNavController(bottomNavigationView, navController)
    }

    private fun initViews() {
        // Collapsed Now Bar
        ivAlbumArt = findViewById(R.id.iv_album_art)
        tvSongTitle = findViewById(R.id.tv_song_title)
        tvArtistName = findViewById(R.id.tv_artist_name)
        btnPlayPause = findViewById(R.id.btn_play_pause)
        btnPrevious = findViewById(R.id.btn_previous)
        btnNext = findViewById(R.id.btn_next)
        
        // Expanded Player
        ivLargeAlbumArt = findViewById(R.id.iv_large_album_art)
        tvLargeSongTitle = findViewById(R.id.tv_large_song_title)
        tvLargeArtistName = findViewById(R.id.tv_large_artist_name)
        seekBar = findViewById(R.id.seek_bar)
        tvCurrentTime = findViewById(R.id.tv_current_time)
        tvTotalTime = findViewById(R.id.tv_total_time)
        btnLargePlayPause = findViewById(R.id.btn_large_play_pause)
        btnLargePrevious = findViewById(R.id.btn_large_previous)
        btnLargeNext = findViewById(R.id.btn_large_next)
        btnShuffle = findViewById(R.id.btn_shuffle)
        btnRepeat = findViewById(R.id.btn_repeat)
    }

    private fun setupBottomSheet() {
        val nowBarSheet = findViewById<View>(R.id.now_bar_sheet)
        bottomSheetBehavior = BottomSheetBehavior.from(nowBarSheet)
        
        bottomSheetBehavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                when (newState) {
                    BottomSheetBehavior.STATE_COLLAPSED -> {
                        findViewById<View>(R.id.collapsed_now_bar).visibility = View.VISIBLE
                        findViewById<View>(R.id.expanded_player).visibility = View.GONE
                    }
                    BottomSheetBehavior.STATE_EXPANDED -> {
                        findViewById<View>(R.id.collapsed_now_bar).visibility = View.GONE
                        findViewById<View>(R.id.expanded_player).visibility = View.VISIBLE
                    }
                }
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                // Handle slide animation if needed
            }
        })
    }

    private fun setupClickListeners() {
        // Collapsed controls
        btnPlayPause.setOnClickListener { togglePlayPause() }
        btnPrevious.setOnClickListener { playPrevious() }
        btnNext.setOnClickListener { playNext() }
        
        // Expanded controls
        btnLargePlayPause.setOnClickListener { togglePlayPause() }
        btnLargePrevious.setOnClickListener { playPrevious() }
        btnLargeNext.setOnClickListener { playNext() }
        btnShuffle.setOnClickListener { toggleShuffle() }
        btnRepeat.setOnClickListener { toggleRepeat() }
        
        // Seek bar
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    seekTo(progress.toLong())
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun togglePlayPause() {
        mediaController?.let { controller ->
            if (controller.isPlaying) {
                controller.pause()
            } else {
                controller.play()
            }
        }
    }

    private fun playPrevious() {
        mediaController?.seekToPrevious()
    }

    private fun playNext() {
        mediaController?.seekToNext()
    }

    private fun seekTo(position: Long) {
        mediaController?.seekTo(position * 1000)
    }

    private fun toggleShuffle() {
        mediaController?.let { controller ->
            controller.shuffleModeEnabled = !(controller.shuffleModeEnabled)
        }
    }

    private fun toggleRepeat() {
        // Implement repeat mode
        val currentMode = mediaController?.repeatMode ?: Player.REPEAT_MODE_OFF
        val nextMode = when (currentMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ONE
            Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_ALL
            else -> Player.REPEAT_MODE_OFF
        }
        mediaController?.repeatMode = nextMode
    }

    private fun updatePlayPauseButton(isPlaying: Boolean) {
        val playIcon = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow
        btnPlayPause.setImageResource(playIcon)
        btnLargePlayPause.setImageResource(playIcon)
    }

    private fun updateNowBarUI(mediaItem: MediaItem?) {
        mediaItem?.let {
            tvSongTitle.text = it.mediaMetadata.title ?: "Unknown"
            tvArtistName.text = it.mediaMetadata.artist ?: "Unknown Artist"
            tvLargeSongTitle.text = it.mediaMetadata.title ?: "Unknown"
            tvLargeArtistName.text = it.mediaMetadata.artist ?: "Unknown Artist"
            
            // Load album art using Glide or Coil
            // Glide.with(this).load(it.mediaMetadata.artworkUri).into(ivAlbumArt)
            // Glide.with(this).load(it.mediaMetadata.artworkUri).into(ivLargeAlbumArt)
            
            tvTotalTime.text = formatDuration(it.mediaMetadata.durationMs ?: 0L)
            seekBar.max = ((it.mediaMetadata.durationMs ?: 0L) / 1000).toInt()
        }
    }

    private fun formatDuration(duration: Long): String {
        val minutes = (duration / 1000) / 60
        val seconds = (duration / 1000) % 60
        return String.format("%d:%02d", minutes, seconds)
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaController?.release()
        mediaController = null
        mediaControllerFuture?.cancel(true)
        mediaControllerFuture = null
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}
