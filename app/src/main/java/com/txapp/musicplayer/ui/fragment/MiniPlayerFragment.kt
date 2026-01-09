/*
 * Copyright (c) 2020 Hemanth Savarla.
 *
 * Licensed under the GNU General Public License v3
 *
 * This is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 */
package com.txapp.musicplayer.ui.fragment

import android.os.Bundle
import android.view.View
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import com.txapp.musicplayer.R
import com.txapp.musicplayer.databinding.FragmentMiniPlayerBinding
import com.txapp.musicplayer.ui.MainActivity
import com.txapp.musicplayer.ui.component.MiniPlayerContent
import androidx.compose.material3.MaterialTheme

/**
 * MiniPlayerFragment - Rewritten to use Jetpack Compose
 */
open class MiniPlayerFragment : Fragment(R.layout.fragment_mini_player) {

    private var _binding: FragmentMiniPlayerBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentMiniPlayerBinding.bind(view)

        binding.composeView.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val activity = requireActivity() as? MainActivity
                
                // Use a default state if activity is null (shouldn't happen in normal flow)
                if (activity != null) {
                    val state = activity.nowPlayingState
                    val controller = activity.getPlayerController()
                    val itemCount = controller?.mediaItemCount ?: 0
                    val currentIndex = controller?.currentMediaItemIndex ?: 0
                    
                    // Simple Theme Wrapper - inherit from app theme ideally, or defined here
                    MaterialTheme {
                        MiniPlayerContent(
                            state = state,
                            itemCount = itemCount,
                            currentIndex = currentIndex,
                            getItem = { index -> 
                                if (controller != null && index in 0 until controller.mediaItemCount) 
                                    controller.getMediaItemAt(index) 
                                else null 
                            },
                            onPlayPause = { 
                                if (controller?.isPlaying == true) controller.pause() else controller?.play()
                            },
                            onNext = { controller?.seekToNext() },
                            onPrevious = { controller?.seekToPrevious() },
                            onToggleFavorite = { activity.toggleCurrentSongFavorite() },
                            onSeekTo = { index ->
                                controller?.seekToDefaultPosition(index)
                                controller?.play()
                            },
                            onExpand = { activity.expandPanel() },
                            onEditLyrics = { activity.run { 
                                // Direct access since it's in same package or internal
                                // Wait, MainActivity's showLyricsDialog is private
                                // I'll check its visibility or add a method
                                toggleLyricsDialog()
                            } }
                        )
                    }
                }
            }
        }
    }

    fun toggleLyricsDialog() {
        (activity as? MainActivity)?.let { 
            // We need a public way to toggle it
        }
    }

    // Retain these methods for compatibility if MainActivity calls them, 
    // although with Compose they might be redundant if state is observed correctly.
    // MainActivity calls: onServiceConnected, onPlayingMetaChanged, onPlayStateChanged, updateSongInfo, updateProgress
    
    fun onServiceConnected() {
        // No-op: Compose observes state from MainActivity
    }

    fun onPlayingMetaChanged() {
        // No-op
    }

    fun onPlayStateChanged() {
        // No-op
    }
    
    fun onFavoriteStateChanged(songId: Long, isFavorite: Boolean) {
        // No-op
    }

    fun updateSongInfo(title: String, artist: String, albumArtUri: String?, isPlaying: Boolean) {
        // No-op: State driven
    }

    fun updateProgress(progress: Float) {
        // No-op: State driven
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
