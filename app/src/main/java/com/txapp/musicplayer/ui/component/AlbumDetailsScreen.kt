package com.txapp.musicplayer.ui.component

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import com.txapp.musicplayer.model.Album
import com.txapp.musicplayer.model.Song
import com.txapp.musicplayer.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumDetailsScreen(
    album: Album,
    moreAlbums: List<Album>,
    onBack: () -> Unit,
    onPlaySong: (Song, List<Song>) -> Unit,
    onPlayAll: (List<Song>) -> Unit,
    onShuffleAll: (List<Song>) -> Unit,
    onAlbumClick: (Long) -> Unit,
    currentSongId: Long = -1L
) {
    val accentColor = Color(android.graphics.Color.parseColor(TXAPreferences.currentAccent))

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Content
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            // Header
            item {
                AlbumHeader(album, accentColor, onPlayAll, onShuffleAll)
            }

            // Song List
            items(album.songs.size) { index ->
                val song = album.songs[index]
                SongItem(
                    song = song,
                    index = index + 1,
                    isCurrent = song.id == currentSongId,
                    accentColor = accentColor,
                    onClick = { onPlaySong(song, album.songs) }
                )
            }

            // More from Artist
            if (moreAlbums.isNotEmpty()) {
                item {
                    Text(
                        text = "txamusic_more_from_artist".txa(album.artistName),
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
                        moreAlbums.forEach { otherAlbum ->
                            OtherAlbumItem(otherAlbum, onAlbumClick)
                        }
                    }
                }
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
fun AlbumHeader(
    album: Album,
    accentColor: Color,
    onPlayAll: (List<Song>) -> Unit,
    onShuffleAll: (List<Song>) -> Unit
) {
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
            .padding(16.dp)
    ) {
        Spacer(modifier = Modifier.height(56.dp))
        
        // Album Art
        Box(
            modifier = Modifier
                .size(240.dp)
                .align(Alignment.CenterHorizontally)
                .clip(RoundedCornerShape(16.dp))
                .shadow(8.dp)
        ) {
            SubcomposeAsyncImage(
                model = MusicUtil.getAlbumArtUri(album.id),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            ) {
                val state = painter.state
                if (state is coil.compose.AsyncImagePainter.State.Loading || state is coil.compose.AsyncImagePainter.State.Error) {
                     DefaultAlbumArt(vibrantColor = accentColor)
                } else {
                    SubcomposeAsyncImageContent()
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Info
        Text(
            text = album.title,
            fontSize = 28.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 34.sp
        )
        Text(
            text = album.artistName,
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = "${album.songCount} ${"txamusic_media_songs".txa()} • ${if (album.year > 0) album.year else ""}",
            fontSize = 14.sp,
            color = Color.Gray
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = { onPlayAll(album.songs) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accentColor)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("txamusic_play_now".txa())
            }
            OutlinedButton(
                onClick = { onShuffleAll(album.songs) },
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
fun SongItem(
    song: Song,
    index: Int,
    isCurrent: Boolean,
    accentColor: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .background(if (isCurrent) accentColor.copy(alpha = 0.1f) else Color.Transparent),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Track number / index
        Text(
            text = index.toString().padStart(2, '0'),
            fontSize = 14.sp,
            color = if (isCurrent) accentColor else Color.Gray,
            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.width(32.dp)
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                fontSize = 16.sp,
                color = if (isCurrent) accentColor else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = song.artist,
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

@Composable
fun OtherAlbumItem(album: Album, onClick: (Long) -> Unit) {
    Column(
        modifier = Modifier
            .width(140.dp)
            .clickable { onClick(album.id) }
    ) {
        SubcomposeAsyncImage(
            model = MusicUtil.getAlbumArtUri(album.id),
            contentDescription = null,
            modifier = Modifier
                .size(140.dp)
                .clip(RoundedCornerShape(12.dp)),
            contentScale = ContentScale.Crop
        ) {
            val state = painter.state
            if (state is coil.compose.AsyncImagePainter.State.Loading || state is coil.compose.AsyncImagePainter.State.Error) {
                 DefaultAlbumArt(vibrantColor = MaterialTheme.colorScheme.primary)
            } else {
                SubcomposeAsyncImageContent()
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = album.title,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = if (album.year > 0) album.year.toString() else "",
            fontSize = 12.sp,
            color = Color.Gray
        )
    }
}
