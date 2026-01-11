package com.txapp.musicplayer.appshortcuts

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.graphics.drawable.LayerDrawable
import android.os.Build
import androidx.annotation.DrawableRes
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.txapp.musicplayer.R
import com.txapp.musicplayer.util.TXAPreferences

object AppShortcutIconGenerator {

    @RequiresApi(Build.VERSION_CODES.N_MR1)
    fun generateThemedIcon(context: Context, @DrawableRes iconResId: Int): Icon {
        // Get current accent color from ThemeStore or default
        val accentColor = try {
            Color.parseColor(TXAPreferences.currentAccent)
        } catch (e: Exception) {
            ContextCompat.getColor(context, R.color.teal_200) // Fallback or defined resource
        }
        
        // Load the vector drawable for the shortcut icon
        val vectorDrawable = ContextCompat.getDrawable(context, iconResId)?.mutate()
        vectorDrawable?.setTint(accentColor)

        // Create the background (white circle/squircle)
        // We use a predefined background drawable or create one programmatically
        val background = ContextCompat.getDrawable(context, R.drawable.ic_app_shortcut_background)
        
        // Combine them
        val layers = arrayOf(background, vectorDrawable)
        val layerDrawable = LayerDrawable(layers)

        // Adjust the icon to be centered and smaller than the background
        val insert = context.resources.getDimensionPixelSize(R.dimen.app_shortcut_icon_inset) // Define this if likely needed or hardcode
        layerDrawable.setLayerInset(1, insert, insert, insert, insert) // index 1 is the foreground icon

        // Convert to Bitmap
        val bitmap = drawableToBitmap(layerDrawable)
        return Icon.createWithBitmap(bitmap)
    }
    
    // Fallback or specific method if needed
    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        if (drawable is BitmapDrawable) {
            return drawable.bitmap
        }
        
        val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 96
        val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 96
        
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }
}
