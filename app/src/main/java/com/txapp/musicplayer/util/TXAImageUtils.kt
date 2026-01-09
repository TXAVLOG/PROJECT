package com.txapp.musicplayer.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import java.util.Random

import java.io.File
import java.io.FileOutputStream
import android.net.Uri

object TXAImageUtils {
    
    fun getFallbackUri(context: Context, songId: Long): Uri {
        val fileName = "fallback_gradient_$songId.png"
        val file = File(context.cacheDir, fileName)
        
        if (file.exists()) {
            return Uri.fromFile(file)
        }
        
        // Generate and save
        val bitmap = generateRandomGradientBitmap(seed = songId)
        try {
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // If save fails, return empty builder that might fail gracefully or just null if we could return null?
            // Actually returning the file Uri even if failed might just show empty.
        }
        return Uri.fromFile(file)
    }
    
    // Cache for generated bitmaps to avoid re-generating too often
    private val bitmapCache = LruCache<Long, Bitmap>(10) // Cache last 10 generated bitmaps by seed/id

    fun generateRandomGradientBitmap(width: Int = 512, height: Int = 512, seed: Long): Bitmap {
        // Check cache first
        bitmapCache.get(seed)?.let { return it }

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val random = Random(seed)

        // Generate 2 random vibrant colors
        // Keep saturation and lightness high for "vibrant" look
        val color1 = Color.HSVToColor(floatArrayOf(random.nextFloat() * 360f, 0.6f + random.nextFloat() * 0.4f, 0.6f + random.nextFloat() * 0.4f))
        val color2 = Color.HSVToColor(floatArrayOf(random.nextFloat() * 360f, 0.6f + random.nextFloat() * 0.4f, 0.6f + random.nextFloat() * 0.4f))

        val paint = Paint()
        // Determine gradient direction randomly
        val direction = random.nextInt(4)
        val shader = when (direction) {
            0 -> LinearGradient(0f, 0f, width.toFloat(), height.toFloat(), color1, color2, Shader.TileMode.CLAMP) // TL to BR
            1 -> LinearGradient(0f, height.toFloat(), width.toFloat(), 0f, color1, color2, Shader.TileMode.CLAMP) // BL to TR
            2 -> LinearGradient(0f, 0f, 0f, height.toFloat(), color1, color2, Shader.TileMode.CLAMP) // Top to Bottom
            else -> LinearGradient(0f, 0f, width.toFloat(), 0f, color1, color2, Shader.TileMode.CLAMP) // Left to Right
        }
        
        paint.shader = shader
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

        bitmapCache.put(seed, bitmap)
        return bitmap
    }

    // Simple LRU Cache
    class LruCache<K, V>(private val maxSize: Int) : java.util.LinkedHashMap<K, V>(maxSize, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean {
            return size > maxSize
        }
    }
}
