package com.txapp.musicplayer.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

/**
 * Utility for image processing like cropping and resizing for Artist logos and Album Art
 */
object TXAImageUtils {
    private const val TAG = "TXAImageUtils"
    
    // Global signature for forcing artwork refresh
    private val _artworkSignature = androidx.compose.runtime.mutableLongStateOf(System.currentTimeMillis())
    val artworkSignature: Long get() = _artworkSignature.longValue
    
    fun invalidateArtworkCache() {
        _artworkSignature.longValue = System.currentTimeMillis()
    }
    
    // Fixed size for Artist logos and Album Art for optimal display
    const val FIXED_SIZE = 1000
    
    /**
     * Resizes and crops a bitmap to a square fixed size
     */
    fun processImage(context: Context, uri: Uri): File? {
        return try {
            // 1. First decode with inJustDecodeBounds=true to check dimensions
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, options)
            } ?: return null

            // 2. Calculate inSampleSize directly
            options.inJustDecodeBounds = false
            // Calculate ratio to scale image to at least FIXED_SIZE
            val srcWidth = options.outWidth
            val srcHeight = options.outHeight
            var inSampleSize = 1
            if (srcHeight > FIXED_SIZE || srcWidth > FIXED_SIZE) {
                val halfHeight: Int = srcHeight / 2
                val halfWidth: Int = srcWidth / 2
                while ((halfHeight / inSampleSize) >= FIXED_SIZE && (halfWidth / inSampleSize) >= FIXED_SIZE) {
                    inSampleSize *= 2
                }
            }
            options.inSampleSize = inSampleSize

            // 3. Decode bitmap with inSampleSize
            val originalBitmap = context.contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, options)
            } ?: return null
            
            // 4. Crop to square (center crop)
            val width = originalBitmap.width
            val height = originalBitmap.height
            val newDimension = if (width < height) width else height
            
            val croppedBitmap = Bitmap.createBitmap(
                originalBitmap,
                (width - newDimension) / 2,
                (height - newDimension) / 2,
                newDimension,
                newDimension
            )
            
            // 5. Resize to FIXED_SIZE
            val scaledBitmap = Bitmap.createScaledBitmap(croppedBitmap, FIXED_SIZE, FIXED_SIZE, true)
            
            // 6. Save to a temporary file
            val tempFile = File(context.cacheDir, "txa_cropped_art_${System.currentTimeMillis()}.jpg")
            FileOutputStream(tempFile).use { out ->
                scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            
            // Cleanup
            if (originalBitmap != croppedBitmap && !originalBitmap.isRecycled) originalBitmap.recycle()
            if (croppedBitmap != scaledBitmap && !croppedBitmap.isRecycled) croppedBitmap.recycle()
            if (!scaledBitmap.isRecycled) scaledBitmap.recycle()
            
            tempFile
        } catch (e: Exception) {
            TXALogger.appE(TAG, "Error processing image: ${e.message}")
            null
        } catch (oom: OutOfMemoryError) {
             TXALogger.appE(TAG, "OOM Error processing image: ${oom.message}")
             System.gc()
             null
        }
    }

    /**
     * Get byte array from File
     */
    fun fileToBytes(file: File): ByteArray? {
        return try {
            file.readBytes()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Fallback for broken artwork URIs
     */
    fun getFallbackUri(context: android.content.Context, songId: Long): android.net.Uri {
        // Return a generic music icon or try to get song uri
        return android.content.ContentUris.withAppendedId(
            android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            songId
        )
    }
}
