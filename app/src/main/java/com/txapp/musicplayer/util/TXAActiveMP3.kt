package com.txapp.musicplayer.util

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * TXAActiveMP3 - Quản lý trạng thái bài đang phát trong toàn bộ app
 * 
 * Cung cấp styling & animation cho bài đang phát tùy theo context:
 * - Songs: Màu accent + icon nhạc nhỏ
 * - Album: Gradient pulse effect
 * - Playlist: Glowing border + equalizer bars animation
 * 
 * Usage:
 * ```
 * // Check if song is currently playing
 * val isActive = TXAActiveMP3.isActive(song.id)
 * 
 * // Get background modifier for songs list
 * val bgModifier = TXAActiveMP3.getSongItemBackground(isActive)
 * 
 * // Display animated indicator for playlist
 * if (isActive) {
 *     TXAActiveMP3.PlaylistNowPlayingIndicator()
 * }
 * ```
 * BUILD BY TXA
 */
object TXAActiveMP3 {

    // ════════════════════════════════════════════════════════════════════
    // STATE MANAGEMENT
    // ════════════════════════════════════════════════════════════════════
    
    private val _currentPlayingSongId = MutableStateFlow(-1L)
    val currentPlayingSongId: StateFlow<Long> = _currentPlayingSongId.asStateFlow()
    
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()
    
    private val _currentPlaylistId = MutableStateFlow(-1L)
    val currentPlaylistId: StateFlow<Long> = _currentPlaylistId.asStateFlow()
    
    private val _currentAlbumId = MutableStateFlow(-1L)
    val currentAlbumId: StateFlow<Long> = _currentAlbumId.asStateFlow()
    
    /**
     * Cập nhật bài đang phát
     */
    fun updateNowPlaying(
        songId: Long,
        isPlaying: Boolean,
        playlistId: Long = -1L,
        albumId: Long = -1L
    ) {
        _currentPlayingSongId.value = songId
        _isPlaying.value = isPlaying
        _currentPlaylistId.value = playlistId
        _currentAlbumId.value = albumId
    }
    
    /**
     * Kiểm tra bài hát có đang phát không
     */
    fun isActive(songId: Long): Boolean {
        return _currentPlayingSongId.value == songId && _isPlaying.value
    }
    
    /**
     * Kiểm tra bài hát có phải là bài hiện tại không (có thể đang pause)
     */
    fun isCurrent(songId: Long): Boolean {
        return _currentPlayingSongId.value == songId
    }
    
    /**
     * Kiểm tra playlist có đang phát không
     */
    fun isPlaylistActive(playlistId: Long): Boolean {
        return _currentPlaylistId.value == playlistId && _isPlaying.value
    }
    
    /**
     * Kiểm tra album có đang phát không  
     */
    fun isAlbumActive(albumId: Long): Boolean {
        return _currentAlbumId.value == albumId && _isPlaying.value
    }

    // ════════════════════════════════════════════════════════════════════
    // STYLE: SONGS LIST (Simple accent highlight)
    // ════════════════════════════════════════════════════════════════════
    
    /**
     * Background color cho item bài hát trong danh sách Songs
     */
    @Composable
    fun getSongItemBackgroundColor(isActive: Boolean): Color {
        return if (isActive) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
        } else {
            Color.Transparent
        }
    }
    
    /**
     * Text color cho item bài hát đang phát
     */
    @Composable
    fun getSongItemTextColor(isActive: Boolean): Color {
        return if (isActive) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurface
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // STYLE: ALBUM (Gradient Pulse Effect)
    // ════════════════════════════════════════════════════════════════════
    
    /**
     * Animated gradient background cho item trong Album detail
     */
    @Composable
    fun AlbumNowPlayingBackground(
        modifier: Modifier = Modifier,
        content: @Composable BoxScope.() -> Unit
    ) {
        val infiniteTransition = rememberInfiniteTransition(label = "albumPulse")
        
        val pulseAlpha by infiniteTransition.animateFloat(
            initialValue = 0.1f,
            targetValue = 0.25f,
            animationSpec = infiniteRepeatable(
                animation = tween(1500, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulseAlpha"
        )
        
        val accentColor = MaterialTheme.colorScheme.primary
        
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(12.dp))
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            accentColor.copy(alpha = pulseAlpha),
                            accentColor.copy(alpha = pulseAlpha * 0.5f),
                            Color.Transparent
                        )
                    )
                ),
            content = content
        )
    }
    
    /**
     * Static background cho album item (không animation)
     */
    @Composable
    fun getAlbumItemBackgroundColor(isActive: Boolean): Color {
        return if (isActive) {
            val accentColor = MaterialTheme.colorScheme.primary
            accentColor.copy(alpha = 0.12f)
        } else {
            Color.Transparent
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // STYLE: PLAYLIST (Glowing Border + Equalizer)
    // ════════════════════════════════════════════════════════════════════
    
    /**
     * Animated equalizer bars indicator cho Playlist
     * Hiệu ứng: 3 thanh nhảy lên xuống
     */
    @Composable
    fun PlaylistNowPlayingIndicator(
        modifier: Modifier = Modifier,
        barColor: Color = MaterialTheme.colorScheme.primary,
        barWidth: Dp = 3.dp,
        maxHeight: Dp = 16.dp
    ) {
        val infiniteTransition = rememberInfiniteTransition(label = "eqBars")
        
        // Bar 1 - Fast
        val bar1Height by infiniteTransition.animateFloat(
            initialValue = 0.3f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(400, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "bar1"
        )
        
        // Bar 2 - Medium
        val bar2Height by infiniteTransition.animateFloat(
            initialValue = 0.5f,
            targetValue = 0.8f,
            animationSpec = infiniteRepeatable(
                animation = tween(550, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "bar2"
        )
        
        // Bar 3 - Slow
        val bar3Height by infiniteTransition.animateFloat(
            initialValue = 0.4f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(650, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "bar3"
        )
        
        Row(
            modifier = modifier.height(maxHeight),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // Bar 1
            Box(
                modifier = Modifier
                    .width(barWidth)
                    .fillMaxHeight(bar1Height)
                    .clip(RoundedCornerShape(1.dp))
                    .background(barColor)
            )
            // Bar 2
            Box(
                modifier = Modifier
                    .width(barWidth)
                    .fillMaxHeight(bar2Height)
                    .clip(RoundedCornerShape(1.dp))
                    .background(barColor)
            )
            // Bar 3
            Box(
                modifier = Modifier
                    .width(barWidth)
                    .fillMaxHeight(bar3Height)
                    .clip(RoundedCornerShape(1.dp))
                    .background(barColor)
            )
        }
    }
    
    /**
     * Glowing border effect cho playlist item đang phát
     */
    @Composable
    fun PlaylistNowPlayingBorder(
        modifier: Modifier = Modifier,
        content: @Composable BoxScope.() -> Unit
    ) {
        val infiniteTransition = rememberInfiniteTransition(label = "glowBorder")
        
        val glowAlpha by infiniteTransition.animateFloat(
            initialValue = 0.4f,
            targetValue = 0.8f,
            animationSpec = infiniteRepeatable(
                animation = tween(1200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "glowAlpha"
        )
        
        val accentColor = MaterialTheme.colorScheme.primary
        
        Surface(
            modifier = modifier,
            shape = RoundedCornerShape(12.dp),
            color = accentColor.copy(alpha = 0.08f),
            shadowElevation = 0.dp,
            tonalElevation = 0.dp
        ) {
            Box(
                modifier = Modifier
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                accentColor.copy(alpha = glowAlpha * 0.15f),
                                Color.Transparent
                            )
                        )
                    ),
                content = content
            )
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // COMBINED SONG ITEM COMPOSABLE
    // ════════════════════════════════════════════════════════════════════
    
    /**
     * Context type cho việc áp dụng style
     */
    enum class Context {
        SONGS,      // Danh sách tất cả bài hát
        ALBUM,      // Chi tiết album
        PLAYLIST    // Chi tiết playlist
    }
    
    /**
     * Composable wrapper giúp áp dụng style tự động dựa vào context
     */
    @Composable
    fun NowPlayingWrapper(
        isActive: Boolean,
        context: Context,
        modifier: Modifier = Modifier,
        content: @Composable BoxScope.() -> Unit
    ) {
        when {
            !isActive -> {
                Box(modifier = modifier, content = content)
            }
            context == Context.ALBUM -> {
                AlbumNowPlayingBackground(modifier = modifier, content = content)
            }
            context == Context.PLAYLIST -> {
                PlaylistNowPlayingBorder(modifier = modifier, content = content)
            }
            else -> {
                // SONGS - Simple background
                Box(
                    modifier = modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(getSongItemBackgroundColor(true)),
                    content = content
                )
            }
        }
    }
}
