package com.txapp.musicplayer.ui.component

// cms

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.txapp.musicplayer.model.Song
import com.txapp.musicplayer.util.TXAFormat
import com.txapp.musicplayer.util.txa

/**
 * Data class để lưu thông tin chỉnh sửa tag
 */
data class TagEditData(
    val title: String,
    val artist: String,
    val album: String,
    val albumArtist: String,
    val composer: String,
    val year: String
)

/**
 * Bottom Sheet để chỉnh sửa thông tin bài hát (Tag Editor)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TXATagEditorSheet(
    song: Song,
    onDismiss: () -> Unit,
    onSave: (TagEditData) -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    
    // Edit state
    var title by remember { mutableStateOf(song.title) }
    var artist by remember { mutableStateOf(song.artist) }
    var album by remember { mutableStateOf(song.album) }
    var albumArtist by remember { mutableStateOf(song.albumArtist ?: "") }
    var composer by remember { mutableStateOf(song.composer ?: "") }
    var year by remember { mutableStateOf(if (song.year > 0) song.year.toString() else "") }
    
    // Check if data changed
    val hasChanges = remember(title, artist, album, albumArtist, composer, year) {
        title != song.title ||
        artist != song.artist ||
        album != song.album ||
        albumArtist != (song.albumArtist ?: "") ||
        composer != (song.composer ?: "") ||
        year != (if (song.year > 0) song.year.toString() else "")
    }
    
    val albumArtUri = remember(song.albumId) {
        if (song.albumId != -1L) {
            android.content.ContentUris.withAppendedId(
                Uri.parse("content://media/external/audio/albumart"),
                song.albumId
            )
        } else null
    }
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(scrollState)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "txamusic_tag_editor".txa(),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                // Save button
                Button(
                    onClick = {
                        onSave(TagEditData(
                            title = title,
                            artist = artist,
                            album = album,
                            albumArtist = albumArtist,
                            composer = composer,
                            year = year
                        ))
                    },
                    enabled = hasChanges,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("txamusic_btn_save".txa())
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Album Art & Info
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Album Art
                Card(
                    modifier = Modifier.size(80.dp),
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(4.dp)
                ) {
                    if (albumArtUri != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(albumArtUri)
                                .crossfade(true)
                                .build(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                
                // File info
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "txamusic_file_path".txa(),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = song.data.substringAfterLast("/"),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Text(
                        text = "txamusic_duration".txa() + ": " + TXAFormat.formatDurationHuman(song.duration),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Edit Fields
            TagEditorTextField(
                label = "txamusic_title".txa(),
                value = title,
                onValueChange = { title = it },
                icon = Icons.Default.Title
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            TagEditorTextField(
                label = "txamusic_artist".txa(),
                value = artist,
                onValueChange = { artist = it },
                icon = Icons.Default.Person
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            TagEditorTextField(
                label = "txamusic_album".txa(),
                value = album,
                onValueChange = { album = it },
                icon = Icons.Default.Album
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            TagEditorTextField(
                label = "txamusic_album_artist".txa(),
                value = albumArtist,
                onValueChange = { albumArtist = it },
                icon = Icons.Default.Groups
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            TagEditorTextField(
                label = "txamusic_composer".txa(),
                value = composer,
                onValueChange = { composer = it },
                icon = Icons.Default.Edit
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            TagEditorTextField(
                label = "txamusic_year".txa(),
                value = year,
                onValueChange = { if (it.all { c -> c.isDigit() }) year = it },
                icon = Icons.Default.CalendarMonth
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Info note
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "txamusic_tag_editor_note".txa(),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp
                    )
                }
            }
            
            Spacer(modifier = Modifier.navigationBarsPadding())
        }
    }
}

@Composable
private fun TagEditorTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        leadingIcon = {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        },
        singleLine = true,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
        )
    )
}
