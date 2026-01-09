package com.txapp.musicplayer.util

import android.content.Context
import android.content.SharedPreferences
import com.txapp.musicplayer.model.Song
import org.json.JSONArray

object TXAPlaybackPersistence {
    private const val PREF_NAME = "txa_playback_prefs"
    private const val KEY_LAST_SONG_ID = "last_song_id"
    private const val KEY_LAST_POSITION = "last_position"
    private const val KEY_QUEUE_IDS = "queue_ids"
    private const val KEY_SHUFFLE_MODE = "shuffle_mode"
    private const val KEY_REPEAT_MODE = "repeat_mode"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    fun savePlaybackState(context: Context, songId: Long, position: Long, queueIds: List<Long>, shuffle: Boolean, repeat: Int) {
        val jsonArray = JSONArray()
        queueIds.forEach { jsonArray.put(it) }

        getPrefs(context).edit().apply {
            putLong(KEY_LAST_SONG_ID, songId)
            putLong(KEY_LAST_POSITION, position)
            putString(KEY_QUEUE_IDS, jsonArray.toString())
            putBoolean(KEY_SHUFFLE_MODE, shuffle)
            putInt(KEY_REPEAT_MODE, repeat)
            apply()
        }
        TXALogger.playbackI("Persistence", "Đã lưu trạng thái: SongID=$songId, Pos=${position}ms, QueueSize=${queueIds.size}")
    }

    fun getLastSongId(context: Context): Long = getPrefs(context).getLong(KEY_LAST_SONG_ID, -1L)
    fun getLastPosition(context: Context): Long = getPrefs(context).getLong(KEY_LAST_POSITION, 0L)
    fun getShuffleMode(context: Context): Boolean = getPrefs(context).getBoolean(KEY_SHUFFLE_MODE, false)
    fun getRepeatMode(context: Context): Int = getPrefs(context).getInt(KEY_REPEAT_MODE, 0)

    fun getQueueIds(context: Context): List<Long> {
        val json = getPrefs(context).getString(KEY_QUEUE_IDS, null) ?: return emptyList()
        return try {
            val array = JSONArray(json)
            val list = mutableListOf<Long>()
            for (i in 0 until array.length()) {
                list.add(array.getLong(i))
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }
}
