package com.txapp.musicplayer.ui.theme

// cms

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.txapp.musicplayer.util.TXAPreferences

// TXA Brand Colors
val TXAPrimary = Color(0xFFFF1744)
val TXASecondary = Color(0xFFA5D6A7) // Neutral green or something else? Let's keep it somewhat harmonious
val TXAAccent = Color(0xFFFF5252)

private val DarkColorScheme = darkColorScheme(
    primary = TXAPrimary,
    secondary = TXASecondary,
    tertiary = TXAAccent,
    background = Color(0xFF121212),
    surface = Color(0xFF1E1E1E),
    surfaceVariant = Color(0xFF2D2D2D),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = TXAPrimary,
    secondary = TXASecondary,
    tertiary = TXAAccent,
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    surfaceVariant = Color(0xFFF0F0F0),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F)
)

@Composable
fun TXAMusicTheme(
    content: @Composable () -> Unit
) {
    val themeMode by TXAPreferences.theme.collectAsState()
    val accentHex by TXAPreferences.accentColor.collectAsState()
    
    val darkTheme = when (themeMode) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }
    
    val customAccent = try { Color(android.graphics.Color.parseColor(accentHex)) } catch (e: Exception) { TXAPrimary }

    val darkColorScheme = DarkColorScheme.copy(primary = customAccent)
    val lightColorScheme = LightColorScheme.copy(primary = customAccent)

    val colorScheme = if (darkTheme) darkColorScheme else lightColorScheme
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content
    )
}
