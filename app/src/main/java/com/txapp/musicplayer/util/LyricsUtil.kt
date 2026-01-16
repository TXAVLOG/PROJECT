package com.txapp.musicplayer.util

import android.content.Context
import android.os.Environment
import java.io.File
import com.txapp.musicplayer.ui.component.LyricLine
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey

/**
 * Lyrics utility to parse and load lyrics
 */
object LyricsUtil {
    
    /**
     * Parse LRC format lyrics with extended support
     * Supported formats:
     * - [mm:ss.xx]lyrics text (standard)
     * - [mm:ss.xx - mm:ss.xx]lyrics text (extended with end time)
     * - [hh:mm:ss.xx]lyrics text (for songs > 1 hour)
     * - [hh:mm:ss.xx - hh:mm:ss.xx]lyrics text (extended for songs > 1 hour)
     */
    fun parseLrc(lrcContent: String): List<LyricLine> {
        if (lrcContent.isBlank()) return emptyList()
        
        val result = mutableListOf<LyricLine>()
        var offset = 0
        val lines = lrcContent.lines()
        
        // Standard format: [mm:ss.xx]
        val timeRegex = Regex("""\[(\d+):(\d{2})[.:](\d{2,3})\]""")
        // Extended format with end time: [mm:ss.xx - mm:ss.xx]
        val extendedTimeRegex = Regex("""\[(\d+):(\d{2})[.:](\d{2,3})\s*-\s*(\d+):(\d{2})[.:](\d{2,3})\]""")
        // Hour format: [hh:mm:ss.xx]
        val hourTimeRegex = Regex("""\[(\d+):(\d{2}):(\d{2})[.:](\d{2,3})\]""")
        // Extended hour format: [hh:mm:ss.xx - hh:mm:ss.xx]
        val extendedHourRegex = Regex("""\[(\d+):(\d{2}):(\d{2})[.:](\d{2,3})\s*-\s*(\d+):(\d{2}):(\d{2})[.:](\d{2,3})\]""")
        val attrRegex = Regex("""\[(\D+):(.+)\]""")

        for (line in lines) {
            val attrMatch = attrRegex.find(line)
            if (attrMatch != null) {
                val key = attrMatch.groupValues[1].lowercase().trim()
                val value = attrMatch.groupValues[2].trim()
                if (key == "offset") {
                    offset = value.toIntOrNull() ?: 0
                }
                continue
            }

            // Try extended hour format first [hh:mm:ss.xx - hh:mm:ss.xx]
            val extHourMatch = extendedHourRegex.find(line)
            if (extHourMatch != null) {
                val hr1 = extHourMatch.groupValues[1].toLong()
                val min1 = extHourMatch.groupValues[2].toLong()
                val sec1 = extHourMatch.groupValues[3].toLong()
                val ms1 = parseMs(extHourMatch.groupValues[4])
                val hr2 = extHourMatch.groupValues[5].toLong()
                val min2 = extHourMatch.groupValues[6].toLong()
                val sec2 = extHourMatch.groupValues[7].toLong()
                val ms2 = parseMs(extHourMatch.groupValues[8])
                
                val startMs = ((hr1 * 3600 + min1 * 60 + sec1) * 1000 + ms1 + offset)
                val endMs = ((hr2 * 3600 + min2 * 60 + sec2) * 1000 + ms2 + offset)
                val text = line.replace(extendedHourRegex, "").trim()
                result.add(LyricLine(startMs, text, endMs))
                continue
            }

            // Try hour format [hh:mm:ss.xx]
            val hourMatch = hourTimeRegex.find(line)
            if (hourMatch != null) {
                val hr = hourMatch.groupValues[1].toLong()
                val min = hourMatch.groupValues[2].toLong()
                val sec = hourMatch.groupValues[3].toLong()
                val ms = parseMs(hourMatch.groupValues[4])
                val totalMs = ((hr * 3600 + min * 60 + sec) * 1000 + ms + offset)
                val text = line.replace(hourTimeRegex, "").trim()
                result.add(LyricLine(totalMs, text))
                continue
            }

            // Try extended format [mm:ss.xx - mm:ss.xx]
            val extMatch = extendedTimeRegex.find(line)
            if (extMatch != null) {
                val min1 = extMatch.groupValues[1].toLong()
                val sec1 = extMatch.groupValues[2].toLong()
                val ms1 = parseMs(extMatch.groupValues[3])
                val min2 = extMatch.groupValues[4].toLong()
                val sec2 = extMatch.groupValues[5].toLong()
                val ms2 = parseMs(extMatch.groupValues[6])
                
                val startMs = ((min1 * 60 + sec1) * 1000 + ms1 + offset)
                val endMs = ((min2 * 60 + sec2) * 1000 + ms2 + offset)
                val text = line.replace(extendedTimeRegex, "").trim()
                result.add(LyricLine(startMs, text, endMs))
                continue
            }

            // Standard format [mm:ss.xx]
            val matches = timeRegex.findAll(line)
            if (matches.none()) continue
            
            val text = line.replace(timeRegex, "").trim()
            for (match in matches) {
                val min = match.groupValues[1].toLong()
                val sec = match.groupValues[2].toLong()
                val ms = parseMs(match.groupValues[3])
                val totalMs = (min * 60 + sec) * 1000 + ms + offset
                result.add(LyricLine(totalMs, text))
            }
        }
        
        // Sort and auto-calculate end timestamps
        // Handle multiple lines with same timestamp by distributing time evenly
        val sorted = result.sortedBy { it.timestamp }.toMutableList()
        
        var i = 0
        while (i < sorted.size) {
            // Skip if already has end timestamp
            if (sorted[i].endTimestamp != -1L) {
                i++
                continue
            }
            
            // Find the group of consecutive lines with same timestamp
            val groupStart = i
            val groupTimestamp = sorted[i].timestamp
            var groupEnd = i
            while (groupEnd < sorted.size && sorted[groupEnd].timestamp == groupTimestamp && sorted[groupEnd].endTimestamp == -1L) {
                groupEnd++
            }
            
            val groupSize = groupEnd - groupStart
            
            // Find the next different timestamp (this will be the end time for the whole group)
            val nextDifferentTimestamp = if (groupEnd < sorted.size) {
                sorted[groupEnd].timestamp
            } else {
                groupTimestamp + 10000 // Last group: 10 seconds default duration
            }
            
            // Calculate total duration for this group
            val totalGroupDuration = nextDifferentTimestamp - groupTimestamp
            
            // Distribute time evenly among lines in the group
            for (j in groupStart until groupEnd) {
                val lineIndex = j - groupStart
                val lineStart = groupTimestamp + (totalGroupDuration * lineIndex / groupSize)
                val lineEnd = groupTimestamp + (totalGroupDuration * (lineIndex + 1) / groupSize)
                
                // Update timestamp and endTimestamp for this line
                sorted[j] = sorted[j].copy(
                    timestamp = lineStart,
                    endTimestamp = lineEnd
                )
            }
            
            i = groupEnd
        }

        // --- GAP FILLING LOGIC (3s Rule) ---
        val processed = mutableListOf<LyricLine>()
        
        // 1. Check start gap
        if (sorted.isNotEmpty()) {
            val firstStart = sorted[0].timestamp
            if (firstStart >= 3000) {
                // Add a single countdown marker for the UI to handle smoothly
                processed.add(LyricLine(firstStart - 3000, "•••", firstStart))
            }
        }

        // 2. Process gaps between lines
        for (i in sorted.indices) {
            processed.add(sorted[i])
            
            if (i < sorted.size - 1) {
                val currentEnd = sorted[i].endTimestamp
                val nextStart = sorted[i+1].timestamp
                
                if (nextStart - currentEnd >= 3000) {
                    // Add "...." filler
                    processed.add(LyricLine(currentEnd, "....", nextStart))
                }
            }
        }

        return processed
    }
    
    private fun parseMs(msStr: String): Long {
        var ms = msStr.toLong()
        if (msStr.length == 2) ms *= 10 // Handle [00:00.10] as 100ms
        return ms
    }
    
    private val centralLrcPath: String
        get() = Environment.getExternalStorageDirectory().toString() + "/TXAMusic/lyrics/"

    /**
     * Get raw lyrics string (LRC or plain text) from file or tags
     */
    fun getRawLyrics(audioFilePath: String, title: String? = null, artist: String? = null): String? {
        if (audioFilePath.isBlank()) return null
        
        // 1. Try .lrc file in same directory
        val lrcPath = audioFilePath.substringBeforeLast('.') + ".lrc"
        val lrcFile = File(lrcPath)
        if (lrcFile.exists()) {
            return try { lrcFile.readText() } catch (e: Exception) { null }
        }
        
        // 2. Try .lrc file in central folder (/TXAMusic/lyrics/Title - Artist.lrc)
        // If title/artist not provided, try to extract from filename
        val finalTitle = title ?: File(audioFilePath).nameWithoutExtension.substringBefore(" - ").trim()
        val finalArtist = artist ?: File(audioFilePath).nameWithoutExtension.substringAfter(" - ", "").trim()

        val centralFile = if (finalArtist.isNotEmpty() && finalArtist != finalTitle) {
            File(centralLrcPath, "$finalTitle - $finalArtist.lrc")
        } else {
            File(centralLrcPath, "$finalTitle.lrc")
        }
        
        if (centralFile.exists()) {
            return try { centralFile.readText() } catch (e: Exception) { null }
        }

        // 3. Try embedded tags
        return try {
            val audioFile = AudioFileIO.read(File(audioFilePath))
            audioFile.tagOrCreateDefault.getFirst(FieldKey.LYRICS)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Build Google Search URL for lyrics
     */
    fun buildSearchUrl(title: String, artist: String): String {
        val query = "$title $artist lyrics".replace(" ", "+")
        return "https://www.google.com/search?q=$query"
    }

    /**
     * Remove timestamps from LRC content
     */
    fun getCleanLyrics(lrcContent: String): String? {
        if (lrcContent.isBlank()) return null
        return lrcContent.replace(Regex("""\[\d+:?\d{2}[:.]\d{2,3}\]"""), "").trim()
    }

    sealed class SaveResult {
        object Success : SaveResult()
        object Failure : SaveResult()
        data class PermissionRequired(val intent: android.app.PendingIntent) : SaveResult()
    }

    /**
     * Save lyrics to file (as .lrc) and optionally to embedded tags
     */
    /**
     * Parsed lyrics container for LyricsFragment
     */
    data class ParsedLyrics(
        val lines: List<LyricLineSimple>,
        val isSynced: Boolean
    )

    data class LyricLineSimple(
        val timestamp: Long,
        val text: String
    )

    /**
     * Load lyrics for a song path and return parsed result
     */
    suspend fun loadLyricsForSong(context: Context, audioFilePath: String): ParsedLyrics? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val raw = getRawLyrics(audioFilePath) ?: return@withContext null
        
        // Check if it's synced (has timestamps)
        val isSynced = raw.contains(Regex("""\[\d+:\d{2}[.:]\d{2,3}\]"""))
        
        if (isSynced) {
            val parsed = parseLrc(raw)
            ParsedLyrics(
                lines = parsed.map { LyricLineSimple(it.timestamp, it.text) },
                isSynced = true
            )
        } else {
            // Plain text lyrics
            val lines = raw.lines().filter { it.isNotBlank() }.map { 
                LyricLineSimple(0L, it.trim()) 
            }
            ParsedLyrics(lines = lines, isSynced = false)
        }
    }

    suspend fun saveLyrics(context: Context, audioFilePath: String, content: String): SaveResult = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            // 1. Save as .lrc file in same directory
            val lrcPath = audioFilePath.substringBeforeLast('.') + ".lrc"
            val lrcFile = File(lrcPath)
            
            try {
                lrcFile.writeText(content)
                SaveResult.Success
            } catch (e: Exception) {
                // If failed due to Scoped Storage, we might need Permission
                if (e is android.system.ErrnoException || e.message?.contains("Permission denied") == true) {
                    // Try to get permission via MediaStore
                    // This is complex, but for now we return Failure or trigger permission elsewhere
                    SaveResult.Failure
                } else {
                    SaveResult.Failure
                }
            }
        } catch (e: Exception) {
            SaveResult.Failure
        }
    }
}
