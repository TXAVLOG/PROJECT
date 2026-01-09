package com.txapp.musicplayer.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import androidx.compose.foundation.Image
import com.txapp.musicplayer.R
import com.txapp.musicplayer.model.Song
import com.txapp.musicplayer.util.txa

/**
 * Recently Added Section - Hiển thị các bài hát mới nhất vừa thêm vào linh lực (thư viện)
 */
@Composable
fun RecentlyAddedSection(
    songs: List<Song>,
    onSongClick: (Song, List<Song>) -> Unit,
    onPlayAllClick: () -> Unit,
    modifier: Modifier = Modifier,
    playingId: Long = -1L
) {
    if (songs.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth()) {
        // Title
        Text(
            text = "txamusic_home_recent_added".txa(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 20.dp, bottom = 4.dp),
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        
        // Header actions
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onPlayAllClick) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("txamusic_action_play_all".txa())
            }
        }

        // Horizontal Scrollable List - Cap width with screen width to avoid infinite constraints crash on some devices
        val screenWidth = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp
        LazyRow(
            modifier = Modifier.widthIn(max = screenWidth),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            itemsIndexed(songs) { index, song ->
                RecentlyAddedCard(
                    song = song,
                    index = index + 1,
                    onClick = { onSongClick(song, songs) },
                    isPlaying = song.id == playingId
                )
            }
        }
    }
}

@Composable
private fun RecentlyAddedCard(
    song: Song,
    index: Int,
    onClick: () -> Unit,
    isPlaying: Boolean = false
) {
    val context = LocalContext.current
    val albumUri = if (song.albumId != -1L) {
        android.content.ContentUris.withAppendedId(
            android.net.Uri.parse("content://media/external/audio/albumart"),
            song.albumId
        )
    } else {
        song.data
    }
    
    val cardModifier = if (isPlaying) {
        Modifier
            .width(140.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp)
            // Active State: Border logic simulating TXAActiveMP3 styling
            .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
            .padding(4.dp) // Inner padding to separate border
    } else {
        Modifier
            .width(140.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp)
    }

    Column(modifier = cardModifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp))
        ) {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(context)
                    .data(albumUri)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            ) {
                val state = painter.state
                if (state is coil.compose.AsyncImagePainter.State.Loading || state is coil.compose.AsyncImagePainter.State.Error) {
                     DefaultAlbumArt(vibrantColor = MaterialTheme.colorScheme.primary)
                } else {
                    SubcomposeAsyncImageContent()
                }
            }

            if (isPlaying) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center
                ) {
                      Image(
                         painter = painterResource(id = R.drawable.ic_launcher),
                         contentDescription = "Playing",
                         modifier = Modifier.size(32.dp)
                      )
                }
            } else {
                // Show index only if not playing (avoid clutter) or always? User said "add number at start".
                // Overlay Number Badge
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .size(24.dp)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = index.toString(),
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Song Info
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = song.title.ifBlank { "txamusic_unknown_title".txa() },
            color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            fontSize = 14.sp,
            fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = song.artist.ifBlank { "txamusic_unknown_artist".txa() },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
