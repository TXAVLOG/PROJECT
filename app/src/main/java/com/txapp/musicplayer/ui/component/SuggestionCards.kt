package com.txapp.musicplayer.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.ui.res.painterResource
import com.txapp.musicplayer.R
import com.txapp.musicplayer.model.Song
import com.txapp.musicplayer.util.MusicUtil
import com.txapp.musicplayer.util.TXAPreferences
import com.txapp.musicplayer.util.txa
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import com.txapp.musicplayer.ui.component.DefaultAlbumArt

//cms

@Composable
fun SuggestionCards(
    songs: List<Song>,
    onSongClick: (Song) -> Unit,
    onPlayAllClick: () -> Unit,
    onRefreshClick: () -> Unit,
    playingId: Long = -1L
) {
    if (songs.isEmpty()) return
    
    val accentColor = Color(android.graphics.Color.parseColor(TXAPreferences.currentAccent))
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "txamusic_home_suggestion_title".txa(),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = onRefreshClick) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    tint = accentColor
                )
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        val screenWidth = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp
        Row(
            modifier = Modifier
                .widthIn(max = screenWidth)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // "New Music Mix" Card (Uniform size now)
            SuggestionMixCard(
                onClick = onPlayAllClick, 
                accentColor = accentColor
            )
            
            // Song Cards
            songs.take(10).forEach { song ->
                SuggestionCard(
                    song = song,
                    onClick = { onSongClick(song) },
                    isPlaying = song.id == playingId
                )
            }
        }
    }
}

@Composable
private fun SuggestionMixCard(
    onClick: () -> Unit,
    accentColor: Color
) {
    Card(
        modifier = Modifier
            .width(140.dp)
            .aspectRatio(1f)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = accentColor.copy(alpha = 0.15f))
    ) {
        Box(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "txamusic_new_music_mix".txa(),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = accentColor,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun SuggestionCard(
    song: Song,
    onClick: () -> Unit,
    isPlaying: Boolean = false
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Column(
        modifier = Modifier
            .width(140.dp)
            .clickable(onClick = onClick)
    ) {
        Card(
            modifier = Modifier
                .width(140.dp)
                .aspectRatio(1f)
                .clip(RoundedCornerShape(16.dp)),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
             SubcomposeAsyncImage(
                model = ImageRequest.Builder(context)
                    .data(MusicUtil.getAlbumArtUri(song.albumId))
                    .crossfade(true)
                    .build(),
                contentDescription = song.title,
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
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = song.title,
            fontSize = 14.sp,
            fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Medium,
            color = if (isPlaying) MaterialTheme.colorScheme.primary else Color.Unspecified,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
        Text(
            text = song.artist,
            fontSize = 12.sp,
            color = Color.Gray,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
    }
}
