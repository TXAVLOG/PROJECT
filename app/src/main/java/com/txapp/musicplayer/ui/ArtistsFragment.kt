package com.txapp.musicplayer.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import coil.compose.AsyncImage
import com.txapp.musicplayer.R
import com.txapp.musicplayer.model.Artist
import com.txapp.musicplayer.util.MusicUtil
import com.txapp.musicplayer.util.TXAPreferences
import com.txapp.musicplayer.util.txa
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import com.txapp.musicplayer.ui.component.DefaultAlbumArt

class ArtistsFragment : Fragment() {

    private val viewModel: HomeViewModel by viewModels {
        MusicViewModelFactory(requireActivity().application)
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
                    val sections = viewModel.homeSections.observeAsState(emptyList()).value
                    val artists = sections.find { it.homeSection == com.txapp.musicplayer.RECENT_ARTISTS }?.arrayList?.filterIsInstance<Artist>() ?: emptyList()
                    
                    ArtistsScreen(
                        artists = artists,
                        onArtistClick = { id -> navigateToArtist(id) }
                    )
                }
            }
        }
    }

    private fun navigateToArtist(id: Long) {
        val bundle = Bundle().apply { putLong("extra_artist_id", id) }
        findNavController().navigate(R.id.artistDetailsFragment, bundle)
    }
}

@Composable
fun ArtistsScreen(
    artists: List<Artist>,
    onArtistClick: (Long) -> Unit
) {
    val windowSize = com.txapp.musicplayer.util.rememberWindowSize()
    val gridColumns = windowSize.artistGridColumns()
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface


    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "txamusic_artists".txa(),
                fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold,
                color = onSurfaceColor
            )
        }
        
        if (artists.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                Text(text = "txamusic_home_no_songs".txa(), color = Color.Gray)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(gridColumns),
                contentPadding = PaddingValues(
                    start = windowSize.contentPadding,
                    end = windowSize.contentPadding,
                    bottom = 100.dp,
                    top = 8.dp
                ),
                horizontalArrangement = Arrangement.spacedBy(windowSize.itemSpacing),
                verticalArrangement = Arrangement.spacedBy(windowSize.itemSpacing * 1.5f),
                modifier = Modifier.fillMaxWidth().weight(1f)
            ) {
                items(artists) { artist ->
                    ArtistGridItem(
                        artist = artist,
                        onClick = { onArtistClick(artist.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun ArtistGridItem(
    artist: Artist,
    onClick: (Long) -> Unit
) {
    val artistImageUrl by produceState<String?>(initialValue = null, artist.name) {
        if (TXAPreferences.isAutoDownloadImagesEnabled) {
            value = com.txapp.musicplayer.network.ArtistImageService.getArtistImageUrl(artist.name)
        }
    }
    val imageSource = remember(artist.name, artistImageUrl) {
        artistImageUrl ?: MusicUtil.getAlbumArtUri(artist.safeGetFirstAlbum().id)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick(artist.id) },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier
                .aspectRatio(1f)
                .clip(CircleShape),
            shadowElevation = 4.dp,
            shape = CircleShape
        ) {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                    .data(imageSource)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            ) {
                 val state = painter.state
                 if (state is coil.compose.AsyncImagePainter.State.Loading || state is coil.compose.AsyncImagePainter.State.Error) {
                     DefaultAlbumArt(iconSize = 48.dp)
                 } else {
                     SubcomposeAsyncImageContent()
                 }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = artist.name,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "${artist.albumCount} ${"txamusic_albums".txa()}",
            fontSize = 12.sp,
            color = Color.Gray
        )
    }
}

