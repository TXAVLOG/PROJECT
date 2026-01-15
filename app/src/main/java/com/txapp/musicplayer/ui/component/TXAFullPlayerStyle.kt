package com.txapp.musicplayer.ui.component

// cms

import android.content.Context
import android.content.res.Configuration
import android.view.WindowManager
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.TextStyle
import com.txapp.musicplayer.R
import com.txapp.musicplayer.util.LyricsUtil
import android.media.AudioManager
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.withStyle
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import com.txapp.musicplayer.util.TXAFormat
import com.txapp.musicplayer.util.TXAToast
import com.txapp.musicplayer.util.TXAPreferences
import com.txapp.musicplayer.ui.component.TXASleepTimerManager
import com.txapp.musicplayer.util.txa
import kotlinx.coroutines.delay

/**
 * TXA Full Player Style - Thiết kế mới từ đầu
 * 
 * Features:
 * - Material Design 3
 * - Circular album art với progress ring
 * - Smooth animations
 * - Real-time settings update
 * - Memory optimized
 */
@Suppress("FunctionName")
@Composable
fun NowPlayingFullStyle(
    state: NowPlayingState,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Float) -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit,
    onShowQueue: () -> Unit,
    onClose: () -> Unit,
    onShowSleepTimer: () -> Unit = {},
    onShowLyrics: (Boolean) -> Unit = {},
    onShowPlaybackSpeed: () -> Unit = {},
    onAddToPlaylist: () -> Unit = {},
    onEditTag: () -> Unit = {},
    onSetRingtone: () -> Unit = {},
    onDriveMode: () -> Unit = {},
    onArtistClick: () -> Unit = {}
) {
    val context = LocalContext.current

    // Remember album URI to avoid recomputation
    val albumUri = remember(state.albumId, state.mediaUri) {
        getAlbumArtUri(state)
    }

    // Timer state for icon update
    var isSleepTimerActive by remember { mutableStateOf(TXASleepTimerManager.isTimerActive(context)) }
    var remainingTime by remember { mutableLongStateOf(TXASleepTimerManager.getRemainingTime(context)) }

    LaunchedEffect(Unit) {
        while (true) {
            isSleepTimerActive = TXASleepTimerManager.isTimerActive(context)
            if (isSleepTimerActive) {
                remainingTime = TXASleepTimerManager.getRemainingTime(context)
            }
            delay(1000)
        }
    }

    // Accent color with animation
    val accentColor by animateColorAsState(
        targetValue = state.vibrantColor,
        animationSpec = tween(500),
        label = "accentColor"
    )

    // Lyrics state
    val showLyrics by TXAPreferences.showLyricsInPlayer.collectAsState()

    // Keep screen on logic
    val lyricsScreenOn by TXAPreferences.lyricsScreenOn.collectAsState()
    val activity = context as? android.app.Activity
    androidx.compose.runtime.DisposableEffect(showLyrics, lyricsScreenOn) {
        if (showLyrics && lyricsScreenOn) {
            activity?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
    
    // Background gradient
    val gradientColors = listOf(
        accentColor.copy(alpha = 0.6f),
        Color.Black.copy(alpha = 0.95f)
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(gradientColors))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ═══════════════════════════════════════════════════════════════
            // TOP BAR
            // ═══════════════════════════════════════════════════════════════
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Close button
                IconButton(onClick = onClose) {
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }

                // Title
                Text(
                    text = "txamusic_settings_section_now_playing".txa(),
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )

                // Actions
                Row {
                    IconButton(onClick = onDriveMode) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_drive),
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    IconButton(onClick = onShowQueue) {
                        Icon(
                            TXAIcons.QueueMusic,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(0.08f))

            // ═══════════════════════════════════════════════════════════════
            // ALBUM ART vs LYRICS 
            // ═══════════════════════════════════════════════════════════════
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxWidth().weight(1f).padding(vertical = 16.dp)
            ) {
                androidx.compose.animation.AnimatedContent(
                    targetState = showLyrics,
                    transitionSpec = {
                        (fadeIn() + scaleIn()).togetherWith(fadeOut() + scaleOut())
                    },
                    label = "LyricsToggle"
                ) { isLyrics ->
                    if (isLyrics) {
                        // Lyrics Display
                        Box(
                            modifier = Modifier.fillMaxSize().padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            // Background tap area - to toggle back to album art
                            Box(modifier = Modifier.fillMaxSize().clickable { 
                                TXAPreferences.setShowLyricsInPlayer(false) 
                            })

                            val cleanLyrics = LyricsUtil.getCleanLyrics(state.lyrics ?: "")
                            
                            if (cleanLyrics.isNullOrBlank()) {
                                // No lyrics found - show empty state with buttons
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        Icons.Outlined.MusicNote,
                                        contentDescription = null,
                                        tint = Color.White.copy(alpha = 0.3f),
                                        modifier = Modifier.size(64.dp)
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = "txamusic_lyrics_not_found".txa(),
                                        color = Color.White.copy(alpha = 0.5f),
                                        fontSize = 16.sp,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(24.dp))
                                    
                                    // Action buttons
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = {
                                                // Open Google search for lyrics
                                                val url = LyricsUtil.buildSearchUrl(state.title, state.artist)
                                                try {
                                                    com.txapp.musicplayer.util.TXAToast.info(context, "txamusic_lyrics_searching".txa())
                                                    context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)))
                                                } catch (e: Exception) {
                                                    com.txapp.musicplayer.util.TXAToast.error(context, "txamusic_browser_not_found".txa())
                                                }
                                            },
                                            colors = ButtonDefaults.outlinedButtonColors(
                                                contentColor = accentColor
                                            ),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, accentColor.copy(alpha = 0.5f))
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
                                            onClick = { onShowLyrics(true) }, // Open full lyrics dialog to add
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = accentColor
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
                            } else {
                                // Has lyrics - show either synced or static lyrics
                                val parsedLyrics = remember(state.lyrics) {
                                    state.lyrics?.let { LyricsUtil.parseLrc(it) } ?: emptyList()
                                }

                                Box(modifier = Modifier.fillMaxSize()) {
                                    if (parsedLyrics.isNotEmpty()) {
                                        // Synced Lyrics Display
                                        SyncedLyricsView(
                                            lyrics = parsedLyrics,
                                            currentPosition = state.position,
                                            accentColor = accentColor,
                                            onLyricClick = { timestamp ->
                                                // Seek to clicked lyric position
                                                // We need a way to seek here, but for now just display
                                            }
                                        )
                                    } else {
                                        // Plain Text Lyrics Display
                                        val cleanLyrics = LyricsUtil.getCleanLyrics(state.lyrics ?: "") ?: ""
                                        Column(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .verticalScroll(rememberScrollState())
                                                .padding(bottom = 60.dp), // Space for FAB
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Text(
                                                text = cleanLyrics,
                                                color = Color.White,
                                                fontSize = 18.sp,
                                                fontWeight = FontWeight.Medium,
                                                textAlign = TextAlign.Center,
                                                lineHeight = 28.sp
                                            )
                                        }
                                    }

                                    // Edit FAB
                                    FloatingActionButton(
                                        onClick = { onShowLyrics(true) },
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(8.dp)
                                            .size(48.dp),
                                        containerColor = accentColor,
                                        contentColor = Color.White
                                    ) {
                                        Icon(
                                            Icons.Outlined.Edit,
                                            contentDescription = "txamusic_edit_lyrics".txa(),
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        // Album Art with Progress Ring
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.clickable { 
                                TXAPreferences.setShowLyricsInPlayer(true) 
                            }
                        ) {
                            // Progress Ring
                            val progress = state.progress / 1000f
                            CircularProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.size(280.dp),
                                color = accentColor,
                                strokeWidth = 1.dp, // Thinner for elegance
                                trackColor = Color.White.copy(alpha = 0.1f),
                                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                            )

                            // Album Art
                            Card(
                                modifier = Modifier.size(250.dp),
                                shape = CircleShape,
                                elevation = CardDefaults.cardElevation(defaultElevation = 16.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                            ) {
                                SubcomposeAsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(albumUri)
                                        .crossfade(300)
                                        .size(500)
                                        .build(),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    val imageState = painter.state
                                    if (imageState is coil.compose.AsyncImagePainter.State.Loading || imageState is coil.compose.AsyncImagePainter.State.Error) {
                                        DefaultAlbumArt(vibrantColor = accentColor)
                                    } else {
                                        SubcomposeAsyncImageContent()
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ═══════════════════════════════════════════════════════════════
            // MUSIC VISUALIZER
            // ═══════════════════════════════════════════════════════════════
            val visualizerEnabled by TXAPreferences.visualizerEnabled.collectAsState()
            val visualizerStylePref by TXAPreferences.visualizerStyle.collectAsState()
            
            if (visualizerEnabled) {
                val vizStyle = remember(visualizerStylePref) {
                    when (visualizerStylePref) {
                        "wave" -> VisualizerStyle.WAVE
                        "circle" -> VisualizerStyle.CIRCLE
                        "spectrum" -> VisualizerStyle.SPECTRUM
                        "glow" -> VisualizerStyle.GLOW_BARS
                        else -> VisualizerStyle.BARS
                    }
                }
                
                TXAVisualizer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .padding(horizontal = 16.dp),
                    style = vizStyle,
                    accentColor = accentColor,
                    isPlaying = state.isPlaying,
                    barCount = 32
                )
            }

            Spacer(modifier = Modifier.weight(0.06f))

            // ═══════════════════════════════════════════════════════════════
            // SONG INFO
            // ═══════════════════════════════════════════════════════════════
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Favorite button
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        if (state.isFavorite) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = null,
                        tint = if (state.isFavorite) Color(0xFFFF6B6B) else Color.White.copy(0.7f),
                        modifier = Modifier.size(26.dp)
                    )
                }

                // Title & Artist
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = state.title.ifBlank { "txamusic_unknown_title".txa() },
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = state.artist.ifBlank { "txamusic_unknown_artist".txa() },
                        color = accentColor.copy(alpha = 0.9f),
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onArtistClick() }
                            .padding(4.dp)
                    )
                }

                // Menu button
                var menuExpanded by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = null,
                            tint = Color.White.copy(0.7f),
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    MoreOptionsDropdown(
                        expanded = menuExpanded,
                        onDismiss = { menuExpanded = false },
                        onAddToPlaylist = {
                            menuExpanded = false
                            onAddToPlaylist()
                        },
                        onEditTag = {
                            menuExpanded = false
                            onEditTag()
                        },
                        onSetRingtone = {
                            menuExpanded = false
                            onSetRingtone()
                        },
                        onShowLyrics = {
                            menuExpanded = false
                            TXAPreferences.setShowLyricsInPlayer(!showLyrics)
                        },
                        isLyricsShowing = showLyrics
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ═══════════════════════════════════════════════════════════════
            // SEEK BAR
            // ═══════════════════════════════════════════════════════════════
            Column(modifier = Modifier.fillMaxWidth()) {
                Slider(
                    value = state.progress,
                    onValueChange = onSeek,
                    valueRange = 0f..1000f,
                    colors = SliderDefaults.colors(
                        thumbColor = accentColor,
                        activeTrackColor = accentColor,
                        inactiveTrackColor = Color.White.copy(alpha = 0.15f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        TXAFormat.formatDurationHuman(state.position),
                        color = Color.White.copy(0.6f),
                        fontSize = 12.sp
                    )
                    Text(
                        TXAFormat.formatDurationHuman(state.duration),
                        color = Color.White.copy(0.6f),
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ═══════════════════════════════════════════════════════════════
            // MAIN CONTROLS
            // ═══════════════════════════════════════════════════════════════
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Shuffle
                IconButton(onClick = onToggleShuffle) {
                    Icon(
                        Icons.Default.Shuffle,
                        contentDescription = null,
                        tint = if (state.shuffleMode) accentColor.copy(alpha = 1.0f) else Color.White.copy(0.3f),
                        modifier = Modifier.size(26.dp)
                    )
                }

                // Previous
                IconButton(
                    onClick = onPrevious,
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        Icons.Default.SkipPrevious,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }

                // Play/Pause FAB
                FloatingActionButton(
                    onClick = onPlayPause,
                    containerColor = accentColor,
                    contentColor = Color.White,
                    modifier = Modifier.size(72.dp)
                ) {
                    Icon(
                        if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp)
                    )
                }

                // Next
                IconButton(
                    onClick = onNext,
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        Icons.Default.SkipNext,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }

                // Repeat
                IconButton(onClick = onToggleRepeat) {
                    val repeatIcon = when (state.repeatMode) {
                        1 -> Icons.Default.RepeatOne
                        else -> Icons.Default.Repeat
                    }
                    Icon(
                        repeatIcon,
                        contentDescription = null,
                        tint = if (state.repeatMode > 0) accentColor.copy(alpha = 1.0f) else Color.White.copy(0.3f),
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ═══════════════════════════════════════════════════════════════
            // VOLUME CONTROL
            // ═══════════════════════════════════════════════════════════════
            VolumeControlCompact(accentColor = accentColor)

            Spacer(modifier = Modifier.height(16.dp))

            // ═══════════════════════════════════════════════════════════════
            // EXTRA CONTROLS
            // ═══════════════════════════════════════════════════════════════
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                IconButton(onClick = onShowSleepTimer) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Outlined.Timer,
                            contentDescription = "Sleep Timer",
                            tint = if (isSleepTimerActive) accentColor else Color.White.copy(0.6f),
                            modifier = Modifier.size(24.dp)
                        )

                        if (isSleepTimerActive && remainingTime > 0) {
                            Surface(
                                color = Color.Red,
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(x = 10.dp, y = (-6).dp)
                            ) {
                                Text(
                                    text = TXAFormat.formatSleepTimer(remainingTime),
                                    color = Color.White,
                                    fontSize = 7.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 3.dp, vertical = 0.5.dp)
                                )
                            }
                        }
                    }
                }
                IconButton(onClick = onShowPlaybackSpeed) {
                    Icon(
                        Icons.Outlined.Speed,
                        contentDescription = "Playback Speed",
                        tint = Color.White.copy(0.6f),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.weight(0.05f))
            Spacer(modifier = Modifier.navigationBarsPadding())
        }
    }
}

/**
 * Compact Volume Control
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VolumeControlCompact(accentColor: Color) {
    val context = LocalContext.current
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }
    var currentVolume by remember { mutableIntStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            TXAIcons.VolumeDown,
            contentDescription = null,
            tint = Color.White.copy(0.5f),
            modifier = Modifier.size(20.dp)
        )

        Slider(
            value = currentVolume.toFloat(),
            onValueChange = {
                currentVolume = it.toInt()
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, it.toInt(), 0)
            },
            valueRange = 0f..maxVolume.toFloat(),
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp),
            thumb = {
                val percentage = if (maxVolume > 0) (currentVolume.toFloat() / maxVolume * 100).toInt() else 0
                Box(contentAlignment = Alignment.TopCenter) {
                    // Tooltip
                    Surface(
                        modifier = Modifier
                            .offset(y = (-35).dp)
                            .clip(RoundedCornerShape(8.dp)),
                        color = accentColor,
                        tonalElevation = 4.dp
                    ) {
                        Text(
                            text = "$percentage%",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    // Actual Thumb
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .padding(2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .background(accentColor.copy(alpha = 0.8f))
                        )
                    }
                }
            },
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.White.copy(0.7f),
                inactiveTrackColor = Color.White.copy(0.15f)
            )
        )

        Icon(
            TXAIcons.VolumeUp,
            contentDescription = null,
            tint = Color.White.copy(0.5f),
            modifier = Modifier.size(20.dp)
        )
    }
}

// ════════════════════════════════════════════════════════════════════════════════
// DRIVE MODE - Thiết kế mới hoàn toàn
// ════════════════════════════════════════════════════════════════════════════════

/**
 * Drive Mode Style - Thiết kế tối giản cho lái xe
 * 
 * Đặc điểm:
 * - NÚT CỰC LỚN - dễ chạm khi lái
 * - Album art background (nếu có)
 * - Bo góc mượt mà
 * - Contrast cao, dễ đọc
 */
@Suppress("FunctionName")
@Composable
fun NowPlayingDriveModeStyle(
    state: NowPlayingState,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleShuffle: () -> Unit = {},
    onToggleRepeat: () -> Unit = {},
    onShowSleepTimer: () -> Unit = {},
    onClose: () -> Unit,
    onSetAsRingtone: () -> Unit = {},
    onShowLyrics: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val accentColor = state.vibrantColor
    val albumUri = remember(state.albumId, state.mediaUri) { getAlbumArtUri(state) }
    val hasAlbumArt = state.albumId > 0 || state.mediaUri.isNotEmpty()

    val digitalFont = FontFamily(Font(R.font.digital_7))

    // Time & Battery state
    var currentTime by remember { mutableStateOf("") }
    var batteryLevel by remember { mutableIntStateOf(0) }
    var isCharging by remember { mutableStateOf(false) }

    val timeFormatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    LaunchedEffect(Unit) {
        while (true) {
            currentTime = timeFormatter.format(Date())

            val batteryStatus = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            batteryLevel = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            isCharging =
                status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

            delay(1000)
        }
    }

    // Timer Logic
    var isTimerActive by remember { mutableStateOf(TXASleepTimerManager.isTimerActive(context)) }
    var remainingTime by remember { mutableLongStateOf(TXASleepTimerManager.getRemainingTime(context)) }

    LaunchedEffect(Unit) {
        while (true) {
            isTimerActive = TXASleepTimerManager.isTimerActive(context)
            if (isTimerActive) {
                remainingTime = TXASleepTimerManager.getRemainingTime(context)
            }
            delay(1000)
        }
    }

    // Check dark mode
    val isDarkMode =
        LocalConfiguration.current.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    val backgroundColor = if (isDarkMode) Color(0xFF121212) else Color(0xFFF5F5F5)

    Box(modifier = Modifier.fillMaxSize()) {
        // Background: Album art blurred or theme color
        if (hasAlbumArt) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(albumUri)
                    .crossfade(300)
                    .size(400)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(50.dp)
            )
            // Dark overlay for readability
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f))
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(backgroundColor)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ═══════════════════════════════════════════════════════════════
            // TOP BAR - Close & Favorite (Rounded)
            // ═══════════════════════════════════════════════════════════════
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Close - Large touch target with rounded corners
                Surface(
                    onClick = onClose,
                    modifier = Modifier.size(60.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = if (hasAlbumArt) Color.White.copy(0.15f) else MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = null,
                            tint = if (hasAlbumArt) Color.White else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                // Drive Mode indicator (Enhanced with Clock)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = accentColor.copy(alpha = 0.2f)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_drive),
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "DRIVE",
                                color = if (hasAlbumArt) Color.White else MaterialTheme.colorScheme.onSurface,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Dashboard Detail: Time & Battery
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = currentTime,
                            color = (if (hasAlbumArt) Color.White else MaterialTheme.colorScheme.onSurface).copy(0.9f),
                            fontSize = 24.sp,
                            fontFamily = digitalFont,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Icon(
                            imageVector = if (isCharging) Icons.Default.BatteryChargingFull else Icons.Default.BatteryStd,
                            contentDescription = null,
                            tint = (if (hasAlbumArt) Color.White else MaterialTheme.colorScheme.onSurface).copy(0.5f),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "$batteryLevel%",
                            color = (if (hasAlbumArt) Color.White else MaterialTheme.colorScheme.onSurface).copy(0.5f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Favorite - Large touch target with rounded corners
                Surface(
                    onClick = onToggleFavorite,
                    modifier = Modifier.size(60.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = if (state.isFavorite) Color(0xFFFF6B6B).copy(0.2f)
                    else if (hasAlbumArt) Color.White.copy(0.15f)
                    else MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            if (state.isFavorite) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = null,
                            tint = if (state.isFavorite) Color(0xFFFF6B6B)
                            else if (hasAlbumArt) Color.White
                            else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(0.12f))

            // ═══════════════════════════════════════════════════════════════
            // SONG INFO - Extra Large Text (Rounded card)
            // ═══════════════════════════════════════════════════════════════
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                color = if (hasAlbumArt) Color.White.copy(0.1f) else MaterialTheme.colorScheme.surfaceVariant.copy(0.5f)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Text(
                        text = state.title.ifBlank { "txamusic_unknown_title".txa() },
                        color = if (hasAlbumArt) Color.White else MaterialTheme.colorScheme.onSurface,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = state.artist.ifBlank { "txamusic_unknown_artist".txa() },
                        color = if (hasAlbumArt) Color.White.copy(0.7f) else MaterialTheme.colorScheme.onSurface.copy(
                            0.7f
                        ),
                        fontSize = 20.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ═══════════════════════════════════════════════════════════════
            // PROGRESS BAR - Rounded pill style
            // ═══════════════════════════════════════════════════════════════
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = if (hasAlbumArt) Color.White.copy(0.1f) else MaterialTheme.colorScheme.surfaceVariant.copy(0.3f)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            TXAFormat.formatDurationHuman(state.position),
                            color = if (hasAlbumArt) Color.White.copy(0.7f) else MaterialTheme.colorScheme.onSurface.copy(
                                0.5f
                            ),
                            fontSize = 14.sp
                        )
                        Text(
                            TXAFormat.formatDurationHuman(state.duration),
                            color = if (hasAlbumArt) Color.White.copy(0.7f) else MaterialTheme.colorScheme.onSurface.copy(
                                0.5f
                            ),
                            fontSize = 14.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Simple progress bar/slider
                    Slider(
                        value = state.progress,
                        onValueChange = {}, // Read-only mostly for drive mode safety
                        valueRange = 0f..1000f,
                        colors = SliderDefaults.colors(
                            thumbColor = accentColor,
                            activeTrackColor = accentColor,
                            inactiveTrackColor = if (hasAlbumArt) Color.White.copy(0.3f) else MaterialTheme.colorScheme.onSurface.copy(
                                0.1f
                            )
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(20.dp)
                    )
                }
            }

            // Sleep Timer Countdown Display (New)
            if (isTimerActive && remainingTime > 0) {
                Spacer(modifier = Modifier.height(16.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "txamusic_drive_mode_sleep_timer_msg".txa(),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (hasAlbumArt) Color.White.copy(0.7f) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        color = accentColor.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = TXAFormat.formatDuration(remainingTime),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = accentColor,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(30.dp))

            // ═══════════════════════════════════════════════════════════════
            // PRIMARY CONTROLS - Previous, Play/Pause, Next
            // ═══════════════════════════════════════════════════════════════
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Previous - LARGE rounded
                Surface(
                    onClick = onPrevious,
                    modifier = Modifier.size(88.dp),
                    shape = RoundedCornerShape(28.dp),
                    color = if (hasAlbumArt) Color.White.copy(0.15f) else MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.SkipPrevious,
                            contentDescription = null,
                            tint = if (hasAlbumArt) Color.White else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }

                // Play/Pause - EXTRA LARGE rounded
                Surface(
                    onClick = onPlayPause,
                    modifier = Modifier.size(130.dp),
                    shape = RoundedCornerShape(40.dp),
                    color = accentColor
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(72.dp)
                        )
                    }
                }

                // Next - LARGE rounded
                Surface(
                    onClick = onNext,
                    modifier = Modifier.size(88.dp),
                    shape = RoundedCornerShape(28.dp),
                    color = if (hasAlbumArt) Color.White.copy(0.15f) else MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.SkipNext,
                            contentDescription = null,
                            tint = if (hasAlbumArt) Color.White else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(0.1f))

            // ═══════════════════════════════════════════════════════════════
            // SECONDARY CONTROLS - Repeat, Sleep Timer, Shuffle
            // ═══════════════════════════════════════════════════════════════
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Repeat
                Surface(
                    onClick = onToggleRepeat,
                    modifier = Modifier.size(64.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = if (state.repeatMode > 0) accentColor.copy(0.2f)
                    else if (hasAlbumArt) Color.White.copy(0.1f)
                    else Color.Transparent
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        val repeatIcon = when (state.repeatMode) {
                            1 -> Icons.Default.RepeatOne
                            else -> Icons.Default.Repeat
                        }
                        Icon(
                            repeatIcon,
                            contentDescription = null,
                            tint = if (state.repeatMode > 0) accentColor
                            else if (hasAlbumArt) Color.White.copy(0.5f)
                            else MaterialTheme.colorScheme.onSurface.copy(0.5f),
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                // Sleep Timer Button (New)
                Surface(
                    onClick = onShowSleepTimer,
                    modifier = Modifier.size(64.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = if (isTimerActive) accentColor.copy(0.2f)
                    else if (hasAlbumArt) Color.White.copy(0.1f)
                    else Color.Transparent
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Outlined.Timer,
                            contentDescription = null,
                            tint = if (isTimerActive) accentColor
                            else if (hasAlbumArt) Color.White.copy(0.5f)
                            else MaterialTheme.colorScheme.onSurface.copy(0.5f),
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                // Shuffle
                Surface(
                    onClick = onToggleShuffle,
                    modifier = Modifier.size(64.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = if (state.shuffleMode) accentColor.copy(0.2f)
                    else if (hasAlbumArt) Color.White.copy(0.1f)
                    else Color.Transparent
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Shuffle,
                            contentDescription = null,
                            tint = if (state.shuffleMode) accentColor
                            else if (hasAlbumArt) Color.White.copy(0.5f)
                            else MaterialTheme.colorScheme.onSurface.copy(0.5f),
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.navigationBarsPadding())
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════════
// LANDSCAPE FULL SCREEN PLAYER
// Thiết kế dành riêng cho màn hình ngang - có scroll, full screen, chặn tương tác bên dưới
// ════════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingLandscapeFullScreen(
    state: NowPlayingState,
    nowPlayingStyle: String,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Float) -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit,
    onShowQueue: () -> Unit,
    onClose: () -> Unit,
    onDriveMode: () -> Unit = {},
    onShowSleepTimer: () -> Unit = {},
    onShowPlaybackSpeed: () -> Unit = {},
    onAddToPlaylist: () -> Unit = {},
    onShowLyrics: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val accentColor = Color(android.graphics.Color.parseColor(TXAPreferences.currentAccent))
    val scrollState = androidx.compose.foundation.rememberScrollState()

    val albumArtUri = getAlbumArtUri(state)

    // Style-specific animations & colors
    val infiniteTransition = rememberInfiniteTransition(label = "landscapeAnimations")

    // Vinyl Rotation
    val vinylRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(10000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "vinylRotation"
    )

    // Neon Pulse
    val neonPulse by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "neonPulse"
    )

    // Aurora Colors
    val auroraColors = listOf(
        Color(0xFF0F2027),
        Color(0xFF203A43),
        Color(0xFF2C5364),
        state.vibrantColor.copy(alpha = 0.4f),
        Color(0xFF0F2027)
    )

    val ringColor = when (nowPlayingStyle) {
        "neon" -> state.vibrantColor
        "glass" -> Color.White.copy(0.6f)

        else -> state.vibrantColor
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // BACKGROUND LAYER based on style
        when (nowPlayingStyle) {
            "aurora" -> {
                Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(auroraColors)))
            }

            "glass", "vinyl", "neon", "spectrum" -> {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(albumArtUri)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(if (nowPlayingStyle == "glass") 60.dp else 40.dp)
                        .graphicsLayer { alpha = if (nowPlayingStyle == "neon") 0.2f else 0.4f }
                )
                Box(
                    modifier = Modifier.fillMaxSize()
                        .background(
                            if (nowPlayingStyle == "neon") Color.Black.copy(alpha = 0.8f)
                            else Color.Black.copy(alpha = 0.5f)
                        )
                )
            }

            else -> {
                Box(
                    modifier = Modifier.fillMaxSize()
                        .background(Brush.verticalGradient(listOf(state.vibrantColor.copy(0.3f), Color.Black)))
                )
            }
        }

        // MAIN CONTENT
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(40.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // LEFT SIDE - Album Art / Visualizer / Volume
            Column(
                modifier = Modifier.weight(0.45f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier.aspectRatio(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        progress = { state.progress / 1000f },
                        modifier = Modifier.fillMaxSize(),
                        strokeWidth = if (nowPlayingStyle == "neon") 6.dp else 4.dp,
                        color = ringColor,
                        trackColor = Color.White.copy(alpha = 0.1f)
                    )

                    // The Album Art Container
                    val albumModifier = Modifier
                        .fillMaxSize()
                        .padding(if (nowPlayingStyle == "neon") 16.dp else 12.dp)
                        .clip(if (nowPlayingStyle == "neon") RoundedCornerShape(24.dp) else CircleShape)
                        .graphicsLayer {
                            if (nowPlayingStyle == "vinyl" && state.isPlaying) rotationZ = vinylRotation
                            if (nowPlayingStyle == "neon" && state.isPlaying) {
                                scaleX = neonPulse
                                scaleY = neonPulse
                            }
                        }

                    if (nowPlayingStyle == "vinyl") {
                        // Vinyl disc background
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp)
                                .background(
                                    Brush.radialGradient(listOf(Color(0xFF2D2D2D), Color.Black, Color(0xFF2D2D2D))),
                                    CircleShape
                                )
                        )
                    }

                    SubcomposeAsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(albumArtUri)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = albumModifier,
                        error = {
                            DefaultAlbumArt(
                                modifier = Modifier.fillMaxSize().clip(CircleShape),
                                vibrantColor = state.vibrantColor,
                                iconSize = 64.dp
                            )
                        }
                    )

                    if (nowPlayingStyle == "vinyl") {
                        // Center dot
                        Box(modifier = Modifier.size(12.dp).background(Color.Gray, CircleShape))
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Volume slider in landscape
                VolumeControlCompact(accentColor = ringColor)
            }

            // RIGHT SIDE - Controls Column
            Column(
                modifier = Modifier.weight(0.55f),
                verticalArrangement = Arrangement.Center
            ) {
                // Style-specific container for controls column (Glass)
                val columnContent = @Composable {
                    // Header Area
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onClose) {
                            Icon(
                                Icons.Default.KeyboardArrowDown,
                                "Close",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        Row {
                            IconButton(onClick = onDriveMode) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_drive_mode),
                                    "Drive Mode",
                                    tint = Color.White.copy(0.8f)
                                )
                            }
                            IconButton(onClick = onShowQueue) {
                                Icon(TXAIcons.QueueMusic, "Queue", tint = Color.White.copy(0.8f))
                            }
                            // More Options
                            var menuExpanded by remember { mutableStateOf(false) }
                            Box {
                                IconButton(onClick = { menuExpanded = true }) {
                                    Icon(Icons.Default.MoreVert, "More", tint = Color.White.copy(0.8f))
                                }
                                MoreOptionsDropdown(
                                    expanded = menuExpanded,
                                    onDismiss = { menuExpanded = false },
                                    onAddToPlaylist = {
                                        menuExpanded = false
                                        onAddToPlaylist()
                                    },
                                    onShowLyrics = { onShowLyrics(false) }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Song Info
                    Text(
                        text = state.title.ifBlank { "txamusic_unknown_title".txa() },
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = state.artist.ifBlank { "txamusic_unknown_artist".txa() },
                        color = (if (nowPlayingStyle == "neon") state.vibrantColor else Color.White).copy(alpha = 0.7f),
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Progress Slider
                    Column {
                        Slider(
                            value = state.progress,
                            onValueChange = onSeek,
                            valueRange = 0f..1000f,
                            colors = SliderDefaults.colors(
                                thumbColor = ringColor,
                                activeTrackColor = ringColor,
                                inactiveTrackColor = Color.White.copy(0.2f)
                            )
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                TXAFormat.formatDurationHuman(state.position),
                                color = Color.White.copy(0.6f),
                                fontSize = 12.sp
                            )
                            Text(
                                TXAFormat.formatDurationHuman(state.duration),
                                color = Color.White.copy(0.6f),
                                fontSize = 12.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Main Controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onToggleShuffle) {
                            Icon(
                                Icons.Default.Shuffle,
                                null,
                                tint = if (state.shuffleMode) ringColor else Color.White.copy(0.5f)
                            )
                        }
                        IconButton(onClick = onPrevious, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Default.SkipPrevious, null, tint = Color.White, modifier = Modifier.size(36.dp))
                        }

                        FloatingActionButton(
                            onClick = onPlayPause,
                            containerColor = ringColor,
                            contentColor = if (nowPlayingStyle == "glass") Color.Black else Color.White,
                            modifier = Modifier.size(64.dp),
                            shape = CircleShape
                        ) {
                            Icon(
                                if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                null,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        IconButton(onClick = onNext, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Default.SkipNext, null, tint = Color.White, modifier = Modifier.size(36.dp))
                        }
                        IconButton(onClick = onToggleRepeat) {
                            Icon(
                                if (state.repeatMode == 1) Icons.Default.RepeatOne else Icons.Default.Repeat,
                                null, tint = if (state.repeatMode > 0) ringColor else Color.White.copy(0.5f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Foot Actions
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        IconButton(onClick = onToggleFavorite) {
                            Icon(
                                if (state.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                null, tint = if (state.isFavorite) Color.Red else Color.White.copy(0.7f)
                            )
                        }
                        IconButton(onClick = onShowSleepTimer) {
                            Icon(Icons.Outlined.Timer, null, tint = Color.White.copy(0.7f))
                        }
                        IconButton(onClick = onShowPlaybackSpeed) {
                            Icon(Icons.Default.Speed, null, tint = Color.White.copy(0.7f))
                        }
                    }
                }

                if (nowPlayingStyle == "glass") {
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        shape = RoundedCornerShape(24.dp),
                        color = Color.White.copy(alpha = 0.1f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) { columnContent() }
                    }
                } else {
                    columnContent()
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════════
// DRIVE MODE LANDSCAPE
// Chế độ lái xe tối ưu cho màn ngang - nút cực lớn, dễ chạm
// ════════════════════════════════════════════════════════════════════════════════

@Composable
fun NowPlayingDriveModeLandscape(
    state: NowPlayingState,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleShuffle: () -> Unit = {},
    onToggleRepeat: () -> Unit = {},
    onShowSleepTimer: () -> Unit = {},
    onShowPlaybackSpeed: () -> Unit = {},
    onAddToPlaylist: () -> Unit = {},
    onClose: () -> Unit,
    onSetAsRingtone: () -> Unit = {}
) {
    val context = LocalContext.current
    val accentColor = Color(android.graphics.Color.parseColor(TXAPreferences.currentAccent))

    // Theme-aware colors
    val surfaceColor = MaterialTheme.colorScheme.surface
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val isDarkTheme = androidx.compose.foundation.isSystemInDarkTheme()

    // Sleep Timer state
    var sleepTimerRemaining by remember { mutableLongStateOf(0L) }

    LaunchedEffect(Unit) {
        while (true) {
            sleepTimerRemaining = TXASleepTimerManager.getRemainingTime(context)
            delay(1000)
        }
    }

    val albumArtUri = getAlbumArtUri(state)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(surfaceColor)
    ) {
        // Background - Album Art (dimmed)
        SubcomposeAsyncImage(
            model = ImageRequest.Builder(context)
                .data(albumArtUri)
                .crossfade(true)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .blur(30.dp)
                .graphicsLayer { alpha = 0.3f },
            error = {
                // Default album art background
                DefaultAlbumArt(
                    modifier = Modifier.fillMaxSize(),
                    vibrantColor = accentColor
                )
            }
        )

        // Overlay theo theme
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(surfaceColor.copy(alpha = 0.7f))
        )

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // LEFT - Album Art with Progress Ring & Volume
            Column(
                modifier = Modifier.weight(0.35f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Album Art with Circular Progress
                Box(
                    modifier = Modifier.size(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Progress Ring Background
                    CircularProgressIndicator(
                        progress = { 1f },
                        modifier = Modifier.fillMaxSize(),
                        strokeWidth = 6.dp,
                        color = onSurfaceColor.copy(alpha = 0.1f)
                    )

                    // Progress Ring
                    CircularProgressIndicator(
                        progress = { state.progress / 1000f },
                        modifier = Modifier.fillMaxSize(),
                        strokeWidth = 6.dp,
                        color = accentColor
                    )

                    // Album Art
                    SubcomposeAsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(albumArtUri)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(170.dp)
                            .clip(CircleShape),
                        error = {
                            DefaultAlbumArt(
                                modifier = Modifier.size(170.dp).clip(CircleShape),
                                vibrantColor = accentColor,
                                iconSize = 60.dp
                            )
                        }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Volume Control (compact)
                VolumeControlCompact(accentColor = accentColor)

                Spacer(modifier = Modifier.height(12.dp))

                // Song Title
                Text(
                    text = state.title,
                    color = onSurfaceColor,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Artist
                Text(
                    text = state.artist,
                    color = onSurfaceColor.copy(alpha = 0.7f),
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Sleep Timer Indicator - LARGE for Drive Mode visibility
                if (sleepTimerRemaining > 0) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = accentColor.copy(alpha = 0.2f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Outlined.Timer,
                                null,
                                tint = accentColor,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = TXAFormat.formatDurationHuman(sleepTimerRemaining),
                                color = accentColor,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // CENTER - Main Controls (HUGE BUTTONS)
            Row(
                modifier = Modifier.weight(0.45f),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Previous - Large
                FilledIconButton(
                    onClick = onPrevious,
                    modifier = Modifier.size(80.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = onSurfaceColor.copy(alpha = 0.15f)
                    )
                ) {
                    Icon(
                        Icons.Default.SkipPrevious,
                        null,
                        tint = onSurfaceColor,
                        modifier = Modifier.size(48.dp)
                    )
                }

                // Play/Pause - HUGE
                FilledIconButton(
                    onClick = onPlayPause,
                    modifier = Modifier.size(120.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = accentColor)
                ) {
                    Icon(
                        if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        null,
                        tint = Color.White,
                        modifier = Modifier.size(72.dp)
                    )
                }

                // Next - Large
                FilledIconButton(
                    onClick = onNext,
                    modifier = Modifier.size(80.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = onSurfaceColor.copy(alpha = 0.15f)
                    )
                ) {
                    Icon(
                        Icons.Default.SkipNext,
                        null,
                        tint = onSurfaceColor,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }

            // RIGHT - Secondary Controls
            Column(
                modifier = Modifier.weight(0.2f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Close
                FilledIconButton(
                    onClick = onClose,
                    modifier = Modifier.size(56.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = onSurfaceColor.copy(alpha = 0.1f)
                    )
                ) {
                    Icon(Icons.Default.Close, null, tint = onSurfaceColor, modifier = Modifier.size(32.dp))
                }

                // Favorite
                FilledIconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier.size(56.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = if (state.isFavorite) Color.Red.copy(alpha = 0.3f) else onSurfaceColor.copy(
                            alpha = 0.1f
                        )
                    )
                ) {
                    Icon(
                        if (state.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        null,
                        tint = if (state.isFavorite) Color.Red else onSurfaceColor,
                        modifier = Modifier.size(32.dp)
                    )
                }

                // Sleep Timer
                FilledIconButton(
                    onClick = onShowSleepTimer,
                    modifier = Modifier.size(56.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = if (sleepTimerRemaining > 0) accentColor.copy(alpha = 0.3f) else onSurfaceColor.copy(
                            alpha = 0.1f
                        )
                    )
                ) {
                    Icon(
                        Icons.Outlined.Timer,
                        null,
                        tint = if (sleepTimerRemaining > 0) accentColor else onSurfaceColor,
                        modifier = Modifier.size(32.dp)
                    )
                }

                // Shuffle
                FilledIconButton(
                    onClick = onToggleShuffle,
                    modifier = Modifier.size(56.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = if (state.shuffleMode) accentColor.copy(alpha = 0.3f) else onSurfaceColor.copy(
                            alpha = 0.1f
                        )
                    )
                ) {
                    Icon(
                        Icons.Default.Shuffle,
                        null,
                        tint = if (state.shuffleMode) accentColor else onSurfaceColor,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════════
// MORE OPTIONS MENU COMPOSABLE
// Dropdown menu với Add to Playlist và Playback Speed
// ════════════════════════════════════════════════════════════════════════════════

@Composable
fun MoreOptionsDropdown(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onEditTag: () -> Unit = {},
    onSetRingtone: () -> Unit = {},
    onShowLyrics: () -> Unit = {},
    isLyricsShowing: Boolean = false
) {
    val context = LocalContext.current
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss
    ) {
        DropdownMenuItem(
            text = { 
                Text(
                    if (isLyricsShowing) "txamusic_hide_lyrics".txa() 
                    else "txamusic_lyrics".txa()
                ) 
            },
            onClick = {
                onDismiss()
                onShowLyrics()
            },
            leadingIcon = { Icon(Icons.Default.Lyrics, null) }
        )



        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = Color.Gray.copy(alpha = 0.2f))

        DropdownMenuItem(
            text = { Text("txamusic_add_to_playlist".txa()) },
            onClick = {
                onDismiss()
                onAddToPlaylist()
            },
            leadingIcon = { Icon(TXAIcons.PlaylistAdd, null) }
        )

        DropdownMenuItem(
            text = { Text("txamusic_edit_tag".txa()) },
            onClick = {
                onDismiss()
                onEditTag()
            },
            leadingIcon = { Icon(Icons.Default.Edit, null) }
        )

        DropdownMenuItem(
            text = { Text("txamusic_set_as_ringtone".txa()) },
            onClick = {
                onDismiss()
                onSetRingtone()
            },
            leadingIcon = { Icon(Icons.Default.Notifications, null) }
        )
    }
}


/**
 * Synced Lyrics View for Player - Karaoke-style (NCT/ZingMP3)
 * Features:
 * - Word-by-word highlight based on timing (Smooth Gradient)
 * - Double-tap to seek to lyric position
 */
@Composable
fun SyncedLyricsView(
    lyrics: List<LyricLine>,
    currentPosition: Long,
    accentColor: Color,
    onLyricClick: (Long) -> Unit = {}
) {
    val listState = rememberLazyListState()
    
    // Calculates the active index based on current position + offset
    val activeIndex = remember(lyrics, currentPosition) {
        val adjustedPos = currentPosition + 300 // Slight offset for better sync
        lyrics.indexOfLast { it.timestamp <= adjustedPos }.coerceAtLeast(0)
    }
    
    // Auto-scroll to active line
    LaunchedEffect(activeIndex) {
        if (lyrics.isNotEmpty() && activeIndex >= 0) {
            // Check if we need to snap (first load) or animate
            val targetIndex = (activeIndex - 1).coerceAtLeast(0)
            
            // If the difference is large (e.g. re-entering screen), snap instead of scroll
            // This prevents the "scroll from top" visual glitch
            val firstVisible = listState.firstVisibleItemIndex
            if (kotlin.math.abs(firstVisible - targetIndex) > 10) {
                listState.scrollToItem(targetIndex)
            } else {
                listState.animateScrollToItem(targetIndex)
            }
        }
    }
    
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(vertical = 120.dp)
    ) {
        itemsIndexed(lyrics) { index, line ->
            val isCurrent = index == activeIndex
            val isPast = index < activeIndex
            
            // Animation for current line scaling
            val scale by animateFloatAsState(
                targetValue = if (isCurrent) 1.05f else 1f, 
                animationSpec = tween(300),
                label = "scale"
            )

            // Karaoke Fill Logic
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp, horizontal = 20.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        alpha = if (isCurrent) 1f else if (isPast) 0.4f else 0.6f
                    }
                    .pointerInput(line.timestamp) {
                        detectTapGestures(
                            onDoubleTap = {
                                onLyricClick(line.timestamp)
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                if (isCurrent) {
                    // Calculate fill progress
                    val duration = line.endTimestamp - line.timestamp
                    val progress = if (duration > 0) {
                        ((currentPosition - line.timestamp).toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                    } else {
                        0f
                    }

                    // Gradient Brush for Karaoke Effect
                    val currentBrush = Brush.horizontalGradient(
                        0.0f to accentColor,
                        progress to accentColor,
                        progress + 0.05f to Color.White.copy(alpha = 0.7f), // Soft edge
                        1.0f to Color.White.copy(alpha = 0.7f)
                    )

                    Text(
                        text = line.text,
                        style = TextStyle(
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            brush = currentBrush
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    // Standard text for non-active lines
                    Text(
                        text = line.text,
                        style = TextStyle(
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            color = if (isPast) Color.White.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.7f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
