package ms.txams.vv.data.manager

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import ms.txams.vv.core.TXAHttp
import ms.txams.vv.core.TXALogger
import okhttp3.Request
import java.io.File
import java.util.regex.Pattern
import javax.inject.Inject
import javax.inject.Singleton

/**
 * TXA Lyrics Manager
 * 
 * Features:
 * - Parse LRC format with timestamp extraction
 * - Fetch lyrics from online sources (LRCLIB, Musixmatch fallback)
 * - Local .lrc file lookup (same folder as audio)
 * - Synced lyrics with current position tracking
 * - Enhanced LRC with word-by-word sync support
 * 
 * LRC Format Support:
 * - Standard: [mm:ss.xx] or [mm:ss.xxx]
 * - Extended: <mm:ss.xx> for word-level sync
 * - Metadata: [ti:Title], [ar:Artist], [al:Album], [offset:+/-ms]
 * 
 * @author TXA - fb.com/vlog.txa.2311 - txavlog7@gmail.com
 */

/**
 * Single lyric line with timing info
 */
data class LyricLine(
    val timeMs: Long,
    val endTimeMs: Long = -1, // For karaoke mode
    val content: String,
    val words: List<LyricWord> = emptyList() // For word-by-word sync
)

/**
 * Word with individual timing (for enhanced LRC)
 */
data class LyricWord(
    val startMs: Long,
    val endMs: Long,
    val text: String
)

/**
 * Lyrics metadata
 */
data class LyricsMeta(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val offset: Long = 0, // Offset in ms
    val length: Long? = null
)

/**
 * Lyrics state
 */
sealed class LyricsState {
    object Idle : LyricsState()
    object Loading : LyricsState()
    data class Loaded(val lines: List<LyricLine>, val meta: LyricsMeta) : LyricsState()
    data class Error(val message: String) : LyricsState()
    object NotFound : LyricsState()
}

@Singleton
class TXALyricsManager @Inject constructor() {

    // Regex patterns
    private val timePattern = Pattern.compile("\\[(\\d{2}):(\\d{2})\\.(\\d{2,3})\\]")
    private val metaPattern = Pattern.compile("\\[([a-z]+):([^\\]]+)\\]", Pattern.CASE_INSENSITIVE)
    private val wordPattern = Pattern.compile("<(\\d{2}):(\\d{2})\\.(\\d{2,3})>([^<]*)")
    
    // State
    private val _lyricsState = MutableStateFlow<LyricsState>(LyricsState.Idle)
    val lyricsState: StateFlow<LyricsState> = _lyricsState.asStateFlow()
    
    private val _currentLineIndex = MutableStateFlow(-1)
    val currentLineIndex: StateFlow<Int> = _currentLineIndex.asStateFlow()
    
    private var currentLyrics: List<LyricLine> = emptyList()
    private var currentMeta: LyricsMeta = LyricsMeta()
    
    // API endpoints
    private val LRCLIB_API = "https://lrclib.net/api"
    
    /**
     * Load lyrics for a track
     * Priority: Local .lrc file > LRCLIB API > Musixmatch (future)
     */
    suspend fun loadLyrics(
        audioPath: String? = null,
        title: String? = null,
        artist: String? = null,
        album: String? = null,
        durationMs: Long? = null
    ) = withContext(Dispatchers.IO) {
        _lyricsState.value = LyricsState.Loading
        _currentLineIndex.value = -1
        
        TXALogger.appI("Loading lyrics for: $title - $artist")
        
        try {
            // 1. Try local .lrc file
            if (!audioPath.isNullOrEmpty()) {
                val lrcFile = findLocalLrc(audioPath)
                if (lrcFile != null && lrcFile.exists()) {
                    TXALogger.appD("Found local LRC: ${lrcFile.absolutePath}")
                    val result = parseLrcFile(lrcFile)
                    if (result.first.isNotEmpty()) {
                        currentLyrics = result.first
                        currentMeta = result.second
                        _lyricsState.value = LyricsState.Loaded(result.first, result.second)
                        return@withContext
                    }
                }
            }
            
            // 2. Try LRCLIB API
            if (!title.isNullOrEmpty() && !artist.isNullOrEmpty()) {
                val onlineLyrics = fetchFromLrcLib(title, artist, album, durationMs)
                if (onlineLyrics != null) {
                    val result = parseLrcContent(onlineLyrics)
                    if (result.first.isNotEmpty()) {
                        currentLyrics = result.first
                        currentMeta = result.second
                        _lyricsState.value = LyricsState.Loaded(result.first, result.second)
                        return@withContext
                    }
                }
            }
            
            // 3. Not found
            TXALogger.appW("Lyrics not found for: $title - $artist")
            _lyricsState.value = LyricsState.NotFound
            
        } catch (e: Exception) {
            TXALogger.appE("Failed to load lyrics", e)
            _lyricsState.value = LyricsState.Error(e.message ?: "Unknown error")
        }
    }
    
    /**
     * Find local .lrc file for audio
     */
    private fun findLocalLrc(audioPath: String): File? {
        val audioFile = File(audioPath)
        if (!audioFile.exists()) return null
        
        val baseName = audioFile.nameWithoutExtension
        val parent = audioFile.parentFile ?: return null
        
        // Check for .lrc with same name
        val lrcFile = File(parent, "$baseName.lrc")
        if (lrcFile.exists()) return lrcFile
        
        // Check for .LRC (uppercase)
        val lrcFileUpper = File(parent, "$baseName.LRC")
        if (lrcFileUpper.exists()) return lrcFileUpper
        
        return null
    }
    
    /**
     * Fetch synced lyrics from LRCLIB
     */
    private suspend fun fetchFromLrcLib(
        title: String,
        artist: String,
        album: String?,
        durationMs: Long?
    ): String? = withContext(Dispatchers.IO) {
        try {
            // Build URL with query params
            val url = buildString {
                append("$LRCLIB_API/get?")
                append("track_name=${java.net.URLEncoder.encode(title, "UTF-8")}")
                append("&artist_name=${java.net.URLEncoder.encode(artist, "UTF-8")}")
                album?.let { append("&album_name=${java.net.URLEncoder.encode(it, "UTF-8")}") }
                durationMs?.let { append("&duration=${it / 1000}") }
            }
            
            TXALogger.apiD("LRCLIB request: $url")
            
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "TXAMusic/1.0 (Android)")
                .build()
            
            TXAHttp.client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    TXALogger.apiW("LRCLIB response: ${response.code}")
                    return@withContext null
                }
                
                val body = response.body?.string() ?: return@withContext null
                
                // Parse JSON response
                val json = org.json.JSONObject(body)
                
                // Prefer synced lyrics, fallback to plain
                val syncedLyrics = json.optString("syncedLyrics", "")
                if (syncedLyrics.isNotEmpty()) {
                    TXALogger.apiI("Got synced lyrics from LRCLIB")
                    return@withContext syncedLyrics
                }
                
                val plainLyrics = json.optString("plainLyrics", "")
                if (plainLyrics.isNotEmpty()) {
                    TXALogger.apiI("Got plain lyrics from LRCLIB")
                    // Convert plain to simple LRC format
                    return@withContext plainLyrics.lines().mapIndexed { idx, line ->
                        "[00:${String.format("%02d", idx)}.00]$line"
                    }.joinToString("\n")
                }
                
                null
            }
        } catch (e: Exception) {
            TXALogger.apiE("LRCLIB fetch failed", e)
            null
        }
    }
    
    /**
     * Parse LRC file
     */
    private fun parseLrcFile(file: File): Pair<List<LyricLine>, LyricsMeta> {
        return try {
            parseLrcContent(file.readText())
        } catch (e: Exception) {
            TXALogger.appE("Failed to read LRC file", e)
            Pair(emptyList(), LyricsMeta())
        }
    }
    
    /**
     * Parse LRC content string
     */
    fun parseLrcContent(lrcContent: String): Pair<List<LyricLine>, LyricsMeta> {
        val lines = lrcContent.lines()
        val lyricList = mutableListOf<LyricLine>()
        var meta = LyricsMeta()
        
        for (line in lines) {
            // Parse metadata
            val metaMatcher = metaPattern.matcher(line)
            while (metaMatcher.find()) {
                val tag = metaMatcher.group(1)?.lowercase() ?: continue
                val value = metaMatcher.group(2)?.trim() ?: continue
                
                meta = when (tag) {
                    "ti", "title" -> meta.copy(title = value)
                    "ar", "artist" -> meta.copy(artist = value)
                    "al", "album" -> meta.copy(album = value)
                    "offset" -> meta.copy(offset = value.toLongOrNull() ?: 0)
                    "length" -> meta.copy(length = parseTimeToMs(value))
                    else -> meta
                }
            }
            
            // Parse lyric lines
            val timeMatcher = timePattern.matcher(line)
            if (timeMatcher.find()) {
                try {
                    val minutes = timeMatcher.group(1)?.toLong() ?: 0
                    val seconds = timeMatcher.group(2)?.toLong() ?: 0
                    val millisStr = timeMatcher.group(3) ?: "00"
                    val millis = if (millisStr.length == 2) {
                        millisStr.toLong() * 10
                    } else {
                        millisStr.toLong()
                    }
                    
                    val timeMs = (minutes * 60 * 1000) + (seconds * 1000) + millis + meta.offset
                    val content = line.substring(timeMatcher.end()).trim()
                    
                    // Skip empty lines
                    if (content.isNotEmpty()) {
                        // Parse word-level timing if present
                        val words = parseWordTiming(content)
                        lyricList.add(LyricLine(timeMs, -1, content.replace(wordPattern.toRegex(), "$4"), words))
                    }
                } catch (e: Exception) {
                    // Ignore parse error
                }
            }
        }
        
        // Sort by time and calculate end times
        val sorted = lyricList.sortedBy { it.timeMs }
        val withEndTimes = sorted.mapIndexed { idx, lyric ->
            val endTime = sorted.getOrNull(idx + 1)?.timeMs ?: (lyric.timeMs + 5000)
            lyric.copy(endTimeMs = endTime)
        }
        
        return Pair(withEndTimes, meta)
    }
    
    /**
     * Parse word-level timing (enhanced LRC)
     */
    private fun parseWordTiming(content: String): List<LyricWord> {
        val words = mutableListOf<LyricWord>()
        val matcher = wordPattern.matcher(content)
        var lastEndMs = 0L
        
        while (matcher.find()) {
            val minutes = matcher.group(1)?.toLong() ?: 0
            val seconds = matcher.group(2)?.toLong() ?: 0
            val millisStr = matcher.group(3) ?: "00"
            val millis = if (millisStr.length == 2) millisStr.toLong() * 10 else millisStr.toLong()
            
            val startMs = (minutes * 60 * 1000) + (seconds * 1000) + millis
            val text = matcher.group(4) ?: ""
            
            if (words.isNotEmpty()) {
                // Update previous word's end time
                val lastWord = words.last()
                words[words.lastIndex] = lastWord.copy(endMs = startMs)
            }
            
            words.add(LyricWord(startMs, startMs + 500, text)) // Default 500ms duration
            lastEndMs = startMs
        }
        
        return words
    }
    
    /**
     * Parse time string (mm:ss or mm:ss.xx) to milliseconds
     */
    private fun parseTimeToMs(time: String): Long? {
        return try {
            val parts = time.split(":")
            if (parts.size >= 2) {
                val minutes = parts[0].toLong()
                val secondsPart = parts[1].split(".")
                val seconds = secondsPart[0].toLong()
                val millis = secondsPart.getOrNull(1)?.toLong() ?: 0
                (minutes * 60 * 1000) + (seconds * 1000) + millis
            } else null
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Update current position and find current line
     */
    fun updatePosition(positionMs: Long) {
        if (currentLyrics.isEmpty()) {
            _currentLineIndex.value = -1
            return
        }
        
        // Binary search for efficiency
        var low = 0
        var high = currentLyrics.size - 1
        var result = -1
        
        while (low <= high) {
            val mid = (low + high) / 2
            val line = currentLyrics[mid]
            
            when {
                positionMs < line.timeMs -> high = mid - 1
                positionMs >= line.timeMs && (line.endTimeMs < 0 || positionMs < line.endTimeMs) -> {
                    result = mid
                    break
                }
                else -> low = mid + 1
            }
        }
        
        // Fallback: linear search for the line that contains current position
        if (result == -1) {
            result = currentLyrics.indexOfLast { positionMs >= it.timeMs }
        }
        
        if (_currentLineIndex.value != result) {
            _currentLineIndex.value = result
        }
    }
    
    /**
     * Get current line
     */
    fun getCurrentLine(): LyricLine? {
        val index = _currentLineIndex.value
        return if (index >= 0 && index < currentLyrics.size) {
            currentLyrics[index]
        } else null
    }
    
    /**
     * Get lines around current position (for UI display)
     */
    fun getLinesAround(count: Int = 3): List<LyricLine> {
        val index = _currentLineIndex.value
        if (index < 0 || currentLyrics.isEmpty()) return emptyList()
        
        val start = (index - count).coerceAtLeast(0)
        val end = (index + count + 1).coerceAtMost(currentLyrics.size)
        
        return currentLyrics.subList(start, end)
    }
    
    /**
     * Clear current lyrics
     */
    fun clear() {
        currentLyrics = emptyList()
        currentMeta = LyricsMeta()
        _lyricsState.value = LyricsState.Idle
        _currentLineIndex.value = -1
    }
    
    /**
     * Legacy method - simple LRC parsing (for backward compatibility)
     */
    fun parseLrc(lrcContent: String): List<LyricLine> {
        return parseLrcContent(lrcContent).first
    }
    
    /**
     * Show floating lyrics (future feature)
     */
    fun showFloatingLyrics(lyric: String) {
        // TODO: Implementation requires SYSTEM_ALERT_WINDOW permission
        TXALogger.appD("Floating lyrics: $lyric")
    }
}
