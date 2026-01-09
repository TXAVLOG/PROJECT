package com.txapp.musicplayer.ui.component

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import com.txapp.musicplayer.util.TXAFormat
import com.txapp.musicplayer.ui.component.TXAIcons
import com.txapp.musicplayer.util.txa
import com.txapp.musicplayer.R

/**
 * CARD STYLE
 * Album art in a floating card, distinct background
 */
@Composable
fun NowPlayingCardStyle(
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
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
                }
                Text("NOW PLAYING", style = MaterialTheme.typography.labelMedium, letterSpacing = 2.sp)
                Row {
                    IconButton(onClick = onDriveMode) {
                        Icon(painter = painterResource(id = R.drawable.ic_drive), contentDescription = "Drive Mode")
                    }
                    IconButton(onClick = onShowQueue) {
                        Icon(TXAIcons.QueueMusic, contentDescription = null)
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Card
            // Card with Circular Progress
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { state.progress / 1000f },
                    modifier = Modifier.size(320.dp),
                    color = state.vibrantColor,
                    strokeWidth = 3.dp,
                    trackColor = MaterialTheme.colorScheme.onSurface.copy(0.05f)
                )
                ElevatedCard(
                    modifier = Modifier
                        .size(280.dp),
                    shape = RoundedCornerShape(24.dp),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 12.dp)
                ) {
                    SubcomposeAsyncImage(
                        model = ImageRequest.Builder(context).data(albumUri).crossfade(true).build(),
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

            // Info in a 'pill' or simpler layout
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(state.title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(state.artist, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, maxLines = 1)
            }

            Spacer(modifier = Modifier.weight(1f))

            // Controls
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                 IconButton(onClick = onPrevious) { Icon(Icons.Default.SkipPrevious, null, modifier = Modifier.size(32.dp)) }
                 
                 FloatingActionButton(
                     onClick = onPlayPause,
                     containerColor = MaterialTheme.colorScheme.primaryContainer,
                     contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                     modifier = Modifier.size(72.dp)
                 ) {
                     Icon(if(state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null, modifier = Modifier.size(32.dp))
                 }
                 
                 IconButton(onClick = onNext) { Icon(Icons.Default.SkipNext, null, modifier = Modifier.size(32.dp)) }
            }
            
            Spacer(modifier = Modifier.navigationBarsPadding())
        }
    }
}

/**
 * CIRCLE STYLE
 * Circular Album Art with Circular Controls around it or cleaner layout
 */
@Composable
fun NowPlayingCircleStyle(
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
    val vibrantColor = state.vibrantColor
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    
    // Brighter background using gradient from vibrant to surface
    val brush = Brush.verticalGradient(
        colors = listOf(
            vibrantColor.copy(alpha = 0.3f),
            MaterialTheme.colorScheme.surface
        )
    )
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .background(brush)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Bar
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.KeyboardArrowDown, null, tint = MaterialTheme.colorScheme.onSurface)
                }
                Text(
                    "NOW PLAYING", 
                    style = MaterialTheme.typography.labelMedium, 
                    letterSpacing = 2.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Row {
                    IconButton(onClick = onDriveMode) {
                        Icon(painter = painterResource(id = R.drawable.ic_drive), null, tint = MaterialTheme.colorScheme.onSurface)
                    }
                    IconButton(onClick = onShowQueue) {
                        Icon(TXAIcons.QueueMusic, null, tint = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Big Circle Art with Progress Ring
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { state.progress / 1000f },
                    modifier = Modifier.size(310.dp),
                    color = vibrantColor,
                    strokeWidth = 4.dp,
                    trackColor = MaterialTheme.colorScheme.onSurface.copy(0.1f)
                )
                Surface(
                    modifier = Modifier.size(280.dp),
                    shape = CircleShape,
                    shadowElevation = 12.dp,
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                     SubcomposeAsyncImage(
                        model = ImageRequest.Builder(context).data(albumUri).crossfade(true).build(),
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
            
            Spacer(modifier = Modifier.height(48.dp))
            
            // Info
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    state.title, 
                    style = MaterialTheme.typography.headlineSmall, 
                    fontWeight = FontWeight.Bold, 
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    state.artist, 
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center, 
                    color = MaterialTheme.colorScheme.onSurfaceVariant, 
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Progress
            Column {
                Slider(
                    value = state.progress, 
                    onValueChange = onSeek, 
                    valueRange = 0f..state.duration.toFloat().coerceAtLeast(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                    )
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(TXAFormat.formatDuration(state.progress.toLong()), style = MaterialTheme.typography.bodySmall)
                    Text(TXAFormat.formatDuration(state.duration), style = MaterialTheme.typography.bodySmall)
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Controls
             Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                 IconButton(onClick = onToggleShuffle) {
                     Icon(Icons.Default.Shuffle, null, tint = if(state.shuffleMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                 }
                 IconButton(onClick = onPrevious) { 
                     Icon(Icons.Default.SkipPrevious, null, modifier = Modifier.size(36.dp), tint = MaterialTheme.colorScheme.onSurface) 
                 }
                 Surface(
                     onClick = onPlayPause,
                     shape = CircleShape,
                     color = MaterialTheme.colorScheme.primaryContainer,
                     modifier = Modifier.size(72.dp)
                 ) {
                     Box(contentAlignment = Alignment.Center) {
                         Icon(
                             if(state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, 
                             null, 
                             modifier = Modifier.size(36.dp),
                             tint = MaterialTheme.colorScheme.onPrimaryContainer
                         ) 
                     }
                 }
                 IconButton(onClick = onNext) { 
                     Icon(Icons.Default.SkipNext, null, modifier = Modifier.size(36.dp), tint = MaterialTheme.colorScheme.onSurface) 
                 }
                 IconButton(onClick = onToggleRepeat) {
                     Icon(
                         if(state.repeatMode == 2) Icons.Default.RepeatOne else Icons.Default.Repeat, 
                         null, 
                         tint = if(state.repeatMode > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                     )
                 }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Bottom Action
            Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                 IconButton(onClick = onToggleFavorite) {
                    Icon(
                        if(state.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, 
                        null, 
                        tint = if(state.isFavorite) Color.Red else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}


/**
 * PEEK STYLE
 * Top half Album Art, Bottom half controls card
 */
@Composable
fun NowPlayingPeekStyle(
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
    
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Top Image
        SubcomposeAsyncImage(
             model = ImageRequest.Builder(context).data(albumUri).crossfade(true).build(),
             contentDescription = null,
             contentScale = ContentScale.Crop,
             modifier = Modifier
                 .fillMaxWidth()
                 .fillMaxHeight(0.55f)
                 .align(Alignment.TopCenter)
        ) {
             val imageState = painter.state
             if (imageState is coil.compose.AsyncImagePainter.State.Loading || imageState is coil.compose.AsyncImagePainter.State.Error) {
                 DefaultAlbumArt(vibrantColor = state.vibrantColor)
             } else {
                 SubcomposeAsyncImageContent()
             }
        }
        
        // Gradient fade
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
                .align(Alignment.Center)
                .offset(y = (-50).dp) 
                .background(Brush.verticalGradient(listOf(Color.Transparent, MaterialTheme.colorScheme.background)))
        )
        
        // Bottom Sheet-like controls
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.5f)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(state.title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
            Text(state.artist, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center)
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Slider(value = state.progress, onValueChange = onSeek, valueRange = 0f..1000f)
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onPrevious) { Icon(Icons.Default.SkipPrevious, null, modifier=Modifier.size(36.dp)) }
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        progress = { state.progress / 1000f },
                        modifier = Modifier.size(80.dp),
                        color = state.vibrantColor,
                        strokeWidth = 3.dp,
                        trackColor = MaterialTheme.colorScheme.onSurface.copy(0.1f)
                    )
                    FilledIconButton(onClick = onPlayPause, modifier = Modifier.size(64.dp)) {
                        Icon(if(state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null)
                    }
                }
                IconButton(onClick = onNext) { Icon(Icons.Default.SkipNext, null, modifier=Modifier.size(36.dp)) }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                IconButton(onClick = onToggleFavorite) { Icon(if(state.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null, tint = if(state.isFavorite) Color.Red else LocalContentColor.current) }
                IconButton(onClick = onDriveMode) { Icon(painter = painterResource(id = R.drawable.ic_drive), null) }
                IconButton(onClick = onShowQueue) { Icon(TXAIcons.QueueMusic, null) }
                IconButton(onClick = onClose) { Icon(Icons.Default.KeyboardArrowDown, null) }
            }
             Spacer(modifier = Modifier.navigationBarsPadding())
        }
    }
}

/**
 * TINY STYLE
 * Very minimalist, mostly typography
 */
@Composable
fun NowPlayingTinyStyle(
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
    
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        Row(
            modifier = Modifier.fillMaxSize()
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxHeight().weight(0.4f)) {
                // Circular progress around edge of strip art? Or just a small one?
                // Let's put a small one in the center-ish or just around the play button on the right.
                // Actually, around the strip art might be hard. Let's do it around the Play button.
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(context).data(albumUri).crossfade(true).build(),
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
                
                // Overlay a small circular progress at the bottom of the art strip
                CircularProgressIndicator(
                    progress = { state.progress / 1000f },
                    modifier = Modifier.size(48.dp).align(Alignment.Center),
                    color = Color.White,
                    strokeWidth = 2.dp,
                    trackColor = Color.White.copy(0.2f)
                )
            }
            
            // Right Side: Controls
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(0.6f)
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp)
                    .statusBarsPadding(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.Start
            ) {
                 Row(modifier = Modifier.align(Alignment.End)) {
                     IconButton(onClick = onDriveMode) { Icon(painter = painterResource(id = R.drawable.ic_drive), null) }
                     IconButton(onClick = onClose) { Icon(Icons.Default.KeyboardArrowDown, null) }
                 }
                 
                 Spacer(modifier = Modifier.height(32.dp))
                 
                 Text(state.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 3)
                 Spacer(modifier = Modifier.height(8.dp))
                 Text(state.artist, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                 
                 Spacer(modifier = Modifier.height(32.dp))
                 
                 Row {
                      IconButton(onClick = onPrevious) { Icon(Icons.Default.SkipPrevious, null) }
                      IconButton(onClick = onPlayPause) { Icon(if(state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null) }
                      IconButton(onClick = onNext) { Icon(Icons.Default.SkipNext, null) }
                 }
                 
                 Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

