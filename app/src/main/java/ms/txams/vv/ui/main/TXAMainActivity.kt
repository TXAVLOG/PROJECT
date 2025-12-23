package ms.txams.vv.ui.main

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import ms.txams.vv.R
import ms.txams.vv.core.TXATranslation
import ms.txams.vv.core.service.TXAMusicService
import ms.txams.vv.core.txa
import ms.txams.vv.databinding.ActivityTxaMainBinding
import ms.txams.vv.ui.main.viewmodel.TXAMainViewModel
import timber.log.Timber
import javax.inject.Inject

/**
 * TXA Main Activity - Màn hình chính với One UI 8 design
 * Architecture: MVVM với StateFlow cho Media3 integration
 * Features: Glassmorphism effects, shared elements, now bar integration
 */
@AndroidEntryPoint
class TXAMainActivity : AppCompatActivity() {

    @Inject
    lateinit var translation: TXATranslation

    private lateinit var binding: ActivityTxaMainBinding
    private val viewModel: TXAMainViewModel by viewModels()
    
    // One UI 8 Design constants
    companion object {
        private const val UI_CORNER_RADIUS = 32f
        private const val UI_BLUR_RADIUS = 25f
        private const val UI_ANIMATION_DURATION = 300L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Enable edge-to-edge display for One UI 8
        enableEdgeToEdge()
        
        binding = ActivityTxaMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupActionBar()
        setupNavigation()
        setupUI()
        observeViewModel()
        initializeService()
        
        // Apply One UI 8 design system
        applyOneUIDesign()
    }

    private fun enableEdgeToEdge() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun setupActionBar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayShowTitleEnabled(false)
            setDisplayHomeAsUpEnabled(false)
        }
    }

    private fun setupNavigation() {
        val navController = findNavController(R.id.nav_host_fragment)
        val appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.navigation_home,
                R.id.navigation_library,
                R.id.navigation_explore,
                R.id.navigation_settings
            )
        )
        
        setupActionBarWithNavController(navController, appBarConfiguration)
        binding.bottomNavigation.setupWithNavController(navController)
        
        // Custom navigation listener for shared element transitions
        navController.addOnDestinationChangedListener { _, destination, _ ->
            handleNavigationTransition(destination.id)
        }
    }

    private fun setupUI() {
        // Setup mini player
        setupMiniPlayer()
        
        // Setup glassmorphism effects
        setupGlassmorphism()
        
        // Setup floating action button for quick actions
        setupFloatingActions()
        
        // Setup now bar integration placeholder
        setupNowBar()
    }

    private fun setupMiniPlayer() {
        binding.miniPlayer.apply {
            // Set click listeners
            setOnClickListener {
                navigateToNowPlaying()
            }
            
            binding.playPauseButton.setOnClickListener {
                viewModel.togglePlayback()
            }
            
            binding.nextButton.setOnClickListener {
                viewModel.playNext()
            }
            
            binding.previousButton.setOnClickListener {
                viewModel.playPrevious()
            }
            
            // Initial state
            updateMiniPlayerVisibility(false)
        }
    }

    private fun setupGlassmorphism() {
        // Apply blur effect to bottom navigation and mini player
        binding.bottomNavigation.apply {
            background = createGlassmorphismBackground()
        }
        
        binding.miniPlayer.apply {
            background = createGlassmorphismBackground()
        }
    }

    private fun createGlassmorphismBackground(): android.graphics.drawable.Drawable {
        // Create glassmorphism effect with blur and transparency
        return android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            setColor(android.graphics.Color.argb(120, 255, 255, 255)) // Semi-transparent white
            cornerRadius = UI_CORNER_RADIUS
        }
    }

    private fun setupFloatingActions() {
        binding.fabQuickAction.setOnClickListener {
            showQuickActionMenu()
        }
    }

    private fun setupNowBar() {
        // Placeholder for Samsung Now Bar integration
        // Will be implemented with Samsung SDK
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            // Observe current song
            viewModel.currentSong.collect { song ->
                updateMiniPlayerSong(song)
            }
            
            // Observe playback state
            viewModel.isPlaying.collect { isPlaying ->
                updatePlaybackState(isPlaying)
            }
            
            // Observe playback progress
            viewModel.playbackProgress.collect { progress ->
                updatePlaybackProgress(progress)
            }
            
            // Observe UI state
            viewModel.uiState.collect { state ->
                handleUIState(state)
            }
        }
    }

    private fun initializeService() {
        // Connect to TXAMusicService
        viewModel.connectToService()
        
        // Handle service intent if launched from notification
        handleServiceIntent(intent)
    }

    private fun updateMiniPlayerSong(song: ms.txams.vv.core.data.database.entity.TXASongEntity?) {
        binding.apply {
            song?.let {
                songTitle.text = it.title
                artistName.text = it.artist
                
                // Load album art with shared element transition
                loadAlbumArt(it.albumArtPath)
                
                // Show mini player with animation
                updateMiniPlayerVisibility(true)
            } ?: run {
                updateMiniPlayerVisibility(false)
            }
        }
    }

    private fun loadAlbumArt(albumArtPath: String?) {
        // Implementation for loading album art with shared element transition
        // Using Glide or similar image loading library
    }

    private fun updatePlaybackState(isPlaying: Boolean) {
        binding.playPauseButton.apply {
            // Update icon with animation
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

    private fun updatePlaybackProgress(progress: Float) {
        binding.progressBar.progress = (progress * 100).toInt()
    }

    private fun updateMiniPlayerVisibility(show: Boolean) {
        binding.miniPlayer.apply {
            if (show) {
                if (visibility != View.VISIBLE) {
                    visibility = View.VISIBLE
                    alpha = 0f
                    translationY = height.toFloat()
                    
                    animate()
                        .alpha(1f)
                        .translationY(0f)
                        .setInterpolator(AccelerateDecelerateInterpolator())
                        .setDuration(UI_ANIMATION_DURATION)
                        .start()
                }
            } else {
                if (visibility == View.VISIBLE) {
                    animate()
                        .alpha(0f)
                        .translationY(height.toFloat())
                        .setInterpolator(AccelerateDecelerateInterpolator())
                        .setDuration(UI_ANIMATION_DURATION)
                        .withEndAction {
                            visibility = View.GONE
                        }
                        .start()
                }
            }
        }
    }

    private fun handleUIState(state: TXAMainViewModel.UIState) {
        when (state) {
            TXAMainViewModel.UIState.LOADING -> {
                // Show loading state
            }
            TXAMainViewModel.UIState.CONTENT -> {
                // Show content
            }
            TXAMainViewModel.UIState.ERROR -> {
                // Show error state
            }
        }
    }

    private fun handleNavigationTransition(destinationId: Int) {
        // Handle shared element transitions for One UI 8 design
        when (destinationId) {
            R.id.navigation_home -> {
                // Animate to home with shared elements
            }
            R.id.navigation_library -> {
                // Animate to library with shared elements
            }
            R.id.navigation_explore -> {
                // Animate to explore with shared elements
            }
            R.id.navigation_settings -> {
                // Animate to settings with shared elements
            }
        }
    }

    private fun navigateToNowPlaying() {
        // Navigate to now playing screen with shared element transition
        val intent = Intent(this, TXANowPlayingActivity::class.java)
        // Add shared element transition for album art
        startActivity(intent)
        overridePendingTransition(R.anim.slide_up, R.anim.fade_out)
    }

    private fun showQuickActionMenu() {
        // Show quick action menu with One UI 8 design
        // Options: Shuffle, Repeat, Equalizer, Sleep Timer
    }

    private fun applyOneUIDesign() {
        // Apply One UI 8 design system
        window.apply {
            // Set status bar and navigation bar colors for One UI 8
            statusBarColor = android.graphics.Color.TRANSPARENT
            navigationBarColor = android.graphics.Color.TRANSPARENT
        }
        
        // Apply corner radius to cards and containers
        applyCornerRadiusToViews()
        
        // Apply typography and spacing
        applyTypographyAndSpacing()
    }

    private fun applyCornerRadiusToViews() {
        // Apply large corner radius (28-32dp) for One UI 8 design
        val radius = UI_CORNER_RADIUS
        
        binding.bottomNavigation.apply {
            background = createRoundedBackground(radius)
        }
        
        binding.miniPlayer.apply {
            background = createRoundedBackground(radius)
        }
    }

    private fun createRoundedBackground(cornerRadius: Float): android.graphics.drawable.Drawable {
        return android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            setColor(android.graphics.Color.WHITE)
            cornerRadius = cornerRadius
        }
    }

    private fun applyTypographyAndSpacing() {
        // Apply One UI 8 typography and spacing guidelines
        // Large headers (1/3 top), interactive elements (2/3 bottom)
    }

    private fun setupMiniPlayer() {
        // Setup mini player with blur and rounded corners
        val radius = resources.getDimension(R.dimen.mini_player_corner_radius)
        
        binding.miniPlayer.apply {
            background = createRoundedBackground(radius)
        }
    }

    private fun handleServiceIntent(intent: Intent?) {
        intent?.let {
            when (it.action) {
                TXAMusicService.ACTION_PLAY -> {
                    // Handle play action from notification
                }
                TXAMusicService.ACTION_PAUSE -> {
                    // Handle pause action from notification
                }
                TXAMusicService.ACTION_NEXT -> {
                    // Handle next action from notification
                }
                TXAMusicService.ACTION_PREVIOUS -> {
                    // Handle previous action from notification
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleServiceIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        // Update UI state and reconnect to service if needed
        viewModel.refreshServiceConnection()
    }

    override fun onPause() {
        super.onPause()
        // Save current state
        viewModel.saveCurrentState()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Disconnect from service
        viewModel.disconnectFromService()
    }

    companion object {
        private const val TAG = "TXAMainActivity"
    }
}
