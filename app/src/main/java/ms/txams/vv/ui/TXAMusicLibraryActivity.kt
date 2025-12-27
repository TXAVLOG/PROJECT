package ms.txams.vv.ui

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import ms.txams.vv.core.TXATranslation
import ms.txams.vv.data.database.SongEntity
import ms.txams.vv.databinding.ActivityMusicLibraryBinding
import ms.txams.vv.R
import android.content.ContentUris
import android.net.Uri


/**
 * TXA Music Library Activity
 * Inspired by Namida music player
 * 
 * Features:
 * - Click: Play song immediately  
 * - Long press: Enable multi-selection mode
 * - Menu: Show context actions for each song
 * - Selection actions: Add to queue, play all selected, etc.
 */
@AndroidEntryPoint
class TXAMusicLibraryActivity : BaseActivity() {
    private lateinit var binding: ActivityMusicLibraryBinding
    private val viewModel: MusicViewModel by viewModels()
    
    private var mediaController: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null

    private var actionMode: ActionMode? = null

    private val adapter = MusicAdapter(
        onItemClick = { song -> 
            playSong(song)
        },
        onItemLongClick = { song, position ->
            startSelectionMode()
        },
        onMenuClick = { song, view, position ->
            showSongContextMenu(song, view)
        }
    )

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.scanLibrary()
        } else {
            Toast.makeText(this, TXATranslation.txa("txamusic_permission_denied"), Toast.LENGTH_SHORT).show()
        }
    }

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            viewModel.addManualSong(it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMusicLibraryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = TXATranslation.txa("txamusic_music_library_title")
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        setupRecyclerView()
        setupSelectionListener()
        setupBackPressHandler()
        observeData()
        checkPermissionAndScan()

        binding.tvEmpty.text = TXATranslation.txa("txamusic_library_empty")
    }
    
    override fun onStart() {
        super.onStart()
        val sessionToken = SessionToken(this, ComponentName(this, ms.txams.vv.service.MusicService::class.java))
        controllerFuture = MediaController.Builder(this, sessionToken).buildAsync()
        controllerFuture?.addListener({
            try {
                mediaController = controllerFuture?.get()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, MoreExecutors.directExecutor())
    }

    override fun onStop() {
        super.onStop()
        controllerFuture?.let { MediaController.releaseFuture(it) }
    }

    private fun setupBackPressHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (adapter.isSelectionMode) {
                    exitSelectionMode()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    private fun setupSelectionListener() {
        adapter.onSelectionChanged = { selectedIds ->
            if (selectedIds.isEmpty()) {
                actionMode?.finish()
            } else {
                actionMode?.title = "${selectedIds.size} ${TXATranslation.txa("txamusic_selected")}"
            }
        }
    }

    private fun startSelectionMode() {
        if (actionMode == null) {
            actionMode = startActionMode(selectionActionModeCallback)
        }
    }

    private fun exitSelectionMode() {
        adapter.exitSelectionMode()
        actionMode?.finish()
        actionMode = null
    }

    private val selectionActionModeCallback = object : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            menuInflater.inflate(R.menu.menu_selection, menu)
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean = false

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            val selectedSongs = adapter.getSelectedSongs()
            when (item.itemId) {
                R.id.action_play_all -> {
                    playAllSelected(selectedSongs)
                    mode.finish()
                    return true
                }
                R.id.action_add_to_queue -> {
                    addToQueue(selectedSongs)
                    mode.finish()
                    return true
                }
                R.id.action_select_all -> {
                    adapter.selectAll()
                    return true
                }
            }
            return false
        }

        override fun onDestroyActionMode(mode: ActionMode) {
            adapter.exitSelectionMode()
            actionMode = null
        }
    }

    private fun showSongContextMenu(song: SongEntity, anchorView: View) {
        val popup = PopupMenu(this, anchorView)
        popup.menu.add(0, 1, 0, TXATranslation.txa("txamusic_play"))
        popup.menu.add(0, 2, 1, TXATranslation.txa("txamusic_add_to_queue"))
        popup.menu.add(0, 3, 2, TXATranslation.txa("txamusic_add_to_playlist"))
        
        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                1 -> {
                    playSong(song)
                    true
                }
                2 -> {
                    addToQueue(listOf(song))
                    true
                }
                3 -> {
                    // TODO: Implement add to playlist
                    Toast.makeText(this, "Coming soon", Toast.LENGTH_SHORT).show()
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun playSong(song: SongEntity) {
        val player = mediaController
        if (player == null) {
            Toast.makeText(this, TXATranslation.txa("txamusic_service_not_ready"), Toast.LENGTH_SHORT).show()
            return
        }
        
        // Get album art URI
        val albumArtUri = try {
            ContentUris.withAppendedId(
                Uri.parse("content://media/external/audio/albumart"),
                song.albumId
            )
        } catch (e: Exception) {
            null
        }
        
        // Use merged path if available (has intro), otherwise use original path
        val playbackUri = song.mergedPath ?: song.path
        val hasIntro = song.mergedPath != null
        
        val metadata = MediaMetadata.Builder()
            .setTitle(song.title)
            .setArtist(song.artist)
            .setArtworkUri(albumArtUri)
            .build()
            
        val item = MediaItem.Builder()
            .setUri(playbackUri)
            .setMediaId(song.id.toString())
            .setMediaMetadata(metadata)
            .build()
            
        player.setMediaItem(item)
        player.prepare()
        
        // Use TXAAudioFader for smooth fade in with intro boost if merged
        if (hasIntro) {
            // Play with intro boost (louder intro, then fade to normal)
            ms.txams.vv.core.TXAAudioFader.playWithIntroBoost(
                player = player,
                introDurationMs = 5000L, // intro_txa.mp3 duration
                fadeInDurationMs = 3000L
            )
        } else {
            // Normal fade in
            ms.txams.vv.core.TXAAudioFader.fadeIn(
                player = player,
                durationMs = 3000L
            )
        }
        
        Toast.makeText(this, TXATranslation.txa("txamusic_playing").format(song.title), Toast.LENGTH_SHORT).show()
    }

    private fun playAllSelected(songs: List<SongEntity>) {
        val player = mediaController
        if (player == null) {
            Toast.makeText(this, TXATranslation.txa("txamusic_service_not_ready"), Toast.LENGTH_SHORT).show()
            return
        }
        
        if (songs.isEmpty()) return
        
        val mediaItems = songs.map { song ->
            val albumArtUri = try {
                ContentUris.withAppendedId(
                    Uri.parse("content://media/external/audio/albumart"),
                    song.albumId
                )
            } catch (e: Exception) {
                null
            }
            
            val playbackUri = song.mergedPath ?: song.path
            
            MediaItem.Builder()
                .setUri(playbackUri)
                .setMediaId(song.id.toString())
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(song.title)
                        .setArtist(song.artist)
                        .setArtworkUri(albumArtUri)
                        .build()
                )
                .build()
        }
        
        player.setMediaItems(mediaItems)
        player.prepare()
        player.play()
        
        Toast.makeText(this, "${songs.size} ${TXATranslation.txa("txamusic_songs")} playing", Toast.LENGTH_SHORT).show()
    }

    private fun addToQueue(songs: List<SongEntity>) {
        val player = mediaController
        if (player == null) {
            Toast.makeText(this, TXATranslation.txa("txamusic_service_not_ready"), Toast.LENGTH_SHORT).show()
            return
        }
        
        songs.forEach { song ->
            val albumArtUri = try {
                ContentUris.withAppendedId(
                    Uri.parse("content://media/external/audio/albumart"),
                    song.albumId
                )
            } catch (e: Exception) {
                null
            }
            
            val playbackUri = song.mergedPath ?: song.path
            
            val mediaItem = MediaItem.Builder()
                .setUri(playbackUri)
                .setMediaId(song.id.toString())
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(song.title)
                        .setArtist(song.artist)
                        .setArtworkUri(albumArtUri)
                        .build()
                )
                .build()
            
            player.addMediaItem(mediaItem)
        }
        
        Toast.makeText(this, "${songs.size} ${TXATranslation.txa("txamusic_add_to_queue")}", Toast.LENGTH_SHORT).show()
    }

    private fun setupRecyclerView() {
        binding.rvSongs.layoutManager = LinearLayoutManager(this)
        binding.rvSongs.adapter = adapter
    }

    private fun observeData() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.songs.collect { songs ->
                        adapter.submitList(songs)
                        binding.tvEmpty.visibility = if (songs.isEmpty()) View.VISIBLE else View.GONE
                         if (songs.isNotEmpty()) {
                             supportActionBar?.subtitle = TXATranslation.txa("txamusic_songs_count").format(songs.size)
                         }
                    }
                }
                launch {
                    viewModel.isLoading.collect { isLoading ->
                        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
                    }
                }
            }
        }
    }

    private fun checkPermissionAndScan() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            viewModel.scanLibrary()
        } else {
            permissionLauncher.launch(permission)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menu?.add(0, 1, 0, TXATranslation.txa("txamusic_refresh_library"))
            ?.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        menu?.add(0, 2, 1, TXATranslation.txa("txamusic_add_manual_file"))
            ?.setIcon(R.drawable.ic_add)
            ?.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                if (adapter.isSelectionMode) {
                    exitSelectionMode()
                } else {
                    finish()
                }
            }
            1 -> checkPermissionAndScan()
            2 -> {
                try {
                    filePickerLauncher.launch(arrayOf("audio/*"))
                } catch (e: android.content.ActivityNotFoundException) {
                    // No document picker available (common on emulators or older devices)
                    Toast.makeText(
                        this,
                        TXATranslation.txa("txamusic_no_file_picker"),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
        return super.onOptionsItemSelected(item)
    }
}
