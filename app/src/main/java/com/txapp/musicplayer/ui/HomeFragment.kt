package com.txapp.musicplayer.ui

import com.txapp.musicplayer.ui.fragment.DetailListFragment

import android.app.SearchManager
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.txapp.musicplayer.R
import com.txapp.musicplayer.databinding.FragmentHomeBinding
import com.txapp.musicplayer.model.Song
import com.txapp.musicplayer.service.MusicService
import com.txapp.musicplayer.ui.component.HomeQuickActions
import com.txapp.musicplayer.ui.component.SuggestionCards
import com.txapp.musicplayer.ui.component.FavoritesSection
import com.txapp.musicplayer.util.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.*
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val _playingId = MutableStateFlow(-1L)
    private val viewModel: HomeViewModel by viewModels {
        MusicViewModelFactory(requireActivity().application)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupUI()
        setupQuickActions()
        setupLibraryHub()
        setupTopTracks()
        setupSuggestions()
        setupRecentlyAdded()
        setupFavorites()
        setupObservers()
        startClock()
    }

    override fun onResume() {
        super.onResume()
        refreshLists()
    }

    private fun setupTopTracks() {
        binding.topTracksComposeView.setContent {
            androidx.compose.material3.MaterialTheme(
                colorScheme = if (androidx.compose.foundation.isSystemInDarkTheme())
                    androidx.compose.material3.darkColorScheme()
                else
                    androidx.compose.material3.lightColorScheme()
            ) {
                val topTracks by viewModel.topTracks.observeAsState(emptyList())
                val playingId by _playingId.collectAsState()

                com.txapp.musicplayer.ui.component.TopTracksSection(
                    songs = topTracks,
                    onSongClick = { song, allSongs -> handleSongClick(song, allSongs) },
                    onPlayAllClick = {
                        if (topTracks.isNotEmpty()) {
                            (activity as? MainActivity)?.playSongs(topTracks, 0)
                        }
                    },
                    playingId = playingId
                )
            }
        }
    }

    private fun startClock() {
        viewLifecycleOwner.lifecycleScope.launch {
            while (true) {
                val timeStr = TXAFormat.formatFullTime(System.currentTimeMillis())
                _binding?.digitalClock?.text = timeStr
                updateGreeting()
                kotlinx.coroutines.delay(1000)
            }
        }
    }

    private lateinit var homeAdapter: com.txapp.musicplayer.ui.adapters.HomeAdapter

    // ...

    private fun setupUI() {
        updateGreeting()

        val welcomeText = "txamusic_home_greeting".txa()
        if (TXADeviceInfo.isEmulator()) {
            val emulatorText = "txamusic_tag_emulator".txa()
            val emulatorTag = " <font color='#FF5722'><b>$emulatorText</b></font>"
            binding.greetingText.text =
                android.text.Html.fromHtml(welcomeText + emulatorTag, android.text.Html.FROM_HTML_MODE_LEGACY)
        } else {
            binding.greetingText.text = welcomeText
        }

        binding.searchPlaceholder.text = "txamusic_action_search".txa()

        // Search card click
        binding.searchCard.setOnClickListener {
            (activity as? MainActivity)?.let {
                it.switchToLibrary(focusSearch = true)
            }
        }

        // Settings button click
        binding.settingsBtn.setOnClickListener {
            findNavController().navigate(R.id.settings_fragment)
        }

        // Refresh button click
        binding.refreshBtn.setOnClickListener {
            (activity as? MainActivity)?.triggerMediaStoreScan()
        }

        // HomeAdapter
        homeAdapter =
            com.txapp.musicplayer.ui.adapters.HomeAdapter(requireActivity() as androidx.appcompat.app.AppCompatActivity)
        binding.recyclerView.apply {
            adapter = homeAdapter
            layoutManager = androidx.recyclerview.widget.LinearLayoutManager(requireContext())
        }

        viewModel.homeSections.observe(viewLifecycleOwner) { sections ->
            homeAdapter.swapData(sections)
        }
    }

    // ...

    fun updatePlayingState(songId: Long, isPlaying: Boolean) {
        _playingId.value = songId
        if (::homeAdapter.isInitialized) {
            homeAdapter.updatePlayingState(songId, isPlaying)
        }
    }

    fun refreshLists() {
        viewModel.refreshFavorites()
        viewModel.refreshLastAdded()
        viewModel.refreshTopTracks()
    }

    private fun setupQuickActions() {
        binding.quickActionsComposeView.setContent {
            androidx.compose.material3.MaterialTheme(
                colorScheme = if (androidx.compose.foundation.isSystemInDarkTheme())
                    androidx.compose.material3.darkColorScheme()
                else
                    androidx.compose.material3.lightColorScheme()
            ) {
                HomeQuickActions(
                    onHistoryClick = { navigateToDetailList(DetailListFragment.HISTORY_PLAYLIST) },
                    onLastAddedClick = { navigateToDetailList(DetailListFragment.LAST_ADDED_PLAYLIST) },
                    onTopPlayedClick = { navigateToDetailList(DetailListFragment.TOP_PLAYED_PLAYLIST) }
                )
            }
        }
    }

    private fun setupLibraryHub() {
        binding.libraryHubComposeView.setContent {
            androidx.compose.material3.MaterialTheme(
                colorScheme = if (androidx.compose.foundation.isSystemInDarkTheme())
                    androidx.compose.material3.darkColorScheme()
                else
                    androidx.compose.material3.lightColorScheme()
            ) {
                com.txapp.musicplayer.ui.component.LibraryHubSection(
                    onAlbumClick = { findNavController().navigate(R.id.action_album) },
                    onArtistClick = { findNavController().navigate(R.id.action_artist) },
                    onPlaylistClick = { findNavController().navigate(R.id.action_playlist) }
                )
            }
        }
    }

    private fun setupSuggestions() {
        binding.suggestionsComposeView.setContent {
            val suggestions = androidx.compose.runtime.remember {
                androidx.compose.runtime.mutableStateOf(emptyList<Song>())
            }

            androidx.compose.runtime.LaunchedEffect(Unit) {
                viewModel.suggestions.observe(viewLifecycleOwner) { songs ->
                    suggestions.value = songs
                }
            }

            androidx.compose.material3.MaterialTheme(
                colorScheme = if (androidx.compose.foundation.isSystemInDarkTheme())
                    androidx.compose.material3.darkColorScheme()
                else
                    androidx.compose.material3.lightColorScheme()
            ) {
                val playingId by _playingId.collectAsState()
                SuggestionCards(
                    songs = suggestions.value,
                    onSongClick = { song -> playSuggestion(song, suggestions.value) },
                    onPlayAllClick = { playAllSuggestions(suggestions.value) },
                    onRefreshClick = { viewModel.refreshSuggestions() },
                    playingId = playingId
                )
            }
        }
    }

    private fun setupRecentlyAdded() {
        binding.recentlyAddedComposeView.setContent {
            androidx.compose.material3.MaterialTheme(
                colorScheme = if (androidx.compose.foundation.isSystemInDarkTheme())
                    androidx.compose.material3.darkColorScheme()
                else
                    androidx.compose.material3.lightColorScheme()
            ) {
                val lastAdded by viewModel.lastAdded.observeAsState(emptyList())
                val playingId by _playingId.collectAsState()

                com.txapp.musicplayer.ui.component.RecentlyAddedSection(
                    songs = lastAdded,
                    onSongClick = { song, allSongs -> handleSongClick(song, allSongs) },
                    onPlayAllClick = {
                        if (lastAdded.isNotEmpty()) {
                            (activity as? MainActivity)?.playSongs(lastAdded, 0)
                        }
                    },
                    playingId = playingId
                )
            }
        }
    }

    private fun setupFavorites() {
        // Set Favorites Title
        binding.favoriteTitle.text = "txamusic_favorites".txa()

        binding.favoritesComposeView.setContent {
            androidx.compose.material3.MaterialTheme(
                colorScheme = if (androidx.compose.foundation.isSystemInDarkTheme())
                    androidx.compose.material3.darkColorScheme()
                else
                    androidx.compose.material3.lightColorScheme()
            ) {
                val favorites by viewModel.favoriteSongs.observeAsState(emptyList())
                val playingId by _playingId.collectAsState()
                FavoritesSection(
                    favorites = favorites,
                    onSongClick = { song, allFavorites -> playFavorite(song, allFavorites) },
                    onPlayAllClick = { playAllFavorites() },
                    playingId = playingId
                )
            }
        }
    }

    private fun playFavorite(song: Song, allFavorites: List<Song>) {
        handleSongClick(song, allFavorites)
    }

    private fun playAllFavorites() {
        viewModel.favoriteSongs.value?.let { songs ->
            if (songs.isNotEmpty()) {
                (activity as? MainActivity)?.playSongs(songs, 0)
            }
        }
    }

    private fun navigateToDetailList(type: Int) {
        val bundle = Bundle().apply { putInt("type", type) }
        findNavController().navigate(R.id.detailListFragment, bundle)
    }


    private fun playSuggestion(song: Song, allSuggestions: List<Song>) {
        handleSongClick(song, allSuggestions)
    }

    private fun handleSongClick(song: Song, albumSongs: List<Song>) {
        val mainActivity = activity as? MainActivity ?: return
        val controller = mainActivity.getPlayerController()

        if (controller != null && controller.currentMediaItem != null) {
            com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle("txamusic_play_options_title".txa())
                .setMessage("txamusic_play_options_desc".txa())
                .setPositiveButton("txamusic_play_now".txa()) { _, _ ->
                    val index = albumSongs.indexOf(song).coerceAtLeast(0)
                    mainActivity.playSongs(albumSongs, index)
                }
                .setNegativeButton("txamusic_add_to_queue".txa()) { _, _ ->
                    mainActivity.addSongsToQueue(listOf(song))
                }
                .setNeutralButton("txamusic_btn_cancel".txa(), null)
                .show()
        } else {
            val index = albumSongs.indexOf(song).coerceAtLeast(0)
            mainActivity.playSongs(albumSongs, index)
        }
    }

    private fun playAllSuggestions(suggestions: List<Song>) {
        if (suggestions.isNotEmpty()) {
            (activity as? MainActivity)?.playSongs(suggestions, 0)
        }
    }

    private fun updateGreeting() {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val greetingKey = when (hour) {
            in 0..11 -> "txamusic_home_greeting_day"
            in 12..16 -> "txamusic_home_greeting_afternoon"
            else -> "txamusic_home_greeting_evening"
        }

        var greeting: CharSequence = greetingKey.txa()

        if (TXADeviceInfo.isEmulator()) {
            val emulatorText = "txamusic_tag_emulator".txa()
            // Use HTML for color
            val emulatorTag = " <font color='#FF5722'><b>$emulatorText</b></font>"
            greeting =
                android.text.Html.fromHtml(greeting.toString() + emulatorTag, android.text.Html.FROM_HTML_MODE_LEGACY)
        }

        _binding?.greetingText?.text = greeting
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            while (true) {
                if (!isAdded) break
                val currentActivity = activity
                if (currentActivity is com.txapp.musicplayer.ui.MainActivity) {
                    val controller = currentActivity.getPlayerController()
                    if (controller != null) {
                        val currentId = controller.currentMediaItem?.mediaId?.toLongOrNull() ?: -1L
                        val isPlaying = controller.isPlaying
                        updatePlayingState(currentId, isPlaying)
                    }
                }
                delay(1000)
            }
        }
    }


    fun setSharedAxisXTransitions() {
        exitTransition = com.google.android.material.transition.MaterialSharedAxis(
            com.google.android.material.transition.MaterialSharedAxis.X,
            true
        ).addTarget(androidx.coordinatorlayout.widget.CoordinatorLayout::class.java)
        reenterTransition = com.google.android.material.transition.MaterialSharedAxis(
            com.google.android.material.transition.MaterialSharedAxis.X,
            false
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
