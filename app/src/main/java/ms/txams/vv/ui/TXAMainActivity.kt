package ms.txams.vv.ui

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import coil.load
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import ms.txams.vv.R
import ms.txams.vv.core.TXATranslation
import ms.txams.vv.data.database.SongEntity
import ms.txams.vv.databinding.ActivityMainTxaBinding
import ms.txams.vv.ui.adapter.TXAQueueAdapter
import javax.inject.Inject

@AndroidEntryPoint
class TXAMainActivity : BaseActivity() {

    @Inject lateinit var lyricsManager: ms.txams.vv.data.manager.TXALyricsManager

    private lateinit var ttsManager: ms.txams.vv.core.TXATTSManager
    private var isTtsInitialized = false

    private lateinit var binding: ActivityMainTxaBinding
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<View>
    private lateinit var queueAdapter: TXAQueueAdapter
    
    private var mediaController: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val progressRunnable = object : Runnable {
        override fun run() {
            updateProgress()
            handler.postDelayed(this, 16) // ~60fps for millisecond precision
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainTxaBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        ttsManager = ms.txams.vv.core.TXATTSManager(this)
        lifecycleScope.launch {
            isTtsInitialized = ttsManager.initialize()
        }

        initUI()
        setupNavigation()
        setupQueue()
        setupNowBar()
        observeLyrics()

        // Initial intent check
        if (intent != null) {
            handleIntent(intent)
        }
    }
    
    // ... handleIntent ...

    private fun addToQueue(uri: android.net.Uri) {
        lifecycleScope.launch {
            // Get sequence (Intro + Song)
            val mediaItems = prepareMediaItems(uri)
            
            val player = mediaController ?: return@launch
            
            if (mediaItems.isNotEmpty()) {
                player.addMediaItems(mediaItems)
                
                android.widget.Toast.makeText(
                    this@TXAMainActivity,
                    TXATranslation.txa("txamusic_added_to_queue"),
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private var pendingUri: android.net.Uri? = null

    private fun playUri(uri: android.net.Uri) {
        lifecycleScope.launch {
            val mediaItems = prepareMediaItems(uri)
            
            val player = mediaController ?: return@launch
            
            if (mediaItems.isNotEmpty()) {
                player.setMediaItems(mediaItems)
                player.prepare()
                player.play()
            }
        }
    }
    
    private suspend fun prepareMediaItems(songUri: android.net.Uri): List<MediaItem> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val items = mutableListOf<MediaItem>()
        
        // 1. Prepare Intro
        try {
            val cacheDir = java.io.File(externalCacheDir, "tts_cache")
            if (!cacheDir.exists()) cacheDir.mkdirs()
            val introFile = java.io.File(cacheDir, "intro_tts_v1.wav")
            
            if (!introFile.exists()) {
                // Initialize if needed (might be on wrong thread, but verify)
                if (!isTtsInitialized) { 
                    // Main thread check required if not init? 
                    // Assuming init in onCreate handles it mostly, or we skip intro.
                    // For robust fallback:
                }
                
                // Synthesize (requires main thread for some engines, but TTSManager puts callback on Main)
                val introText = ttsManager.getIntroTextForSynthesis()
                ttsManager.synthesizeToFile(introText, introFile)
            }
            
            if (introFile.exists() && introFile.length() > 0) {
                 val introItem = MediaItem.Builder()
                    .setUri(android.net.Uri.fromFile(introFile))
                    .setMediaId("intro_txa")
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle("TXA Intro")
                            .setArtist("TXA Assistant")
                            .build()
                    )
                    .build()
                 items.add(introItem)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        // 2. Prepare Song
        // Get file name/metadata
        val fileName = try {
            contentResolver.query(songUri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) cursor.getString(nameIndex) else null
                } else null
            }
        } catch (e: Exception) { null } ?: songUri.lastPathSegment ?: TXATranslation.txa("txamusic_unknown")

        val metadata = MediaMetadata.Builder()
            .setTitle(fileName)
            .setArtist(TXATranslation.txa("txamusic_unknown"))
            .setArtworkUri(songUri)
            .build()
            
        val songItem = MediaItem.Builder()
            .setUri(songUri)
            .setMediaId(songUri.toString())
            .setMediaMetadata(metadata)
            .build()
            
        items.add(songItem)
        
        return@withContext items
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        if (Intent.ACTION_VIEW == intent.action) {
            val uri = intent.data
            if (uri != null) {
                // Check if music is currently playing
                mediaController?.let { controller ->
                    if (controller.isPlaying || controller.currentMediaItem != null) {
                        // Show dialog to ask: queue or play now
                        showQueueOrPlayDialog(uri)
                    } else {
                        playUri(uri)
                    }
                } ?: run {
                    pendingUri = uri
                }
            }
        }
    }
    
    private fun showQueueOrPlayDialog(uri: android.net.Uri) {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(TXATranslation.txa("txamusic_external_file_title"))
            .setMessage(TXATranslation.txa("txamusic_external_file_message"))
            .setPositiveButton(TXATranslation.txa("txamusic_play_now")) { _, _ ->
                playUri(uri)
            }
            .setNeutralButton(TXATranslation.txa("txamusic_add_to_queue")) { _, _ ->
                addToQueue(uri)
            }
            .setNegativeButton(TXATranslation.txa("txamusic_cancel"), null)
            .show()
    }
    

    
    private fun initUI() {
        binding.tvHeader.text = TXATranslation.txa("txamusic_app_name")
        binding.tvCardLibrary.text = TXATranslation.txa("txamusic_music_library_title")
        binding.tvQueueTitle.text = TXATranslation.txa("txamusic_queue")
        
        // Initial state
        binding.tvNowPlayingTitle.text = TXATranslation.txa("txamusic_now_bar_waiting")
    }

    override fun onStart() {
        super.onStart()
        initializeController()
        handler.post(progressRunnable)
    }

    override fun onStop() {
        super.onStop()
        releaseController()
        handler.removeCallbacks(progressRunnable)
    }

    private fun initializeController() {
        val sessionToken = SessionToken(this, ComponentName(this, ms.txams.vv.service.MusicService::class.java))
        controllerFuture = MediaController.Builder(this, sessionToken).buildAsync()
        controllerFuture?.addListener({
            try {
                mediaController = controllerFuture?.get()
                setupControllerListener()
                updateNowBarUI()
                
                // Handle pending URI if any
                pendingUri?.let {
                    playUri(it)
                    pendingUri = null
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, MoreExecutors.directExecutor())
    }

    private fun releaseController() {
        val future = controllerFuture
        if (future != null) {
            MediaController.releaseFuture(future)
        }
        mediaController = null
    }
    
    private fun setupControllerListener() {
        mediaController?.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                updateNowBarUI()
                loadLyrics(mediaItem)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updateNowBarUI()
            }
        })
    }

    private var isRotating = false

    private fun startRotation() {
        if (isRotating) return
        isRotating = true
        binding.ivLargeArt.animate()
            .rotationBy(360f)
            .setDuration(12000)
            .setInterpolator(android.view.animation.LinearInterpolator())
            .withEndAction {
                if (isRotating) {
                    isRotating = false
                    startRotation()
                }
            }
            .start()
    }

    private fun stopRotation() {
        isRotating = false
        binding.ivLargeArt.animate().cancel()
    }

    private fun updateNowBarUI() {
        val player = mediaController ?: return
        val currentMedia = player.currentMediaItem
        
        if (currentMedia == null) {
            binding.tvNowPlayingTitle.text = TXATranslation.txa("txamusic_now_bar_waiting")
            binding.ivNowPlayingArt.setImageResource(R.drawable.txa_default_art)
            binding.ivLargeArt.setImageResource(R.drawable.txa_default_art)
            binding.ivNowPlayingBackground.setImageDrawable(null)
            binding.btnPlayPause.setImageResource(R.drawable.ic_play)
            binding.cpDiskProgress.progress = 0
            binding.cpNowBarProgress.progress = 0
            binding.tvCurrentTime.text = "00:00.000"
            binding.tvTotalTime.text = "00:00.000"
            stopRotation()
            return
        }

        val unknown = TXATranslation.txa("txamusic_unknown")
        val title = currentMedia.mediaMetadata.title?.toString() ?: unknown
        val artist = currentMedia.mediaMetadata.artist?.toString() ?: unknown
        val artworkUri = currentMedia.mediaMetadata.artworkUri
        
        binding.tvNowPlayingTitle.text = title
        binding.tvLargeTitle.text = title
        binding.tvLargeArtist.text = artist

        // Load artwork
        if (artworkUri != null) {
            binding.ivNowPlayingArt.load(artworkUri) {
                placeholder(R.drawable.txa_default_art)
                error(R.drawable.txa_default_art)
            }
            binding.ivLargeArt.load(artworkUri) {
                placeholder(R.drawable.txa_default_art)
                error(R.drawable.txa_default_art)
            }
            binding.ivNowPlayingBackground.load(artworkUri)
        } else {
            binding.ivNowPlayingArt.setImageResource(R.drawable.txa_default_art)
            binding.ivLargeArt.setImageResource(R.drawable.txa_default_art)
            binding.ivNowPlayingBackground.setImageDrawable(null)
        }

        // Custom Icons
        val iconRes = if (player.isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        binding.btnPlayPause.setImageResource(iconRes)
        
        if (player.isPlaying) startRotation() else stopRotation()
    }

    private fun updateProgress() {
        val player = mediaController ?: return
        val duration = player.duration
        val position = player.currentPosition
        
        if (duration > 0) {
            val progressPercent = (position * 100 / duration).toInt()
            binding.songProgress.value = progressPercent.toFloat().coerceIn(0f, 100f)
            binding.cpDiskProgress.progress = progressPercent
            binding.cpNowBarProgress.progress = progressPercent
            
            binding.tvCurrentTime.text = ms.txams.vv.core.TXAFormat.formatDuration(position, true)
            binding.tvTotalTime.text = ms.txams.vv.core.TXAFormat.formatDuration(duration, true)
        }
        
        if (player.isPlaying) {
            startRotation()
            lyricsManager.updatePosition(position)
        } else {
            stopRotation()
        }
    }

    private fun loadLyrics(mediaItem: MediaItem?) {
        if (mediaItem == null) {
            lyricsManager.clear()
            return
        }
        
        val title = mediaItem.mediaMetadata.title?.toString()
        val artist = mediaItem.mediaMetadata.artist?.toString()
        val path = mediaItem.mediaMetadata.artworkUri?.toString() // Path stored in artworkUri in playSong

        lifecycleScope.launch {
            lyricsManager.loadLyrics(audioPath = path, title = title, artist = artist)
        }
    }

    private fun observeLyrics() {
        lifecycleScope.launch {
            lyricsManager.lyricsState.collect { state ->
                when (state) {
                    is ms.txams.vv.data.manager.LyricsState.Loaded -> {
                        // Handled by position update
                    }
                    is ms.txams.vv.data.manager.LyricsState.NotFound -> {
                        binding.tvLyricsPlaceholder.text = TXATranslation.txa("txamusic_lyrics_not_found")
                    }
                    else -> {}
                }
            }
        }

        lifecycleScope.launch {
            lyricsManager.currentLineIndex.collect { index ->
                val line = lyricsManager.getCurrentLine()
                if (line != null) {
                    binding.tvLyricsPlaceholder.text = line.content
                }
            }
        }
    }

    private fun setupNavigation() {
        // Handle Liquid Tab Bar
        binding.liquidTabBar.onTabSelected = { index ->
            when (index) {
                0 -> { // Home
                    binding.tabHome.visibility = View.VISIBLE
                    binding.tabSettings.visibility = View.GONE
                }
                1 -> { // Settings
                    binding.tabHome.visibility = View.GONE
                    binding.tabSettings.visibility = View.VISIBLE
                    loadSettingsContent()
                }
            }
        }

        // Home Tab Interactions
        binding.cardLibrary.setOnClickListener {
            startActivity(Intent(this, TXAMusicLibraryActivity::class.java))
        }
        

        
        binding.btnPlayPause.setOnClickListener {
            val player = mediaController ?: return@setOnClickListener
            if (player.isPlaying) player.pause() else player.play()
        }

        binding.songProgress.addOnChangeListener { slider, value, fromUser ->
             if (fromUser) {
                 val player = mediaController ?: return@addOnChangeListener
                 val newPos = (value * player.duration / 100).toLong()
                 player.seekTo(newPos)
             }
         }
    }

    private fun loadSettingsContent() {
        // Check if already loaded to avoid inflation overhead
        if (binding.tabSettings.childCount > 1) return 
        
        // Clear placeholder
        binding.tabSettings.removeAllViews()
        
        // Inflate simple settings dashboard
        val settingsView = layoutInflater.inflate(R.layout.item_setting_choice, binding.tabSettings, false)
        // Adjust style manually since we are reusing a layout designed for something else, 
        // OR better: Create a programmatic view structure here.
        
        val context = this
        val container = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(32, 120, 32, 32)
            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        
        // Title
        container.addView(android.widget.TextView(context).apply {
            text = TXATranslation.txa("txamusic_settings_title")
            textSize = 32f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(android.graphics.Color.WHITE)
            setPadding(0, 0, 0, 48)
        })

        // Full Settings Button
        container.addView(com.google.android.material.button.MaterialButton(context).apply {
            text = TXATranslation.txa("txamusic_settings_title") 
            setOnClickListener { startActivity(Intent(context, TXASettingsActivity::class.java)) }
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 24 }
        })
        
        // Music Library Button
        container.addView(com.google.android.material.button.MaterialButton(context).apply {
            text = TXATranslation.txa("txamusic_music_library_title")
            setOnClickListener { startActivity(Intent(context, TXAMusicLibraryActivity::class.java)) }
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 24 }
        })

        // Changelog Button
        container.addView(com.google.android.material.button.MaterialButton(context).apply {
            text = TXATranslation.txa("txamusic_changelog")
            setOnClickListener { showChangelog() }
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 24 }
        })

        binding.tabSettings.addView(container)
    }

    private fun showChangelog() {
        // Reuse Splash Activity logic or create simple dialog
        // Quick dialog
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(TXATranslation.txa("txamusic_changelog"))
            .setMessage("Version 1.5.0\n\n- New Player UI\n- Waveform\n- Liquid Tabs")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun setupQueue() {
        bottomSheetBehavior = BottomSheetBehavior.from(binding.playerSheet)
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        bottomSheetBehavior.peekHeight = 0 

        queueAdapter = TXAQueueAdapter(
            onSongClick = { song -> playSong(song) },
            onStartDrag = { viewHolder -> itemTouchHelper.startDrag(viewHolder) }
        )
        binding.rvQueue.layoutManager = LinearLayoutManager(this)
        binding.rvQueue.adapter = queueAdapter
        itemTouchHelper.attachToRecyclerView(binding.rvQueue)
    }
    
    private fun playSong(song: SongEntity) {
        lifecycleScope.launch {
            val originalUri = android.net.Uri.parse(song.path)
            val mediaItems = prepareMediaItems(originalUri)
            
            val player = mediaController ?: return@launch
             
            if (mediaItems.isNotEmpty()) {
                // Update metadata for the Song item (index 1 if intro exists, else 0)
                // The prepareMediaItems already sets metadata for the song item.
                // But we derived it from File/ContentResolver. 
                // Here we have SongEntity which is better.
                // Let's update the song item (last item) with Entity metadata.
                
                val songItem = mediaItems.last()
                val betterMetadata = songItem.mediaMetadata.buildUpon()
                    .setTitle(song.title)
                    .setArtist(song.artist)
                    .setArtworkUri(originalUri)
                    .build()
                    
                val betterItem = songItem.buildUpon()
                    .setMediaMetadata(betterMetadata)
                    .setMediaId(song.id.toString())
                    .build()
                
                val finalItems = mediaItems.toMutableList()
                finalItems[finalItems.lastIndex] = betterItem
                
                player.setMediaItems(finalItems)
                player.prepare()
                player.play()
            }
        }
    }
    
    private val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
        ItemTouchHelper.UP or ItemTouchHelper.DOWN,
        ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
    ) {
        override fun onMove(
            recyclerView: androidx.recyclerview.widget.RecyclerView,
            viewHolder: androidx.recyclerview.widget.RecyclerView.ViewHolder,
            target: androidx.recyclerview.widget.RecyclerView.ViewHolder
        ): Boolean {
            queueAdapter.onItemMove(viewHolder.bindingAdapterPosition, target.bindingAdapterPosition)
            return true
        }

        override fun onSwiped(viewHolder: androidx.recyclerview.widget.RecyclerView.ViewHolder, direction: Int) {
            queueAdapter.onItemDismiss(viewHolder.bindingAdapterPosition)
        }
    })

    private fun setupNowBar() {
        binding.nowBar.setOnClickListener {
            if (bottomSheetBehavior.state != BottomSheetBehavior.STATE_EXPANDED) {
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
            } else {
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
            }
        }
    }
}
