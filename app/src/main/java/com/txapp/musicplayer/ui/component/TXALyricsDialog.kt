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

    // Find current lyric index
    val activeIndex = remember(lyrics, currentPosition) {
        // Add 500ms offset to currentPosition to highlight upcoming line slightly early (like Retro Music)
        val adjustedPos = currentPosition + 500
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
                    } else if (lyrics.isEmpty()) {
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
                                
                                Text(
                                    text = line.text,
                                    fontSize = if (isCurrent) 22.sp else 16.sp,
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                    color = when {
                                        isCurrent -> MaterialTheme.colorScheme.primary
                                        isPast -> Color.White.copy(alpha = 0.4f)
                                        else -> Color.White.copy(alpha = 0.7f)
                                    },
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 12.dp, horizontal = 16.dp)
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

/**
 * Lyrics utility để parse và load lyrics
 */
object LyricsUtil {
    
    /**
     * Parse LRC format lyrics with extended support
     * Supported formats:
     * - [mm:ss.xx]lyrics text (standard)
     * - [mm:ss.xx - mm:ss.xx]lyrics text (extended with end time)
     * - [hh:mm:ss.xx]lyrics text (for songs > 1 hour)
     * - [hh:mm:ss.xx - hh:mm:ss.xx]lyrics text (extended for songs > 1 hour)
     */
    fun parseLrc(lrcContent: String): List<LyricLine> {
        if (lrcContent.isBlank()) return emptyList()
        
        val result = mutableListOf<LyricLine>()
        var offset = 0
        val lines = lrcContent.lines()
        
        // Standard format: [mm:ss.xx]
        val timeRegex = Regex("""\[(\d+):(\d{2})[.:](\d{2,3})\]""")
        // Extended format with end time: [mm:ss.xx - mm:ss.xx]
        val extendedTimeRegex = Regex("""\[(\d+):(\d{2})[.:](\d{2,3})\s*-\s*(\d+):(\d{2})[.:](\d{2,3})\]""")
        // Hour format: [hh:mm:ss.xx]
        val hourTimeRegex = Regex("""\[(\d+):(\d{2}):(\d{2})[.:](\d{2,3})\]""")
        // Extended hour format: [hh:mm:ss.xx - hh:mm:ss.xx]
        val extendedHourRegex = Regex("""\[(\d+):(\d{2}):(\d{2})[.:](\d{2,3})\s*-\s*(\d+):(\d{2}):(\d{2})[.:](\d{2,3})\]""")
        val attrRegex = Regex("""\[(\D+):(.+)\]""")

        for (line in lines) {
            val attrMatch = attrRegex.find(line)
            if (attrMatch != null) {
                val key = attrMatch.groupValues[1].lowercase().trim()
                val value = attrMatch.groupValues[2].trim()
                if (key == "offset") {
                    offset = value.toIntOrNull() ?: 0
                }
                continue
            }

            // Try extended hour format first [hh:mm:ss.xx - hh:mm:ss.xx]
            val extHourMatch = extendedHourRegex.find(line)
            if (extHourMatch != null) {
                val hr1 = extHourMatch.groupValues[1].toLong()
                val min1 = extHourMatch.groupValues[2].toLong()
                val sec1 = extHourMatch.groupValues[3].toLong()
                val ms1 = parseMs(extHourMatch.groupValues[4])
                val hr2 = extHourMatch.groupValues[5].toLong()
                val min2 = extHourMatch.groupValues[6].toLong()
                val sec2 = extHourMatch.groupValues[7].toLong()
                val ms2 = parseMs(extHourMatch.groupValues[8])
                
                val startMs = ((hr1 * 3600 + min1 * 60 + sec1) * 1000 + ms1 + offset)
                val endMs = ((hr2 * 3600 + min2 * 60 + sec2) * 1000 + ms2 + offset)
                val text = line.replace(extendedHourRegex, "").trim()
                result.add(LyricLine(startMs, text, endMs))
                continue
            }

            // Try hour format [hh:mm:ss.xx]
            val hourMatch = hourTimeRegex.find(line)
            if (hourMatch != null) {
                val hr = hourMatch.groupValues[1].toLong()
                val min = hourMatch.groupValues[2].toLong()
                val sec = hourMatch.groupValues[3].toLong()
                val ms = parseMs(hourMatch.groupValues[4])
                val totalMs = ((hr * 3600 + min * 60 + sec) * 1000 + ms + offset)
                val text = line.replace(hourTimeRegex, "").trim()
                result.add(LyricLine(totalMs, text))
                continue
            }

            // Try extended format [mm:ss.xx - mm:ss.xx]
            val extMatch = extendedTimeRegex.find(line)
            if (extMatch != null) {
                val min1 = extMatch.groupValues[1].toLong()
                val sec1 = extMatch.groupValues[2].toLong()
                val ms1 = parseMs(extMatch.groupValues[3])
                val min2 = extMatch.groupValues[4].toLong()
                val sec2 = extMatch.groupValues[5].toLong()
                val ms2 = parseMs(extMatch.groupValues[6])
                
                val startMs = ((min1 * 60 + sec1) * 1000 + ms1 + offset)
                val endMs = ((min2 * 60 + sec2) * 1000 + ms2 + offset)
                val text = line.replace(extendedTimeRegex, "").trim()
                result.add(LyricLine(startMs, text, endMs))
                continue
            }

            // Standard format [mm:ss.xx]
            val matches = timeRegex.findAll(line)
            if (matches.none()) continue
            
            val text = line.replace(timeRegex, "").trim()
            for (match in matches) {
                val min = match.groupValues[1].toLong()
                val sec = match.groupValues[2].toLong()
                val ms = parseMs(match.groupValues[3])
                val totalMs = (min * 60 + sec) * 1000 + ms + offset
                result.add(LyricLine(totalMs, text))
            }
        }
        
        // Sort and auto-calculate end timestamps
        val sorted = result.sortedBy { it.timestamp }.toMutableList()
        for (i in sorted.indices) {
            if (sorted[i].endTimestamp == -1L) {
                val endTime = if (i < sorted.size - 1) {
                    sorted[i + 1].timestamp // Next line's start time
                } else {
                    sorted[i].timestamp + 10000 // Last line: 10 seconds duration
                }
                sorted[i] = sorted[i].copy(endTimestamp = endTime)
            }
        }
        return sorted
    }
    
    private fun parseMs(msStr: String): Long {
        var ms = msStr.toLong()
        if (msStr.length == 2) ms *= 10 // Handle [00:00.10] as 100ms
        return ms
    }
    
    private val centralLrcPath: String
        get() = android.os.Environment.getExternalStorageDirectory().toString() + "/TXAMusic/lyrics/"

    /**
     * Get raw lyrics string (LRC or plain text) from file or tags
     */
    fun getRawLyrics(audioFilePath: String, title: String? = null, artist: String? = null): String? {
        // 1. Try .lrc file in same directory
        val lrcPath = audioFilePath.substringBeforeLast('.') + ".lrc"
        val lrcFile = File(lrcPath)
        if (lrcFile.exists()) {
            return try { lrcFile.readText() } catch (e: Exception) { null }
        }
        
        // 2. Try .lrc file in central folder (/TXAMusic/lyrics/Title - Artist.lrc)
        // If title/artist not provided, try to extract from filename
        val finalTitle = title ?: File(audioFilePath).nameWithoutExtension.substringBefore(" - ").trim()
        val finalArtist = artist ?: File(audioFilePath).nameWithoutExtension.substringAfter(" - ", "").trim()

        val centralFile = if (finalArtist.isNotEmpty() && finalArtist != finalTitle) {
            File(centralLrcPath, "$finalTitle - $finalArtist.lrc")
        } else {
            File(centralLrcPath, "$finalTitle.lrc")
        }
        
        if (centralFile.exists()) {
            return try { centralFile.readText() } catch (e: Exception) { null }
        }

        // 3. Try embedded tags
        return try {
            val audioFile = org.jaudiotagger.audio.AudioFileIO.read(File(audioFilePath))
            audioFile.tagOrCreateDefault.getFirst(org.jaudiotagger.tag.FieldKey.LYRICS)
        } catch (e: Exception) {
            null
        }
    }

    sealed class SaveResult {
        object Success : SaveResult()
        object Failure : SaveResult()
        data class PermissionRequired(val intent: android.app.PendingIntent) : SaveResult()
    }

    /**
     * Save lyrics to file (as .lrc) and optionally to embedded tags
     */
    suspend fun saveLyrics(context: android.content.Context, audioFilePath: String, content: String): SaveResult = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            // 1. Save as .lrc file in same directory
            val lrcPath = audioFilePath.substringBeforeLast('.') + ".lrc"
            val lrcFile = File(lrcPath)
            
            try {
                lrcFile.writeText(content)
            } catch (e: Exception) {
                // Ignore .lrc write failure if it's permission issues, we'll try embedded tags next
                com.txapp.musicplayer.util.TXALogger.appW("LyricsUtil", "Failed to write .lrc file, continuing to embedded tags...")
            }

            // 1.5 Save to central folder
            try {
                val fileName = File(audioFilePath).nameWithoutExtension
                val centralFile = File(centralLrcPath, "$fileName.lrc")
                if (centralFile.parentFile?.exists() != true) {
                    centralFile.parentFile?.mkdirs()
                }
                centralFile.writeText(content)
            } catch (e: Exception) {
                com.txapp.musicplayer.util.TXALogger.appE("LyricsUtil", "Failed to write to central lrc folder", e)
            }
            
            // 2. Save to embedded tags
            // This now handles PermissionRequired correctly on Android 11+
            val result = TXATagWriter.writeLyrics(context, audioFilePath, content)
            
            return@withContext when (result) {
                is TXATagWriter.WriteResult.Success -> SaveResult.Success
                is TXATagWriter.WriteResult.PermissionRequired -> SaveResult.PermissionRequired(result.intent)
                else -> SaveResult.Failure
            }
        } catch (e: Exception) {
            com.txapp.musicplayer.util.TXALogger.appE("LyricsUtil", "Failed to save lyrics", e)
            return@withContext SaveResult.Failure
        }
    }

    
    /**
     * Load processed lyrics for display
     */
    fun loadLyricsFromFile(audioFilePath: String): String? = getRawLyrics(audioFilePath)
    
    /**
     * Build Google search URL for lyrics
     */
    fun buildSearchUrl(title: String, artist: String): String {
        val query = "$title $artist lyrics".replace(" ", "+")
        return "https://www.google.com/search?q=$query"
    }

    /**
     * Get clean lyrics without LRC timestamps for display
     */
    fun getCleanLyrics(rawLyrics: String?): String? {
        if (rawLyrics.isNullOrBlank()) return null
        
        // Remove [mm:ss.xx] and metadata like [ar:...] [ti:...]
        val pattern = Regex("""\[\d+:?\d{2}[:.]\d{2,3}\]|\[[a-z]{2}:.*\]""")
        return rawLyrics.lines()
            .map { it.replace(pattern, "").trim() }
            .filter { it.isNotBlank() }
            .joinToString("\n")
    }
}

