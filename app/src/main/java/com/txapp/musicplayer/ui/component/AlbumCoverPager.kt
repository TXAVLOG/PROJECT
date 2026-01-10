package com.txapp.musicplayer.ui.component

import android.content.ContentUris
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import com.txapp.musicplayer.transform.PagerTransformStyle
import com.txapp.musicplayer.transform.pagerTransform
import com.txapp.musicplayer.util.TXAPreferences
import com.txapp.musicplayer.util.TXAImageUtils
import kotlin.math.abs

/**
 * Data class representing a queue item for the pager
 */
data class AlbumCoverItem(
    val index: Int,
    val albumId: Long,
    val mediaUri: String = "",
    val webArtUrl: String = ""
)

/**
 * Album Cover Pager with page transformations
 * Allows swiping between songs with beautiful animations
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AlbumCoverPager(
    items: List<AlbumCoverItem>,
    currentIndex: Int,
    vibrantColor: Color,
    onPageChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 280.dp,
    isCircular: Boolean = true,
    showProgress: Boolean = true,
    progress: Float = 0f
) {
    val context = LocalContext.current
    val transformStyle = remember { getTransformStyle(TXAPreferences.currentAlbumCoverTransform) }
    val isCarousel = TXAPreferences.isCarouselEffect
    
    // Pager state with initial page
    val pagerState = rememberPagerState(
        initialPage = currentIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0)),
        pageCount = { items.size }
    )
    
    // Sync pager with external current index
    LaunchedEffect(currentIndex) {
        if (items.isNotEmpty() && currentIndex in 0 until items.size) {
            if (pagerState.currentPage != currentIndex) {
                pagerState.animateScrollToPage(currentIndex)
            }
        }
    }
    
    // Notify when page changes
    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage != currentIndex) {
            onPageChanged(pagerState.currentPage)
        }
    }
    
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // Circular Progress around image (if enabled)
        if (showProgress) {
            CircularProgressIndicator(
                progress = { progress / 1000f },
                modifier = Modifier.size(size + 30.dp),
                color = vibrantColor,
                strokeWidth = 4.dp,
                trackColor = MaterialTheme.colorScheme.onSurface.copy(0.05f)
            )
        }
        
        // Horizontal Pager with transforms
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .size(size)
                .then(
                    if (isCarousel) {
                        Modifier
                            .padding(horizontal = 40.dp)
                    } else Modifier
                ),
            contentPadding = if (isCarousel) PaddingValues(horizontal = 40.dp) else PaddingValues(0.dp),
            pageSpacing = if (isCarousel) 0.dp else 0.dp
        ) { page ->
            val pageOffset = (pagerState.currentPage - page + pagerState.currentPageOffsetFraction)
            val item = items.getOrNull(page)
            
            if (item != null) {
                val albumArtUri = getAlbumArtUriForItem(item)
                
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pagerTransform(pageOffset, transformStyle),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(if (isCircular) CircleShape else RoundedCornerShape(16.dp)),
                        shadowElevation = if (android.os.Build.VERSION.SDK_INT <= 28) 4.dp else 16.dp,
                        color = Color.Black,
                        shape = if (isCircular) CircleShape else RoundedCornerShape(16.dp)
                    ) {
                        SubcomposeAsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(albumArtUri)
                                .size(600)
                                .crossfade(true)
                                .setParameter("sig", TXAImageUtils.artworkSignature)
                                .build(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            val imageState = painter.state
                            if (imageState is coil.compose.AsyncImagePainter.State.Loading || 
                                imageState is coil.compose.AsyncImagePainter.State.Error) {
                                DefaultAlbumArt(vibrantColor = vibrantColor)
                            } else {
                                SubcomposeAsyncImageContent()
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Single Album Cover with transform effect (for non-pager use)
 */
@Composable
fun AlbumCoverWithTransform(
    albumId: Long,
    mediaUri: String = "",
    webArtUrl: String = "",
    vibrantColor: Color,
    modifier: Modifier = Modifier,
    size: Dp = 280.dp,
    isCircular: Boolean = true,
    showProgress: Boolean = true,
    progress: Float = 0f
) {
    val context = LocalContext.current
    val albumArtUri = remember(albumId, mediaUri, webArtUrl) {
        when {
            webArtUrl.isNotEmpty() -> webArtUrl
            albumId != -1L -> ContentUris.withAppendedId(
                Uri.parse("content://media/external/audio/albumart"),
                albumId
            )
            mediaUri.isNotEmpty() -> Uri.parse(mediaUri)
            else -> ""
        }
    }
    
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // Circular Progress around image
        if (showProgress) {
            CircularProgressIndicator(
                progress = { progress / 1000f },
                modifier = Modifier.size(size + 30.dp),
                color = vibrantColor,
                strokeWidth = 4.dp,
                trackColor = MaterialTheme.colorScheme.onSurface.copy(0.05f)
            )
        }
        
        Surface(
            modifier = Modifier
                .size(size)
                .clip(if (isCircular) CircleShape else RoundedCornerShape(16.dp)),
            shadowElevation = if (android.os.Build.VERSION.SDK_INT <= 28) 4.dp else 16.dp,
            color = Color.Black,
            shape = if (isCircular) CircleShape else RoundedCornerShape(16.dp)
        ) {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(context)
                    .data(albumArtUri)
                    .size(600)
                    .crossfade(true)
                    .setParameter("sig", TXAImageUtils.artworkSignature)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            ) {
                val imageState = painter.state
                if (imageState is coil.compose.AsyncImagePainter.State.Loading || 
                    imageState is coil.compose.AsyncImagePainter.State.Error) {
                    DefaultAlbumArt(vibrantColor = vibrantColor)
                } else {
                    SubcomposeAsyncImageContent()
                }
            }
        }
    }
}

/**
 * Helper to get album art URI from an AlbumCoverItem
 */
private fun getAlbumArtUriForItem(item: AlbumCoverItem): Any {
    return when {
        item.webArtUrl.isNotEmpty() -> item.webArtUrl
        item.albumId != -1L -> ContentUris.withAppendedId(
            Uri.parse("content://media/external/audio/albumart"),
            item.albumId
        )
        item.mediaUri.isNotEmpty() -> Uri.parse(item.mediaUri)
        else -> ""
    }
}

/**
 * Convert preference string to PagerTransformStyle
 */
private fun getTransformStyle(pref: String): PagerTransformStyle {
    return when (pref.lowercase()) {
        "normal" -> PagerTransformStyle.NORMAL
        "cascading" -> PagerTransformStyle.CASCADING
        "depth" -> PagerTransformStyle.DEPTH
        "horizontal_flip" -> PagerTransformStyle.HORIZONTAL_FLIP
        "vertical_flip" -> PagerTransformStyle.VERTICAL_FLIP
        "hinge" -> PagerTransformStyle.HINGE
        "vertical_stack" -> PagerTransformStyle.VERTICAL_STACK
        "carousel" -> PagerTransformStyle.CAROUSEL
        else -> PagerTransformStyle.NORMAL
    }
}
