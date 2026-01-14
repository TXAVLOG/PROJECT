package com.txapp.musicplayer.ui.component

import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.Lyrics
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
import androidx.media3.common.MediaItem
import androidx.palette.graphics.Palette
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import com.txapp.musicplayer.R
import com.txapp.musicplayer.util.TXAPreferences
import com.txapp.musicplayer.util.txa
import com.txapp.musicplayer.util.LyricsUtil



@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MiniPlayerContent(
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
        if (pagerState.currentPage != currentIndex) {
             if (!pagerState.isScrollInProgress) {
                 // onSeekTo(pagerState.currentPage) // Don't auto seek on initial sync
             } else {
                 onSeekTo(pagerState.currentPage)
             }
        }
    }
    
    // Handle manual swipe finish
    LaunchedEffect(pagerState.isScrollInProgress) {
        if (!pagerState.isScrollInProgress && pagerState.currentPage != currentIndex) {
            onSeekTo(pagerState.currentPage)
        }
    }
    
    val currentItem = remember(currentIndex) { getItem(currentIndex) }
    
    // Extract color
    var backgroundColor by remember { mutableStateOf(Color.Transparent) }
    val context = LocalContext.current
    
    // Helper to get art uri
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
                    backgroundColor = Color(palette?.getVibrantColor(0) ?: 0)
                }
            } else {
                backgroundColor = Color.Transparent
            } 
        } else {
            backgroundColor = Color.Transparent
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
    
    // Dark/Light mode detection for Liquid Glass
    val isDarkTheme = isSystemInDarkTheme()
    
    // Liquid Glass colors based on theme
    val glassBackground = if (isDarkTheme) {
        Color(0xFF1A1A2E).copy(alpha = 0.65f)
    } else {
        Color(0xFFF8F8FC).copy(alpha = 0.75f)
    }
    
    val glassBorder = if (isDarkTheme) {
        Color.White.copy(alpha = 0.12f)
    } else {
        Color.White.copy(alpha = 0.6f)
    }
    
    val glassHighlight = if (isDarkTheme) {
        Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = 0.08f),
                Color.Transparent
            )
        )
    } else {
        Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = 0.5f),
                Color.White.copy(alpha = 0.1f)
            )
        )
    }
    
    // Text colors for glass effect
    val glassTextPrimary = if (isDarkTheme) Color.White else Color(0xFF1A1A2E)
    val glassTextSecondary = if (isDarkTheme) Color.White.copy(alpha = 0.7f) else Color(0xFF1A1A2E).copy(alpha = 0.7f)
    
    // Check if we can use advanced glass effects (Android 13+)
    val supportsAdvancedGlass = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .clickable { onExpand() }
    ) {
        // Liquid Glass Background Container
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(16.dp))
                // Glass background with blur effect on Android 12+
                .then(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        Modifier.blur(if (supportsAdvancedGlass) 0.5.dp else 0.dp)
                    } else Modifier
                )
                .background(glassBackground)
                // Glass border for depth
                .border(
                    width = 1.dp,
                    color = glassBorder,
                    shape = RoundedCornerShape(16.dp)
                )
        ) {
            // Highlight overlay at top (glass reflection effect)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .background(glassHighlight)
            )
            
            // Progress Fill with glass tint
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        clip = true
                        shape = androidx.compose.ui.graphics.RectangleShape
                        this.scaleX = safeProgress
                        this.transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
                    }
                    .background(
                        if (backgroundColor != Color.Transparent) 
                            backgroundColor.copy(alpha = if (isDarkTheme) 0.35f else 0.25f)
                        else 
                            accentColor.copy(alpha = if (isDarkTheme) 0.3f else 0.2f)
                    )
            )
            
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Pager for Art
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.size(56.dp),
                    contentPadding = PaddingValues(0.dp),
                    pageSpacing = 16.dp
                ) { page ->
                    val item = getItem(page)
                    val artUri = getArtUri(item)
                    
                    Card(
                        shape = CircleShape,
                        elevation = CardDefaults.cardElevation(4.dp),
                        modifier = Modifier.fillMaxSize(),
                        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
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
                                    modifier = Modifier.fillMaxSize().padding(8.dp),
                                    contentScale = ContentScale.Fit
                                )
                            } else {
                                SubcomposeAsyncImageContent()
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.width(12.dp))
                
                // Info
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    val displayItem = getItem(pagerState.currentPage)
                    
                    Text(
                        text = displayItem?.mediaMetadata?.title?.toString() ?: "Unknown",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            shadow = if (isDarkTheme) androidx.compose.ui.graphics.Shadow(
                                color = Color.Black.copy(alpha = 0.3f),
                                offset = androidx.compose.ui.geometry.Offset(0f, 1f),
                                blurRadius = 2f
                            ) else null
                        ),
                        color = glassTextPrimary,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    // Lyrics display snippet or Artist
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
                    
                    Text(
                        text = displayLyrics ?: (displayItem?.mediaMetadata?.artist?.toString() ?: "Unknown Artist"),
                        style = MaterialTheme.typography.bodySmall.copy(
                            shadow = if (isDarkTheme) androidx.compose.ui.graphics.Shadow(
                                color = Color.Black.copy(alpha = 0.5f),
                                offset = androidx.compose.ui.geometry.Offset(0f, 2f),
                                blurRadius = 4f
                            ) else null
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (displayLyrics != null) glassTextPrimary else glassTextSecondary,
                        fontWeight = if (displayLyrics != null) FontWeight.Bold else FontWeight.Normal
                    )
                }
                
                // Controls
                val extraControls by TXAPreferences.extraControls.collectAsState()
                
                Row(
                     verticalAlignment = Alignment.CenterVertically,
                     horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                     IconButton(onClick = { 
                         onEditLyrics() 
                     }, modifier = Modifier.size(32.dp)) {
                          Icon(
                              imageVector = androidx.compose.material.icons.Icons.Outlined.Lyrics,
                              contentDescription = "Edit Lyrics",
                              tint = glassTextSecondary,
                              modifier = Modifier.size(20.dp)
                          )
                     }
                     
                     IconButton(onClick = onToggleFavorite, modifier = Modifier.size(32.dp)) {
                          Icon(
                              imageVector = if (state.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                              contentDescription = "Favorite",
                              tint = if (state.isFavorite) Color.Red else glassTextSecondary,
                              modifier = Modifier.size(20.dp)
                          )
                     }

                     // Only show prev/next if extraControls is enabled
                     if (extraControls) {
                         IconButton(onClick = onPrevious, modifier = Modifier.size(32.dp)) {
                              Icon(Icons.Default.SkipPrevious, null, modifier = Modifier.size(24.dp), tint = glassTextSecondary)
                         }
                     }

                     // Play/Pause
                     Box(contentAlignment = Alignment.Center) {
                          CircularProgressIndicator(
                              progress = { safeProgress },
                              modifier = Modifier.size(32.dp),
                              strokeWidth = 2.dp,
                              color = accentColor,
                              trackColor = accentColor.copy(alpha = 0.2f),
                          )
                          IconButton(onClick = onPlayPause, modifier = Modifier.size(28.dp)) {
                              Icon(
                                  imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                  contentDescription = "Play/Pause",
                                  modifier = Modifier.size(16.dp),
                                  tint = glassTextPrimary
                              )
                          }
                     }

                     if (extraControls) {
                         IconButton(onClick = onNext, modifier = Modifier.size(32.dp)) {
                              Icon(Icons.Default.SkipNext, null, modifier = Modifier.size(24.dp), tint = glassTextSecondary)
                         }
                     }
                }
            }
        }
    }
}
