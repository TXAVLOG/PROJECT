package com.txapp.musicplayer.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.txapp.musicplayer.util.TXAPreferences
import com.txapp.musicplayer.util.TXAActiveMP3
import com.txapp.musicplayer.util.LyricsUtil
import com.txapp.musicplayer.util.txa
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class LyricsFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Keep screen on while viewing lyrics
        requireActivity().window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        return ComposeView(requireContext()).apply {
            setContent {
                MaterialTheme(
                    colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
                ) {
                    val currentSongId = TXAActiveMP3.currentPlayingSongId.collectAsState().value
                    val currentPosition = TXAActiveMP3.currentPosition.collectAsState().value
                    val currentSongTitle = TXAActiveMP3.currentSongTitle.collectAsState().value
                    val currentSongArtist = TXAActiveMP3.currentSongArtist.collectAsState().value
                    val currentSongPath = TXAActiveMP3.currentSongPath.collectAsState().value
                    val isPlaying = TXAActiveMP3.isPlaying.collectAsState().value
                    
                    // Load lyrics for current song
                    var lyrics by remember { mutableStateOf<LyricsUtil.ParsedLyrics?>(null) }
                    var isLoading by remember { mutableStateOf(true) }
                    
                    LaunchedEffect(currentSongPath) {
                        isLoading = true
                        lyrics = LyricsUtil.loadLyricsForSong(requireContext(), currentSongPath)
                        isLoading = false
                    }

                    KaraokeLyricsScreen(
                        title = currentSongTitle,
                        artist = currentSongArtist,
                        lyrics = lyrics,
                        isLoading = isLoading,
                        currentPosition = currentPosition,
                        isPlaying = isPlaying,
                        onBack = { findNavController().navigateUp() },
                        onSeek = { position -> seekTo(position) }
                    )
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        requireActivity().window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun seekTo(position: Long) {
        val intent = android.content.Intent("com.txapp.musicplayer.action.SEEK_TO")
        intent.putExtra("position", position)
        requireContext().sendBroadcast(intent)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KaraokeLyricsScreen(
    title: String,
    artist: String,
    lyrics: LyricsUtil.ParsedLyrics?,
    isLoading: Boolean,
    currentPosition: Long,
    isPlaying: Boolean,
    onBack: () -> Unit,
    onSeek: (Long) -> Unit
) {
    val accentColor = Color(android.graphics.Color.parseColor(TXAPreferences.currentAccent))
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Find current line index based on timestamp
    val currentLineIndex = remember(lyrics, currentPosition) {
        if (lyrics == null || !lyrics.isSynced) -1
        else lyrics.lines.indexOfLast { it.timestamp <= currentPosition }
    }

    // Auto-scroll to current line with smooth animation
    LaunchedEffect(currentLineIndex) {
        if (currentLineIndex >= 0 && lyrics?.isSynced == true) {
            scope.launch {
                // Scroll to center the current line
                val targetIndex = (currentLineIndex).coerceIn(0, (lyrics.lines.size - 1).coerceAtLeast(0))
                listState.animateScrollToItem(
                    index = targetIndex,
                    scrollOffset = -300 // Offset to center the line
                )
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = title.ifBlank { "txamusic_playing".txa() },
                            maxLines = 1,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = artist,
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.08f),
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.background,
                            accentColor.copy(alpha = 0.05f)
                        )
                    )
                )
        ) {
            when {
                isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = accentColor)
                    }
                }
                lyrics == null || lyrics.lines.isEmpty() -> {
                    // No lyrics found
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.MusicOff,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "txamusic_no_lyrics".txa(),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "txamusic_no_lyrics_hint".txa(),
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 32.dp)
                            )
                        }
                    }
                }
                lyrics.isSynced -> {
                    // Synced Lyrics with Karaoke effect
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 120.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        itemsIndexed(lyrics.lines) { index, line ->
                            KaraokeLyricLine(
                                text = line.text,
                                timestamp = line.timestamp,
                                isCurrent = index == currentLineIndex,
                                isPast = index < currentLineIndex,
                                isFuture = index > currentLineIndex,
                                accentColor = accentColor,
                                onTap = { onSeek(line.timestamp) }
                            )
                        }
                    }
                }
                else -> {
                    // Plain text lyrics (no timestamps)
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 48.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        itemsIndexed(lyrics.lines) { _, line ->
                            Text(
                                text = line.text,
                                fontSize = 18.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KaraokeLyricLine(
    text: String,
    timestamp: Long,
    isCurrent: Boolean,
    isPast: Boolean,
    isFuture: Boolean,
    accentColor: Color,
    onTap: () -> Unit
) {
    // Animate scale and alpha for karaoke effect
    val scale by animateFloatAsState(
        targetValue = if (isCurrent) 1.15f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "scale"
    )
    
    val alpha by animateFloatAsState(
        targetValue = when {
            isCurrent -> 1f
            isPast -> 0.4f
            else -> 0.6f
        },
        animationSpec = tween(durationMillis = 300),
        label = "alpha"
    )

    val textColor = when {
        isCurrent -> accentColor
        isPast -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    }

    val fontSize = if (isCurrent) 26.sp else 20.sp
    val fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal

    // Shadow/glow effect for current line
    val shadowAlpha by animateFloatAsState(
        targetValue = if (isCurrent) 0.5f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "shadow"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .alpha(alpha)
            .clip(RoundedCornerShape(12.dp))
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onTap() })
            }
            .padding(vertical = 8.dp, horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        // Glow effect behind current line
        if (isCurrent) {
            Text(
                text = text,
                fontSize = fontSize,
                fontWeight = fontWeight,
                color = accentColor.copy(alpha = shadowAlpha),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .graphicsLayer {
                        this.shadowElevation = 20f
                    }
            )
        }
        
        // Main text
        Text(
            text = text,
            fontSize = fontSize,
            fontWeight = fontWeight,
            color = textColor,
            textAlign = TextAlign.Center,
            lineHeight = fontSize * 1.4f
        )
    }
}
