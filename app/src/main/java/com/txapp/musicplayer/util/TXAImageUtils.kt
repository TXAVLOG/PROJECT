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
    
    // Fixed size for Artist logos and Album Art for optimal display
    const val FIXED_SIZE = 1000
    
    /**
     * Resizes and crops a bitmap to a square fixed size
     */
    fun processImage(context: Context, uri: Uri): File? {
        return try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            val originalBitmap = BitmapFactory.decodeStream(inputStream) ?: return null
            
            // 1. Crop to square (center crop)
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
            
            // 2. Resize to FIXED_SIZE
            val scaledBitmap = Bitmap.createScaledBitmap(croppedBitmap, FIXED_SIZE, FIXED_SIZE, true)
            
            // 3. Save to a temporary file
            val tempFile = File(context.cacheDir, "txa_cropped_art_${System.currentTimeMillis()}.jpg")
            FileOutputStream(tempFile).use { out ->
                scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            
            // Cleanup
            if (originalBitmap != croppedBitmap) originalBitmap.recycle()
            croppedBitmap.recycle()
            scaledBitmap.recycle()
            
            tempFile
        } catch (e: Exception) {
            TXALogger.appE(TAG, "Error processing image: ${e.message}")
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
