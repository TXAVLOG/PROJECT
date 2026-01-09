package com.txapp.musicplayer.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import coil.compose.AsyncImage
import com.txapp.musicplayer.R
import com.txapp.musicplayer.model.Album
import com.txapp.musicplayer.util.MusicUtil
import com.txapp.musicplayer.util.TXAPreferences
import com.txapp.musicplayer.util.txa
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import com.txapp.musicplayer.ui.component.DefaultAlbumArt

class AlbumsFragment : Fragment() {

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
                    val sections by viewModel.homeSections.observeAsState(emptyList())
                    val albums = sections.find { it.homeSection == com.txapp.musicplayer.RECENT_ALBUMS }?.arrayList?.filterIsInstance<Album>() ?: emptyList()
                    
                    AlbumsScreen(
                        albums = albums,
                        onAlbumClick = { id -> navigateToAlbum(id) }
                    )
                }
            }
        }
    }

    private fun navigateToAlbum(id: Long) {
        val bundle = Bundle().apply { putLong("extra_album_id", id) }
        findNavController().navigate(R.id.albumDetailsFragment, bundle)
    }
}

@Composable
fun AlbumsScreen(
    albums: List<Album>,
    onAlbumClick: (Long) -> Unit
) {
    val gridSize by TXAPreferences.gridSize.collectAsState()
    
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
                text = "txamusic_albums".txa(),
                fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold,
                color = onSurfaceColor
            )
        }
        
        if (albums.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                Text(text = "txamusic_home_no_songs".txa(), color = Color.Gray)
            }
        } else {
            val windowSize = com.txapp.musicplayer.util.rememberWindowSize()
            val gridColumns = windowSize.albumGridColumns()
            
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
                items(albums) { album ->
                    AlbumGridItem(album, onAlbumClick, isLarge = false)
                }
            }
        }
    }
}

@Composable
fun AlbumGridItem(album: Album, onClick: (Long) -> Unit, isLarge: Boolean = false) {
    val context = androidx.compose.ui.platform.LocalContext.current
    
    if (isLarge) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .clickable { onClick(album.id) },
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    shadowElevation = 2.dp
                ) {
                    SubcomposeAsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(MusicUtil.getAlbumArtUri(album.id))
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    ) {
                         val state = painter.state
                         if (state is coil.compose.AsyncImagePainter.State.Loading || state is coil.compose.AsyncImagePainter.State.Error) {
                             DefaultAlbumArt(iconSize = 40.dp)
                         } else {
                             SubcomposeAsyncImageContent()
                         }
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = album.title,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = album.artistName,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (album.year > 0) {
                        Text(
                            text = album.year.toString(),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick(album.id) }
        ) {
            Surface(
                modifier = Modifier
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(12.dp)),
                shadowElevation = 4.dp
            ) {
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(MusicUtil.getAlbumArtUri(album.id))
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
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = album.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = album.artistName,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
