package com.txapp.musicplayer.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.txapp.musicplayer.model.Playlist
import com.txapp.musicplayer.util.txa

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToPlaylistSheet(
    playlists: List<Playlist>,
    containingPlaylistIds: List<Long>,
    onPlaylistSelected: (Long) -> Unit,
    onCreatePlaylist: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "txamusic_add_to_playlist".txa(),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(16.dp)
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp) // Limit height to prevent infinite constraints
            ) {
                item {
                    ListItem(
                        headlineContent = { Text("txamusic_btn_create_playlist".txa()) },
                        leadingContent = {
                            Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.primary)
                        },
                        modifier = Modifier.clickable {
                            // Keep 'create' dismissing the sheet or handle dialog
                            onCreatePlaylist()
                        }
                    )
                }

                items(playlists) { playlist ->
                    // Logic: playing playlist vs containing playlist
                    // We prioritize showing "Contains song" status for this feature request
                    val currentPlaylistId by com.txapp.musicplayer.util.TXAActiveMP3.currentPlaylistId.collectAsState()
                    val isPlayingPlaylist = playlist.id == currentPlaylistId
                    
                    val containsSong = containingPlaylistIds.contains(playlist.id)
                    
                    ListItem(
                        headlineContent = { 
                            Text(
                                playlist.name,
                                color = if (containsSong) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                fontWeight = if (containsSong) FontWeight.Bold else FontWeight.Normal
                            ) 
                        },
                        supportingContent = { 
                            val statusText = if (isPlayingPlaylist) " • Playing Now" else ""
                            Text("${playlist.songCount} ${"txamusic_media_songs".txa()}$statusText") 
                        },
                        leadingContent = {
                            if (containsSong) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Added",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            } else if (isPlayingPlaylist) {
                                com.txapp.musicplayer.util.TXAActiveMP3.PlaylistNowPlayingIndicator(
                                    modifier = Modifier.size(24.dp),
                                    maxHeight = 24.dp
                                )
                            } else {
                                Icon(TXAIcons.PlaylistAdd, null)
                            }
                        },
                        trailingContent = {
                            if (containsSong) {
                                Text(
                                    "txamusic_playlist_added_status".txa(), 
                                    style = MaterialTheme.typography.labelSmall, 
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        },
                        modifier = Modifier
                            .clickable {
                                onPlaylistSelected(playlist.id)
                            }
                            .then(
                                if (containsSong) Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)) 
                                else Modifier
                            )
                    )
                }
                
                if (playlists.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "txamusic_playlist_empty".txa(),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
