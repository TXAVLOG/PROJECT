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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        if (Intent.ACTION_VIEW == intent.action) {
            val uri = intent.data
            if (uri != null) {
                // If controller is ready, play immediately.
                // Otherwise, it will be played in initializeController.
                mediaController?.let {
                    playUri(uri)
                } ?: run {
                    pendingUri = uri
                }
            }
        }
    }

    private var pendingUri: android.net.Uri? = null

    private fun playUri(uri: android.net.Uri) {
        val player = mediaController ?: return
        
        // Get file name from URI
        val fileName = try {
            val cursor = contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) it.getString(nameIndex) else null
                } else null
            }
        } catch (e: Exception) {
            null
        } ?: uri.lastPathSegment ?: TXATranslation.txa("txamusic_unknown")

        val metadata = MediaMetadata.Builder()
            .setTitle(fileName)
            .setArtist(TXATranslation.txa("txamusic_unknown"))
            .setArtworkUri(uri)
            .build()
            
        val item = MediaItem.Builder()
            .setUri(uri)
            .setMediaId(uri.toString())
            .setMediaMetadata(metadata)
            .build()
            
        player.setMediaItem(item)
        player.prepare()
        player.play()
    }
    
    private fun initUI() {
        binding.tvHeader.text = TXATranslation.txa("txamusic_app_name")
        binding.tvCardLibrary.text = TXATranslation.txa("txamusic_music_library_title")
        binding.tvCardSettings.text = TXATranslation.txa("txamusic_settings_title")
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
        controllerFuture?.let { MediaController.releaseFuture(it) }
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
        binding.cardLibrary.setOnClickListener {
            startActivity(Intent(this, TXAMusicLibraryActivity::class.java))
        }
        
        binding.cardSettings.setOnClickListener {
            startActivity(Intent(this, TXASettingsActivity::class.java))
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
        val player = mediaController ?: return
         
        val metadata = MediaMetadata.Builder()
            .setTitle(song.title)
            .setArtist(song.artist)
            .setArtworkUri(android.net.Uri.parse(song.path)) // Local path
            .build()
            
        val item = MediaItem.Builder()
            .setUri(song.path)
            .setMediaId(song.id.toString())
            .setMediaMetadata(metadata)
            .build()
            
        player.setMediaItem(item)
        player.prepare()
        player.play()
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
