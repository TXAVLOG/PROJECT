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
            handler.postDelayed(this, 1000)
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

    private fun updateNowBarUI() {
        val player = mediaController ?: return
        val currentMedia = player.currentMediaItem
        
        if (currentMedia == null) {
            binding.tvNowPlayingTitle.text = TXATranslation.txa("txamusic_now_bar_waiting")
            binding.ivNowPlayingArt.setImageResource(R.drawable.ic_music_note)
            binding.ivNowPlayingBackground.setImageDrawable(null)
            binding.btnPlayPause.setImageResource(R.drawable.ic_play)
            return
        }

        val title = currentMedia.mediaMetadata.title?.toString() ?: "Unknown"
        val artist = currentMedia.mediaMetadata.artist?.toString() ?: "Unknown"
        val artworkUri = currentMedia.mediaMetadata.artworkUri
        
        binding.tvNowPlayingTitle.text = title
        binding.tvLargeTitle.text = title
        binding.tvLargeArtist.text = artist

        // Load artwork
        if (artworkUri != null) {
            binding.ivNowPlayingArt.load(artworkUri)
            binding.ivLargeArt.load(artworkUri)
            binding.ivNowPlayingBackground.load(artworkUri)
        } else {
            binding.ivNowPlayingArt.setImageResource(R.drawable.ic_music_note)
            binding.ivLargeArt.setImageResource(R.drawable.ic_music_note)
            binding.ivNowPlayingBackground.setImageDrawable(null)
        }

        // Custom Icons
        val iconRes = if (player.isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        binding.btnPlayPause.setImageResource(iconRes)
    }

    private fun updateProgress() {
        val player = mediaController ?: return
        if (player.isPlaying) {
            val progress = if (player.duration > 0) (player.currentPosition * 100 / player.duration).toFloat() else 0f
            binding.songProgress.value = progress.coerceIn(0f, 100f)
            lyricsManager.updatePosition(player.currentPosition)
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
                        binding.tvLyricsPlaceholder.text = "Lyrics not found"
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
