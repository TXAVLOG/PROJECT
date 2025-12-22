package gc.txa.demo.ui.library

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dagger.hilt.android.AndroidEntryPoint
import gc.txa.demo.R
import gc.txa.demo.ui.viewmodel.MusicViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class LibraryFragment : Fragment() {

    private val viewModel: MusicViewModel by viewModels()
    private lateinit var rvMenuItems: RecyclerView
    private lateinit var tvSongsCount: TextView
    private lateinit var tvAlbumsCount: TextView
    private lateinit var tvArtistsCount: TextView
    private lateinit var btnScanLibrary: Button
    private lateinit var menuAdapter: LibraryMenuAdapter

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            scanMusicLibrary()
        } else {
            Toast.makeText(requireContext(), "Permission denied to access audio files", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_library, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        initViews(view)
        setupRecyclerView()
        setupClickListeners()
        observeViewModel()
        
        // Check permission on load
        checkAudioPermissionAndScan()
    }

    private fun initViews(view: View) {
        rvMenuItems = view.findViewById(R.id.rv_menu_items)
        tvSongsCount = view.findViewById(R.id.tv_songs_count)
        tvAlbumsCount = view.findViewById(R.id.tv_albums_count)
        tvArtistsCount = view.findViewById(R.id.tv_artists_count)
        btnScanLibrary = view.findViewById(R.id.btn_scan_library)
    }

    private fun setupRecyclerView() {
        menuAdapter = LibraryMenuAdapter { menuItem ->
            when (menuItem.type) {
                LibraryMenuItem.Type.SONGS -> {
                    // Navigate to songs fragment
                }
                LibraryMenuItem.Type.ALBUMS -> {
                    // Navigate to albums fragment
                }
                LibraryMenuItem.Type.ARTISTS -> {
                    // Navigate to artists fragment
                }
                LibraryMenuItem.Type.FAVORITES -> {
                    // Navigate to favorites fragment
                }
                LibraryMenuItem.Type.RECENTLY_ADDED -> {
                    // Navigate to recently added fragment
                }
                LibraryMenuItem.Type.MOST_PLAYED -> {
                    // Navigate to most played fragment
                }
            }
        }
        
        rvMenuItems.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = menuAdapter
        }
        
        // Setup menu items
        val menuItems = listOf(
            LibraryMenuItem(LibraryMenuItem.Type.SONGS, "All Songs", R.drawable.ic_music_note),
            LibraryMenuItem(LibraryMenuItem.Type.ALBUMS, "Albums", R.drawable.ic_album),
            LibraryMenuItem(LibraryMenuItem.Type.ARTISTS, "Artists", R.drawable.ic_artist),
            LibraryMenuItem(LibraryMenuItem.Type.FAVORITES, "Favorites", R.drawable.ic_favorite),
            LibraryMenuItem(LibraryMenuItem.Type.RECENTLY_ADDED, "Recently Added", R.drawable.ic_recent),
            LibraryMenuItem(LibraryMenuItem.Type.MOST_PLAYED, "Most Played", R.drawable.ic_trending)
        )
        menuAdapter.submitList(menuItems)
    }

    private fun setupClickListeners() {
        btnScanLibrary.setOnClickListener {
            checkAudioPermissionAndScan()
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.allSongs.collect { songs ->
                tvSongsCount.text = "${songs.size} Songs"
                updateAlbumsAndArtistsCount()
            }
        }
        
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.isLoading.collect { isLoading ->
                btnScanLibrary.isEnabled = !isLoading
                btnScanLibrary.text = if (isLoading) "Scanning..." else "Scan Music Library"
            }
        }
    }

    private fun checkAudioPermissionAndScan() {
        when {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.READ_MEDIA_AUDIO
            ) == PackageManager.PERMISSION_GRANTED -> {
                scanMusicLibrary()
            }
            shouldShowRequestPermissionRationale(Manifest.permission.READ_MEDIA_AUDIO) -> {
                Toast.makeText(requireContext(), "Audio permission is required to scan music library", Toast.LENGTH_LONG).show()
                requestPermissionLauncher.launch(Manifest.permission.READ_MEDIA_AUDIO)
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.READ_MEDIA_AUDIO)
            }
        }
    }

    private fun scanMusicLibrary() {
        try {
            viewModel.scanMusicLibrary()
        } catch (e: SecurityException) {
            Toast.makeText(requireContext(), "Permission denied. Please grant audio access permission.", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Error scanning music library: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun updateAlbumsAndArtistsCount() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.getAllAlbums().collect { albums ->
                tvAlbumsCount.text = "${albums.size} Albums"
            }
        }
        
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.getAllArtists().collect { artists ->
                tvArtistsCount.text = "${artists.size} Artists"
            }
        }
    }
}
