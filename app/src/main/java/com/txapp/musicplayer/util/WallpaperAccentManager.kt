package com.txapp.musicplayer.util

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Extract accent colors from system wallpaper
 * Used for dynamic theming based on wallpaper
 */
object WallpaperAccentManager {
    private const val TAG = "WallpaperAccent"
    
    /**
     * Extract dominant color from wallpaper
     */
    suspend fun getWallpaperAccentColor(context: Context): Int? {
        return withContext(Dispatchers.IO) {
            try {
                val wallpaperManager = WallpaperManager.getInstance(context)
                val wallpaper = wallpaperManager.drawable ?: return@withContext null
                
                // Convert to bitmap (scaled down for performance)
                val bitmap = wallpaper.toBitmap(
                    width = 200,
                    height = 200,
                    config = Bitmap.Config.ARGB_8888
                )
                
                // Extract palette
                val palette = Palette.from(bitmap).generate()
                
                // Try vibrant first, fallback to dominant
                palette.vibrantSwatch?.rgb
                    ?: palette.dominantSwatch?.rgb
                    ?: palette.mutedSwatch?.rgb
                
            } catch (e: Exception) {
                TXALogger.e(TAG, "Failed to extract wallpaper color", e)
                null
            }
        }
    }
    
    /**
     * Get multiple accent colors from wallpaper
     */
    suspend fun getWallpaperPalette(context: Context): WallpaperPalette? {
        return withContext(Dispatchers.IO) {
            try {
                val wallpaperManager = WallpaperManager.getInstance(context)
                val wallpaper = wallpaperManager.drawable ?: return@withContext null
                
                val bitmap = wallpaper.toBitmap(200, 200, Bitmap.Config.ARGB_8888)
                val palette = Palette.from(bitmap).generate()
                
                WallpaperPalette(
                    vibrant = palette.vibrantSwatch?.rgb,
                    vibrantDark = palette.darkVibrantSwatch?.rgb,
                    vibrantLight = palette.lightVibrantSwatch?.rgb,
                    muted = palette.mutedSwatch?.rgb,
                    mutedDark = palette.darkMutedSwatch?.rgb,
                    mutedLight = palette.lightMutedSwatch?.rgb,
                    dominant = palette.dominantSwatch?.rgb
                )
            } catch (e: Exception) {
                TXALogger.e(TAG, "Failed to extract wallpaper palette", e)
                null
            }
        }
    }
    
    data class WallpaperPalette(
        val vibrant: Int?,
        val vibrantDark: Int?,
        val vibrantLight: Int?,
        val muted: Int?,
        val mutedDark: Int?,
        val mutedLight: Int?,
        val dominant: Int?
    )
}
