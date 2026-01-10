package com.txapp.musicplayer.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Singleton to manage real-time playback signals between Service and Activities
 */
object TXAPlaybackManager {
    
    data class ResumePromptRequest(
        val songId: String,
        val title: String,
        val position: Long,
        val path: String
    )

    private val _resumePromptRequest = MutableStateFlow<ResumePromptRequest?>(null)
    val resumePromptRequest: StateFlow<ResumePromptRequest?> = _resumePromptRequest.asStateFlow()

    fun requestResumePrompt(songId: String, title: String, position: Long, path: String) {
        _resumePromptRequest.value = ResumePromptRequest(songId, title, position, path)
    }

    fun clearResumePrompt() {
        _resumePromptRequest.value = null
    }
}
