package ms.txams.vv.ui

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
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


@AndroidEntryPoint
class TXAMusicLibraryActivity : BaseActivity() {
    private lateinit var binding: ActivityMusicLibraryBinding
    private val viewModel: MusicViewModel by viewModels()
    
    private var mediaController: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null

    private val adapter = MusicAdapter { song ->
        playSong(song)
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.scanLibrary()
        } else {
            Toast.makeText(this, TXATranslation.txa("txamusic_permission_denied"), Toast.LENGTH_SHORT).show()
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
        observeData()
        checkPermissionAndScan()
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

    private fun playSong(song: SongEntity) {
        val player = mediaController
        if (player == null) {
            Toast.makeText(this, "Service not ready", Toast.LENGTH_SHORT).show()
            return
        }
        
        val metadata = MediaMetadata.Builder()
            .setTitle(song.title)
            .setArtist(song.artist)
             .setArtworkUri(android.net.Uri.parse(song.path)) // Or album art
            .build()
            
        val item = MediaItem.Builder()
            .setUri(song.path)
            .setMediaId(song.id.toString())
            .setMediaMetadata(metadata)
            .build()
            
        player.setMediaItem(item)
        player.prepare()
        player.play()
        
        // Show simplified feedback
        Toast.makeText(this, "Playing: ${song.title}", Toast.LENGTH_SHORT).show()
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
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> finish()
            1 -> checkPermissionAndScan()
        }
        return super.onOptionsItemSelected(item)
    }
}
