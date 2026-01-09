package com.txapp.musicplayer.ui.component

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import kotlin.OptIn
import androidx.compose.runtime.*
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import com.txapp.musicplayer.ui.component.DefaultAlbumArt
import com.txapp.musicplayer.model.Artist
import com.txapp.musicplayer.model.Album
import com.txapp.musicplayer.model.Song
import com.txapp.musicplayer.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistDetailsScreen(
    artist: Artist,
    onBack: () -> Unit,
    onPlaySong: (Song, List<Song>) -> Unit,
    onPlayAll: (List<Song>) -> Unit,
    onShuffleAll: (List<Song>) -> Unit,
    onAlbumClick: (Long) -> Unit,
    currentSongId: Long = -1L
) {
    val accentColor = Color(android.graphics.Color.parseColor(TXAPreferences.currentAccent))
    
    // State for expanded image
    var expandedImageUrl by remember { mutableStateOf<String?>(null) }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            // Header
            item {
                ArtistHeader(artist, accentColor, onPlayAll, onShuffleAll) { imageUrl ->
                    expandedImageUrl = imageUrl
                }
            }

            // Albums Section
            item {
                Text(
                    text = "txamusic_albums".txa(),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(16.dp)
                )
            }
            item {
            val screenWidth = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp
            Row(
                modifier = Modifier
                    .widthIn(max = screenWidth)
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    artist.sortedAlbums.forEach { album ->
                        ArtistAlbumItem(album, onAlbumClick)
                    }
                }
            }

            // Songs Section
            item {
                Text(
                    text = "txamusic_media_songs".txa(),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp)
                )
            }
            items(artist.songs.size) { index ->
                val song = artist.songs[index]
                ArtistSongItem(
                    song = song,
                    index = index + 1,
                    isCurrent = song.id == currentSongId,
                    accentColor = accentColor,
                    onClick = { onPlaySong(song, artist.songs) }
                )
            }
        }

        // Top Bar
        TopAppBar(
            title = { },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                navigationIconContentColor = Color.White
            )
        )

        // Expanded image dialog
        expandedImageUrl?.let { url ->
            ExpandedArtistImageDialog(
                artist = artist,
                imageSource = url,
                onDismiss = { expandedImageUrl = null }
            )
        }
    }
}

@Composable
fun ArtistHeader(
    artist: Artist,
    accentColor: Color,
    onPlayAll: (List<Song>) -> Unit,
    onShuffleAll: (List<Song>) -> Unit,
    onImageClick: (String?) -> Unit
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(accentColor.copy(alpha = 0.5f), Color.Transparent),
                    startY = 0f,
                    endY = 500f
                )
            )
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(56.dp))

        // Artist Image (Network search with fallback)
        val artistImageUrl by produceState<String?>(initialValue = null, artist.name) {
            if (TXAPreferences.isAutoDownloadImagesEnabled) {
                value = com.txapp.musicplayer.network.ArtistImageService.getArtistImageUrl(artist.name)
            }
        }
        val imageSource = remember(artist.name, artistImageUrl) {
            artistImageUrl ?: MusicUtil.getAlbumArtUri(artist.safeGetFirstAlbum().id)
        }

        Box(
            modifier = Modifier
                .size(180.dp)
                .clip(CircleShape)
                .background(Color.Gray.copy(alpha = 0.2f))
                .border(4.dp, accentColor.copy(alpha = 0.3f), CircleShape)
                .clickable {
                    onImageClick(imageSource.toString())
                }
        ) {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(context)
                    .data(imageSource)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            ) {
                 val state = painter.state
                 if (state is coil.compose.AsyncImagePainter.State.Loading || state is coil.compose.AsyncImagePainter.State.Error) {
                     DefaultAlbumArt()
                 } else {
                     SubcomposeAsyncImageContent()
                 }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = artist.name,
            fontSize = 32.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "${artist.albumCount} ${"txamusic_albums".txa()} • ${artist.songCount} ${"txamusic_media_songs".txa()}",
            fontSize = 16.sp,
            color = Color.Gray,
            fontWeight = FontWeight.Medium
        )

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = { onPlayAll(artist.songs) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accentColor)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("txamusic_play_now".txa())
            }
            OutlinedButton(
                onClick = { onShuffleAll(artist.songs) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, accentColor)
            ) {
                Icon(Icons.Default.Shuffle, contentDescription = null, tint = accentColor)
                Spacer(modifier = Modifier.width(8.dp))
                Text("txamusic_shuffle_on".txa(), color = accentColor)
            }
        }
    }
}

@Composable
fun ArtistAlbumItem(album: Album, onClick: (Long) -> Unit) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .width(160.dp)
            .clickable { onClick(album.id) }
    ) {
        SubcomposeAsyncImage(
            model = ImageRequest.Builder(context)
                .data(MusicUtil.getAlbumArtUri(album.id))
                .crossfade(true)
                .build(),
            contentDescription = null,
            modifier = Modifier
                .size(160.dp)
                .clip(RoundedCornerShape(16.dp)),
            contentScale = ContentScale.Crop
        ) {
             val state = painter.state
             if (state is coil.compose.AsyncImagePainter.State.Loading || state is coil.compose.AsyncImagePainter.State.Error) {
                 DefaultAlbumArt()
             } else {
                 SubcomposeAsyncImageContent()
             }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = album.title,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = album.year.toString(),
            fontSize = 12.sp,
            color = Color.Gray
        )
    }
}

@Composable
fun ArtistSongItem(
    song: Song,
    index: Int,
    isCurrent: Boolean,
    accentColor: Color,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .background(if (isCurrent) accentColor.copy(alpha = 0.1f) else Color.Transparent),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Index
        Text(
            text = index.toString().padStart(2, '0'),
            fontSize = 14.sp,
            color = if (isCurrent) accentColor else Color.Gray,
            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.width(32.dp)
        )

        SubcomposeAsyncImage(
            model = ImageRequest.Builder(context)
                .data(MusicUtil.getAlbumArtUri(song.albumId))
                .crossfade(true)
                .build(),
            contentDescription = null,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop
        ) {
             val state = painter.state
             if (state is coil.compose.AsyncImagePainter.State.Loading || state is coil.compose.AsyncImagePainter.State.Error) {
                 DefaultAlbumArt()
             } else {
                 SubcomposeAsyncImageContent()
             }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                fontSize = 16.sp,
                color = if (isCurrent) accentColor else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = song.album,
                fontSize = 12.sp,
                color = if (isCurrent) accentColor.copy(alpha = 0.7f) else Color.Gray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        
        if (isCurrent) {
            Icon(
                Icons.Default.PlayArrow,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(20.dp).padding(end = 8.dp)
            )
        }

        Text(
            text = TXAFormat.formatDuration(song.duration),
            fontSize = 12.sp,
            color = if (isCurrent) accentColor else Color.Gray
        )
    }
}

/**
 * Artist image expand dialog with zoom animation
 */
@Composable
fun ExpandedArtistImageDialog(
    artist: Artist,
    imageSource: Any?,
    onDismiss: () -> Unit
) {
    // Animation states
    var isVisible by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0.3f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "scale"
    )
    val alpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(300),
        label = "alpha"
    )
    
    // Trigger animation on composition
    LaunchedEffect(Unit) {
        isVisible = true
    }
    
    Dialog(
        onDismissRequest = {
            isVisible = false
            onDismiss()
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = alpha * 0.85f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    isVisible = false
                    onDismiss()
                },
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        this.alpha = alpha
                    }
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Expanded image
                Card(
                    modifier = Modifier
                        .size(300.dp)
                        .clip(CircleShape),
                    shape = CircleShape,
                    elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
                ) {
                    SubcomposeAsyncImage(
                        model = ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                            .data(imageSource)
                            .crossfade(true)
                            .size(1000)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        val state = painter.state
                        if (state is coil.compose.AsyncImagePainter.State.Loading || state is coil.compose.AsyncImagePainter.State.Error) {
                            DefaultAlbumArt(iconSize = 80.dp)
                        } else {
                            SubcomposeAsyncImageContent()
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(32.dp))
                
                // Artist name
                Text(
                    text = artist.name,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Details
                Text(
                    text = "${artist.albumCount} ${"txamusic_albums".txa()} • ${artist.songCount} ${"txamusic_media_songs".txa()}",
                    fontSize = 16.sp,
                    color = Color.White.copy(alpha = 0.7f)
                )
                
                Spacer(modifier = Modifier.height(48.dp))
                
                // Close hint
                Text(
                    text = "txamusic_tap_to_close".txa(),
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.5f)
                )
            }
        }
    }
}
