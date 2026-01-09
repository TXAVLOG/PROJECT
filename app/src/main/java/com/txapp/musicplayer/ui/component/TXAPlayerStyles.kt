package com.txapp.musicplayer.ui.component

import android.content.Context
import android.media.AudioManager
import android.content.res.Configuration
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import com.txapp.musicplayer.R
import com.txapp.musicplayer.util.TXAFormat
import com.txapp.musicplayer.util.txa

/**
 * ══════════════════════════════════════════════════════════════════════════════
 * TXA PLAYER STYLES - Bộ giao diện mới hoàn toàn
 * 
 * 6 Styles chính:
 * 1. AURORA   - Gradient aurora borealis effect
 * 2. GLASS    - Glassmorphism với blur effect  
 * 3. VINYL    - Đĩa than xoay retro
 * 4. NEON     - Dark với neon glow
 * 5. WAVES    - Minimalist với wave patterns
 * 6. SPECTRUM - Color spectrum dynamic
 * 
 * + DRIVE MODE - Cho lái xe
 * ══════════════════════════════════════════════════════════════════════════════
 */

// ════════════════════════════════════════════════════════════════════════════════
// 1. AURORA STYLE - Northern lights gradient
// ════════════════════════════════════════════════════════════════════════════════

@Suppress("FunctionName")
@Composable
fun TXAPlayerAuroraStyle(
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
    onShowLyrics: () -> Unit = {},
    onPageChanged: (Int) -> Unit = {}
) {
    val context = LocalContext.current
    val albumUri = remember(state.albumId, state.mediaUri) { getAlbumArtUri(state) }

    // Aurora gradient colors
    val auroraColors = listOf(
        Color(0xFF0F2027),
        Color(0xFF203A43),
        Color(0xFF2C5364),
        state.vibrantColor.copy(alpha = 0.4f),
        Color(0xFF0F2027)
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(auroraColors))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top bar
            TXAPlayerTopBar(
                onClose = onClose,
                onDriveMode = onDriveMode,
                onShowQueue = onShowQueue,
                onAddToPlaylist = onAddToPlaylist,
                onEditTag = onEditTag,
                onSetRingtone = onSetRingtone
            )

            Spacer(modifier = Modifier.weight(0.1f))

            // Album art với glow effect và Circular Progress
            // Use AlbumCoverPager if queue available, otherwise single cover
            if (state.queueItems.isNotEmpty()) {
                AlbumCoverPager(
                    items = state.queueItems,
                    currentIndex = state.currentQueueIndex,
                    vibrantColor = state.vibrantColor,
                    onPageChanged = onPageChanged,
                    modifier = Modifier.size(290.dp),
                    size = 240.dp,
                    isCircular = true,
                    showProgress = true,
                    progress = state.progress
                )
            } else {
                // Fallback to single album art
                Box(contentAlignment = Alignment.Center) {
                    // Circular Progress
                    CircularProgressIndicator(
                        progress = { state.progress / 1000f },
                        modifier = Modifier.size(280.dp),
                        color = state.vibrantColor,
                        strokeWidth = 4.dp,
                        trackColor = Color.White.copy(alpha = 0.1f)
                    )

                    // Glow
                    Box(
                        modifier = Modifier
                            .size(250.dp)
                            .background(state.vibrantColor.copy(alpha = 0.3f), CircleShape)
                            .blur(40.dp)
                    )
                    // Album art
                    Card(
                        modifier = Modifier.size(240.dp),
                        shape = CircleShape,
                        elevation = CardDefaults.cardElevation(defaultElevation = 20.dp),
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
                                DefaultAlbumArt(vibrantColor = state.vibrantColor)
                            } else {
                                SubcomposeAsyncImageContent()
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Song info
            TXAPlayerSongInfo(state = state, onToggleFavorite = onToggleFavorite)

            // Tools Row (Sleep, Speed)
            TXAPlayerToolsRow(
                onShowSleepTimer = onShowSleepTimer,
                onShowPlaybackSpeed = onShowPlaybackSpeed,
                tintColor = Color.White,
                sleepTimerRemainingMs = state.sleepTimerRemainingMs
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Progress
            TXAPlayerProgress(state = state, onSeek = onSeek, accentColor = state.vibrantColor)

            Spacer(modifier = Modifier.height(24.dp))

            // Controls
            TXAPlayerControls(
                state = state,
                onPlayPause = onPlayPause,
                onNext = onNext,
                onPrevious = onPrevious,
                onToggleShuffle = onToggleShuffle,
                onToggleRepeat = onToggleRepeat,
                accentColor = state.vibrantColor
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Volume Control
            VolumeControl(vibrantColor = state.vibrantColor)

            Spacer(modifier = Modifier.weight(0.1f))
            Spacer(modifier = Modifier.navigationBarsPadding())
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════════
// 2. GLASS STYLE - Glassmorphism
// ════════════════════════════════════════════════════════════════════════════════

@Suppress("FunctionName")
@Composable
fun TXAPlayerGlassStyle(
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
    onSetRingtone: () -> Unit = {}
) {
    val context = LocalContext.current
    val albumUri = remember(state.albumId, state.mediaUri) { getAlbumArtUri(state) }

    Box(modifier = Modifier.fillMaxSize()) {
        // Blur background
        AsyncImage(
            model = ImageRequest.Builder(context).data(albumUri).size(400).build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().blur(60.dp)
        )

        // Glass overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f))
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            TXAPlayerTopBar(
                onClose = onClose,
                onDriveMode = onDriveMode,
                onShowQueue = onShowQueue,
                onAddToPlaylist = onAddToPlaylist,
                onEditTag = onEditTag,
                onSetRingtone = onSetRingtone
            )


            Spacer(modifier = Modifier.weight(0.1f))

            // Glass card containing album art with Circular Progress
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { state.progress / 1000f },
                    modifier = Modifier.size(300.dp),
                    color = Color.White.copy(alpha = 0.6f),
                    strokeWidth = 4.dp,
                    trackColor = Color.White.copy(alpha = 0.1f)
                )

                Surface(
                    modifier = Modifier.size(260.dp),
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.1f),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        Color.White.copy(alpha = 0.2f)
                    )
                ) {
                    Box(
                        modifier = Modifier.padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            modifier = Modifier.fillMaxSize(),
                            shape = CircleShape,
                            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(albumUri)
                                    .crossfade(300)
                                    .size(480)
                                    .build(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Glass card for controls
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = Color.White.copy(alpha = 0.1f),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    Color.White.copy(alpha = 0.15f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    TXAPlayerSongInfo(state = state, onToggleFavorite = onToggleFavorite, textColor = Color.White)
                    Spacer(modifier = Modifier.height(20.dp))
                    TXAPlayerProgress(state = state, onSeek = onSeek, accentColor = Color.White)
                    Spacer(modifier = Modifier.height(20.dp))
                    TXAPlayerControls(
                        state = state,
                        onPlayPause = onPlayPause,
                        onNext = onNext,
                        onPrevious = onPrevious,
                        onToggleShuffle = onToggleShuffle,
                        onToggleRepeat = onToggleRepeat,
                        accentColor = if (state.vibrantColor != Color.White) state.vibrantColor else MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Volume Control
                    VolumeControl(vibrantColor = if (state.vibrantColor != Color.White) state.vibrantColor else MaterialTheme.colorScheme.primary)
                }
            }

            Spacer(modifier = Modifier.weight(0.1f))
            Spacer(modifier = Modifier.navigationBarsPadding())
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════════
// 3. VINYL STYLE - Retro vinyl record
// ════════════════════════════════════════════════════════════════════════════════

@Suppress("FunctionName")
@Composable
fun TXAPlayerVinylStyle(
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
    onSetRingtone: () -> Unit = {}
) {
    val context = LocalContext.current
    val albumUri = remember(state.albumId, state.mediaUri) { getAlbumArtUri(state) }

    // Vinyl rotation animation
    val infiniteTransition = rememberInfiniteTransition(label = "VinylRotation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(15000, easing = LinearEasing)
        ),
        label = "Rotation"
    )

    val currentRotation = if (state.isPlaying) rotation else 0f

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            TXAPlayerTopBar(
                onClose = onClose,
                onDriveMode = onDriveMode,
                onShowQueue = onShowQueue,
                onAddToPlaylist = onAddToPlaylist,
                onEditTag = onEditTag,
                onSetRingtone = onSetRingtone
            )

            Spacer(modifier = Modifier.weight(0.08f))

            // Vinyl record with Circular Progress
            Box(contentAlignment = Alignment.Center) {
                // Progress Ring
                CircularProgressIndicator(
                    progress = { state.progress / 1000f },
                    modifier = Modifier.size(310.dp),
                    color = state.vibrantColor,
                    strokeWidth = 3.dp,
                    trackColor = Color.White.copy(alpha = 0.1f)
                )

                // Vinyl disc
                Box(
                    modifier = Modifier
                        .size(280.dp)
                        .graphicsLayer { rotationZ = currentRotation }
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    Color(0xFF2D2D2D),
                                    Color(0xFF1A1A1A),
                                    Color(0xFF2D2D2D),
                                    Color(0xFF1A1A1A)
                                )
                            ),
                            CircleShape
                        )
                )

                // Album art in center
                Card(
                    modifier = Modifier
                        .size(120.dp)
                        .graphicsLayer { rotationZ = currentRotation },
                    shape = CircleShape,
                    elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(albumUri)
                            .crossfade(300)
                            .size(240)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Center dot
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .background(Color(0xFF4A4A4A), CircleShape)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            TXAPlayerSongInfo(state = state, onToggleFavorite = onToggleFavorite)

            Spacer(modifier = Modifier.height(24.dp))

            TXAPlayerProgress(state = state, onSeek = onSeek, accentColor = state.vibrantColor)

            Spacer(modifier = Modifier.height(24.dp))

            TXAPlayerControls(
                state = state,
                onPlayPause = onPlayPause,
                onNext = onNext,
                onPrevious = onPrevious,
                onToggleShuffle = onToggleShuffle,
                onToggleRepeat = onToggleRepeat,
                accentColor = state.vibrantColor
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Volume Control
            VolumeControl(vibrantColor = state.vibrantColor)

            Spacer(modifier = Modifier.weight(0.1f))
            Spacer(modifier = Modifier.navigationBarsPadding())
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════════
// 4. NEON STYLE - Dark với neon glow
// ════════════════════════════════════════════════════════════════════════════════

@Suppress("FunctionName")
@Composable
fun TXAPlayerNeonStyle(
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
    onSetRingtone: () -> Unit = {}
) {
    val context = LocalContext.current
    val albumUri = remember(state.albumId, state.mediaUri) { getAlbumArtUri(state) }
    val neonColor = state.vibrantColor

    // Pulse animation for beat effect
    val infiniteTransition = rememberInfiniteTransition(label = "neonPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    val scale = if (state.isPlaying) pulseScale else 1f

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            TXAPlayerTopBar(
                onClose = onClose,
                onDriveMode = onDriveMode,
                onShowQueue = onShowQueue,
                onAddToPlaylist = onAddToPlaylist,
                onEditTag = onEditTag,
                onSetRingtone = onSetRingtone
            )


            Spacer(modifier = Modifier.weight(0.1f))

            // Neon bordered album art
            Box(contentAlignment = Alignment.Center) {
                // Outer neon glow
                Box(
                    modifier = Modifier
                        .size(270.dp)
                        .background(neonColor.copy(alpha = 0.4f), RoundedCornerShape(28.dp))
                        .blur(20.dp)
                )
                // Inner glow
                Box(
                    modifier = Modifier
                        .size(260.dp)
                        .background(neonColor.copy(alpha = 0.6f), RoundedCornerShape(24.dp))
                        .blur(10.dp)
                )
                // Album art
                Card(
                    modifier = Modifier.size(240.dp).graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    },
                    shape = RoundedCornerShape(20.dp),
                    border = androidx.compose.foundation.BorderStroke(3.dp, neonColor)
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(albumUri)
                            .crossfade(300)
                            .size(480)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            TXAPlayerSongInfo(state = state, onToggleFavorite = onToggleFavorite, accentColor = neonColor)

            Spacer(modifier = Modifier.height(24.dp))

            TXAPlayerProgress(state = state, onSeek = onSeek, accentColor = neonColor)

            Spacer(modifier = Modifier.height(24.dp))

            TXAPlayerControls(
                state = state,
                onPlayPause = onPlayPause,
                onNext = onNext,
                onPrevious = onPrevious,
                onToggleShuffle = onToggleShuffle,
                onToggleRepeat = onToggleRepeat,
                accentColor = neonColor
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Volume Control
            VolumeControl(vibrantColor = neonColor)

            Spacer(modifier = Modifier.weight(0.1f))
            Spacer(modifier = Modifier.navigationBarsPadding())
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════════


// ════════════════════════════════════════════════════════════════════════════════
// 6. SPECTRUM STYLE - Colorful gradient
// ════════════════════════════════════════════════════════════════════════════════

@Suppress("FunctionName")
@Composable
fun TXAPlayerSpectrumStyle(
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
    onSetRingtone: () -> Unit = {}
) {
    val context = LocalContext.current
    val albumUri = remember(state.albumId, state.mediaUri) { getAlbumArtUri(state) }

    // Animated spectrum colors
    val infiniteTransition = rememberInfiniteTransition(label = "spectrum")
    val colorShift by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(5000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "colorShift"
    )

    val gradientColors = listOf(
        Color(0xFFFF6B6B).copy(alpha = 0.8f),
        Color(0xFF4ECDC4).copy(alpha = 0.7f),
        Color(0xFF45B7D1).copy(alpha = 0.6f),
        state.vibrantColor.copy(alpha = 0.5f),
        Color(0xFF2D2D2D)
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
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            TXAPlayerTopBar(
                onClose = onClose,
                onDriveMode = onDriveMode,
                onShowQueue = onShowQueue,
                onAddToPlaylist = onAddToPlaylist,
                onEditTag = onEditTag,
                onSetRingtone = onSetRingtone
            )


            Spacer(modifier = Modifier.weight(0.1f))

            // Redesigned Spectrum Layout
            Box(contentAlignment = Alignment.Center) {
                // Dynamic Progress Ring
                CircularProgressIndicator(
                    progress = { state.progress / 1000f },
                    modifier = Modifier.size(280.dp),
                    color = Color.White,
                    strokeWidth = 2.dp,
                    trackColor = Color.White.copy(alpha = 0.1f)
                )

                // Multiple borders for spectrum feel
                Box(
                    modifier = Modifier
                        .size(255.dp)
                        .background(Color.White.copy(alpha = 0.1f), CircleShape)
                )

                Card(
                    modifier = Modifier.size(240.dp),
                    shape = CircleShape,
                    elevation = CardDefaults.cardElevation(defaultElevation = 24.dp)
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(albumUri)
                            .crossfade(300)
                            .size(500)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Enhanced Spectrum Info
            TXAPlayerSongInfo(state = state, onToggleFavorite = onToggleFavorite, textColor = Color.White)

            Spacer(modifier = Modifier.height(24.dp))

            TXAPlayerProgress(state = state, onSeek = onSeek, accentColor = Color.White)

            Spacer(modifier = Modifier.height(24.dp))

            TXAPlayerControls(
                state = state,
                onPlayPause = onPlayPause,
                onNext = onNext,
                onPrevious = onPrevious,
                onToggleShuffle = onToggleShuffle,
                onToggleRepeat = onToggleRepeat,
                accentColor = Color.White
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Volume Control
            VolumeControl(vibrantColor = Color.White)

            Spacer(modifier = Modifier.weight(0.1f))
            Spacer(modifier = Modifier.navigationBarsPadding())
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════════
// SHARED COMPONENTS
// ════════════════════════════════════════════════════════════════════════════════

@Composable
private fun TXAPlayerTopBar(
    onClose: () -> Unit,
    onDriveMode: () -> Unit,
    onShowQueue: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onEditTag: () -> Unit = {},
    onSetRingtone: () -> Unit = {},
    tintColor: Color = Color.White
) {
    var showMoreOptions by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onClose) {
            Icon(
                Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = tintColor,
                modifier = Modifier.size(32.dp)
            )
        }

        Text(
            "Now Playing",
            color = tintColor.copy(alpha = 0.8f),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )

        Row {
            IconButton(onClick = onDriveMode) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_drive),
                    contentDescription = null,
                    tint = tintColor.copy(alpha = 0.8f),
                    modifier = Modifier.size(22.dp)
                )
            }
            IconButton(onClick = onShowQueue) {
                Icon(
                    TXAIcons.QueueMusic,
                    contentDescription = null,
                    tint = tintColor.copy(alpha = 0.8f),
                    modifier = Modifier.size(22.dp)
                )
            }
            Box {
                IconButton(onClick = { showMoreOptions = true }) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = null,
                        tint = tintColor.copy(alpha = 0.8f),
                        modifier = Modifier.size(22.dp)
                    )
                }
                MoreOptionsPlayerDropdown(
                    expanded = showMoreOptions,
                    onDismiss = { showMoreOptions = false },
                    onAddToPlaylist = onAddToPlaylist,
                    onEditTag = onEditTag,
                    onSetRingtone = onSetRingtone
                )
            }
        }
    }
}

@Composable
private fun TXAPlayerSongInfo(
    state: NowPlayingState,
    onToggleFavorite: () -> Unit,
    textColor: Color = Color.White,
    accentColor: Color = Color.White
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onToggleFavorite) {
            Icon(
                if (state.isFavorite) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                null,
                tint = if (state.isFavorite) Color(0xFFFF6B6B) else textColor.copy(0.7f),
                modifier = Modifier.size(26.dp)
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = state.title.ifBlank { "txamusic_unknown_title".txa() },
                color = textColor,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = state.artist.ifBlank { "txamusic_unknown_artist".txa() },
                color = textColor.copy(alpha = 0.7f),
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }

        Spacer(modifier = Modifier.size(48.dp)) // Balance with favorite button
    }
}

@Composable
fun TXAPlayerToolsRow(
    onShowSleepTimer: () -> Unit,
    onShowPlaybackSpeed: () -> Unit,
    tintColor: Color,
    sleepTimerRemainingMs: Long? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 48.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            IconButton(onClick = onShowSleepTimer) {
                Icon(
                    imageVector = Icons.Default.Timer,
                    contentDescription = "Sleep Timer",
                    tint = tintColor.copy(0.7f),
                    modifier = Modifier.size(24.dp)
                )
            }
            // Sleep Timer Badge
            if (sleepTimerRemainingMs != null && sleepTimerRemainingMs > 0) {
                // Convert ms to minutes
                val minutesTotal = sleepTimerRemainingMs / 60000f

                val text = if (minutesTotal >= 60) {
                    // >= 1 Hour
                    val hours = (minutesTotal / 60).toInt()
                    val minutes = (minutesTotal % 60).toInt()
                    if (minutes > 0) "${hours}${"txamusic_unit_hour".txa()} ${minutes}${"txamusic_unit_minute".txa()}" else "${hours}${"txamusic_unit_hour".txa()}"
                } else if (minutesTotal >= 1) {
                    // 1 min to 60 min
                    "${minutesTotal.toInt()} ${"txamusic_unit_minute".txa()}"
                } else {
                    // < 1 minute (e.g. 0.5p)
                    String.format("%.2f %s", minutesTotal, "txamusic_unit_minute".txa())
                }

                Surface(
                    color = Color.Red,
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.offset(x = 10.dp, y = (-2).dp)
                ) {
                    Text(
                        text = text,
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }
        }

        IconButton(onClick = onShowPlaybackSpeed) {
            Icon(
                imageVector = Icons.Default.Speed, // Using Speed icon
                contentDescription = "Playback Speed",
                tint = tintColor.copy(0.7f),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun TXAPlayerProgress(
    state: NowPlayingState,
    onSeek: (Float) -> Unit,
    accentColor: Color
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Slider(
            value = state.progress,
            onValueChange = onSeek,
            valueRange = 0f..1000f,
            colors = SliderDefaults.colors(
                thumbColor = accentColor,
                activeTrackColor = accentColor,
                inactiveTrackColor = accentColor.copy(alpha = 0.2f)
            )
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
}

@Composable
private fun TXAPlayerControls(
    state: NowPlayingState,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit,
    accentColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onToggleShuffle) {
            Icon(
                Icons.Default.Shuffle,
                null,
                tint = if (state.shuffleMode) accentColor.copy(alpha = 1.0f) else Color.White.copy(0.3f),
                modifier = Modifier.size(26.dp)
            )
        }

        IconButton(onClick = onPrevious, modifier = Modifier.size(56.dp)) {
            Icon(
                Icons.Default.SkipPrevious,
                null,
                tint = Color.White,
                modifier = Modifier.size(36.dp)
            )
        }

        FloatingActionButton(
            onClick = onPlayPause,
            containerColor = accentColor,
            contentColor = Color.White,
            modifier = Modifier.size(72.dp)
        ) {
            Icon(
                if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                null,
                modifier = Modifier.size(40.dp)
            )
        }

        IconButton(onClick = onNext, modifier = Modifier.size(56.dp)) {
            Icon(
                Icons.Default.SkipNext,
                null,
                tint = Color.White,
                modifier = Modifier.size(36.dp)
            )
        }

        IconButton(onClick = onToggleRepeat) {
            val repeatIcon = if (state.repeatMode == 1) Icons.Default.RepeatOne else Icons.Default.Repeat
            Icon(
                repeatIcon,
                null,
                tint = if (state.repeatMode > 0) accentColor.copy(alpha = 1.0f) else Color.White.copy(0.3f),
                modifier = Modifier.size(26.dp)
            )
        }
    }
}

@Composable
fun MoreOptionsPlayerDropdown(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onEditTag: () -> Unit = {},
    onSetRingtone: () -> Unit = {}
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss
    ) {
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

