package com.txapp.musicplayer.ui

import android.content.Intent
import android.os.Bundle
import android.os.Build
import android.text.Editable
import android.text.Html
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.txapp.musicplayer.MusicApplication
import com.txapp.musicplayer.data.MusicRepository
import com.txapp.musicplayer.databinding.FragmentLibraryBinding
import com.txapp.musicplayer.model.Song
import com.txapp.musicplayer.service.MusicService
import com.txapp.musicplayer.ui.adapters.SongAdapter
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import com.txapp.musicplayer.util.txa

class LibraryFragment : Fragment() {

    private var _binding: FragmentLibraryBinding? = null
    private val binding get() = _binding!!

    private lateinit var songsAdapter: SongAdapter
    private lateinit var musicRepository: MusicRepository
    private var allSongs: List<Song> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLibraryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupTitle()
        setupRepository()
        setupAdapter()
        setupSearch()
        observeSongs()
    }

    private fun setupTitle() {
        val primaryColor = com.google.android.material.color.MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorPrimary)
        val hexColor = String.format("#%06X", 0xFFFFFF and primaryColor)
        val appName = "TXA <font color='$hexColor'>Music</font>"
        binding.appNameText.text = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            android.text.Html.fromHtml(appName, android.text.Html.FROM_HTML_MODE_LEGACY)
        } else {
            @Suppress("DEPRECATION")
            android.text.Html.fromHtml(appName)
        }
    }

    private fun setupRepository() {
        val app = requireActivity().application as MusicApplication
        musicRepository = MusicRepository(app.database, requireContext().contentResolver)
    }

    private fun setupAdapter() {
        songsAdapter = SongAdapter(isVertical = true) { song -> onSongClick(song) }
        binding.songsRecyclerView.adapter = songsAdapter
        updateLayoutManager()
    }

    private fun updateLayoutManager() {
        val gridSize = com.txapp.musicplayer.util.TXAPreferences.gridSize.value
        binding.songsRecyclerView.layoutManager = if (gridSize <= 1) {
            androidx.recyclerview.widget.LinearLayoutManager(requireContext())
        } else {
            androidx.recyclerview.widget.GridLayoutManager(requireContext(), gridSize)
        }
    }

    override fun onResume() {
        super.onResume()
        if (requireActivity().intent.getBooleanExtra("FOCUS_SEARCH", false)) {
            binding.searchEditText.requestFocus()
            val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.showSoftInput(binding.searchEditText, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
            requireActivity().intent.removeExtra("FOCUS_SEARCH")
        }
    }

    private fun setupSearch() {
        binding.searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterSongs(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun filterSongs(query: String) {
        val filtered = if (query.isEmpty()) {
            allSongs
        } else {
            allSongs.filter { 
                it.title.contains(query, ignoreCase = true) || 
                it.artist.contains(query, ignoreCase = true)
            }
        }
        songsAdapter.setSearchQuery(query)
        songsAdapter.submitList(filtered)
    }

    private fun observeSongs() {
        viewLifecycleOwner.lifecycleScope.launch {
            musicRepository.allSongs.collectLatest { songs ->
                allSongs = songs
                filterSongs(binding.searchEditText.text.toString())
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            com.txapp.musicplayer.util.TXAPreferences.gridSize.collect {
                updateLayoutManager()
            }
        }
    }

    private fun onSongClick(song: Song) {
        val context = requireContext()
        val activity = activity as? MainActivity
        val controller = activity?.getPlayerController()
        
        // Check if currently playing something
        val isCurrentlyPlaying = controller?.isPlaying == true || controller?.currentMediaItem != null
        
        if (isCurrentlyPlaying) {
            // Show dialog: Play Now or Add to Queue
            androidx.appcompat.app.AlertDialog.Builder(context)
                .setTitle(song.title)
                .setItems(arrayOf(
                    "txamusic_play_now".txa(),
                    "txamusic_add_to_queue".txa()
                )) { _, which ->
                    when (which) {
                        0 -> playSongInLibraryContext(song) // Play Now
                        1 -> enqueueSong(song) // Add to Queue
                    }
                }
                .show()
        } else {
            // Nothing playing, just play in context
            playSongInLibraryContext(song)
        }
    }
    
    private fun playSongInLibraryContext(song: Song) {
        val context = requireContext()
        // Use current visible list as context
        val currentVisibleList = songsAdapter.currentList
        if (currentVisibleList.isEmpty()) return
        
        val songIds = currentVisibleList.map { it.id }.toLongArray()
        val serviceIntent = Intent(context, MusicService::class.java).apply {
            action = MusicService.ACTION_PLAY_SONG_IN_CONTEXT
            putExtra(MusicService.EXTRA_SONG_ID, song.id)
            putExtra(MusicService.EXTRA_CONTEXT_SONG_IDS, songIds)
        }
        ContextCompat.startForegroundService(context, serviceIntent)
    }
    
    private fun enqueueSong(song: Song) {
        val context = requireContext()
        val serviceIntent = Intent(context, MusicService::class.java).apply {
            action = MusicService.ACTION_ENQUEUE_SONG
            putExtra(MusicService.EXTRA_SONG_ID, song.id)
        }
        ContextCompat.startForegroundService(context, serviceIntent)
        com.txapp.musicplayer.util.TXAToast.success(context, "txamusic_add_to_queue".txa())
    }

    fun updatePlayingState(songId: Long, isPlaying: Boolean) {
        if (::songsAdapter.isInitialized) {
            songsAdapter.updatePlayingState(songId, isPlaying)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
