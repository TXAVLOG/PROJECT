package com.txapp.musicplayer.ui.component

import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import com.txapp.musicplayer.R
import com.txapp.musicplayer.model.Song
import com.txapp.musicplayer.util.TXAFormat
import com.txapp.musicplayer.util.txa

/**
 * Top Tracks Section - Hiển thị danh sách bài hát nghe nhiều nhất
 */
@Composable
fun TopTracksSection(
    songs: List<Song>,
    onSongClick: (Song, List<Song>) -> Unit,
    onPlayAllClick: () -> Unit,
    modifier: Modifier = Modifier,
    playingId: Long = -1L
) {
    if (songs.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f),
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                        )
                    )
                )
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Filled.Star,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "txamusic_top_played_empty".txa(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp
                )
            }
        }
        return
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // Title
        Text(
            text = "txamusic_home_top_tracks_title".txa(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
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
            itemsIndexed(songs.take(15)) { index, song ->
                TopTrackCard(
                    song = song,
                    rank = index + 1,
                    onClick = { onSongClick(song, songs) },
                    isPlaying = song.id == playingId
                )
            }
        }
    }
}

@Composable
private fun TopTrackCard(
    song: Song,
    rank: Int,
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
    
    // Determine Medal Color
    val medalColor = when (rank) {
        1 -> Color(0xFFFFD700) // Gold
        2 -> Color(0xFFC0C0C0) // Silver
        3 -> Color(0xFFCD7F32) // Bronze
        else -> MaterialTheme.colorScheme.primary
    }

    val containerColor = if (isPlaying) 
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) 
    else 
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        
    val cardModifier = if (isPlaying) {
        Modifier
            .width(160.dp)
            .clickable(onClick = onClick)
            .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp))
    } else {
        Modifier
            .width(160.dp)
            .clickable(onClick = onClick)
    }

    Card(
        modifier = cardModifier,
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isPlaying) 8.dp else 2.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
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

                // Playing Overlay
                if (isPlaying) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.4f)),
                        contentAlignment = Alignment.Center
                    ) {
                        // Falling Petals or Active Animation Placeholder
                        // For now detailed Active Indicator
                        Image(
                            painter = painterResource(id = R.drawable.ic_launcher),
                            contentDescription = "Playing",
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }
                
                // MEDAL / RANKING
                if (rank <= 3) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                            .size(36.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // Draw tintable medal
                        Image(
                            painter = painterResource(id = R.drawable.ic_txa_medal),
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(medalColor),
                            modifier = Modifier.fillMaxSize()
                        )
                        // Rank Number embedded
                        Text(
                            text = rank.toString(),
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.offset(y = (-2).dp) // Adjust based on icon shape
                        )
                    }
                } else {
                    // Standard Rank Badge
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                            .size(28.dp)
                            .background(Color.Black.copy(alpha = 0.6f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = rank.toString(),
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Play Count Badge (Top End)
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "${TXAFormat.format2Digits(song.playCount.toLong())} 🎧", // Use format2Digits for play count
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Song Info
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                Text(
                    text = song.title.ifBlank { "txamusic_unknown_title".txa() },
                    color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = song.artist.ifBlank { "txamusic_unknown_artist".txa() },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
