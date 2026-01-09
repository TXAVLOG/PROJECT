package com.txapp.musicplayer.service

import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import com.google.common.collect.ImmutableList
import androidx.core.app.NotificationCompat
import androidx.media3.session.MediaNotification
import com.txapp.musicplayer.R
import com.txapp.musicplayer.util.TXALogger

/**
 * Custom MediaNotificationProvider for TXA Music
 * 
 * Version compatibility logic:
 * - Android 13+ (API 33+): Full custom layout with Shuffle & Favorite buttons
 * - Android 12 and below (API < 33 including Android 9): Use default system layout 
 *   to prevent crashes on older APIs
 */
@OptIn(UnstableApi::class)
class TXAMediaNotificationProvider(context: Context) : DefaultMediaNotificationProvider(context) {
    
    companion object {
        private const val TAG = "TXAMediaNotificationProvider"
        
        // Custom command actions
        const val ACTION_TOGGLE_FAVORITE = "txa.action.TOGGLE_FAVORITE"
        const val ACTION_TOGGLE_SHUFFLE = "txa.action.TOGGLE_SHUFFLE"
        
        // SessionCommand objects
        val COMMAND_TOGGLE_FAVORITE = SessionCommand(ACTION_TOGGLE_FAVORITE, Bundle.EMPTY)
        val COMMAND_TOGGLE_SHUFFLE = SessionCommand(ACTION_TOGGLE_SHUFFLE, Bundle.EMPTY)
    }
    
    /**
     * Override getMediaButtons with version-specific logic
     * 
     * Android 9 (API 28) and older versions have compatibility issues with
     * custom CommandButton configurations in Media3 notifications.
     * To prevent crashes, we use the default implementation for these versions.
     */
    override fun getMediaButtons(
        session: MediaSession,
        playerCommands: Player.Commands,
        customLayout: ImmutableList<CommandButton>,
        showPauseButton: Boolean
    ): ImmutableList<CommandButton> {
        
        // Android 9-12 Fix: Use default implementation for API < 33
        // This prevents crashes related to custom button configurations
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            TXALogger.d(TAG, "Using default media buttons for API ${Build.VERSION.SDK_INT}")
            return super.getMediaButtons(session, playerCommands, customLayout, showPauseButton)
        }

        // For Android 13+ (API 33+), use full custom layout
        return try {
            // If custom layout is provided (via MediaSession.setCustomLayout), use it
            if (customLayout.isNotEmpty()) {
                return customLayout
            }
            buildCustomMediaButtons(session, playerCommands, showPauseButton)
        } catch (e: Exception) {
            TXALogger.e(TAG, "Error building custom media buttons, falling back to default", e)
            super.getMediaButtons(session, playerCommands, customLayout, showPauseButton)
        }
    }

    override fun addNotificationActions(
        mediaSession: MediaSession,
        mediaButtons: ImmutableList<CommandButton>,
        builder: NotificationCompat.Builder,
        actionFactory: MediaNotification.ActionFactory
    ): IntArray {
        // Use super to add actions to the builder
        val defaultIndices = super.addNotificationActions(mediaSession, mediaButtons, builder, actionFactory)
        
        // 1. Android 13+ (API 33+) supports up to 5 actions in compact view
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val compactIndices = mutableListOf<Int>()
            
            // Prefer: [Previous, Play, Next]
            // Indices depend on what buttons were added in buildCustomMediaButtons
            // Assuming order: [Shuffle, Prev, Play, Next, Favorite]
            val showShuffle = com.txapp.musicplayer.util.TXAPreferences.isShowShuffleBtn
            val showFavorite = com.txapp.musicplayer.util.TXAPreferences.isShowFavoriteBtn
            
            val offset = if (showShuffle) 1 else 0
            
            // Middle buttons (Prev, Play, Next)
            for (i in 0..2) {
                val idx = offset + i
                if (idx < mediaButtons.size) {
                    compactIndices.add(idx)
                }
            }
            
            // On API 33+, we can add favorite too
            if (showFavorite) {
                val favIdx = if (showShuffle) 4 else 3
                if (favIdx < mediaButtons.size) {
                    compactIndices.add(favIdx)
                }
            }
            
            return compactIndices.toIntArray()
        }

        // 2. Android 12 and below (API < 33) ONLY supports 3 actions in compact view
        // IMPORTANT: We must NOT return indices that don't exist in the actual buttons list.
        val compactIndices = mutableListOf<Int>()
        
        // Use defaultIndices as a base, but strictly limit to available buttons and max 3
        for (i in defaultIndices.indices) {
            val indexValue = defaultIndices[i]
            // Standard MediaStyle only allows up to 3 actions in compact view for old Android
            if (compactIndices.size < 3) {
                compactIndices.add(indexValue)
            }
        }
        
        return compactIndices.toIntArray()
    }
    
    /**
     * Build custom media buttons for Android 13+
     * Layout: [Shuffle] [Previous] [Play/Pause] [Next] [Favorite]
     */
    private fun buildCustomMediaButtons(
        session: MediaSession,
        playerCommands: Player.Commands,
        showPauseButton: Boolean
    ): ImmutableList<CommandButton> {
        val buttons = mutableListOf<CommandButton>()
        
        val player = session.player
        val isShuffleOn = player.shuffleModeEnabled
        val currentItem = player.currentMediaItem
        val isFavorite = currentItem?.mediaMetadata?.extras?.getBoolean("is_favorite", false) ?: false
        
        // 1. Shuffle Button (leftmost)
        if (com.txapp.musicplayer.util.TXAPreferences.isShowShuffleBtn) {
            val shuffleIcon = if (isShuffleOn) R.drawable.ic_shuffle_on else R.drawable.ic_shuffle
            buttons.add(
                CommandButton.Builder()
                    .setDisplayName("Shuffle")
                    .setIconResId(shuffleIcon)
                    .setSessionCommand(COMMAND_TOGGLE_SHUFFLE)
                    .build()
            )
        }
        
        // 2. Previous Button
        if (playerCommands.contains(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)) {
            buttons.add(
                CommandButton.Builder()
                    .setDisplayName("Previous")
                    .setIconResId(R.drawable.ic_skip_previous)
                    .setPlayerCommand(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                    .build()
            )
        }
        
        // 3. Play/Pause Button (center)
        if (playerCommands.contains(Player.COMMAND_PLAY_PAUSE)) {
            buttons.add(
                CommandButton.Builder()
                    .setDisplayName(if (showPauseButton) "Pause" else "Play")
                    .setIconResId(if (showPauseButton) R.drawable.ic_pause else R.drawable.ic_play)
                    .setPlayerCommand(Player.COMMAND_PLAY_PAUSE)
                    .build()
            )
        }
        
        // 4. Next Button
        if (playerCommands.contains(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)) {
            buttons.add(
                CommandButton.Builder()
                    .setDisplayName("Next")
                    .setIconResId(R.drawable.ic_skip_next)
                    .setPlayerCommand(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                    .build()
            )
        }
        
        // 5. Favorite Button (rightmost)
        if (com.txapp.musicplayer.util.TXAPreferences.isShowFavoriteBtn) {
            val favoriteIcon = if (isFavorite) R.drawable.ic_favorite else R.drawable.ic_favorite_border
            buttons.add(
                CommandButton.Builder()
                    .setDisplayName("Favorite")
                    .setIconResId(favoriteIcon)
                    .setSessionCommand(COMMAND_TOGGLE_FAVORITE)
                    .build()
            )
        }
        
        return ImmutableList.copyOf(buttons)
    }
    
    override fun getNotificationContentTitle(metadata: androidx.media3.common.MediaMetadata): CharSequence {
        return metadata.title ?: metadata.displayTitle ?: "Unknown"
    }
    
    override fun getNotificationContentText(metadata: androidx.media3.common.MediaMetadata): CharSequence {
        return metadata.artist ?: metadata.albumArtist ?: "Unknown Artist"
    }
}
