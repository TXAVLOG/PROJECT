package com.txapp.musicplayer.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.txapp.musicplayer.model.Song
import com.txapp.musicplayer.ui.component.ArtistDetailsScreen
import androidx.media3.session.MediaController
import androidx.media3.common.Player
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.txapp.musicplayer.util.txa

class ArtistDetailsFragment : Fragment() {

    private val artistId: Long by lazy {
        arguments?.getLong("extra_artist_id") ?: -1L
    }

    private val artistName: String? by lazy {
        arguments?.getString("extra_artist_name")
    }

    private val viewModel: ArtistDetailsViewModel by viewModels {
        DetailViewModelFactory(requireActivity().application, artistId, artistName)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                MaterialTheme(
                    colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
                ) {
                    val artist by viewModel.artist.observeAsState()
                    val currentSongId = (activity as? MainActivity)?.nowPlayingState?.songId ?: -1L
                    
                    if (artist != null) {
                        ArtistDetailsScreen(
                            artist = artist!!,
                            onBack = { findNavController().navigateUp() },
                            onPlaySong = { song, list -> playSong(song, list) },
                            onPlayAll = { list -> playSongs(list, 0) },
                            onShuffleAll = { list -> shuffleSongs(list) },
                            onAlbumClick = { id -> navigateToAlbum(id) },
                            currentSongId = currentSongId,
                            onUpdateLogo = { bitmap -> updateArtistLogo(bitmap) }
                        )
                    } else {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                }
            }
        }
    }

    private fun playSong(song: Song, list: List<Song>) {
        val index = list.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
        playSongs(list, index)
    }

    private fun playSongs(list: List<Song>, index: Int) {
        (activity as? MainActivity)?.playSongs(list, index)
    }

    private fun shuffleSongs(list: List<Song>) {
        (activity as? MainActivity)?.playSongs(list, 0, shuffle = true)
    }

    private fun navigateToAlbum(id: Long) {
        val bundle = Bundle().apply { putLong("extra_album_id", id) }
        findNavController().navigate(com.txapp.musicplayer.R.id.albumDetailsFragment, bundle)
    }

    private fun updateArtistLogo(bitmap: android.graphics.Bitmap) {
        val name = artistName ?: return
        val mainActivity = activity as? MainActivity ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            val result = mainActivity.repository.updateArtistArtwork(requireContext(), name, bitmap)
            
            when (result) {
                is com.txapp.musicplayer.util.TXATagWriter.WriteResult.Success -> {
                    com.txapp.musicplayer.util.TXAToast.show(requireContext(), "txamusic_tag_saved".txa())
                    viewModel.loadArtist() 
                    mainActivity.updateNowPlayingState()
                }
                is com.txapp.musicplayer.util.TXATagWriter.WriteResult.PermissionRequired -> {
                    mainActivity.startArtistLogoUpdate(name, bitmap, result.intent)
                }
                else -> {
                    com.txapp.musicplayer.util.TXAToast.show(requireContext(), "txamusic_tag_save_failed".txa())
                }
            }
        }
    }
}
