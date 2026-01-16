package com.txapp.musicplayer.ui.component

// cms

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.txapp.musicplayer.util.txa
import com.txapp.musicplayer.util.TXAToast
import com.txapp.musicplayer.util.TXATagWriter
import com.txapp.musicplayer.util.LyricsUtil
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/**
 * Lyrics data class - Enhanced for karaoke-style support
 * @param timestamp Start time in milliseconds
 * @param endTimestamp End time in milliseconds (auto-calculated if not provided)
 * @param text Lyric line text
 */
data class LyricLine(
    val timestamp: Long, // Start time in milliseconds
    val text: String,
    val endTimestamp: Long = -1 // End time in milliseconds (-1 = auto-calculate)
)

/**
 * Lyrics Screen/Dialog - Hiển thị lời bài hát
 */
/**
 * Lyrics Screen/Dialog - Hiển thị lời bài hát
 */
@Composable
fun LyricsDialog(
    songTitle: String,
    artistName: String,
    songPath: String,
    lyrics: List<LyricLine>,
    currentPosition: Long,
    isPlaying: Boolean,
    onSeek: (Long) -> Unit,
    onDismiss: () -> Unit,
    onSearchLyrics: () -> Unit = {},
    onLyricsUpdated: () -> Unit = {},
    onPermissionRequest: (android.app.PendingIntent, String) -> Unit = { _, _ -> },
    startInEditMode: Boolean = false,
    songDuration: Long = 0 // Duration in milliseconds
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    
    var isEditing by remember { mutableStateOf(startInEditMode) }
    val originalContent = remember { mutableStateOf(if (startInEditMode) LyricsUtil.getRawLyrics(songPath) ?: "" else "") }
    var editContent by remember { mutableStateOf(originalContent.value) }
    var isSaving by remember { mutableStateOf(false) }
    // Tab for edit mode: 0 = Synced (LRC), 1 = Normal (embedded)
    var editTab by remember { mutableIntStateOf(0) }
    // Show unsaved changes warning
    var showUnsavedWarning by remember { mutableStateOf(false) }
    
    // Check if content has changed from original
    val hasChanges = remember(editContent, originalContent.value) {
        editContent != originalContent.value
    }
    
    // Handler for dismiss with unsaved changes check
    val handleDismiss: () -> Unit = {
        if (isEditing && hasChanges) {
            showUnsavedWarning = true
        } else {
            onDismiss()
        }
    }
    
    // Handler for cancel edit with unsaved changes check
    val handleCancelEdit: () -> Unit = {
        if (hasChanges) {
            showUnsavedWarning = true
        } else {
            isEditing = false
        }
    }

    var syncedBackup by remember { mutableStateOf<String?>(null) }

    // Logic for tab switching conversion
    LaunchedEffect(editTab) {
        if (editTab == 1) { // Switching to Normal
            // If it has timestamps, back it up and clean it
            if (editContent.contains(Regex("""\[\d+:?\d{2}[:.]\d{2,3}\]"""))) {
                syncedBackup = editContent
                editContent = LyricsUtil.getCleanLyrics(editContent) ?: ""
            }
        } else { // Switching back to Synced
            // Restore from backup if we have one
            syncedBackup?.let {
                editContent = it
            }
        }
    }

    // Smooth position interpolation - Giúp hiệu ứng karaoke mượt mà 60fps
    val livePosition by produceState(initialValue = currentPosition, currentPosition, isPlaying) {
        if (!isPlaying) {
            value = currentPosition
            return@produceState
        }
        val startWallTime = System.currentTimeMillis()
        val startPos = currentPosition
        while (true) {
            val elapsed = System.currentTimeMillis() - startWallTime
            value = startPos + elapsed
            withFrameMillis { } 
        }
    }

    // Find current lyric index
    val activeIndex = remember(lyrics, livePosition) {
        // Add 500ms offset to livePosition to highlight upcoming line slightly early (like Retro Music)
        val adjustedPos = livePosition + 500
        lyrics.indexOfLast { it.timestamp <= adjustedPos }.coerceAtLeast(0)
    }
    
    // Auto scroll to current lyric
    LaunchedEffect(activeIndex, isPlaying, isEditing) {
        if (!isEditing && isPlaying && lyrics.isNotEmpty() && activeIndex >= 0) {
            coroutineScope.launch {
                listState.animateScrollToItem(
                    index = (activeIndex - 2).coerceAtLeast(0),
                    scrollOffset = 0
                )
            }
        }
    }
    
    var rawLyrics by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(songPath) {
        if (lyrics.isEmpty()) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                rawLyrics = LyricsUtil.getRawLyrics(songPath)
            }
        }
    }
    
    Dialog(
        onDismissRequest = handleDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = !isEditing || !hasChanges, // Only allow direct back dismiss if no changes
            dismissOnClickOutside = !isEditing
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(24.dp),
                color = Color.Black.copy(alpha = 0.95f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isEditing) "txamusic_edit_lyrics".txa() else songTitle,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1
                            )
                            if (!isEditing) {
                                Text(
                                    text = artistName,
                                    fontSize = 14.sp,
                                    color = Color.White.copy(alpha = 0.7f),
                                    maxLines = 1
                                )
                            }
                        }
                        
                        Row {
                            if (isEditing) {
                                // Cancel button in edit mode
                                TextButton(onClick = handleCancelEdit) {
                                    Text("txamusic_btn_cancel".txa(), color = Color.White.copy(alpha = 0.7f))
                                }
                            } else {
                                IconButton(onClick = onSearchLyrics) {
                                    Icon(
                                        Icons.Outlined.Search,
                                        contentDescription = "txamusic_lyrics_search".txa(),
                                        tint = Color.White.copy(alpha = 0.7f)
                                    )
                                }
                            }
                            IconButton(onClick = handleDismiss) {
                                Icon(
                                    Icons.Outlined.Close,
                                    contentDescription = "Close",
                                    tint = Color.White
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Content
                    if (isEditing) {
                        // Edit Mode - Tab selector for synced vs normal lyrics
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = editTab == 0,
                                onClick = { editTab = 0 },
                                label = { Text("txamusic_synced_lyrics".txa()) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = Color.White
                                )
                            )
                            FilterChip(
                                selected = editTab == 1,
                                onClick = { editTab = 1 },
                                label = { Text("txamusic_normal_lyrics".txa()) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                        
                        // Helper text - Dynamic format based on song duration
                        val formatKey = if (songDuration >= 3600000) { // >= 1 hour
                            "txamusic_lyrics_format_long_extended".txa()
                        } else {
                            "txamusic_lyrics_format_short_extended".txa()
                        }
                        Text(
                            text = if (editTab == 0) "txamusic_paste_synced_lyrics_hint".txa().replace("%s", formatKey)
                                   else "txamusic_paste_normal_lyrics_hint".txa(),
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        
                        // Edit text field
                        OutlinedTextField(
                            value = editContent,
                            onValueChange = { editContent = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            placeholder = { 
                                Text(
                                    if (editTab == 0) "txamusic_paste_timeframe_lyrics_here".txa()
                                    else "txamusic_paste_lyrics_here".txa(), 
                                    color = Color.Gray
                                ) 
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                                cursorColor = MaterialTheme.colorScheme.primary
                            )
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // Save button
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    isSaving = true
                                    val result = LyricsUtil.saveLyrics(context, songPath, editContent)
                                    when (result) {
                                        is LyricsUtil.SaveResult.Success -> {
                                            isSaving = false
                                            TXAToast.success(context, "txamusic_lyrics_saved".txa())
                                            isEditing = false
                                            // Refresh raw lyrics
                                            rawLyrics = editContent
                                            onLyricsUpdated()
                                        }
                                        is LyricsUtil.SaveResult.PermissionRequired -> {
                                            // Keep isSaving = true while permission dialog is showing
                                            // The parent activity will handle the retry and eventually color/toast
                                            onPermissionRequest(result.intent, editContent)
                                            // We reset it here too because the system dialog might be canceled
                                            // or we might not come back to this exact coroutine state clearly
                                            // But for the "effect", we can delay it
                                            delay(1000)
                                            isSaving = false
                                        }
                                        else -> {
                                            isSaving = false
                                            TXAToast.error(context, "txamusic_lyrics_save_failed".txa())
                                        }
                                    }
                                }
                            },

                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isSaving && hasChanges,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            if (isSaving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp), 
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text("txamusic_btn_save".txa())
                            }
                        }
                    } else if (lyrics.isEmpty() && rawLyrics.isNullOrBlank()) {
                        // No lyrics found - show empty state with edit button
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    Icons.Outlined.Search,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.3f),
                                    modifier = Modifier.size(64.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "txamusic_lyrics_not_found".txa(),
                                    fontSize = 16.sp,
                                    color = Color.White.copy(alpha = 0.5f),
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                
                                // Action buttons
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = onSearchLyrics,
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = MaterialTheme.colorScheme.primary
                                        )
                                    ) {
                                        Icon(
                                            Icons.Outlined.Search,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("txamusic_lyrics_search".txa())
                                    }
                                    
                                    Button(
                                        onClick = {
                                            originalContent.value = LyricsUtil.getRawLyrics(songPath) ?: ""
                                            editContent = originalContent.value
                                            isEditing = true
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary
                                        )
                                    ) {
                                        Icon(
                                            Icons.Outlined.Edit,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("txamusic_add_lyrics".txa())
                                    }
                                }
                            }
                        }
                    } else if (lyrics.isEmpty() && !rawLyrics.isNullOrBlank()) {
                        // Display Raw Lyrics (Normal Mode)
                         LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .weight(1f),
                            contentPadding = PaddingValues(vertical = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            item {
                                Text(
                                    text = rawLyrics!!,
                                    fontSize = 18.sp,
                                    color = Color.White.copy(alpha = 0.9f),
                                    textAlign = TextAlign.Center,
                                    lineHeight = 32.sp,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 32.dp)
                                )
                            }
                        }
                    } else {
                        // Synced lyrics display
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .weight(1f),
                            contentPadding = PaddingValues(vertical = 100.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            itemsIndexed(lyrics) { index, line ->
                                val isCurrent = index == activeIndex
                                val isPast = index < activeIndex
                                
                                val duration = line.endTimestamp - line.timestamp
                                val progress = if (isCurrent && duration > 0) {
                                    ((livePosition - line.timestamp).toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                                } else {
                                    0f
                                }

                                val primaryColor = MaterialTheme.colorScheme.primary
                                val secondaryColor = Color.White.copy(alpha = 0.7f)
                                
                                val brush = if (isCurrent) {
                                    androidx.compose.ui.graphics.Brush.horizontalGradient(
                                        0.0f to primaryColor,
                                        progress to primaryColor,
                                        progress + 0.05f to secondaryColor, // Smooth edge
                                        1.0f to secondaryColor
                                    )
                                } else {
                                    androidx.compose.ui.graphics.SolidColor(
                                        if (isPast) Color.White.copy(alpha = 0.4f) else secondaryColor
                                    )
                                }

                                Text(
                                    text = line.text,
                                    fontSize = if (isCurrent) 22.sp else 16.sp,
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                    style = if (isCurrent) {
                                         androidx.compose.ui.text.TextStyle(
                                             brush = brush
                                         )
                                    } else {
                                         LocalTextStyle.current.copy(color = if (isPast) Color.White.copy(alpha = 0.4f) else secondaryColor)
                                    },
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 12.dp, horizontal = 16.dp)
                                        // Animate scale for smoother entry
                                        .graphicsLayer {
                                            val scale = if (isCurrent) 1.05f else 1f
                                            scaleX = scale
                                            scaleY = scale
                                        }
                                )
                            }
                        }
                    }
                }
            }
            
            // FAB Edit button (only show when not editing and has lyrics)
            if (!isEditing && lyrics.isNotEmpty()) {
                FloatingActionButton(
                    onClick = {
                        originalContent.value = LyricsUtil.getRawLyrics(songPath) ?: ""
                        editContent = originalContent.value
                        isEditing = true
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(24.dp),
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White
                ) {
                    Icon(
                        Icons.Outlined.Edit,
                        contentDescription = "txamusic_edit_lyrics".txa()
                    )
                }
            }
        }
    }
    
    // Unsaved changes warning dialog
    if (showUnsavedWarning) {
        AlertDialog(
            onDismissRequest = { showUnsavedWarning = false },
            title = { Text("txamusic_lyrics_unsaved_title".txa()) },
            text = { Text("txamusic_lyrics_unsaved_desc".txa()) },
            confirmButton = {
                Button(
                    onClick = {
                        showUnsavedWarning = false
                        coroutineScope.launch {
                            isSaving = true
                            val result = LyricsUtil.saveLyrics(context, songPath, editContent)
                            isSaving = false
                            when (result) {
                                is LyricsUtil.SaveResult.Success -> {
                                    TXAToast.success(context, "txamusic_lyrics_saved".txa())
                                    isEditing = false
                                    onLyricsUpdated()
                                    onDismiss()
                                }
                                is LyricsUtil.SaveResult.PermissionRequired -> {
                                    onPermissionRequest(result.intent, editContent)
                                }
                                else -> {
                                    TXAToast.error(context, "txamusic_lyrics_save_failed".txa())
                                }
                            }
                        }
                    }
                ) {
                    Text("txamusic_btn_save".txa())
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showUnsavedWarning = false
                        editContent = originalContent.value // Reset changes
                        isEditing = false
                        onDismiss()
                    }
                ) {
                    Text("txamusic_btn_discard".txa())
                }
            }
        )
    }
}

/**
 * Simple lyrics overlay for player (non-synced)
 */
@Composable
fun SimpleLyricsOverlay(
    lyrics: String,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            color = Color.Black.copy(alpha = 0.95f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "txamusic_lyrics".txa(),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Outlined.Close,
                            contentDescription = "Close",
                            tint = Color.White
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Lyrics content
                if (lyrics.isBlank()) {
                    Box(
                        modifier = Modifier.fillMaxSize().weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "txamusic_lyrics_not_found".txa(),
                            color = Color.White.copy(alpha = 0.5f),
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().weight(1f),
                        contentPadding = PaddingValues(vertical = 16.dp)
                    ) {
                        item {
                            Text(
                                text = lyrics,
                                fontSize = 16.sp,
                                color = Color.White.copy(alpha = 0.9f),
                                lineHeight = 28.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
}



