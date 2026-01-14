package com.txapp.musicplayer.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.txapp.musicplayer.model.Song
import com.txapp.musicplayer.util.TXAFormat
import com.txapp.musicplayer.util.TXAPreferences
import com.txapp.musicplayer.util.TXAActiveMP3
import com.txapp.musicplayer.util.txa

class PlayingQueueFragment : Fragment() {

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
                    val queue = TXAActiveMP3.currentQueue.collectAsState().value
                    val currentIndex = TXAActiveMP3.currentQueueIndex.collectAsState().value
                    val currentPlayingSongId = TXAActiveMP3.currentPlayingSongId.collectAsState().value
                    val isPlaying = TXAActiveMP3.isPlaying.collectAsState().value

                    PlayingQueueScreen(
                        queue = queue,
                        currentIndex = currentIndex,
                        currentPlayingSongId = currentPlayingSongId,
                        isPlaying = isPlaying,
                        onBack = { findNavController().navigateUp() },
                        onSongClick = { index -> playAtIndex(index) },
                        onRemoveSong = { index -> removeFromQueue(index) },
                        onClearQueue = { clearQueue() }
                    )
                }
            }
        }
    }

    private fun playAtIndex(index: Int) {
        // Use broadcast to seek to specific queue position
        val intent = android.content.Intent("com.txapp.musicplayer.action.PLAY_QUEUE_INDEX")
        intent.putExtra("index", index)
        requireContext().sendBroadcast(intent)
    }

    private fun removeFromQueue(index: Int) {
        // For now, just show a toast - full implementation requires more backend work
        com.txapp.musicplayer.util.TXAToast.show(
            requireContext(), 
            "txamusic_removed_from_playlist".txa()
        )
    }

    private fun clearQueue() {
        // Use broadcast to stop playback
        val intent = android.content.Intent("com.txapp.musicplayer.action.STOP_PLAYBACK")
        requireContext().sendBroadcast(intent)
        findNavController().navigateUp()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayingQueueScreen(
    queue: List<Song>,
    currentIndex: Int,
    currentPlayingSongId: Long,
    isPlaying: Boolean,
    onBack: () -> Unit,
    onSongClick: (Int) -> Unit,
    onRemoveSong: (Int) -> Unit,
    onClearQueue: () -> Unit
) {
    val accentColor = Color(android.graphics.Color.parseColor(TXAPreferences.currentAccent))
    var showClearConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("txamusic_playing_queue".txa())
                        Text(
                            text = "${queue.size} ${"txamusic_media_songs".txa()}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "txamusic_btn_back".txa())
                    }
                },
                actions = {
                    if (queue.isNotEmpty()) {
                        IconButton(onClick = { showClearConfirm = true }) {
                            Icon(Icons.Default.ClearAll, contentDescription = "Clear Queue")
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (queue.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.AutoMirrored.Filled.QueueMusic,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "txamusic_queue_empty".txa(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(bottom = 100.dp)
            ) {
                // Now Playing Section Header
                item {
                    Text(
                        text = "txamusic_playing".txa(),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = accentColor,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                itemsIndexed(queue) { index, song ->
                    val isCurrent = song.id == currentPlayingSongId
                    val isActive = isCurrent && isPlaying
                    val isPast = index < currentIndex
                    val isUpcoming = index > currentIndex

                    QueueSongItem(
                        song = song,
                        index = index,
                        isActive = isActive,
                        isCurrent = isCurrent,
                        isPast = isPast,
                        onClick = { onSongClick(index) },
                        onRemove = { onRemoveSong(index) },
                        accentColor = accentColor
                    )

                    // Separator after current song
                    if (isCurrent && isUpcoming.not() && index < queue.size - 1) {
                        Text(
                            text = "txamusic_up_next".txa(),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }
    }

    // Clear Queue Confirmation
    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("txamusic_clear_queue".txa()) },
            text = { Text("txamusic_clear_queue_confirm".txa()) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearConfirm = false
                        onClearQueue()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("txamusic_btn_clear".txa())
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text("txamusic_btn_cancel".txa())
                }
            }
        )
    }
}

@Composable
private fun QueueSongItem(
    song: Song,
    index: Int,
    isActive: Boolean,
    isCurrent: Boolean,
    isPast: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    accentColor: Color
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val albumUri = remember(song.albumId) { com.txapp.musicplayer.util.MusicUtil.getAlbumArtUri(song.albumId) }

    val textColor = when {
        isCurrent -> accentColor
        isPast -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        else -> MaterialTheme.colorScheme.onSurface
    }
    val bgColor = when {
        isActive -> accentColor.copy(alpha = 0.12f)
        isCurrent -> accentColor.copy(alpha = 0.06f)
        else -> Color.Transparent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Index or Now Playing Indicator
        Box(
            modifier = Modifier.width(32.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isActive) {
                TXAActiveMP3.PlaylistNowPlayingIndicator(
                    barColor = accentColor,
                    maxHeight = 18.dp
                )
            } else {
                Text(
                    text = TXAFormat.format2Digits(index + 1),
                    color = if (isCurrent) accentColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(
                        alpha = if (isPast) 0.4f else 1f
                    ),
                    fontSize = 14.sp
                )
            }
        }

        // Album Art
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(albumUri)
                .crossfade(true)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
        )

        Spacer(modifier = Modifier.width(12.dp))

        // Song Info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title.ifBlank { "txamusic_unknown_title".txa() },
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = song.artist.ifBlank { "txamusic_unknown_artist".txa() },
                fontSize = 13.sp,
                color = if (isCurrent) accentColor.copy(alpha = 0.7f) 
                       else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (isPast) 0.4f else 1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Duration
        Text(
            text = TXAFormat.formatDurationHuman(song.duration),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (isPast) 0.4f else 1f)
        )

        // Remove Button
        IconButton(onClick = onRemove) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Remove",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
