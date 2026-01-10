package com.txapp.musicplayer.glide

import android.graphics.drawable.Drawable
import android.net.Uri
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.signature.MediaStoreSignature
import com.txapp.musicplayer.R
import com.txapp.musicplayer.model.Song
import com.txapp.musicplayer.network.ArtistImageService
import com.txapp.musicplayer.util.TXALogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * TXA Glide Extension - Optimized image loading utilities
 * Pattern inspired by RetroMusic's RetroGlideExtension
 */
object TXAGlideExtension {
    
    private const val TAG = "TXAGlideExtension"
    
    // Cache strategies optimized for different use cases
    private val DISK_CACHE_STRATEGY_ALBUM = DiskCacheStrategy.NONE // MediaStore already caches
    private val DISK_CACHE_STRATEGY_DEFAULT = DiskCacheStrategy.AUTOMATIC
    private val DISK_CACHE_STRATEGY_NETWORK = DiskCacheStrategy.ALL // Cache network images
    
    // Default transition animation
    private const val DEFAULT_ANIMATION = android.R.anim.fade_in
    private const val CROSSFADE_DURATION = 200
    
    /**
     * Get album art URI from MediaStore
     */
    fun getAlbumArtUri(albumId: Long): Uri {
        return android.content.ContentUris.withAppendedId(
            Uri.parse("content://media/external/audio/albumart"),
            albumId
        )
    }
    
    /**
     * Optimized request options for song covers
     * Uses NONE cache strategy since MediaStore handles caching
     */
    fun songCoverOptions(song: Song): RequestOptions {
        return RequestOptions()
            .diskCacheStrategy(DISK_CACHE_STRATEGY_ALBUM)
            .placeholder(R.drawable.ic_launcher)
            .error(R.drawable.ic_launcher)
            .centerCrop()
            .signature(MediaStoreSignature("", song.dateModified, 0))
    }
    
    /**
     * Request options for network-loaded artist images
     */
    fun artistImageOptions(): RequestOptions {
        return RequestOptions()
            .diskCacheStrategy(DISK_CACHE_STRATEGY_NETWORK)
            .placeholder(R.drawable.ic_launcher)
            .error(R.drawable.ic_launcher)
            .centerCrop()
    }
    
    /**
     * Load album art with optimized settings
     */
    fun loadAlbumArt(
        imageView: ImageView,
        song: Song,
        crossfade: Boolean = true
    ) {
        val uri = getAlbumArtUri(song.albumId)
        val request = Glide.with(imageView)
            .load(uri)
            .apply(songCoverOptions(song))
        
        if (crossfade) {
            request.transition(DrawableTransitionOptions.withCrossFade(CROSSFADE_DURATION))
        }
        
        request.into(imageView)
    }
    
    /**
     * Load album art by albumId with optimized settings
     */
    fun loadAlbumArt(
        imageView: ImageView,
        albumId: Long,
        crossfade: Boolean = true
    ) {
        val uri = getAlbumArtUri(albumId)
        val request = Glide.with(imageView)
            .load(uri)
            .apply(
                RequestOptions()
                    .diskCacheStrategy(DISK_CACHE_STRATEGY_ALBUM)
                    .placeholder(R.drawable.ic_launcher)
                    .error(R.drawable.ic_launcher)
                    .centerCrop()
            )
        
        if (crossfade) {
            request.transition(DrawableTransitionOptions.withCrossFade(CROSSFADE_DURATION))
        }
        
        request.into(imageView)
    }
    
    /**
     * Load artist image from Deezer API with fallback to album art
     * 
     * @param imageView Target ImageView
     * @param artistName Name of the artist
     * @param fallbackAlbumId Album ID to use if network image fails
     * @param scope CoroutineScope for async operations
     */
    fun loadArtistImage(
        imageView: ImageView,
        artistName: String,
        fallbackAlbumId: Long = -1L,
        scope: CoroutineScope
    ) {
        // First load fallback/placeholder
        if (fallbackAlbumId != -1L) {
            Glide.with(imageView)
                .load(getAlbumArtUri(fallbackAlbumId))
                .apply(
                    RequestOptions()
                        .diskCacheStrategy(DISK_CACHE_STRATEGY_ALBUM)
                        .placeholder(R.drawable.ic_launcher)
                        .error(R.drawable.ic_launcher)
                        .centerCrop()
                )
                .into(imageView)
        } else {
            Glide.with(imageView)
                .load(R.drawable.ic_launcher)
                .into(imageView)
        }
        
        // Then try to load from network
        scope.launch {
            try {
                val imageUrl = ArtistImageService.getArtistImageUrl(artistName)
                
                if (!imageUrl.isNullOrEmpty()) {
                    withContext(Dispatchers.Main) {
                        Glide.with(imageView)
                            .load(imageUrl)
                            .apply(artistImageOptions())
                            .transition(DrawableTransitionOptions.withCrossFade(CROSSFADE_DURATION))
                            .into(imageView)
                    }
                    TXALogger.appD(TAG, "Loaded artist image from network: $artistName")
                }
            } catch (e: Exception) {
                TXALogger.appE(TAG, "Failed to load artist image for: $artistName", e)
                // Keep fallback image - already shown
            }
        }
    }
    
    /**
     * Load artist image synchronously - for use in suspend functions
     * Returns the URL if found, null otherwise
     */
    suspend fun getArtistImageUrl(artistName: String): String? {
        return ArtistImageService.getArtistImageUrl(artistName)
    }
    
    /**
     * Extension function for RequestBuilder to apply song cover options
     */
    fun <T> RequestBuilder<T>.songCoverOptions(song: Song): RequestBuilder<T> {
        return diskCacheStrategy(DISK_CACHE_STRATEGY_ALBUM)
            .placeholder(R.drawable.ic_launcher)
            .error(R.drawable.ic_launcher)
            .signature(MediaStoreSignature("", song.dateModified, 0))
    }
    
    /**
     * Clear Glide cache (call on low memory)
     */
    fun clearMemoryCache(imageView: ImageView) {
        Glide.get(imageView.context).clearMemory()
    }
}

