package com.txapp.musicplayer.ui.component

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import com.txapp.musicplayer.ui.component.TXAIcons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import com.txapp.musicplayer.util.TXAFormat
import com.txapp.musicplayer.util.TXALogger
import com.txapp.musicplayer.util.txa
import android.content.Context
import android.media.AudioManager
import android.content.res.Configuration
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.txapp.musicplayer.R
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex

/**
 * Now Playing View State to share data between Activity and Compose
 */
data class NowPlayingState(
    val songId: Long = -1L,
    val title: String = "",
    val artist: String = "",
    val albumId: Long = -1L,
    val mediaUri: String = "",
    val isPlaying: Boolean = false,
    val progress: Float = 0f, // 0..1000
    val position: Long = 0,
    val duration: Long = 0,
    val isFavorite: Boolean = false,
    val shuffleMode: Boolean = false,
    val repeatMode: Int = 0, // 0: off, 1: all, 2: one
    val vibrantColor: Color = Color(0xFF00D269),
    val nextSongTitle: String = "", // For Full style top bar
    val songInfo: String = "", // For Full style codec/bitrate info
    val webArtUrl: String = "", // Web fetched album art
    // Queue support for AlbumCoverPager
    val queueItems: List<AlbumCoverItem> = emptyList(),
    val currentQueueIndex: Int = 0,
    val sleepTimerRemainingMs: Long? = null, // Remaining sleep timer time
    val lyrics: String? = null // Lyrics content
)

/**
 * Main switch for Now Playing Styles
 */
@Composable
fun NowPlayingContent(
    style: String,
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
    onPageChanged: (Int) -> Unit = {},
    onArtistClick: () -> Unit = {}
) {
    // Swipe to dismiss state
    var offsetY by remember { mutableFloatStateOf(0f) }
    val animatedOffsetY by animateFloatAsState(
        targetValue = offsetY,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "swipeOffset"
    )
    val dismissThreshold = 200f // pixels to trigger dismiss

    Box(
        modifier = Modifier
            .fillMaxSize()
            .offset { IntOffset(0, animatedOffsetY.toInt()) }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        if (offsetY > dismissThreshold) {
                            // Trigger close with animation
                            onClose()
                        }
                        // Reset offset
                        offsetY = 0f
                    },
                    onDragCancel = {
                        offsetY = 0f
                    },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        // Only allow dragging down (positive direction)
                        if (dragAmount > 0 || offsetY > 0) {
                            offsetY = (offsetY + dragAmount).coerceAtLeast(0f)
                        }
                    }
                )
            }
            .graphicsLayer {
                // Add slight scale and alpha effect while swiping
                val progress = (offsetY / dismissThreshold).coerceIn(0f, 1f)
                scaleX = 1f - (progress * 0.05f)
                scaleY = 1f - (progress * 0.05f)
                alpha = 1f - (progress * 0.3f)
            }
    ) {
        // Swipe indicator at top
        if (offsetY > 10f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .zIndex(10f),
                contentAlignment = Alignment.TopCenter
            ) {
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = 0.5f))
                )
            }
        }

        when (style) {
            // === NEW TXA PLAYER STYLES ===
            "aurora" -> TXAPlayerAuroraStyle(
                state = state,
                onPlayPause = onPlayPause,
                onNext = onNext,
                onPrevious = onPrevious,
                onSeek = onSeek,
                onToggleFavorite = onToggleFavorite,
                onToggleShuffle = onToggleShuffle,
                onToggleRepeat = onToggleRepeat,
                onShowQueue = onShowQueue,
                onClose = onClose,
                onDriveMode = onDriveMode,
                onAddToPlaylist = onAddToPlaylist,
                onEditTag = onEditTag,
                onSetRingtone = onSetRingtone,
                onShowSleepTimer = onShowSleepTimer,
                onShowPlaybackSpeed = onShowPlaybackSpeed,
                onShowLyrics = onShowLyrics,
                onPageChanged = onPageChanged
            )

            "glass" -> TXAPlayerGlassStyle(
                state = state,
                onPlayPause = onPlayPause,
                onNext = onNext,
                onPrevious = onPrevious,
                onSeek = onSeek,
                onToggleFavorite = onToggleFavorite,
                onToggleShuffle = onToggleShuffle,
                onToggleRepeat = onToggleRepeat,
                onShowQueue = onShowQueue,
                onClose = onClose,
                onDriveMode = onDriveMode,
                onAddToPlaylist = onAddToPlaylist,
                onEditTag = onEditTag,
                onSetRingtone = onSetRingtone,
                onShowLyrics = onShowLyrics
            )

            "vinyl" -> TXAPlayerVinylStyle(
                state = state,
                onPlayPause = onPlayPause,
                onNext = onNext,
                onPrevious = onPrevious,
                onSeek = onSeek,
                onToggleFavorite = onToggleFavorite,
                onToggleShuffle = onToggleShuffle,
                onToggleRepeat = onToggleRepeat,
                onShowQueue = onShowQueue,
                onClose = onClose,
                onDriveMode = onDriveMode,
                onAddToPlaylist = onAddToPlaylist,
                onEditTag = onEditTag,
                onSetRingtone = onSetRingtone,
                onShowLyrics = onShowLyrics
            )

            "neon" -> TXAPlayerNeonStyle(
                state = state,
                onPlayPause = onPlayPause,
                onNext = onNext,
                onPrevious = onPrevious,
                onSeek = onSeek,
                onToggleFavorite = onToggleFavorite,
                onToggleShuffle = onToggleShuffle,
                onToggleRepeat = onToggleRepeat,
                onShowQueue = onShowQueue,
                onClose = onClose,
                onDriveMode = onDriveMode,
                onAddToPlaylist = onAddToPlaylist,
                onEditTag = onEditTag,
                onSetRingtone = onSetRingtone,
                onShowLyrics = onShowLyrics
            )

            "spectrum" -> TXAPlayerSpectrumStyle(
                state = state,
                onPlayPause = onPlayPause,
                onNext = onNext,
                onPrevious = onPrevious,
                onSeek = onSeek,
                onToggleFavorite = onToggleFavorite,
                onToggleShuffle = onToggleShuffle,
                onToggleRepeat = onToggleRepeat,
                onShowQueue = onShowQueue,
                onClose = onClose,
                onDriveMode = onDriveMode,
                onAddToPlaylist = onAddToPlaylist,
                onEditTag = onEditTag,
                onSetRingtone = onSetRingtone,
                onShowLyrics = onShowLyrics
            )

            "adaptive" -> NowPlayingAdaptiveStyle(
                state = state,
                onPlayPause = onPlayPause,
                onNext = onNext,
                onPrevious = onPrevious,
                onSeek = onSeek,
                onToggleFavorite = onToggleFavorite,
                onToggleShuffle = onToggleShuffle,
                onToggleRepeat = onToggleRepeat,
                onShowQueue = onShowQueue,
                onClose = onClose,
                onDriveMode = onDriveMode,
                onAddToPlaylist = onAddToPlaylist,
                onEditTag = onEditTag,
                onSetRingtone = onSetRingtone,
                onShowSleepTimer = onShowSleepTimer,
                onShowPlaybackSpeed = onShowPlaybackSpeed,
                onShowLyrics = onShowLyrics
            )

            // Full player with all features

            "full" -> NowPlayingFullStyle(
                state = state,
                onPlayPause = onPlayPause,
                onNext = onNext,
                onPrevious = onPrevious,
                onSeek = onSeek,
                onToggleFavorite = onToggleFavorite,
                onToggleShuffle = onToggleShuffle,
                onToggleRepeat = onToggleRepeat,
                onShowQueue = onShowQueue,
                onClose = onClose,
                onShowSleepTimer = onShowSleepTimer,
                onShowLyrics = onShowLyrics,
                onShowPlaybackSpeed = onShowPlaybackSpeed,
                onAddToPlaylist = onAddToPlaylist,
                onEditTag = onEditTag,
                onSetRingtone = onSetRingtone,
                onDriveMode = onDriveMode,
                onArtistClick = onArtistClick
            )

            // Drive Mode
            "drive" -> NowPlayingDriveModeStyle(
                state = state,
                onPlayPause = onPlayPause,
                onNext = onNext,
                onPrevious = onPrevious,
                onToggleFavorite = onToggleFavorite,
                onToggleShuffle = onToggleShuffle,
                onToggleRepeat = onToggleRepeat,
                onShowSleepTimer = onShowSleepTimer,
                onClose = onDriveMode,
                onSetAsRingtone = onSetRingtone
            )

            // Default to Aurora (new default)
            else -> TXAPlayerAuroraStyle(
                state,
                onPlayPause,
                onNext,
                onPrevious,
                onSeek,
                onToggleFavorite,
                onToggleShuffle,
                onToggleRepeat,
                onShowQueue,
                onClose,
                onDriveMode,
                onAddToPlaylist,
                onEditTag,
                onSetRingtone,
                onShowSleepTimer,
                onShowPlaybackSpeed,
                onShowLyrics,
                onPageChanged
            )
        }
    }
}


/**
 * Component for Volume Control
 */
@Composable
fun VolumeControl(
    modifier: Modifier = Modifier,
    vibrantColor: Color = MaterialTheme.colorScheme.primary
) {
    val context = LocalContext.current
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    var currentVolume by remember {
        mutableStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC))
    }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (currentVolume == 0) TXAIcons.VolumeOff else TXAIcons.VolumeDown,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.size(20.dp)
        )
        Slider(
            value = currentVolume.toFloat(),
            onValueChange = {
                currentVolume = it.toInt()
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, it.toInt(), 0)
            },
            valueRange = 0f..maxVolume.toFloat(),
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = vibrantColor,
                inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
            )
        )
        Icon(
            TXAIcons.VolumeUp,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.size(20.dp)
        )
    }
}

/**
 * Premium Default Album Art with Gradient
 */
@Composable
fun DefaultAlbumArt(
    modifier: Modifier = Modifier,
    vibrantColor: Color = MaterialTheme.colorScheme.primary,
    iconSize: androidx.compose.ui.unit.Dp = 48.dp
) {
    LaunchedEffect(Unit) {
        TXALogger.albumArtI("NowPlayingStyles", "Showing DefaultAlbumArt (AlbumArt failed to load or missing)")
    }
    Box(
        modifier = modifier
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        vibrantColor.copy(alpha = 0.8f),
                        vibrantColor.copy(alpha = 0.4f),
                        Color.Black.copy(alpha = 0.6f)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        // Use app logo instead of music note icon
        Image(
            painter = painterResource(id = com.txapp.musicplayer.R.drawable.ic_launcher),
            contentDescription = null,
            modifier = Modifier.size(iconSize),
            contentScale = ContentScale.Fit
        )
    }
}

/**
 * Helper to get proper Album Art URI
 */
fun getAlbumArtUri(state: NowPlayingState): Any {
    // 1. Emulator Specific Logic: Prefer Web Art
    if (com.txapp.musicplayer.util.TXADeviceInfo.isEmulator()) {
        if (state.webArtUrl.isNotEmpty()) return state.webArtUrl
        // If web art not found yet on emulator, fallthrough to try local or default
    }

    // 2. Standard Logic (Restored): Priorities robust local MediaStore URI
    // This was the "old code" that worked reliable on real devices
    return if (state.albumId != -1L) {
        android.content.ContentUris.withAppendedId(
            android.net.Uri.parse("content://media/external/audio/albumart"),
            state.albumId
        )
    } else {
        // Fallback for files without album ID (e.g. external files)
        state.mediaUri.ifEmpty { "" }
    }
}

/**
 * ADAPTIVE STYLE
 * Vibrant backgrounds, large album art
 */
@Composable
fun NowPlayingAdaptiveStyle(
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
    onDriveMode: () -> Unit = {},
    onAddToPlaylist: () -> Unit = {},
    onEditTag: () -> Unit = {},
    onSetRingtone: () -> Unit = {},
    onShowSleepTimer: () -> Unit = {},
    onShowPlaybackSpeed: () -> Unit = {},
    onShowLyrics: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val albumUri = getAlbumArtUri(state)
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val scrollState = rememberScrollState()
    var showMoreOptions by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Dynamic Gradient Background
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            state.vibrantColor.copy(alpha = 0.4f),
                            MaterialTheme.colorScheme.background
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(24.dp)
                .then(if (isLandscape) Modifier.verticalScroll(scrollState) else Modifier),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Volume Area
            VolumeControl(vibrantColor = state.vibrantColor)

            Spacer(modifier = Modifier.height(16.dp))

            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(
                        TXAIcons.KeyboardArrowDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = "txamusic_settings_section_now_playing".txa(),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Row {
                    IconButton(onClick = onDriveMode) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_drive),
                            contentDescription = "txamusic_drive_mode".txa(),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = onShowQueue) {
                        Icon(TXAIcons.QueueMusic, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
                    }
                    Box {
                        IconButton(onClick = { showMoreOptions = true }) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = "txamusic_more".txa(),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        MoreOptionsPlayerDropdown(
                            expanded = showMoreOptions,
                            onDismiss = { showMoreOptions = false },
                            onAddToPlaylist = onAddToPlaylist,
                            onEditTag = onEditTag,
                            onSetRingtone = onSetRingtone,
                            onShowLyrics = { onShowLyrics(false) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(0.1f))

            // Album Art with Circular Progress
            Box(contentAlignment = Alignment.Center) {
                // Circular Progress around image
                CircularProgressIndicator(
                    progress = { state.progress / 1000f },
                    modifier = Modifier.size(if (isLandscape) 220.dp else 310.dp),
                    color = state.vibrantColor,
                    strokeWidth = 4.dp,
                    trackColor = MaterialTheme.colorScheme.onSurface.copy(0.05f)
                )

                Surface(
                    modifier = Modifier
                        .size(if (isLandscape) 180.dp else 260.dp)
                        .clip(CircleShape),
                    shadowElevation = if (android.os.Build.VERSION.SDK_INT <= 28) 4.dp else 16.dp,
                    color = Color.Black
                ) {
                    SubcomposeAsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(albumUri)
                            .size(600)
                            .crossfade(true).build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        val imageState = painter.state
                        if (imageState is coil.compose.AsyncImagePainter.State.Loading || imageState is coil.compose.AsyncImagePainter.State.Error) {
                            DefaultAlbumArt(vibrantColor = state.vibrantColor)
                        } else {
                            SubcomposeAsyncImageContent()
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(0.1f))

            // Info
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = state.title,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = if (isLandscape) 20.sp else 26.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = state.artist,
                    color = state.vibrantColor,
                    fontSize = if (isLandscape) 14.sp else 18.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Progress
            Column {
                Slider(
                    value = state.progress,
                    onValueChange = onSeek,
                    valueRange = 0f..1000f,
                    colors = SliderDefaults.colors(
                        thumbColor = state.vibrantColor,
                        activeTrackColor = state.vibrantColor,
                        inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                    )
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        TXAFormat.formatDurationHuman(state.position),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        fontSize = 12.sp
                    )
                    Text(
                        TXAFormat.formatDurationHuman(state.duration),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.weight(0.1f))

            // Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onToggleShuffle) {
                    Icon(
                        TXAIcons.Shuffle,
                        contentDescription = null,
                        tint = if (state.shuffleMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(
                            alpha = 0.5f
                        )
                    )
                }

                IconButton(onClick = onPrevious) {
                    Icon(
                        TXAIcons.SkipPrevious,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(40.dp)
                    )
                }

                FloatingActionButton(
                    onClick = onPlayPause,
                    containerColor = state.vibrantColor,
                    shape = CircleShape
                ) {
                    Icon(
                        if (state.isPlaying) TXAIcons.Pause else TXAIcons.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(40.dp)
                    )
                }

                IconButton(onClick = onNext) {
                    Icon(
                        TXAIcons.SkipNext,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(40.dp)
                    )
                }

                IconButton(onClick = onToggleRepeat) {
                    val icon = when (state.repeatMode) {
                        1 -> TXAIcons.RepeatOne
                        else -> TXAIcons.Repeat
                    }
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = if (state.repeatMode > 0) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.5f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Bottom Row: Extra Tools & Favorite
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left side: Tools
                Row {
                    IconButton(onClick = onShowSleepTimer) {
                        Icon(
                            imageVector = Icons.Default.Timer,
                            contentDescription = "txamusic_sleep_timer".txa(),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                    IconButton(onClick = onShowPlaybackSpeed) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = "txamusic_playback_speed".txa(),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                    IconButton(onClick = { onShowLyrics(true) }) {
                        Icon(
                            imageVector = TXAIcons.Lyrics,
                            contentDescription = "txamusic_playing".txa(),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }

                // Right side: Drive Mode & Favorite
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDriveMode) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_drive),
                            contentDescription = "txamusic_drive_mode".txa(),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(onClick = onToggleFavorite) {
                        Icon(
                            if (state.isFavorite) TXAIcons.Favorite else TXAIcons.FavoriteBorder,
                            contentDescription = null,
                            tint = if (state.isFavorite) Color.Red else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.navigationBarsPadding())
        }
    }
}

/**
 * CLASSIC STYLE
 * Rotating Vinyl Record
 */
@Suppress("FunctionName")
@Composable
fun NowPlayingClassicStyle(
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
    onDriveMode: () -> Unit = {}
) {
    val context = LocalContext.current
    val albumUri = getAlbumArtUri(state)
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val scrollState = rememberScrollState()

    // Rotation animation
    val infiniteTransition = rememberInfiniteTransition(label = "Rotation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "VinylRotation"
    )

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(24.dp)
                .then(if (isLandscape) Modifier.verticalScroll(scrollState) else Modifier),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            VolumeControl(vibrantColor = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(16.dp))

            // Header
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                IconButton(onClick = onClose) {
                    Icon(
                        TXAIcons.KeyboardArrowDown,
                        null,
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Row {
                    IconButton(onClick = onDriveMode) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_drive),
                            contentDescription = "txamusic_drive_mode".txa(),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = onShowQueue) {
                        Icon(
                            TXAIcons.QueueMusic,
                            null,
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(0.1f))

            // Vinyl Record
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { state.progress / 1000f },
                    modifier = Modifier.size(if (isLandscape) 220.dp else 320.dp),
                    color = state.vibrantColor,
                    strokeWidth = 2.dp,
                    trackColor = MaterialTheme.colorScheme.onSurface.copy(0.05f)
                )

                Box(
                    modifier = Modifier
                        .size(if (isLandscape) 180.dp else 280.dp)
                        .graphicsLayer { rotationZ = if (state.isPlaying) rotation else 0f },
                    contentAlignment = Alignment.Center
                ) {
                    // Outer black circle
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        shape = CircleShape,
                        color = Color.Black,
                        border = BorderStroke(1.dp, Color.DarkGray)
                    ) {}

                    // Center Album Art
                    Surface(
                        modifier = Modifier
                            .size(if (isLandscape) 80.dp else 120.dp)
                            .clip(CircleShape),
                        color = Color.Black
                    ) {
                        SubcomposeAsyncImage(
                            model = ImageRequest.Builder(context).data(albumUri)
                                .size(300)
                                .crossfade(true).build(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            val imageState = painter.state
                            if (imageState is coil.compose.AsyncImagePainter.State.Loading || imageState is coil.compose.AsyncImagePainter.State.Error) {
                                DefaultAlbumArt(vibrantColor = state.vibrantColor, iconSize = 40.dp)
                            } else {
                                SubcomposeAsyncImageContent()
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(0.1f))

            // Traditional Lyrics/Info layout
            Text(
                state.title,
                fontSize = if (isLandscape) 20.sp else 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Text(
                state.artist,
                fontSize = if (isLandscape) 14.sp else 16.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(0.6f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Simple Linear Controls
            Slider(
                value = state.progress,
                onValueChange = onSeek,
                valueRange = 0f..1000f,
                colors = SliderDefaults.colors(
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    thumbColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                )
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    TXAFormat.formatDurationHuman(state.position),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    fontSize = 12.sp
                )
                Text(
                    TXAFormat.formatDurationHuman(state.duration),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.weight(0.1f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onPrevious) {
                    Icon(
                        TXAIcons.SkipPrevious,
                        null,
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                IconButton(
                    onClick = onPlayPause,
                    modifier = Modifier.size(64.dp).background(MaterialTheme.colorScheme.primary, CircleShape)
                ) {
                    Icon(
                        if (state.isPlaying) TXAIcons.Pause else TXAIcons.PlayArrow,
                        null,
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }

                IconButton(onClick = onNext) {
                    Icon(
                        TXAIcons.SkipNext,
                        null,
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.navigationBarsPadding())
        }
    }
}

/**
 * MATERIAL STYLE
 * Modern MD3 design with cards and distinct sections
 */
@Composable
fun NowPlayingMaterialStyle(
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
    onDriveMode: () -> Unit = {}
) {
    val context = LocalContext.current
    val albumUri = getAlbumArtUri(state)
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val scrollState = rememberScrollState()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(16.dp)
                .then(if (isLandscape) Modifier.verticalScroll(scrollState) else Modifier),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            VolumeControl(vibrantColor = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))

            // Minimal Header
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(TXAIcons.Close, null) }
                Spacer(modifier = Modifier.weight(1f))
                Text("txamusic_settings_section_now_playing".txa(), fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.weight(1f))
                Row {
                    IconButton(onClick = onDriveMode) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_drive),
                            contentDescription = "txamusic_drive_mode".txa(),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    IconButton(onClick = onShowQueue) { Icon(TXAIcons.QueueMusic, null) }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Large Rounded Album Art
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { state.progress / 1000f },
                    modifier = Modifier.size(320.dp),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 2.dp
                )
                ElevatedCard(
                    modifier = Modifier
                        .size(280.dp),
                    shape = RoundedCornerShape(32.dp),
                    elevation = CardDefaults.elevatedCardElevation(
                        defaultElevation = if (android.os.Build.VERSION.SDK_INT <= 28) 2.dp else 4.dp
                    )
                ) {
                    SubcomposeAsyncImage(
                        model = ImageRequest.Builder(context).data(albumUri)
                            .size(600)
                            .crossfade(true).build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        val imageState = painter.state
                        if (imageState is coil.compose.AsyncImagePainter.State.Loading || imageState is coil.compose.AsyncImagePainter.State.Error) {
                            DefaultAlbumArt(vibrantColor = state.vibrantColor)
                        } else {
                            SubcomposeAsyncImageContent()
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Title & Favorite
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        state.title,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(state.artist, fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        if (state.isFavorite) TXAIcons.Favorite else TXAIcons.FavoriteBorder,
                        null,
                        tint = if (state.isFavorite) Color.Red else LocalContentColor.current
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Modern Slider
            Column {
                Slider(
                    value = state.progress,
                    onValueChange = onSeek,
                    valueRange = 0f..1000f
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(TXAFormat.formatDurationHuman(state.position), style = MaterialTheme.typography.labelSmall)
                    Text(TXAFormat.formatDurationHuman(state.duration), style = MaterialTheme.typography.labelSmall)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Controls with MD3 FAB feel
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onToggleShuffle) {
                    Icon(
                        TXAIcons.Shuffle,
                        null,
                        tint = if (state.shuffleMode) MaterialTheme.colorScheme.primary else LocalContentColor.current
                    )
                }

                IconButton(onClick = onPrevious) { Icon(TXAIcons.SkipPrevious, null) }

                FilledIconButton(
                    onClick = onPlayPause,
                    modifier = Modifier.size(64.dp)
                ) {
                    Icon(
                        if (state.isPlaying) TXAIcons.Pause else TXAIcons.PlayArrow,
                        null
                    )
                }

                IconButton(onClick = onNext) { Icon(TXAIcons.SkipNext, null) }

                IconButton(onClick = onToggleRepeat) {
                    val icon = if (state.repeatMode == 1) TXAIcons.RepeatOne else TXAIcons.Repeat
                    Icon(
                        icon,
                        null,
                        tint = if (state.repeatMode > 0) MaterialTheme.colorScheme.primary else LocalContentColor.current.copy(
                            0.4f
                        )
                    )
                }


            }

            Spacer(modifier = Modifier.height(32.dp))
            Spacer(modifier = Modifier.navigationBarsPadding())
        }
    }
}

/**
 * BLUR STYLE
 * Heavy blur background, focus on typography and minimalist art
 */
@Composable
fun NowPlayingBlurStyle(
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
    onDriveMode: () -> Unit = {}
) {
    val context = LocalContext.current
    val albumUri = getAlbumArtUri(state)
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val scrollState = rememberScrollState()

    Box(modifier = Modifier.fillMaxSize()) {
        // Fullscreen Blurred Background
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(albumUri)
                .size(400) // Lower res for background
                .crossfade(true)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().graphicsLayer { alpha = 0.5f }
        )

        // Darkened glass overlay
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background.copy(alpha = 0.6f)))

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(24.dp)
                .then(if (isLandscape) Modifier.verticalScroll(scrollState) else Modifier),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            VolumeControl(vibrantColor = state.vibrantColor)
            Spacer(modifier = Modifier.height(16.dp))

            // Header
            Row(modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = onClose) {
                    Icon(
                        TXAIcons.KeyboardArrowDown,
                        null,
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Row {
                    IconButton(onClick = onDriveMode) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_drive),
                            contentDescription = "txamusic_drive_mode".txa(),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = onShowQueue) {
                        Icon(
                            TXAIcons.QueueMusic,
                            null,
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(0.2f))

            // Floating Small Art
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { state.progress / 1000f },
                    modifier = Modifier.size(if (isLandscape) 180.dp else 260.dp),
                    color = state.vibrantColor,
                    strokeWidth = 4.dp,
                    trackColor = MaterialTheme.colorScheme.onSurface.copy(0.1f)
                )
                Surface(
                    modifier = Modifier.size(if (isLandscape) 150.dp else 220.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shadowElevation = if (android.os.Build.VERSION.SDK_INT <= 28) 4.dp else 24.dp
                ) {
                    SubcomposeAsyncImage(
                        model = ImageRequest.Builder(context).data(albumUri)
                            .size(300)
                            .crossfade(true).build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        val imageState = painter.state
                        if (imageState is coil.compose.AsyncImagePainter.State.Loading || imageState is coil.compose.AsyncImagePainter.State.Error) {
                            DefaultAlbumArt(vibrantColor = state.vibrantColor)
                        } else {
                            SubcomposeAsyncImageContent()
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(0.1f))

            // Text Info
            Text(
                state.title,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = if (isLandscape) 22.sp else 28.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                state.artist,
                color = MaterialTheme.colorScheme.onSurface.copy(0.7f),
                fontSize = if (isLandscape) 14.sp else 18.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.weight(0.2f))

            // Glass Controls
            Surface(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(0.1f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.Center) {
                    Slider(
                        value = state.progress,
                        onValueChange = onSeek,
                        valueRange = 0f..1000f,
                        colors = SliderDefaults.colors(
                            activeTrackColor = state.vibrantColor,
                            thumbColor = state.vibrantColor
                        )
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            TXAFormat.formatDurationHuman(state.position),
                            color = MaterialTheme.colorScheme.onSurface.copy(0.5f),
                            fontSize = 10.sp
                        )
                        Text(
                            TXAFormat.formatDurationHuman(state.duration),
                            color = MaterialTheme.colorScheme.onSurface.copy(0.5f),
                            fontSize = 10.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onPrevious) {
                            Icon(
                                TXAIcons.SkipPrevious,
                                null,
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        IconButton(onClick = onPlayPause) {
                            Icon(
                                if (state.isPlaying) TXAIcons.Pause else TXAIcons.PlayArrow,
                                null,
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(48.dp)
                            )
                        }
                        IconButton(onClick = onNext) {
                            Icon(
                                TXAIcons.SkipNext,
                                null,
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.navigationBarsPadding())
        }
    }
}

/**
 * GRADIENT STYLE
 * Fullscreen colorful gradient based on album art colors
 */
@Composable
fun NowPlayingGradientStyle(
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
    onDriveMode: () -> Unit = {}
) {
    val context = LocalContext.current
    val albumUri = getAlbumArtUri(state)
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val scrollState = rememberScrollState()

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            state.vibrantColor,
                            MaterialTheme.colorScheme.background
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(24.dp)
                .then(if (isLandscape) Modifier.verticalScroll(scrollState) else Modifier),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            VolumeControl(vibrantColor = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(
                        TXAIcons.KeyboardArrowLeft,
                        null,
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Row {
                    IconButton(onClick = onDriveMode) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_drive),
                            contentDescription = "txamusic_drive_mode".txa(),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = onShowQueue) {
                        Icon(
                            TXAIcons.PlaylistPlay,
                            null,
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(0.1f))

            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { state.progress / 1000f },
                    modifier = Modifier.size(if (isLandscape) 200.dp else 300.dp),
                    color = MaterialTheme.colorScheme.onSurface,
                    strokeWidth = 3.dp,
                    trackColor = MaterialTheme.colorScheme.onSurface.copy(0.1f)
                )
                Surface(
                    modifier = Modifier.size(if (isLandscape) 180.dp else 280.dp),
                    shape = CircleShape,
                    shadowElevation = 8.dp,
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    SubcomposeAsyncImage(
                        model = ImageRequest.Builder(context).data(albumUri)
                            .crossfade(true).build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        val imageState = painter.state
                        if (imageState is coil.compose.AsyncImagePainter.State.Loading || imageState is coil.compose.AsyncImagePainter.State.Error) {
                            DefaultAlbumArt(vibrantColor = state.vibrantColor)
                        } else {
                            SubcomposeAsyncImageContent()
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(if (isLandscape) 12.dp else 24.dp))

            Text(
                state.title,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = if (isLandscape) 20.sp else 24.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                state.artist,
                color = MaterialTheme.colorScheme.onSurface.copy(0.7f),
                fontSize = if (isLandscape) 14.sp else 16.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.weight(1f))

            Slider(
                value = state.progress,
                onValueChange = onSeek,
                valueRange = 0f..1000f,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.onSurface,
                    activeTrackColor = MaterialTheme.colorScheme.onSurface,
                    inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                )
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    TXAFormat.formatDurationHuman(state.position),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    fontSize = 12.sp
                )
                Text(
                    TXAFormat.formatDurationHuman(state.duration),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    fontSize = 12.sp
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onToggleShuffle) {
                    Icon(
                        TXAIcons.Shuffle,
                        null,
                        tint = if (state.shuffleMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(
                            0.4f
                        )
                    )
                }

                IconButton(onClick = onPrevious) {
                    Icon(
                        TXAIcons.SkipPrevious,
                        null,
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                IconButton(onClick = onPlayPause) {
                    Icon(
                        if (state.isPlaying) TXAIcons.PauseCircleFilled else TXAIcons.PlayCircleFilled,
                        null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(64.dp)
                    )
                }
                IconButton(onClick = onNext) {
                    Icon(
                        TXAIcons.SkipNext,
                        null,
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }


                IconButton(onClick = onToggleRepeat) {
                    val icon = if (state.repeatMode == 1) TXAIcons.RepeatOne else TXAIcons.Repeat
                    Icon(
                        icon,
                        null,
                        tint = if (state.repeatMode > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(
                            0.4f
                        )
                    )
                }
            }
            Spacer(modifier = Modifier.navigationBarsPadding())
        }
    }
}

/**
 * MINIMAL STYLE
 * Super clean, lot of white space, small elements
 */
@Composable
fun NowPlayingMinimalStyle(
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
    onDriveMode: () -> Unit = {}
) {
    val context = LocalContext.current
    val albumUri = getAlbumArtUri(state)
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val scrollState = rememberScrollState()

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(24.dp)
                .then(if (isLandscape) Modifier.verticalScroll(scrollState) else Modifier),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            VolumeControl(vibrantColor = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) { Icon(TXAIcons.ExpandMore, null) }
                Row {
                    IconButton(onClick = onDriveMode) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_drive),
                            contentDescription = "txamusic_drive_mode".txa()
                        )
                    }
                    IconButton(onClick = onShowQueue) { Icon(TXAIcons.QueueMusic, null) }
                }
            }

            Spacer(modifier = Modifier.weight(0.1f))

            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { state.progress / 1000f },
                    modifier = Modifier.size(if (isLandscape) 140.dp else 200.dp),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 2.dp,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.3f)
                )
                Surface(
                    modifier = Modifier.size(if (isLandscape) 120.dp else 180.dp),
                    shape = CircleShape,
                    tonalElevation = 4.dp
                ) {
                    SubcomposeAsyncImage(
                        model = ImageRequest.Builder(context).data(albumUri)
                            .crossfade(true).build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        val imageState = painter.state
                        if (imageState is coil.compose.AsyncImagePainter.State.Loading || imageState is coil.compose.AsyncImagePainter.State.Error) {
                            DefaultAlbumArt(vibrantColor = state.vibrantColor, iconSize = 32.dp)
                        } else {
                            SubcomposeAsyncImageContent()
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(if (isLandscape) 16.dp else 32.dp))

            Text(
                state.title,
                fontSize = if (isLandscape) 18.sp else 22.sp,
                fontWeight = FontWeight.Light,
                textAlign = TextAlign.Center
            )
            Text(
                state.artist,
                fontSize = if (isLandscape) 12.sp else 14.sp,
                color = MaterialTheme.colorScheme.primary.copy(0.7f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.weight(1f))

            LinearProgressIndicator(
                progress = { state.progress / 1000f },
                modifier = Modifier.fillMaxWidth().height(2.dp).clip(CircleShape),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    TXAFormat.formatDurationHuman(state.position),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.5f)
                )
                Text(
                    TXAFormat.formatDurationHuman(state.duration),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.5f)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onToggleShuffle) {
                    Icon(
                        TXAIcons.Shuffle,
                        null,
                        tint = if (state.shuffleMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(
                            0.4f
                        )
                    )
                }

                IconButton(onClick = onShowQueue) { Icon(TXAIcons.List, null) }

                IconButton(onClick = onPlayPause, modifier = Modifier.size(48.dp)) {
                    Icon(if (state.isPlaying) TXAIcons.Pause else TXAIcons.PlayArrow, null)
                }

                IconButton(onClick = onNext) { Icon(TXAIcons.SkipNext, null) }

                IconButton(onClick = onToggleRepeat) {
                    val icon = if (state.repeatMode == 1) TXAIcons.RepeatOne else TXAIcons.Repeat
                    Icon(
                        icon,
                        null,
                        tint = if (state.repeatMode > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(
                            0.4f
                        )
                    )
                }
            }
            Spacer(modifier = Modifier.navigationBarsPadding())
        }
    }
}
