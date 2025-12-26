package ms.txams.vv.data.manager

import javax.inject.Inject
import javax.inject.Singleton
import java.io.File
import java.util.regex.Pattern

data class LyricLine(
    val timeMs: Long,
    val content: String
)

@Singleton
class TXALyricsManager @Inject constructor() {

    private val timePattern = Pattern.compile("\\[(\\d{2}):(\\d{2})\\.(\\d{2,3})\\]")

    fun parseLrc(lrcContent: String): List<LyricLine> {
        val lines = lrcContent.lines()
        val lyricList = mutableListOf<LyricLine>()

        for (line in lines) {
            val matcher = timePattern.matcher(line)
            if (matcher.find()) {
                try {
                    val minutes = matcher.group(1)?.toLong() ?: 0
                    val seconds = matcher.group(2)?.toLong() ?: 0
                    val millisStr = matcher.group(3) ?: "00"
                    // If 2 digits, multiply by 10. If 3 digits, take as is.
                    val millis = if (millisStr.length == 2) millisStr.toLong() * 10 else millisStr.toLong()

                    val timeMs = (minutes * 60 * 1000) + (seconds * 1000) + millis
                    val content = line.substring(matcher.end()).trim()
                    
                    lyricList.add(LyricLine(timeMs, content))
                } catch (e: Exception) {
                    // Ignore parse error
                }
            }
        }
        return lyricList.sortedBy { it.timeMs }
    }

    // Floating Lyrics Service Logic would go here (requires WindowManager permission)
    fun showFloatingLyrics(lyric: String) {
        // Implementation for drawing over other apps
    }
}
