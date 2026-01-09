package com.txapp.musicplayer.ui

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import android.widget.Toast
import com.txapp.musicplayer.data.MusicRepository
import com.txapp.musicplayer.data.MusicDatabase
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import com.txapp.musicplayer.MusicApplication
import androidx.compose.runtime.collectAsState
import com.txapp.musicplayer.ui.component.AddToPlaylistSheet
import com.txapp.musicplayer.ui.component.NowPlayingState
import com.txapp.musicplayer.ui.component.NowPlayingContent
import com.txapp.musicplayer.ui.component.QueueBottomSheet
import com.txapp.musicplayer.ui.component.QueueItem
import com.txapp.musicplayer.ui.component.SleepTimerDialog
import com.txapp.musicplayer.ui.component.PlaybackSpeedDialog
import com.txapp.musicplayer.ui.component.MoreOptionsDropdown
import com.txapp.musicplayer.ui.component.NowPlayingLandscapeFullScreen
import com.txapp.musicplayer.ui.component.NowPlayingDriveModeLandscape
import com.txapp.musicplayer.util.TXANetworkHelper
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import com.txapp.musicplayer.R
import com.bumptech.glide.Glide
import android.graphics.drawable.GradientDrawable
import androidx.palette.graphics.Palette
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.txapp.musicplayer.databinding.SlidingMusicPanelLayoutBinding
import com.txapp.musicplayer.util.TXALocationHelper
import com.txapp.musicplayer.util.TXALogger
import com.txapp.musicplayer.util.TXAUpdateManager
import com.txapp.musicplayer.util.TXACrashHandler
import com.txapp.musicplayer.util.TXAHolidayManager
import com.txapp.musicplayer.util.TXAPreferences
import com.txapp.musicplayer.util.txa
import com.txapp.musicplayer.service.MusicService
import com.txapp.musicplayer.util.TXAActiveMP3
import com.txapp.musicplayer.ui.component.TXAHolidayBorderOverlay
import com.txapp.musicplayer.ui.component.AudioFileInfoModal
import com.txapp.musicplayer.ui.component.UpdateDialog
import com.txapp.musicplayer.util.TXAHttp
import com.txapp.musicplayer.ui.fragment.MiniPlayerFragment
import com.txapp.musicplayer.util.TXAFormat
import com.txapp.musicplayer.util.TXAToast
import com.txapp.musicplayer.util.TXASuHelper

class MainActivity : AppCompatActivity() {

    private lateinit var composeView: ComposeView
    // Thay thế BottomSheetBehavior bằng state đơn giản
    private var isFullPlayerExpanded by mutableStateOf(false)
    
    private val viewModel: MainViewModel by viewModels()

    private lateinit var mediaControllerFuture: ListenableFuture<MediaController>
    internal val mediaController: MediaController?
        get() = if (::mediaControllerFuture.isInitialized && mediaControllerFuture.isDone) {
            mediaControllerFuture.get()
        } else {
            null
        }

    fun getPlayerController(): MediaController? {
        return mediaController
    }

    private var pendingOpenUri: Uri? = null
    
    // Compose State
    private var showAudioFileModal by mutableStateOf(false)
    private var modalUri: Uri? by mutableStateOf(null)
    private var modalFileName: String? by mutableStateOf(null)
    private var modalTitle: String? by mutableStateOf(null)
    private var modalArtist: String? by mutableStateOf(null)
    private var modalAlbum: String? by mutableStateOf(null)
    private var modalDuration: Long by mutableStateOf(0L)
    private var isPlayerPlaying by mutableStateOf(false)
    private var modalSourceAppName: String? by mutableStateOf(null)
    private var showUpdateDialog by mutableStateOf(false)
    
    // Full Player State
    internal var nowPlayingState by mutableStateOf(NowPlayingState())
    private var showQueueSheet by mutableStateOf(false)
    private var isDriveModeActive by mutableStateOf(false)
    private var showSleepTimerDialog by mutableStateOf(false)
    private var showPlaybackSpeedDialog by mutableStateOf(false)
    private var showMoreOptionsMenu by mutableStateOf(false)
    private var showAddToPlaylistSheet by mutableStateOf(false)
    private var showCreatePlaylistDialog by mutableStateOf(false)
    private var showTagEditorSheet by mutableStateOf(false)
    private var showLyricsDialog by mutableStateOf(false)
    private var startLyricsInEditMode by mutableStateOf(false)
    private var currentSongForEdit by mutableStateOf<com.txapp.musicplayer.model.Song?>(null)
    
    // In-App AOD State
    private var showInAppAod by mutableStateOf(false)
    private var inactivityTimerJob: kotlinx.coroutines.Job? = null
    
    private val inactivityTimeout: Long
        get() = com.txapp.musicplayer.util.TXAAODSettings.inactivityTimeout.value

    private var pendingLyricsUpdate: String? = null
    private var pendingTagUpdate: com.txapp.musicplayer.ui.component.TagEditData? = null
    private val writeRequestLauncher =
        registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult()) { result ->
            if (result.resultCode == android.app.Activity.RESULT_OK) {
                pendingLyricsUpdate?.let { lyrics ->
                    val mediaUri = nowPlayingState.mediaUri
                    if (mediaUri.isNotEmpty()) {
                        saveLyricsUpdate(mediaUri, lyrics)
                    }
                }
                pendingTagUpdate?.let { data ->
                    val songId = nowPlayingState.songId
                    if (songId != -1L) {
                         // We need to trigger the save again. 
                         // To avoid duplicate code, I'll rely on the existing onSave logic or similar.
                         // But since it's a simple call, I'll just redo it here.
                         retryTagUpdate(songId, data)
                    }
                }
            }
            pendingLyricsUpdate = null
            pendingTagUpdate = null
        }

    private fun retryTagUpdate(songId: Long, data: com.txapp.musicplayer.ui.component.TagEditData) {
        lifecycleScope.launch {
             val result = repository.updateSongMetadata(
                context = this@MainActivity,
                songId = songId,
                title = data.title,
                artist = data.artist,
                album = data.album,
                albumArtist = data.albumArtist,
                composer = data.composer,
                year = data.year.toIntOrNull() ?: 0,
                trackNumber = 0 // We don't have track number here easily, but repository handles it
            )
            if (result is com.txapp.musicplayer.util.TXATagWriter.WriteResult.Success) {
                TXAToast.success(this@MainActivity, "txamusic_tag_saved".txa())
                updateNowPlayingState()
            }
        }
    }

    
    fun toggleLyricsDialog(inEditMode: Boolean = false) {
        startLyricsInEditMode = inEditMode
        showLyricsDialog = !showLyricsDialog
    }
    
    private lateinit var repository: MusicRepository
    
    // Bottom Nav State
    private var currentTabId by mutableStateOf(R.id.action_home)
    private var isBottomNavVisible by mutableStateOf(true)
    
    private val requestLocationPermissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[android.Manifest.permission.ACCESS_FINE_LOCATION] == true || 
            permissions[android.Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
            TXALocationHelper.fetchAndLogLocation(this)
        } else {
            TXALogger.appW("MainActivity", "Location permission denied for diagnostics.")
        }
    }

    private var systemNavHeight = 0

    private lateinit var binding: SlidingMusicPanelLayoutBinding
    
    // Tab Info Data Class
    data class CategoryInfo(val id: Int, val stringKey: String, val icon: Int)

    private val defaultTabs = listOf(
        CategoryInfo(R.id.action_home, "txamusic_home", R.drawable.ic_home),
        CategoryInfo(R.id.action_song, "txamusic_songs", R.drawable.ic_audiotrack),
        CategoryInfo(R.id.action_album, "txamusic_albums", R.drawable.ic_album),
        CategoryInfo(R.id.action_artist, "txamusic_artists", R.drawable.ic_artist),
        CategoryInfo(R.id.action_playlist, "txamusic_playlists", R.drawable.ic_playlist_play)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        try {
            com.txapp.musicplayer.util.TXAAODSettings.init(this)
            binding = SlidingMusicPanelLayoutBinding.inflate(layoutInflater)
            setContentView(binding.root)
            
            // Apply Insets to main content to stay below status bar
            ViewCompat.setOnApplyWindowInsetsListener(binding.mainContent) { v, insets ->
                val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                val navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
                
                systemNavHeight = navBars.bottom
                
                // Apply TOP padding to fragment container
                binding.fragmentContainer.updatePadding(top = systemBars.top)
                
                insets
            }
            
            val app = application as MusicApplication
            repository = MusicRepository(app.database, contentResolver)

            // Setup panels với visibility thay vì BottomSheetBehavior
            setupSlidingPanel()

            composeView = binding.composeView
            setupCompose()
            
            setupNavigation()
            
            // Leveraging ROOT if available and Power Mode is enabled
            lifecycleScope.launch(Dispatchers.IO) {
                if (TXASuHelper.isRooted() && TXAPreferences.isPowerMode) {
                    TXALogger.i("MainActivity", "Turbo Power Mode active, applying performance optimizations...")
                    TXASuHelper.boostAppPriority(packageName)
                    TXASuHelper.enableHighPriorityAudio()
                    TXASuHelper.whitelistAppFromBatteryOptimizations(packageName)
                }
            }
            
            // Always setup BottomNav for all screen types (Phone/Tablet)
            setupBottomNavCompose()
            binding.bottomNavComposeView?.visibility = View.VISIBLE
            
            setupListeners()
            initializeController()
            startInactivityTimer()

            handleIntent(intent)
            maybeShowOpenDialog()
            
            // Request Location for diagnostics (User Request)
            if (TXALocationHelper.checkPermissions(this)) {
                TXALocationHelper.fetchAndLogLocation(this)
            } else {
                 requestLocationPermissionLauncher.launch(
                    arrayOf(
                        android.Manifest.permission.ACCESS_FINE_LOCATION,
                        android.Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                 )
            }
            
            // Check for updates
            // Monitor Restricted Mode Restoration
            if (TXAPreferences.isRestrictedMode) {
                lifecycleScope.launch(Dispatchers.IO) {
                     while(isActive) {
                         kotlinx.coroutines.delay(5000)
                         // Check for restoration
                         if (TXANetworkHelper.getNetworkStatus(this@MainActivity) == TXANetworkHelper.NetworkStatus.WIFI_CONNECTED ||
                             TXANetworkHelper.getNetworkStatus(this@MainActivity) == TXANetworkHelper.NetworkStatus.CELLULAR_CONNECTED) {
                             
                             withContext(Dispatchers.Main) {
                                 // Show Restoration Modal/Toast
                                 TXAToast.success(this@MainActivity, "txamusic_network_restored_title".txa())
                                 TXAToast.info(this@MainActivity, "txamusic_network_restored_desc".txa())
                                 
                                 // Restart to proper state
                                 kotlinx.coroutines.delay(1500)
                                 finishAffinity()
                                 startActivity(android.content.Intent(this@MainActivity, SplashActivity::class.java))
                             }
                             break
                         }
                     }
                }
            }

            // Check for updates (Network Aware)
            binding.root.post {
                lifecycleScope.launch {
                    try {
                        kotlinx.coroutines.delay(1500)
                        
                        val netStatus = TXANetworkHelper.getNetworkStatus(this@MainActivity)
                        if (netStatus == TXANetworkHelper.NetworkStatus.WIFI_NO_INTERNET) {
                             // WiFi Connected but No Internet - Show Error
                             TXAToast.error(this@MainActivity, "txamusic_network_wifi_no_internet".txa())
                             // Requirement: Show Modal "lỗi modal lên lun". 
                             // Using Toast for non-intrusive startup, but if manual check requested this would be modal.
                             // For now, this suffices to warn user.
                        } else {
                            val info = TXAUpdateManager.checkForUpdate()
                            if (info != null && info.updateAvailable) {
                                TXAUpdateManager.updateInfo.value = info
                                binding.root.post {
                                    showUpdateDialog = true
                                }
                            }
                        }
                    } catch (e: Exception) {
                        // Silent fail or log
                        TXACrashHandler.handleError(this@MainActivity, e, "MainActivity")
                    }
                }
            }
            
            // Auto Smart Scan on startup (background)
            if (checkStoragePermission()) {
                triggerSmartScan()
            }

            // Show Holiday / New Year greetings if applicable
            TXAHolidayManager.checkAndShowHoliday(this)
            TXAHolidayManager.scheduleNotifications(this)
        } catch (e: Exception) {
            com.txapp.musicplayer.util.TXACrashHandler.reportFatalError(this, e, "MainActivity", killProcess = true)
        }
    }
    
    private fun setupBottomNavCompose() {
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.fragment_container) as androidx.navigation.fragment.NavHostFragment
        val navController = navHostFragment.navController

        binding.bottomNavComposeView?.setContent {
            val themePref by TXAPreferences.theme.collectAsState()
            val isSystemDark = androidx.compose.foundation.isSystemInDarkTheme()
            val isDark = remember(themePref, isSystemDark) {
                when (themePref) {
                    "dark" -> true
                    "light" -> false
                    else -> isSystemDark
                }
            }
            
            val accentColor = try {
                Color(android.graphics.Color.parseColor(TXAPreferences.currentAccent))
            } catch (e: Exception) {
                MaterialTheme.colorScheme.primary
            }
            
            MaterialTheme(
                colorScheme = if (isDark) 
                    androidx.compose.material3.darkColorScheme(primary = accentColor) 
                else 
                    androidx.compose.material3.lightColorScheme(primary = accentColor)
            ) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = isBottomNavVisible,
                    enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(),
                    exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut()
                ) {
                    Column {
                        // Separator line
                        androidx.compose.material3.HorizontalDivider(
                            thickness = 1.dp,
                            color = if (isDark) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.08f)
                        )
                        NavigationBar(
                            // Avoid pure white in Light mode to prevent "washed out" look
                            containerColor = if (isDark) 
                                Color(0xFF141414) // Dark Grey/Black
                            else 
                                Color(0xFFF8F8F8), // Slightly Off-White
                            tonalElevation = 16.dp, // High elevation to sit ABOVE collapsed sheet
                            modifier = Modifier.fillMaxWidth().navigationBarsPadding()
                        ) {
                            val visibleTabs = if (TXAPreferences.isRestrictedMode) {
                                defaultTabs.filter { it.id == R.id.action_home || it.id == R.id.action_song }
                            } else {
                                defaultTabs
                            }
                            
                            visibleTabs.forEach { tab ->
                                val isSelected = currentTabId == tab.id
                                NavigationBarItem(
                                    selected = isSelected,
                                    onClick = {
                                        if (currentTabId != tab.id) {
                                            currentTabId = tab.id
                                            // Directly navigate to the target fragment to ensure UI updates
                                            // NavigationUI.onNavDestinationSelected can sometimes fail to update the UI state
                                            navController.navigate(tab.id)
                                        }
                                    },
                                    icon = {
                                        androidx.compose.animation.Crossfade(targetState = isSelected) { selected ->
                                            Icon(
                                                painter = androidx.compose.ui.res.painterResource(id = tab.icon),
                                                contentDescription = null,
                                                modifier = Modifier.size(24.dp),
                                                tint = if (selected) accentColor else if (isDark) Color.White.copy(alpha = 0.6f) else Color.Black.copy(alpha = 0.6f)
                                            )
                                        }
                                    },
                                    label = {
                                        Text(
                                            text = tab.stringKey.txa(),
                                            fontSize = 11.sp, // Slightly bigger
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            color = if (isSelected) accentColor else if (isDark) Color.White.copy(alpha = 0.6f) else Color.Black.copy(alpha = 0.6f)
                                        )
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = accentColor,
                                        selectedTextColor = accentColor,
                                        indicatorColor = accentColor.copy(alpha = 0.15f), // Stronger indicator
                                        unselectedIconColor = if (isDark) Color.White.copy(alpha = 0.6f) else Color.Black.copy(alpha = 0.6f),
                                        unselectedTextColor = if (isDark) Color.White.copy(alpha = 0.6f) else Color.Black.copy(alpha = 0.6f)
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun setupNavigationRailCompose() {
        // Obsolete: Navigation Rail is removed to fix tablet layout issues
    }

    // Helper to mock MenuItem for NavigationUI
    private fun android.view.View.menu_placeholder(id: Int): android.view.MenuItem {
        val menu = androidx.appcompat.widget.ActionMenuView(context).menu
        return menu.add(0, id, 0, "")
    }

    private fun updateTabs() {
        // No longer needed for BottomNavigationView as we use Compose
    }

    private fun setupNavigation() {
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.fragment_container) as androidx.navigation.fragment.NavHostFragment
        val navController = navHostFragment.navController
        
        // Restore last tab if enabled
        if (TXAPreferences.isRememberLastTab) {
            val lastTab = TXAPreferences.currentLastTab
            if (lastTab != 0 && defaultTabs.any { it.id == lastTab }) {
                try {
                    navController.navigate(lastTab)
                } catch (e: Exception) {
                    TXALogger.e("MainActivity", "Failed to navigate to last tab", e)
                }
            }
        }
        
        // Add listener for visibility logic (Restored from Backup Ref)
        navController.addOnDestinationChangedListener { _, destination, _ ->
            // Sync currentTabId with destination
            if (defaultTabs.any { it.id == destination.id }) {
                currentTabId = destination.id
                // Save last tab if remember is enabled
                if (TXAPreferences.isRememberLastTab) {
                    TXAPreferences.currentLastTab = destination.id
                }
            }
            
            // Show/Hide Top Tabs & Bottom Nav based on screen
            // isMainScreen should include all major navigation tabs
            val isMainScreen = destination.id == R.id.action_home || 
                               destination.id == R.id.action_song || 
                               destination.id == R.id.action_album || 
                               destination.id == R.id.action_artist || 
                               destination.id == R.id.action_playlist ||
                               destination.id == R.id.libraryFragment || 
                               destination.id == R.id.settings_fragment
            
            isBottomNavVisible = isMainScreen
            
            // Apply visibility to old Bridge UI if needed
            if (isMainScreen) {
                if (binding.root.isAttachedToWindow) {
                    setBottomNavVisibility(visible = true, animate = true)
                }
            } else {
                setBottomNavVisibility(visible = false, animate = true)
            }
        }
    }

    /**
     * Trigger a system-wide media scan to refresh MediaStore database
     */
    /**
     * Trigger a system-wide media scan to refresh MediaStore database
     * Runs in background and syncs local DB with MediaStore
     */
    fun triggerMediaStoreScan() {
        lifecycleScope.launch {
            TXAToast.show(this@MainActivity, "txamusic_refreshing_library".txa())
            
            // Run scanning and syncing in background to avoid blocking UI
            val count = withContext(Dispatchers.IO) {
                try {
                    // 1. Trigger legacy file scan on older Android versions to ensure MediaStore is up-to-date
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                        try {
                            val root = android.os.Environment.getExternalStorageDirectory()
                            android.media.MediaScannerConnection.scanFile(
                                this@MainActivity,
                                arrayOf(root.absolutePath),
                                null,
                                null
                            )
                        } catch (e: Exception) {
                            TXALogger.appE("MainActivity", "Legacy scan trigger failed", e)
                        }
                    }

                    // 2. Load fresh data from Android MediaStore
                    val songs = repository.loadSongsFromMediaStore()
                    
                    // 3. Fully sync with internal DB (add new, update existing, remove deleted)
                    repository.syncLibrary(songs)
                    
                    songs.size
                } catch (e: Exception) {
                    TXALogger.appE("MainActivity", "Library sync failed", e)
                    -1
                }
            }
            
            if (count >= 0) {
                TXAToast.show(this@MainActivity, "txamusic_refresh_done".txa())
            } else {
                TXAToast.show(this@MainActivity, "txamusic_error_unknown".txa())
            }
        }
    }

    private fun checkStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_MEDIA_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }

    private fun triggerSmartScan() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Ensure we have scanned at least once or user looks for update
                val songs = repository.loadSongsFromMediaStore()
                repository.syncLibrary(songs, smart = true)
            } catch (e: Exception) {
                TXALogger.appE("MainActivity", "Smart scan failed", e)
            }
        }
    }

    private fun setupSlidingPanel() {
        // Layout mới: dùng visibility thay vì BottomSheetBehavior
        // Mini Player visibility được quản lý bởi hideBottomSheet()
        // Full Player visibility được quản lý bởi expandPanel()/collapsePanel()
        
        // Initial State: ẩn cả 2
        binding.slidingPanel.visibility = View.GONE
        binding.miniPlayer.visibility = View.GONE
        binding.playerFragmentContainer.alpha = 1f
        
        // Setup Full Player ComposeView
        setupFullPlayerCompose()
    }
    
    private fun setupFullPlayerCompose() {
        val fullPlayerComposeView = ComposeView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        binding.playerFragmentContainer.addView(fullPlayerComposeView)
        
        fullPlayerComposeView.setContent {
            val nowPlayingStyle by TXAPreferences.nowPlayingUI.collectAsState()
            
            androidx.compose.material3.MaterialTheme {
                // Portrait mode UI for all orientations to avoid infinite constraints in landscape
                NowPlayingContent(
                    style = if (isDriveModeActive) "drive" else nowPlayingStyle,
                    state = nowPlayingState,
                    onPlayPause = { mediaController?.let { if (it.isPlaying) it.pause() else it.play() } },
                    onNext = { mediaController?.seekToNext() },
                    onPrevious = { mediaController?.seekToPrevious() },
                    onSeek = { progress ->
                        mediaController?.let { controller ->
                            val duration = controller.duration
                            if (duration != androidx.media3.common.C.TIME_UNSET && duration > 0) {
                                val newPosition = (progress / 1000f * duration).toLong().coerceIn(0, duration)
                                controller.seekTo(newPosition)
                                nowPlayingState = nowPlayingState.copy(
                                    progress = progress,
                                    position = newPosition
                                )
                            }
                        }
                    },
                    onToggleFavorite = { toggleCurrentSongFavorite() },
                    onToggleShuffle = {
                        mediaController?.let { controller ->
                            val newMode = !controller.shuffleModeEnabled
                            controller.shuffleModeEnabled = newMode
                            updateNowPlayingState()
                            val msg = if (newMode) "txamusic_shuffle_on".txa() else "txamusic_shuffle_off".txa()
                            TXAToast.show(this@MainActivity, msg)
                        }
                    },
                    onToggleRepeat = {
                        mediaController?.let { controller ->
                            val newMode = when (controller.repeatMode) {
                                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                                else -> Player.REPEAT_MODE_OFF
                            }
                            controller.repeatMode = newMode
                            updateNowPlayingState()
                            val msgKey = when (newMode) {
                                Player.REPEAT_MODE_ONE -> "txamusic_repeat_one"
                                Player.REPEAT_MODE_ALL -> "txamusic_repeat_all"
                                else -> "txamusic_repeat_off"
                            }
                            TXAToast.show(this@MainActivity, msgKey.txa())
                        }
                    },
                    onShowQueue = { showQueueSheet = true },
                    onClose = { collapsePanel() },
                    onDriveMode = { isDriveModeActive = !isDriveModeActive },
                    onShowSleepTimer = { showSleepTimerDialog = true },
                    onShowPlaybackSpeed = { showPlaybackSpeedDialog = true },
                    onAddToPlaylist = { showAddToPlaylistSheet = true },
                    onEditTag = { openTagEditorForCurrentSong() },
                    onSetRingtone = { setCurrentSongAsRingtone() },
                    onShowLyrics = { inEditMode -> toggleLyricsDialog(inEditMode) },
                    onArtistClick = { navigateToArtist(nowPlayingState.artist) }
                )

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
                            showSleepTimerDialog = false
                        }
                    )
                }
                
                // Playback Speed Dialog
                if (showPlaybackSpeedDialog) {
                    PlaybackSpeedDialog(
                        onDismiss = { showPlaybackSpeedDialog = false },
                        onSpeedChanged = { speed ->
                            // Apply speed to media player
                            mediaController?.setPlaybackSpeed(speed)
                        }
                    )
                }
                
                if (showAddToPlaylistSheet) {
                    val playlists by repository.playlists.collectAsState(initial = emptyList())
                    
                    // Observe playlists containing the current song
                    val currentSongId = mediaController?.currentMediaItem?.mediaId?.toLongOrNull() ?: -1L
                    val containingPlaylistIds by remember(currentSongId) {
                        if (currentSongId != -1L) {
                            repository.getPlaylistsContainingSong(currentSongId)
                        } else {
                            kotlinx.coroutines.flow.flowOf(emptyList())
                        }
                    }.collectAsState(initial = emptyList())

                    AddToPlaylistSheet(
                        playlists = playlists,
                        containingPlaylistIds = containingPlaylistIds,
                        onPlaylistSelected = { playlistId ->
                            toggleSongInPlaylist(playlistId, currentSongId)
                        },
                        onCreatePlaylist = {
                            showCreatePlaylistDialog = true
                        },
                        onDismiss = { showAddToPlaylistSheet = false }
                    )
                }
                
                // Create Playlist Dialog
                if (showCreatePlaylistDialog) {
                    var playlistName by remember { mutableStateOf("") }
                    val scope = rememberCoroutineScope()
                    AlertDialog(
                        onDismissRequest = { showCreatePlaylistDialog = false },
                        title = { Text("txamusic_btn_create_playlist".txa()) },
                        text = {
                            TextField(
                                value = playlistName,
                                onValueChange = { playlistName = it },
                                label = { Text("txamusic_playlist_name_hint".txa()) },
                                singleLine = true
                            )
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    if (playlistName.isNotBlank()) {
                                        scope.launch {
                                            val success = repository.createPlaylist(playlistName)
                                            if (success > 0) {
                                                // Success Logic
                                                playlistName = ""
                                                showCreatePlaylistDialog = false
                                            }
                                        }
                                    }
                                }
                            ) {
                                Text("txamusic_btn_confirm".txa())
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showCreatePlaylistDialog = false }) {
                                Text("txamusic_btn_cancel".txa())
                            }
                        }
                    )
                }
                
                // Tag Editor Sheet (from Full Player)
                if (showTagEditorSheet && currentSongForEdit != null) {
                    com.txapp.musicplayer.ui.component.TXATagEditorSheet(
                        song = currentSongForEdit!!,
                        onDismiss = { 
                            showTagEditorSheet = false
                            currentSongForEdit = null
                        },
                        onSave = { editData ->
                            showTagEditorSheet = false
                            val song = currentSongForEdit ?: return@TXATagEditorSheet
                            currentSongForEdit = null
                            
                            lifecycleScope.launch {
                                val result = repository.updateSongMetadata(
                                    context = this@MainActivity,
                                    songId = song.id,
                                    title = editData.title,
                                    artist = editData.artist,
                                    album = editData.album,
                                    albumArtist = editData.albumArtist,
                                    composer = editData.composer,
                                    year = editData.year.toIntOrNull() ?: 0,
                                    trackNumber = song.trackNumber
                                )
                                when (result) {
                                    is com.txapp.musicplayer.util.TXATagWriter.WriteResult.Success -> {
                                        TXAToast.success(this@MainActivity, "txamusic_tag_saved".txa())
                                        // Update now playing state with new info
                                        nowPlayingState = nowPlayingState.copy(
                                            title = editData.title,
                                            artist = editData.artist
                                        )
                                        updateNowPlayingState()
                                    }
                                    is com.txapp.musicplayer.util.TXATagWriter.WriteResult.PermissionRequired -> {
                                        // Store data for retry
                                        pendingTagUpdate = editData
                                        writeRequestLauncher.launch(
                                            androidx.activity.result.IntentSenderRequest.Builder(result.intent).build()
                                        )
                                    }

                                    else -> {
                                        TXAToast.show(this@MainActivity, "txamusic_tag_save_failed".txa())
                                    }
                                }
                            }

                        }
                    )
                }
                
                // In-App AOD Overlay
                androidx.compose.animation.AnimatedVisibility(
                    visible = showInAppAod,
                    enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(500)),
                    exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(300))
                ) {
                    com.txapp.musicplayer.ui.component.AODScreen(
                        onDismiss = {
                            resetInactivityTimer()
                        },
                        // Media playback info - chỉ hiển thị khi đang phát nhạc
                        isPlayingMusic = nowPlayingState.isPlaying,
                        nowPlayingTitle = nowPlayingState.title,
                        nowPlayingArtist = nowPlayingState.artist,
                        albumId = nowPlayingState.albumId,
                        progress = nowPlayingState.progress,
                        position = nowPlayingState.position,
                        duration = nowPlayingState.duration,
                        onPlayPause = { mediaController?.let { if (it.isPlaying) it.pause() else it.play() } },
                        onNext = { mediaController?.seekToNext() },
                        onPrevious = { mediaController?.seekToPrevious() }
                    )
                }

                // Lyrics Dialog
                if (showLyricsDialog) {
                    var lyricsVersion by remember { mutableIntStateOf(0) }
                    val currentLyrics = remember(nowPlayingState.lyrics, lyricsVersion) {
                        nowPlayingState.lyrics?.let { lrcContent ->
                            com.txapp.musicplayer.ui.component.LyricsUtil.parseLrc(lrcContent)
                        } ?: emptyList()
                    }

                    com.txapp.musicplayer.ui.component.LyricsDialog(
                        songTitle = nowPlayingState.title,
                        artistName = nowPlayingState.artist,
                        songPath = try {
                            android.net.Uri.parse(nowPlayingState.mediaUri).path ?: ""
                        } catch (e: Exception) {
                            ""
                        },
                        lyrics = currentLyrics,
                        currentPosition = nowPlayingState.position,
                        isPlaying = nowPlayingState.isPlaying,
                        onSeek = { position ->
                            mediaController?.seekTo(position)
                        },
                        onDismiss = { showLyricsDialog = false },
                        onLyricsUpdated = {
                            lyricsVersion++
                            updateNowPlayingState() // Refresh lyrics in player
                        },
                        onSearchLyrics = {
                             val url = com.txapp.musicplayer.ui.component.LyricsUtil.buildSearchUrl(nowPlayingState.title, nowPlayingState.artist)
                             try {
                                 com.txapp.musicplayer.util.TXAToast.info(this@MainActivity, "txamusic_lyrics_searching".txa())
                                 startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)))
                             } catch (e: Exception) {
                                 com.txapp.musicplayer.util.TXAToast.error(this@MainActivity, "txamusic_browser_not_found".txa())
                             }
                        },
                        onPermissionRequest = { intent, content ->
                            pendingLyricsUpdate = content
                            writeRequestLauncher.launch(
                                androidx.activity.result.IntentSenderRequest.Builder(intent).build()
                            )
                        },
                        startInEditMode = startLyricsInEditMode,
                        songDuration = nowPlayingState.duration
                    )
                }
            }

        }
    }

    // Restored Visibility Logic
    private fun setBottomNavVisibility(
        visible: Boolean,
        animate: Boolean = true,
        hideBottomSheet: Boolean = false
    ) {
        if (binding.root.isAttachedToWindow) {
             isBottomNavVisible = visible
             
             binding.bottomNavComposeView?.isVisible = visible
        }
        hideBottomSheet(
            hide = hideBottomSheet,
            animate = animate,
            navVisible = visible
        )
    }

    fun hideBottomSheet(
        hide: Boolean,
        animate: Boolean = false,
        navVisible: Boolean = this.isBottomNavVisible
    ) {
        // Layout mới: chỉ cần show/hide miniPlayer
        if (hide) {
            binding.miniPlayer.visibility = View.GONE
        } else {
            if (mediaController?.currentMediaItem != null && !isFullPlayerExpanded) {
                binding.miniPlayer.visibility = View.VISIBLE
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

            val result = com.txapp.musicplayer.ui.component.LyricsUtil.saveLyrics(this@MainActivity, path, lyrics)
            when (result) {
                is com.txapp.musicplayer.ui.component.LyricsUtil.SaveResult.Success -> {
                    TXAToast.success(this@MainActivity, "txamusic_lyrics_saved".txa())
                    updateNowPlayingState()
                }
                is com.txapp.musicplayer.ui.component.LyricsUtil.SaveResult.PermissionRequired -> {
                    // This could happen if permission was lost or another file needed
                    pendingLyricsUpdate = lyrics
                    writeRequestLauncher.launch(
                        androidx.activity.result.IntentSenderRequest.Builder(result.intent).build()
                    )
                }
                is com.txapp.musicplayer.ui.component.LyricsUtil.SaveResult.Failure -> {
                    TXAToast.error(this@MainActivity, "txamusic_lyrics_save_failed".txa())
                }
            }
        }
    }


    override fun dispatchTouchEvent(ev: android.view.MotionEvent?): Boolean {
        try {
            if (!isFinishing && !isDestroyed) {
                resetInactivityTimer()
            }
        } catch (e: Exception) {
            com.txapp.musicplayer.util.TXALogger.appE("MainActivity", "Error in dispatchTouchEvent", e)
        }
        return super.dispatchTouchEvent(ev)
    }
    
    private fun startInactivityTimer() {
        inactivityTimerJob?.cancel()
        inactivityTimerJob = lifecycleScope.launch {
            val timeout = inactivityTimeout
            if (timeout <= 0) return@launch // 0 means disabled
            kotlinx.coroutines.delay(timeout)
            if (!showInAppAod && !isDriveModeActive && nowPlayingState.isPlaying) {
                showInAppAod = true
            }
        }
    }
    
    private fun resetInactivityTimer() {
        try {
            if (showInAppAod) {
                showInAppAod = false
            }
            startInactivityTimer()
        } catch (e: Exception) {
            com.txapp.musicplayer.util.TXALogger.appE("MainActivity", "Error in resetInactivityTimer", e)
        }
    }

    fun expandPanel() {
        // Ẩn mini player, hiện full player
        isFullPlayerExpanded = true
        binding.miniPlayer.visibility = View.GONE
        binding.slidingPanel.visibility = View.VISIBLE
        // Ẩn bottom nav khi full player hiện
        isBottomNavVisible = false
        binding.bottomNavComposeView.visibility = View.GONE
    }

    fun collapsePanel() {
        // Ẩn full player, hiện mini player và bottom nav
        isFullPlayerExpanded = false
        binding.slidingPanel.visibility = View.GONE
        if (mediaController?.currentMediaItem != null) {
            binding.miniPlayer.visibility = View.VISIBLE
        }
        isBottomNavVisible = true
        binding.bottomNavComposeView.visibility = View.VISIBLE
    }
    
    private fun updateNowPlayingState() {
        val controller = mediaController ?: return
        val currentItem = controller.currentMediaItem ?: return
        
        val duration = if (controller.duration == androidx.media3.common.C.TIME_UNSET) 0L else controller.duration.coerceAtLeast(0L)
        val position = controller.currentPosition.coerceAtLeast(0L).coerceAtMost(duration) // Prevent position > duration
        val progress = if (duration > 0) (position.toFloat() / duration * 1000f) else 0f
        
        val extras = currentItem.mediaMetadata.extras ?: android.os.Bundle.EMPTY
        val isFavoriteFromMetadata = try { extras.getBoolean("is_favorite", false) } catch (e: Exception) { false }
        
        // If current song matches, prioritize the state we already have if it was changed by broadcast
        val finalFavorite = if (nowPlayingState.songId == (currentItem.mediaId.toLongOrNull() ?: -1L)) {
             // If metadata version is different or we just transitioned, trust metadata
             // but if we are just updating progress/playstate, don't overwrite with potentially stale metadata extras
             isFavoriteFromMetadata 
        } else {
             isFavoriteFromMetadata
        }

        nowPlayingState = nowPlayingState.copy(
            songId = currentItem.mediaId.toLongOrNull() ?: -1L,
            title = currentItem.mediaMetadata.title?.toString() ?: currentItem.mediaMetadata.displayTitle?.toString() ?: "Unknown",
            artist = currentItem.mediaMetadata.artist?.toString() ?: "Unknown Artist",
            albumId = try { extras.getLong("album_id", -1L) } catch (e: Exception) { -1L },
            mediaUri = currentItem.localConfiguration?.uri?.toString() ?: "",
            isPlaying = controller.isPlaying,
            progress = progress,
            position = position,
            duration = duration,
            isFavorite = finalFavorite,
            shuffleMode = controller.shuffleModeEnabled,
            repeatMode = when(controller.repeatMode) {
                Player.REPEAT_MODE_ONE -> 1
                Player.REPEAT_MODE_ALL -> 2
                else -> 0
            },
            lyrics = com.txapp.musicplayer.ui.component.LyricsUtil.getRawLyrics(
                audioFilePath = try { android.net.Uri.parse(currentItem.localConfiguration?.uri?.toString() ?: "").path ?: "" } catch (e: Exception) { "" },
                title = currentItem.mediaMetadata.title?.toString() ?: "",
                artist = currentItem.mediaMetadata.artist?.toString() ?: ""
            )
        )
        
        // Update TXAActiveMP3 global state for all fragments to observe
        val songId = currentItem.mediaId.toLongOrNull() ?: -1L
        val albumId = try { extras.getLong("album_id", -1L) } catch (e: Exception) { -1L }
        TXAActiveMP3.updateNowPlaying(
            songId = songId,
            isPlaying = controller.isPlaying,
            albumId = albumId
        )
    }
    
    fun toggleCurrentSongFavorite() {
        val controller = mediaController ?: return
        val mediaId = controller.currentMediaItem?.mediaId?.toLongOrNull() ?: return
        
        val intent = Intent(this, MusicService::class.java).apply {
            action = MusicService.ACTION_TOGGLE_FAVORITE
            putExtra(MusicService.EXTRA_SONG_ID, mediaId)
        }
        startService(intent)
        
        // Optimistic update for Full Player (Compose)
        val newFavorite = !nowPlayingState.isFavorite
        nowPlayingState = nowPlayingState.copy(isFavorite = newFavorite)
        
        // Optimistic update for MiniPlayer (Fragment)
        supportFragmentManager.fragments.forEach { frag ->
            if (frag is MiniPlayerFragment) {
                frag.onFavoriteStateChanged(mediaId, newFavorite)
            }
        }
        
        TXAToast.show(this, 
            if (newFavorite) "txamusic_action_add_to_favorites".txa() else "txamusic_action_remove_from_favorites".txa())
    }
    
    private fun openTagEditorForCurrentSong() {
        val controller = mediaController ?: run {
            TXAToast.show(this, "txamusic_error_song_not_loaded".txa())
            return
        }
        val mediaId = controller.currentMediaItem?.mediaId?.toLongOrNull() ?: run {
            TXAToast.show(this, "txamusic_error_song_not_loaded".txa())
            return
        }
        
        lifecycleScope.launch {
            val song = repository.getSongById(mediaId)
            if (song != null) {
                currentSongForEdit = song
                showTagEditorSheet = true
            } else {
                TXAToast.show(this@MainActivity, "txamusic_error_song_not_found".txa())
            }
        }
    }
    
    private fun setCurrentSongAsRingtone() {
        val controller = mediaController ?: run {
            TXAToast.show(this, "txamusic_error_song_not_loaded".txa())
            return
        }
        val mediaId = controller.currentMediaItem?.mediaId?.toLongOrNull() ?: run {
            TXAToast.show(this, "txamusic_error_song_not_loaded".txa())
            return
        }
        
        lifecycleScope.launch {
            val song = repository.getSongById(mediaId)
            if (song != null) {
                if (com.txapp.musicplayer.util.TXARingtoneManager.setRingtone(this@MainActivity, song)) {
                    TXAToast.success(this@MainActivity, "txamusic_ringtone_set_success".txa())
                } else {
                    TXAToast.show(this@MainActivity, "txamusic_ringtone_set_failed".txa())
                }
            } else {
                TXAToast.show(this@MainActivity, "txamusic_error_song_not_found".txa())
            }
        }
    }

    private fun toggleSongInPlaylist(playlistId: Long, songId: Long) {
        if (songId == -1L) return
        
        lifecycleScope.launch {
            // Check if exist in list first - but we can also just rely on db check or passed param
            // Let's check DB for source of truth
            val isAlreadyIn = repository.isSongInPlaylist(playlistId, songId)
            
            if (isAlreadyIn) {
                val success = repository.removeSongFromPlaylist(playlistId, songId)
                if (success) {
                    TXAToast.show(this@MainActivity, "txamusic_removed_from_playlist".txa())
                }
            } else {
                val success = repository.addSongToPlaylist(playlistId, songId)
                if (success) {
                    TXAToast.success(this@MainActivity, "txamusic_action_added_to_playlist".txa())
                }
            }
            // No need to close sheet here, user might want to toggle others
        }
    }

    private fun createNewPlaylistAndAddCurrent(name: String) {
        val controller = mediaController ?: return
        val currentSongId = controller.currentMediaItem?.mediaId?.toLongOrNull() ?: return
        
        lifecycleScope.launch {
            val playlistId = repository.createPlaylist(name)
            if (playlistId != -1L) {
                repository.addSongToPlaylist(playlistId, currentSongId)
                TXAToast.success(this@MainActivity, "txamusic_action_added_to_playlist".txa())
                showCreatePlaylistDialog = false
                showAddToPlaylistSheet = false
            }
        }
    }
    
    // --- Existing Functionality Preserved Below ---

    private fun setupCompose() {
        composeView.setContent {
            TXAHolidayBorderOverlay {
                if (showAudioFileModal) {
                    AudioFileInfoModal(
                        song = null, 
                        uri = modalUri,
                        fileName = modalFileName,
                        title = modalTitle,
                        artist = modalArtist,
                        album = modalAlbum,
                        duration = modalDuration,
                        isPlaying = isPlayerPlaying,
                        sourceAppName = modalSourceAppName,
                        onPlay = {
                             modalUri?.let { playExternalUri(it) }
                             showAudioFileModal = false
                        },
                        onAddToQueue = {
                             modalUri?.let { enqueueExternalUri(it) }
                             showAudioFileModal = false
                        },
                        onDismiss = {
                            showAudioFileModal = false
                            pendingOpenUri = null
                        }
                    )
                }

                val updateInfo by TXAUpdateManager.updateInfo.collectAsState()
                val downloadState by TXAUpdateManager.downloadState.collectAsState()
                val isResolving by TXAUpdateManager.isResolving.collectAsState()

                if (showUpdateDialog && updateInfo != null) {
                    UpdateDialog(
                        updateInfo = updateInfo!!,
                        downloadState = downloadState,
                        resolving = isResolving,
                        onUpdateClick = {
                            TXAUpdateManager.startDownload(this@MainActivity, updateInfo!!)
                        },
                        onDismiss = {
                            showUpdateDialog = false
                        },
                        onInstallClick = { file ->
                            TXAUpdateManager.installApk(this@MainActivity, file)
                        }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onDestroy() {
        stopProgressPolling()
        super.onDestroy()
        if (::mediaControllerFuture.isInitialized) {
            MediaController.releaseFuture(mediaControllerFuture)
        }
    }

    private fun handleIntent(intent: Intent) {
        when (intent.action) {
            Intent.ACTION_VIEW -> {
                val uri: Uri? = intent.data
                if (uri != null) {
                    pendingOpenUri = uri
                    val callerPkg = callingPackage ?: try { getReferrer()?.authority } catch (e: Exception) { null }
                    modalSourceAppName = getAppNameFromPackage(callerPkg)
                    maybeShowOpenDialog()
                }
            }
            // Handle ACTION_OPEN_GIFT
            "com.txapp.musicplayer.action.OPEN_GIFT" -> {
                playRandomTetSong()
            }
            "ACTION_VIEW_ARTIST" -> {
                val artistName = intent.getStringExtra("artist_name")
                if (!artistName.isNullOrEmpty()) {
                    navigateToArtist(artistName)
                }
            }
        }
    }


    fun playSongs(songs: List<com.txapp.musicplayer.model.Song>, startIndex: Int = 0, shuffle: Boolean = false) {
        val controller = mediaController ?: return
        val mediaItems = songs.map { song ->
            androidx.media3.common.MediaItem.Builder()
                .setMediaId(song.id.toString())
                .setUri(song.data)
                .setMediaMetadata(
                    androidx.media3.common.MediaMetadata.Builder()
                        .setTitle(song.title)
                        .setArtist(song.artist)
                        .setAlbumTitle(song.album)
                        .setExtras(android.os.Bundle().apply {
                            putLong("album_id", song.albumId)
                            putBoolean("is_favorite", song.isFavorite)
                        })
                        .build()
                )
                .build()
        }
        controller.setMediaItems(mediaItems, if (shuffle) (0 until mediaItems.size).random() else startIndex, 0)
        controller.shuffleModeEnabled = shuffle
        controller.prepare()
        controller.play()
    }

    fun addSongsToQueue(songs: List<com.txapp.musicplayer.model.Song>) {
        val controller = mediaController ?: return
        val mediaItems = songs.map { song ->
            androidx.media3.common.MediaItem.Builder()
                .setMediaId(song.id.toString())
                .setUri(song.data)
                .setMediaMetadata(
                    androidx.media3.common.MediaMetadata.Builder()
                        .setTitle(song.title)
                        .setArtist(song.artist)
                        .setAlbumTitle(song.album)
                        .setExtras(android.os.Bundle().apply {
                            putLong("album_id", song.albumId)
                            putBoolean("is_favorite", song.isFavorite)
                        })
                        .build()
                )
                .build()
        }
        controller.addMediaItems(mediaItems)
        TXAToast.success(this, "txamusic_added_to_queue".txa())
    }


    private fun setupListeners() {
        // Broadcasters or general activity-level listeners
    }
    
    // BroadcastReceiver for global app events
    private val globalBroadcastReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
            when (intent?.action) {
                "com.txapp.musicplayer.action.FAVORITE_STATE_CHANGED" -> {
                    val songId = intent.getLongExtra("song_id", -1L)
                    val isFavorite = intent.getBooleanExtra("is_favorite", false)
                    
                    // Sync Full Player State (Compose)
                    if (nowPlayingState.songId == songId) {
                        nowPlayingState = nowPlayingState.copy(isFavorite = isFavorite)
                    }
                    
                    // Sync MiniPlayer Fragments (View based)
                    supportFragmentManager.fragments.forEach { frag ->
                        if (frag is MiniPlayerFragment) {
                            frag.onFavoriteStateChanged(songId, isFavorite)
                        } else if (frag is HomeFragment) {
                            // Also refresh Home list if favorite changed
                            frag.refreshLists()
                        }
                    }
                }
                "com.txapp.musicplayer.action.SHUFFLE_MODE_CHANGED" -> {
                    val isEnabled = intent.getBooleanExtra("is_enabled", false)
                    nowPlayingState = nowPlayingState.copy(shuffleMode = isEnabled)
                }
                "com.txapp.musicplayer.action.REPEAT_MODE_CHANGED" -> {
                    val mode = intent.getIntExtra("mode", 0)
                    nowPlayingState = nowPlayingState.copy(repeatMode = mode)
                }
                "com.txapp.musicplayer.action.TRIGGER_RESCAN" -> {
                    TXALogger.playbackI("MainActivity", "Yêu cầu quét lại thư viện từ Service")
                    triggerMediaStoreScan()
                }
                "com.txapp.musicplayer.action.RESTORE_COMPLETED" -> {
                    TXALogger.playbackI("MainActivity", "Khôi phục sao lưu hoàn tất, cập nhật UI")
                    updateNowPlayingState()
                    // Refresh Home Fragment data
                    supportFragmentManager.fragments.forEach { frag ->
                        if (frag is HomeFragment) {
                            frag.refreshLists()
                        }
                    }
                }
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        // Register receiver
        val filter = android.content.IntentFilter().apply {
            addAction("com.txapp.musicplayer.action.FAVORITE_STATE_CHANGED")
            addAction("com.txapp.musicplayer.action.SHUFFLE_MODE_CHANGED")
            addAction("com.txapp.musicplayer.action.REPEAT_MODE_CHANGED")
            addAction("com.txapp.musicplayer.action.TRIGGER_RESCAN")
            addAction("com.txapp.musicplayer.action.RESTORE_COMPLETED")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(globalBroadcastReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(globalBroadcastReceiver, filter)
        }
        // Also refresh metadata on resume
    }
    
    override fun onPause() {
        super.onPause()
        try {
            unregisterReceiver(globalBroadcastReceiver)
        } catch (e: Exception) {
            TXACrashHandler.handleError(this, e, "MainActivity")
        }
    }

    private fun initializeController() {
        val sessionToken = SessionToken(
            this,
            ComponentName(this, MusicService::class.java)
        )
        mediaControllerFuture = MediaController.Builder(this, sessionToken).buildAsync()
        mediaControllerFuture.addListener(
            { setupControllerListener() },
            MoreExecutors.directExecutor()
        )
    }

    private fun setupControllerListener() {
        val controller = mediaController ?: return
        controller.addListener(object : Player.Listener {
            override fun onMediaMetadataChanged(mediaMetadata: androidx.media3.common.MediaMetadata) {
                updateMiniPlayer()
                updateNowPlayingState()
            }

            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                updateMiniPlayer()
                updateNowPlayingState() 
                updateBottomSheetState() 
                
                // Notify Fragments
                supportFragmentManager.fragments.forEach { frag ->
                    if (frag is HomeFragment) {
                         val songId = mediaItem?.mediaId?.toLongOrNull() ?: -1L
                         frag.updatePlayingState(songId, controller.isPlaying)
                    } else if (frag is LibraryFragment) {
                        val songId = mediaItem?.mediaId?.toLongOrNull() ?: -1L
                        frag.updatePlayingState(songId, controller.isPlaying)
                    }
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                isPlayerPlaying = isPlaying
                updateMiniPlayer()
                updateNowPlayingState() // Update Full Player
                
                // Notify Fragments
                supportFragmentManager.fragments.forEach { frag ->
                    if (frag is HomeFragment) {
                        val mediaItem = controller.currentMediaItem
                        val songId = mediaItem?.mediaId?.toLongOrNull() ?: -1L
                        frag.updatePlayingState(songId, isPlaying)
                    } else if (frag is LibraryFragment) {
                        val mediaItem = controller.currentMediaItem
                        val songId = mediaItem?.mediaId?.toLongOrNull() ?: -1L
                        frag.updatePlayingState(songId, isPlaying)
                    }
                }

                if (isPlaying) startProgressPolling() else stopProgressPolling()
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                updateNowPlayingState()
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                updateNowPlayingState()
            }
            
            override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                updateNowPlayingState()
            }
        })

        isPlayerPlaying = controller.isPlaying
        updateMiniPlayer()
        updateNowPlayingState() // Crucial: sync UI immediately
        updateBottomSheetState()
        
        // Apply saved playback speed
        controller.setPlaybackSpeed(TXAPreferences.currentPlaybackSpeed)
        
        if (isPlayerPlaying) startProgressPolling()
        maybeShowOpenDialog()
    }

    private fun updateBottomSheetState() {
        // Logic to show/hide bottom sheet if queue is empty/not empty
        val hasMedia = mediaController?.currentMediaItem != null
        // Call generic hideBottomSheet function
        hideBottomSheet(!hasMedia, animate = true)
    }

    private fun updateMiniPlayer() {
        val controller = mediaController ?: return
        val mediaItem = controller.currentMediaItem ?: return
        val metadata = mediaItem.mediaMetadata
        
        // Fix: Use correct Album Art CONTENT_URI
        val albumId = metadata.extras?.getLong("album_id") ?: -1L
        val artUri = if (albumId > 0) {
            android.content.ContentUris.withAppendedId(
                Uri.parse("content://media/external/audio/albumart"),
                albumId
            ).toString()
        } else {
            mediaItem.localConfiguration?.uri?.toString()
        }
        
        val miniPlayerFrag = supportFragmentManager.findFragmentById(R.id.miniPlayerFragment) as? com.txapp.musicplayer.ui.fragment.MiniPlayerFragment
        miniPlayerFrag?.updateSongInfo(
            title = metadata.title?.toString() ?: "Unknown",
            artist = metadata.artist?.toString() ?: "Unknown",
            albumArtUri = artUri,
            isPlaying = controller.isPlaying
        )
    }

    private val progressHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var isProgressPollingActive = false
    private val progressRunnable = object : Runnable {
        override fun run() {
            val controller = mediaController
            if (controller == null) {
                // Controller not ready, try again shortly
                if (isProgressPollingActive) {
                    progressHandler.postDelayed(this, 500)
                }
                return
            }
            
            // Always update progress if we have a media item, regardless of isPlaying state
            // This handles buffering, seeking, and edge cases
            val currentItem = controller.currentMediaItem
            if (currentItem != null) {
                val duration = if (controller.duration == androidx.media3.common.C.TIME_UNSET) 0L else controller.duration.coerceAtLeast(0L)
                val position = controller.currentPosition.coerceAtLeast(0L).coerceAtMost(duration)
                
                val progress = if (duration > 0) (position.toFloat() / duration * 1000f) else 0f
                
                // Update Mini Player via Fragment
                val miniPlayerFrag = supportFragmentManager.findFragmentById(R.id.miniPlayerFragment) as? com.txapp.musicplayer.ui.fragment.MiniPlayerFragment
                miniPlayerFrag?.updateProgress(progress)
                
                // Update Full Player state - ONLY Time/Progress
                nowPlayingState = nowPlayingState.copy(
                    progress = progress,
                    position = position,
                    duration = duration,
                    isPlaying = controller.isPlaying
                )
            }
            
            // Continue polling while active
            if (isProgressPollingActive) {
                progressHandler.postDelayed(this, 500)
            }
        }
    }

    private fun startProgressPolling() {
        if (!isProgressPollingActive) {
            isProgressPollingActive = true
            progressHandler.removeCallbacks(progressRunnable)
            progressHandler.post(progressRunnable)
        }
    }

    private fun stopProgressPolling() {
        isProgressPollingActive = false
        progressHandler.removeCallbacks(progressRunnable)
    }

    private fun maybeShowOpenDialog() {
        val uri = pendingOpenUri ?: return
        val controller = mediaController ?: return // Wait for controller

        // Data for Compose Modal
        modalUri = uri
        modalFileName = Uri.decode(uri.lastPathSegment ?: uri.toString())
        
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val retriever = android.media.MediaMetadataRetriever()
            try {
                // Important: Grant permission to the URI again for the retriever
                retriever.setDataSource(this@MainActivity, uri)
                val title = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_TITLE)
                val artist = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ARTIST)
                val album = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ALBUM)
                val duration = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    modalTitle = title
                    modalArtist = artist
                    modalAlbum = album
                    modalDuration = duration
                    showAudioFileModal = true
                }
            } catch (e: Exception) {
                TXALogger.appE("MainActivity", "Failed to extract metadata", e)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    showAudioFileModal = true // Show anyway with filename
                }
            } finally {
                retriever.release()
            }
        }
    }

    fun switchToLibrary(focusSearch: Boolean = false) {
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.fragment_container) as? androidx.navigation.fragment.NavHostFragment
        val navController = navHostFragment?.navController
        navController?.navigate(R.id.action_song)
        
        if (focusSearch) {
            intent.putExtra("FOCUS_SEARCH", true)
        }
    }

    fun navigateToLibraryTab(tabIndex: Int) {
        val itemId = when (tabIndex) {
            0 -> R.id.action_album
            1 -> R.id.action_artist
            2 -> R.id.action_playlist
            else -> R.id.action_song
        }
        currentTabId = itemId
        // Actually navigate to the destination
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.fragment_container) as? androidx.navigation.fragment.NavHostFragment
        navHostFragment?.navController?.navigate(itemId)
    }

    private fun playExternalUri(uri: Uri) {
        val serviceIntent = Intent(this, MusicService::class.java).apply {
            action = MusicService.ACTION_PLAY_EXTERNAL_URI
            data = uri // Pass as data to allow flag granting
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            putExtra(MusicService.EXTRA_URI, uri.toString())
        }
        startService(serviceIntent)
    }

    private fun enqueueExternalUri(uri: Uri) {
        val serviceIntent = Intent(this, MusicService::class.java).apply {
            action = MusicService.ACTION_ENQUEUE_EXTERNAL_URI
            data = uri
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            putExtra(MusicService.EXTRA_URI, uri.toString())
        }
        startService(serviceIntent)
    }
    private fun getAppNameFromPackage(packageName: String?): String? {
        if (packageName.isNullOrEmpty()) return null
        return try {
            val pm = packageManager
            val ai = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                pm.getApplicationInfo(packageName, android.content.pm.PackageManager.ApplicationInfoFlags.of(0))
            } else {
                pm.getApplicationInfo(packageName, 0)
            }
            pm.getApplicationLabel(ai).toString()
        } catch (e: Exception) {
            packageName // Fallback to package ID if name not found
        }
    }

    private fun playRandomTetSong() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Try listing files in mp3/tet
                // Users might have put them in assets/mp3/tet
                val songs = assets.list("mp3/tet")?.filter { it.endsWith(".mp3") }
                if (!songs.isNullOrEmpty()) {
                    val randomSong = songs.random()
                    val assetUri = Uri.parse("asset:///mp3/tet/$randomSong")
                    
                    withContext(Dispatchers.Main) {
                        val controller = mediaController ?: return@withContext
                        
                        // Create a special MediaItem for the asset
                        val mediaItem = androidx.media3.common.MediaItem.Builder()
                            .setUri(assetUri)
                            // Use a unique ID to prevent conflicts
                            .setMediaId("asset_tet_${System.currentTimeMillis()}")
                            .setMediaMetadata(
                                androidx.media3.common.MediaMetadata.Builder()
                                    .setTitle("txamusic_gift_title".txa())
                                    .setArtist("txamusic_gift_artist".txa())
                                    // We don't have artwork, maybe use a resource drawable URI if needed later
                                    .build()
                            )
                            .build()

                        controller.setMediaItem(mediaItem)
                        controller.prepare()
                        controller.play()
                        
                        TXAToast.success(this@MainActivity, "🎁 " + "txamusic_holiday_noti_body".txa())
                        
                        // Expand player UI
                        expandPanel()
                    }
                } else {
                    TXALogger.appW("MainActivity", "No Tet songs found in assets/mp3/tet")
                    withContext(Dispatchers.Main) {
                        // Fallback message if no songs found (or user didn't put any)
                        TXAToast.info(this@MainActivity, "No gift found 😅")
                    }
                }
            } catch (e: Exception) {
                TXALogger.appE("MainActivity", "Error playing random Tet song", e)
            }
        }
    }
    private fun navigateToArtist(artistName: String) {
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.fragment_container) as? androidx.navigation.fragment.NavHostFragment
        val navController = navHostFragment?.navController ?: return
        
        // Navigate to Artist Tab with arguments
        // The destination fragment should handle "artist_name" argument to open details or filter
        val bundle = android.os.Bundle().apply {
            putString("artist_name", artistName)
            putBoolean("open_details", true)
        }
        
        try {
            // First ensure we are on the graph
            currentTabId = R.id.action_artist
            navController.navigate(R.id.action_artist, bundle)
        } catch (e: Exception) {
            TXALogger.appE("MainActivity", "Failed to navigate to artist", e)
        }
        
        // Collapse player to show the content
        collapsePanel()
    }
}
