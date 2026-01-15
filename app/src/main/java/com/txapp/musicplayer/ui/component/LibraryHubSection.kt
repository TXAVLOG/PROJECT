package com.txapp.musicplayer.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.txapp.musicplayer.util.TXAPreferences
import com.txapp.musicplayer.util.txa

@Composable
fun LibraryHubSection(
    onAlbumClick: () -> Unit,
    onArtistClick: () -> Unit,
    onPlaylistClick: () -> Unit
) {
    val accentColor = Color(android.graphics.Color.parseColor(TXAPreferences.currentAccent))
    
    val hubItems = listOf(
        HubItemData("txamusic_albums".txa(), Icons.Default.Album, onAlbumClick),
        HubItemData("txamusic_artists".txa(), Icons.Default.Person, onArtistClick),
        HubItemData("txamusic_playlists".txa(), Icons.AutoMirrored.Filled.PlaylistPlay, onPlaylistClick)
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        Text(
            text = "txamusic_library".txa(),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
        )
        
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            items(hubItems) { item ->
                HubIconItem(
                    data = item,
                    accentColor = accentColor
                )
            }
        }
    }
}

@Composable
private fun HubIconItem(
    data: HubItemData,
    accentColor: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(70.dp)
            .clickable(onClick = data.onClick)
    ) {
        Surface(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape),
            color = accentColor.copy(alpha = 0.12f),
            shape = CircleShape
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = data.icon,
                    contentDescription = data.label,
                    tint = accentColor,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = data.label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private data class HubItemData(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit
)
