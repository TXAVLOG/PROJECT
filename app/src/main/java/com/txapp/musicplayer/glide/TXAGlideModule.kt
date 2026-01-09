package com.txapp.musicplayer.glide

import android.content.Context
import android.graphics.Bitmap
import com.bumptech.glide.Glide
import com.bumptech.glide.Registry
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.module.AppGlideModule

/**
 * TXA Music Glide Module
 * Performance optimizations:
 * 1. Disable manifest parsing for faster initialization
 * 2. Custom loaders can be added here for audio covers
 */
@GlideModule
class TXAGlideModule : AppGlideModule() {
    
    override fun registerComponents(context: Context, glide: Glide, registry: Registry) {
        // Custom loaders can be registered here if needed
        // For now, we use default loaders with optimized settings
    }

    override fun isManifestParsingEnabled(): Boolean {
        // Disable manifest parsing for faster app startup
        return false
    }
}
