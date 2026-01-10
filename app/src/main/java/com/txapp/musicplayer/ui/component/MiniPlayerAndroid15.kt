package com.txapp.musicplayer.ui.component

import android.os.Build
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Lyrics
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.Canvas
import androidx.compose.ui.draw.alpha
import androidx.media3.common.MediaItem
import androidx.palette.graphics.Palette
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import com.txapp.musicplayer.R
import com.txapp.musicplayer.util.TXAPreferences
import com.txapp.musicplayer.util.txa
import com.txapp.musicplayer.util.LyricsUtil

/**
 * MiniPlayerAndroid15 - Specialized mini player for Android 15+ 
 * Featuring Liquid Glass effect, taller design, and enhanced lyrics display.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MiniPlayerAndroid15(
    state: NowPlayingState,
    itemCount: Int,
    currentIndex: Int,
    getItem: (Int) -> MediaItem?,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onToggleFavorite: () -> Unit,
    onSeekTo: (Int) -> Unit,
    onExpand: () -> Unit,
    onEditLyrics: () -> Unit = {}
) {
    val pagerState = rememberPagerState(
        initialPage = currentIndex.coerceIn(0, (itemCount - 1).coerceAtLeast(0)),
        pageCount = { itemCount.takeIf { it > 0 } ?: 1 }
    )

    // Sync pager when song changes externally
    LaunchedEffect(currentIndex) {
        if (currentIndex != pagerState.currentPage && currentIndex in 0 until itemCount) {
             pagerState.scrollToPage(currentIndex)
        }
    }

    // Sync Playback when user swipes
    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage != currentIndex && pagerState.isScrollInProgress) {
             onSeekTo(pagerState.currentPage)
        }
    }
    
    val currentItem = remember(currentIndex) { getItem(currentIndex) }
    val context = LocalContext.current
    
    // Extract dominant color for progress fill
    var dominantColor by remember { mutableStateOf(Color.Transparent) }
    
    fun getArtUri(item: MediaItem?): Any? {
        if (item == null) return null
        val albumId = item.mediaMetadata.extras?.getLong("album_id") ?: -1L
        return if (albumId > 0) {
            android.content.ContentUris.withAppendedId(
                android.net.Uri.parse("content://media/external/audio/albumart"),
                albumId
            )
        } else {
            item.mediaMetadata.artworkUri ?: item.localConfiguration?.uri
        }
    }
    
    val currentArtUri = getArtUri(currentItem)

    LaunchedEffect(currentArtUri) {
        if (currentArtUri != null) {
            val request = ImageRequest.Builder(context)
                .data(currentArtUri)
                .allowHardware(false)
                .build()
            val result = coil.ImageLoader(context).execute(request)
            if (result.drawable != null) {
                val bitmap = (result.drawable as android.graphics.drawable.BitmapDrawable).bitmap
                Palette.from(bitmap).generate { palette ->
                    dominantColor = Color(palette?.getVibrantColor(0) ?: palette?.getDominantColor(0) ?: 0)
                }
            }
        }
    }
    
    val defaultAccentColor = MaterialTheme.colorScheme.primary
    val accentColor = try {
         Color(android.graphics.Color.parseColor(TXAPreferences.currentAccent))
    } catch (e: Exception) {
         defaultAccentColor
    }
    
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val onSurface = MaterialTheme.colorScheme.onSurface
    
    val safeProgress = if (state.duration > 0) state.position.toFloat() / state.duration else 0f
    
    // Lyrics logic for Mini Player
    val displayLyrics = remember(state.lyrics, state.position) {
        val raw = state.lyrics
        if (raw.isNullOrBlank()) return@remember null
        
        // Check if synced
        if (raw.contains(Regex("""\[\d+:?\d{2}[:.]\d{2,3}\]"""))) {
            val parsed = LyricsUtil.parseLrc(raw)
            val currentLine = parsed.findLast { it.timestamp <= state.position }
            currentLine?.text
        } else {
            // Normal lyrics: first 200 chars + ellipsis
            val clean = LyricsUtil.getCleanLyrics(raw) ?: ""
            if (clean.length > 200) {
                clean.take(200) + "..."
            } else {
                clean
            }
        }
    }

    // Determine contrast color for lyrics
    // Determine contrast color for lyrics - Always White for Glass look but with optional shadow
    val lyricsColor = Color.White
    val secondaryTextColor = Color.White.copy(alpha = 0.7f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(84.dp) // Taller than standard
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(24.dp)) // More rounded for A15
            .background(surfaceVariant.copy(alpha = 0.4f))
            .clickable { onExpand() }
    ) {
        // Liquid Glass Blur Overlay - Enhanced for A15
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.12f),
                            Color.White.copy(alpha = 0.02f)
                        )
                    )
                )
                .blur(40.dp) // More blur for liquid look
        )
        
        // Subtle Noise/Grain Effect (Simulated)
        Canvas(modifier = Modifier.fillMaxSize().alpha(0.05f)) {
            // Draw random points or a pattern to simulate texture
            // For now simple semi-transparent white
        }

        // Progress Fill - Full from left
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(safeProgress)
                .background(
                    if (dominantColor != Color.Transparent) 
                        dominantColor.copy(alpha = 0.45f) 
                    else 
                        accentColor.copy(alpha = 0.35f)
                )
        )
        
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Pager for Art
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.size(60.dp),
                contentPadding = PaddingValues(0.dp),
            ) { page ->
                val item = getItem(page)
                val artUri = getArtUri(item)
                
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Black.copy(alpha = 0.2f),
                    shadowElevation = 8.dp
                ) {
                    SubcomposeAsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(artUri)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        val imgState = painter.state
                        if (imgState is coil.compose.AsyncImagePainter.State.Loading || imgState is coil.compose.AsyncImagePainter.State.Error) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_launcher),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize().padding(10.dp),
                                contentScale = ContentScale.Fit
                            )
                        } else {
                            SubcomposeAsyncImageContent()
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // Info & Lyrics
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                val displayItem = getItem(pagerState.currentPage)
                
                Text(
                    text = displayItem?.mediaMetadata?.title?.toString() ?: "Unknown",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = androidx.compose.ui.text.TextStyle(
                        shadow = androidx.compose.ui.graphics.Shadow(
                            color = Color.Black.copy(alpha = 0.3f),
                            offset = androidx.compose.ui.geometry.Offset(0f, 1f),
                            blurRadius = 2f
                        )
                    )
                )
                
                if (displayLyrics != null) {
                    Text(
                        text = displayLyrics,
                        fontSize = 13.sp,
                        color = lyricsColor,
                        maxLines = 2,
                        lineHeight = 16.sp,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Bold, // Bolder for better visibility
                        style = androidx.compose.ui.text.TextStyle(
                            shadow = androidx.compose.ui.graphics.Shadow(
                                color = Color.Black.copy(alpha = 0.5f),
                                offset = androidx.compose.ui.geometry.Offset(0f, 2f),
                                blurRadius = 4f
                            )
                        )
                    )
                } else {
                    Text(
                        text = displayItem?.mediaMetadata?.artist?.toString() ?: "Unknown Artist",
                        fontSize = 14.sp,
                        color = secondaryTextColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            
            // Controls
            val extraControls by TXAPreferences.extraControls.collectAsState()
            
            Row(
                 verticalAlignment = Alignment.CenterVertically,
                 horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                 IconButton(onClick = onEditLyrics, modifier = Modifier.size(36.dp)) {
                      Icon(
                          imageVector = Icons.Outlined.Lyrics,
                          contentDescription = "Edit Lyrics",
                          tint = secondaryTextColor,
                          modifier = Modifier.size(22.dp)
                      )
                 }
                 
                 IconButton(onClick = onToggleFavorite, modifier = Modifier.size(36.dp)) {
                      Icon(
                          imageVector = if (state.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                          contentDescription = "Favorite",
                          tint = if (state.isFavorite) Color.Red else secondaryTextColor,
                          modifier = Modifier.size(22.dp)
                      )
                 }

                 if (extraControls) {
                     IconButton(onClick = onPrevious, modifier = Modifier.size(36.dp)) {
                          Icon(Icons.Default.SkipPrevious, null, modifier = Modifier.size(28.dp), tint = secondaryTextColor)
                     }
                 }

                 // Play/Pause with Circle Progress
                 Box(contentAlignment = Alignment.Center, modifier = Modifier.size(44.dp)) {
                      CircularProgressIndicator(
                          progress = { safeProgress },
                          modifier = Modifier.fillMaxSize(),
                          strokeWidth = 3.dp,
                          color = dominantColor.takeIf { it != Color.Transparent } ?: accentColor,
                          trackColor = Color.White.copy(alpha = 0.1f),
                      )
                      IconButton(onClick = onPlayPause, modifier = Modifier.size(36.dp)) {
                          Icon(
                              imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                              contentDescription = "Play/Pause",
                              modifier = Modifier.size(24.dp),
                              tint = Color.White
                          )
                      }
                 }

                 if (extraControls) {
                     IconButton(onClick = onNext, modifier = Modifier.size(36.dp)) {
                          Icon(Icons.Default.SkipNext, null, modifier = Modifier.size(28.dp))
                     }
                 }
            }
        }
    }
}
