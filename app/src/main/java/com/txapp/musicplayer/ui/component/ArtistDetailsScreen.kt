package com.txapp.musicplayer.ui.component

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import com.txapp.musicplayer.ui.component.DefaultAlbumArt
import com.txapp.musicplayer.model.Artist
import com.txapp.musicplayer.model.Album
import com.txapp.musicplayer.model.Song
import com.txapp.musicplayer.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistDetailsScreen(
    artist: Artist,
    onBack: () -> Unit,
    onPlaySong: (Song, List<Song>) -> Unit,
    onPlayAll: (List<Song>) -> Unit,
    onShuffleAll: (List<Song>) -> Unit,
    onAlbumClick: (Long) -> Unit,
    currentSongId: Long = -1L
) {
    val accentColor = Color(android.graphics.Color.parseColor(TXAPreferences.currentAccent))

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            // Header
            item {
                ArtistHeader(artist, accentColor, onPlayAll, onShuffleAll)
            }

            // Albums Section
            item {
                Text(
                    text = "txamusic_albums".txa(),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(16.dp)
                )
            }
            item {
            val screenWidth = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp
            Row(
                modifier = Modifier
                    .widthIn(max = screenWidth)
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    artist.sortedAlbums.forEach { album ->
                        ArtistAlbumItem(album, onAlbumClick)
                    }
                }
            }

            // Songs Section
            item {
                Text(
                    text = "txamusic_media_songs".txa(),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp)
                )
            }
            items(artist.songs.size) { index ->
                val song = artist.songs[index]
                ArtistSongItem(
                    song = song,
                    index = index + 1,
                    isCurrent = song.id == currentSongId,
                    accentColor = accentColor,
                    onClick = { onPlaySong(song, artist.songs) }
                )
            }
        }

        // Top Bar
        TopAppBar(
            title = { },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                navigationIconContentColor = Color.White
            )
        )
    }
}

@Composable
fun ArtistHeader(
    artist: Artist,
    accentColor: Color,
    onPlayAll: (List<Song>) -> Unit,
    onShuffleAll: (List<Song>) -> Unit
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(accentColor.copy(alpha = 0.5f), Color.Transparent),
                    startY = 0f,
                    endY = 500f
                )
            )
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(56.dp))

        // Artist Image (Network search with fallback)
        val artistImageUrl by produceState<String?>(initialValue = null, artist.name) {
            if (TXAPreferences.isAutoDownloadImagesEnabled) {
                value = com.txapp.musicplayer.network.ArtistImageService.getArtistImageUrl(artist.name)
            }
        }
        val imageSource = remember(artist.name, artistImageUrl) {
            artistImageUrl ?: MusicUtil.getAlbumArtUri(artist.safeGetFirstAlbum().id)
        }

        Box(
            modifier = Modifier
                .size(180.dp)
                .clip(CircleShape)
                .background(Color.Gray.copy(alpha = 0.2f))
                .border(4.dp, accentColor.copy(alpha = 0.3f), CircleShape)
                .clickable {
                    // TODO: Add manual search/pick image feature later if needed
                }
        ) {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(context)
                    .data(imageSource)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            ) {
                 val state = painter.state
                 if (state is coil.compose.AsyncImagePainter.State.Loading || state is coil.compose.AsyncImagePainter.State.Error) {
                     DefaultAlbumArt()
                 } else {
                     SubcomposeAsyncImageContent()
                 }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = artist.name,
            fontSize = 32.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "${artist.albumCount} ${"txamusic_albums".txa()} • ${artist.songCount} ${"txamusic_media_songs".txa()}",
            fontSize = 16.sp,
            color = Color.Gray,
            fontWeight = FontWeight.Medium
        )

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = { onPlayAll(artist.songs) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accentColor)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("txamusic_play_now".txa())
            }
            OutlinedButton(
                onClick = { onShuffleAll(artist.songs) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, accentColor)
            ) {
                Icon(Icons.Default.Shuffle, contentDescription = null, tint = accentColor)
                Spacer(modifier = Modifier.width(8.dp))
                Text("txamusic_shuffle_on".txa(), color = accentColor)
            }
        }
    }
}

@Composable
fun ArtistAlbumItem(album: Album, onClick: (Long) -> Unit) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .width(160.dp)
            .clickable { onClick(album.id) }
    ) {
        SubcomposeAsyncImage(
            model = ImageRequest.Builder(context)
                .data(MusicUtil.getAlbumArtUri(album.id))
                .crossfade(true)
                .build(),
            contentDescription = null,
            modifier = Modifier
                .size(160.dp)
                .clip(RoundedCornerShape(16.dp)),
            contentScale = ContentScale.Crop
        ) {
             val state = painter.state
             if (state is coil.compose.AsyncImagePainter.State.Loading || state is coil.compose.AsyncImagePainter.State.Error) {
                 DefaultAlbumArt()
             } else {
                 SubcomposeAsyncImageContent()
             }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = album.title,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = album.year.toString(),
            fontSize = 12.sp,
            color = Color.Gray
        )
    }
}

@Composable
fun ArtistSongItem(
    song: Song,
    index: Int,
    isCurrent: Boolean,
    accentColor: Color,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .background(if (isCurrent) accentColor.copy(alpha = 0.1f) else Color.Transparent),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Index
        Text(
            text = index.toString().padStart(2, '0'),
            fontSize = 14.sp,
            color = if (isCurrent) accentColor else Color.Gray,
            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.width(32.dp)
        )

        SubcomposeAsyncImage(
            model = ImageRequest.Builder(context)
                .data(MusicUtil.getAlbumArtUri(song.albumId))
                .crossfade(true)
                .build(),
            contentDescription = null,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop
        ) {
             val state = painter.state
             if (state is coil.compose.AsyncImagePainter.State.Loading || state is coil.compose.AsyncImagePainter.State.Error) {
                 DefaultAlbumArt()
             } else {
                 SubcomposeAsyncImageContent()
             }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                fontSize = 16.sp,
                color = if (isCurrent) accentColor else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = song.album,
                fontSize = 12.sp,
                color = if (isCurrent) accentColor.copy(alpha = 0.7f) else Color.Gray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        
        if (isCurrent) {
            Icon(
                Icons.Default.PlayArrow,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(20.dp).padding(end = 8.dp)
            )
        }

        Text(
            text = TXAFormat.formatDuration(song.duration),
            fontSize = 12.sp,
            color = if (isCurrent) accentColor else Color.Gray
        )
    }
}
