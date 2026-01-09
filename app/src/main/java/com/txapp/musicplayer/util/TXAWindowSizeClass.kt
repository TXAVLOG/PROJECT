package com.txapp.musicplayer.util

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * TXAWindowSizeClass - Helper để xác định kích thước màn hình và thiết bị
 * 
 * Sử dụng Window Size Classes theo Material Design guidelines:
 * - Compact: < 600dp (phones portrait)
 * - Medium: 600dp - 840dp (tablets portrait, phones landscape)
 * - Expanded: > 840dp (tablets landscape, desktops)
 */
object TXAWindowSizeClass {
    
    /**
     * Width size classes
     */
    enum class WidthSizeClass {
        COMPACT,  // < 600dp - Phone portrait
        MEDIUM,   // 600dp - 840dp - Tablet portrait, Phone landscape
        EXPANDED  // > 840dp - Tablet landscape
    }
    
    /**
     * Height size classes
     */
    enum class HeightSizeClass {
        COMPACT,  // < 480dp
        MEDIUM,   // 480dp - 900dp
        EXPANDED  // > 900dp
    }
    
    /**
     * Combined size class for layout decisions
     */
    data class WindowSize(
        val widthClass: WidthSizeClass,
        val heightClass: HeightSizeClass,
        val widthDp: Dp,
        val heightDp: Dp,
        val isLandscape: Boolean
    ) {
        val isTablet: Boolean
            get() = minOf(widthDp.value, heightDp.value) >= 600
        
        val isPhonePortrait: Boolean
            get() = widthClass == WidthSizeClass.COMPACT && !isLandscape
        
        val isPhoneLandscape: Boolean
            get() = widthClass == WidthSizeClass.COMPACT && isLandscape
        
        val isTabletPortrait: Boolean
            get() = widthClass == WidthSizeClass.MEDIUM && !isLandscape
        
        val isTabletLandscape: Boolean
            get() = widthClass == WidthSizeClass.EXPANDED
        
        val shouldUseTwoPane: Boolean
            get() = widthClass == WidthSizeClass.EXPANDED || 
                    (widthClass == WidthSizeClass.MEDIUM && isLandscape)
        
        val gridColumns: Int
            get() = when {
                widthClass == WidthSizeClass.EXPANDED -> 4
                widthClass == WidthSizeClass.MEDIUM -> 3
                isLandscape -> 3
                else -> 2
            }
        
        @Composable
        fun albumGridColumns(): Int {
            val pref = TXAPreferences.albumGridSize.collectAsState().value
            return when {
                widthClass == WidthSizeClass.EXPANDED -> pref + 3
                widthClass == WidthSizeClass.MEDIUM -> pref + 2
                isLandscape -> pref + 2
                else -> pref
            }
        }
        
        @Composable
        fun artistGridColumns(): Int {
            val pref = TXAPreferences.artistGridSize.collectAsState().value
            return when {
                widthClass == WidthSizeClass.EXPANDED -> pref + 3
                widthClass == WidthSizeClass.MEDIUM -> pref + 1
                isLandscape -> pref + 1
                else -> pref
            }
        }
        
        val contentPadding: Dp
            get() = when (widthClass) {
                WidthSizeClass.EXPANDED -> 32.dp
                WidthSizeClass.MEDIUM -> 24.dp
                WidthSizeClass.COMPACT -> 16.dp
            }
        
        val itemSpacing: Dp
            get() = when (widthClass) {
                WidthSizeClass.EXPANDED -> 16.dp
                WidthSizeClass.MEDIUM -> 12.dp
                WidthSizeClass.COMPACT -> 8.dp
            }
    }
    
    /**
     * Tính width size class từ width dp
     */
    fun calculateWidthClass(widthDp: Dp): WidthSizeClass {
        return when {
            widthDp < 600.dp -> WidthSizeClass.COMPACT
            widthDp < 840.dp -> WidthSizeClass.MEDIUM
            else -> WidthSizeClass.EXPANDED
        }
    }
    
    /**
     * Tính height size class từ height dp
     */
    fun calculateHeightClass(heightDp: Dp): HeightSizeClass {
        return when {
            heightDp < 480.dp -> HeightSizeClass.COMPACT
            heightDp < 900.dp -> HeightSizeClass.MEDIUM
            else -> HeightSizeClass.EXPANDED
        }
    }
    
    /**
     * Lấy WindowSize từ Activity
     */
    fun getWindowSize(activity: Activity): WindowSize {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            val metrics = activity.windowManager.currentWindowMetrics
            val density = activity.resources.displayMetrics.density
            
            val widthDp = (metrics.bounds.width() / density).dp
            val heightDp = (metrics.bounds.height() / density).dp
            
            val isLandscape = activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            
            return WindowSize(
                widthClass = calculateWidthClass(widthDp),
                heightClass = calculateHeightClass(heightDp),
                widthDp = widthDp,
                heightDp = heightDp,
                isLandscape = isLandscape
            )
        } else {
            // Fallback for Android versions prior to 11
            return getWindowSize(activity as Context)
        }
    }
    
    /**
     * Lấy WindowSize từ Context (đơn giản hơn)
     */
    fun getWindowSize(context: Context): WindowSize {
        val config = context.resources.configuration
        val widthDp = config.screenWidthDp.dp
        val heightDp = config.screenHeightDp.dp
        val isLandscape = config.orientation == Configuration.ORIENTATION_LANDSCAPE
        
        return WindowSize(
            widthClass = calculateWidthClass(widthDp),
            heightClass = calculateHeightClass(heightDp),
            widthDp = widthDp,
            heightDp = heightDp,
            isLandscape = isLandscape
        )
    }
}

/**
 * Composable helper để lấy WindowSize trong Compose
 */
@Composable
fun rememberWindowSize(): TXAWindowSizeClass.WindowSize {
    val configuration = LocalConfiguration.current
    
    return remember(configuration) {
        val widthDp = configuration.screenWidthDp.dp
        val heightDp = configuration.screenHeightDp.dp
        val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        
        TXAWindowSizeClass.WindowSize(
            widthClass = TXAWindowSizeClass.calculateWidthClass(widthDp),
            heightClass = TXAWindowSizeClass.calculateHeightClass(heightDp),
            widthDp = widthDp,
            heightDp = heightDp,
            isLandscape = isLandscape
        )
    }
}

/**
 * Extension function để kiểm tra nhanh tablet
 */
@Composable
fun isTablet(): Boolean {
    val windowSize = rememberWindowSize()
    return windowSize.isTablet
}

/**
 * Extension function để kiểm tra landscape
 */
@Composable
fun isLandscape(): Boolean {
    val configuration = LocalConfiguration.current
    return configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
}
