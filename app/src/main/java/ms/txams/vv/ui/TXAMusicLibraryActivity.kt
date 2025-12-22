package ms.txams.vv.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import ms.txams.vv.data.database.SongEntity
import ms.txams.vv.data.repository.MusicRepository
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import ms.txams.vv.core.TXATranslation
import ms.txams.vv.databinding.ActivityTxaFileManagerBinding
import javax.inject.Inject

@AndroidEntryPoint
class TXAMusicLibraryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTxaFileManagerBinding
    private lateinit var adapter: MusicAdapter

    @Inject
    lateinit var musicRepository: MusicRepository

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            loadMusicLibrary()
        } else {
            showPermissionDeniedMessage()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTxaFileManagerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        setupRecyclerView()
        checkPermissionsAndLoad()
    }

    private fun setupUI() {
        binding.apply {
            toolbar.title = TXATranslation.txa("txamusic_music_library_title")
            toolbar.setNavigationOnClickListener {
                finish()
            }

            tvStoragePath.text = TXATranslation.txa("txamusic_all_songs")
            btnRefresh.text = TXATranslation.txa("txamusic_refresh_library")
            btnCleanUp.text = TXATranslation.txa("txamusic_scan_library")

            btnRefresh.setOnClickListener {
                loadMusicLibrary()
            }

            btnCleanUp.setOnClickListener {
                scanMusicLibrary()
            }
        }
    }

    private fun setupRecyclerView() {
        adapter = MusicAdapter { song, action ->
            when (action) {
                "play" -> playSong(song)
                "add_to_playlist" -> addToPlaylist(song)
            }
        }
        
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(this@TXAMusicLibraryActivity)
            adapter = this@TXAMusicLibraryActivity.adapter
        }
    }

    private fun checkPermissionsAndLoad() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        when {
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED -> {
                loadMusicLibrary()
            }
            shouldShowRequestPermissionRationale(permission) -> {
                showPermissionRationale()
            }
            else -> {
                requestPermissionLauncher.launch(permission)
            }
        }
    }

    private fun loadMusicLibrary() {
        lifecycleScope.launch {
            musicRepository.getAllSongs().collect { songs ->
                updateUI(songs)
            }
        }
    }

    private fun scanMusicLibrary() {
        lifecycleScope.launch {
            try {
                val count = musicRepository.scanMusicLibrary()
                Toast.makeText(
                    this@TXAMusicLibraryActivity,
                    TXATranslation.txa("txamusic_library_scanned").format(count.toString()),
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                Toast.makeText(
                    this@TXAMusicLibraryActivity,
                    TXATranslation.txa("txamusic_scan_failed"),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun updateUI(songs: List<SongEntity>) {
        if (songs.isEmpty()) {
            binding.recyclerView.visibility = android.view.View.GONE
            binding.emptyState.visibility = android.view.View.VISIBLE
            binding.tvFileCount.text = TXATranslation.txa("txamusic_songs_count").format("0")
        } else {
            binding.recyclerView.visibility = android.view.View.VISIBLE
            binding.emptyState.visibility = android.view.View.GONE
            
            binding.tvFileCount.text = TXATranslation.txa("txamusic_songs_count").format(songs.size.toString())
            
            adapter.updateSongs(songs)
        }
    }

    private fun playSong(song: SongEntity) {
        // TODO: Integrate with MusicService to play the song
        Toast.makeText(this, "Playing: ${song.title}", Toast.LENGTH_SHORT).show()
    }

    private fun addToPlaylist(song: SongEntity) {
        // TODO: Add song to playlist functionality
        Toast.makeText(this, "Added to playlist: ${song.title}", Toast.LENGTH_SHORT).show()
    }

    private fun showPermissionRationale() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(TXATranslation.txa("txamusic_permission_required"))
            .setMessage(TXATranslation.txa("txamusic_permission_audio_rationale"))
            .setPositiveButton(TXATranslation.txa("txamusic_action_grant")) { _, _ ->
                val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Manifest.permission.READ_MEDIA_AUDIO
                } else {
                    Manifest.permission.READ_EXTERNAL_STORAGE
                }
                requestPermissionLauncher.launch(permission)
            }
            .setNegativeButton(TXATranslation.txa("txamusic_action_cancel"), null)
            .show()
    }

    private fun showPermissionDeniedMessage() {
        Toast.makeText(
            this,
            TXATranslation.txa("txamusic_permission_denied"),
            Toast.LENGTH_LONG
        ).show()
    }
}
