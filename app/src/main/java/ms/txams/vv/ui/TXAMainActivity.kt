package ms.txams.vv.ui

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
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
import ms.txams.vv.R
import ms.txams.vv.core.TXATranslation
import ms.txams.vv.data.database.SongEntity
import ms.txams.vv.databinding.ActivityMainTxaBinding

import ms.txams.vv.ui.adapter.TXAQueueAdapter

@AndroidEntryPoint
class TXAMainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainTxaBinding
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<View>
    private lateinit var queueAdapter: TXAQueueAdapter
    
    private var mediaController: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainTxaBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initUI()
        setupNavigation()
        setupQueue()
        setupNowBar()
    }
    
    private fun initUI() {
        binding.tvHeader.text = TXATranslation.txa("txamusic_app_name")
        binding.tvCardLibrary.text = TXATranslation.txa("txamusic_music_library_title")
        binding.tvCardSettings.text = TXATranslation.txa("txamusic_settings_title")
        binding.tvQueueTitle.text = TXATranslation.txa("txamusic_queue")
        binding.tvNowPlayingTitle.text = TXATranslation.txa("txamusic_now_playing")
    }

    override fun onStart() {
        super.onStart()
        initializeController()
    }

    override fun onStop() {
        super.onStop()
        releaseController()
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
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updateNowBarUI()
            }
        })
    }

    private fun updateNowBarUI() {
        val player = mediaController ?: return
        val currentMedia = player.currentMediaItem
        
        binding.tvNowPlayingTitle.text = currentMedia?.mediaMetadata?.title 
            ?: TXATranslation.txa("txamusic_now_playing")
        
        binding.ivNowPlayingArt.load(currentMedia?.mediaMetadata?.artworkUri ?: R.drawable.ic_music_note)

        val icon = if (player.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        binding.btnPlayPause.setImageResource(icon)
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
    }

    private fun setupQueue() {
        bottomSheetBehavior = BottomSheetBehavior.from(binding.queueSheet)
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
            .setArtworkUri(android.net.Uri.parse(song.path))
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
            if (bottomSheetBehavior.state == BottomSheetBehavior.STATE_COLLAPSED || 
                bottomSheetBehavior.state == BottomSheetBehavior.STATE_HIDDEN) {
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
            } else {
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
            }
        }
    }
}
