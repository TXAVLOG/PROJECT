package ms.txams.vv.core

import android.text.format.DateUtils
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*

/**
 * TXA Format - Utility class cho formatting dữ liệu
 * Hỗ trợ format thời gian, dung lượng, phần trăm, và các định dạng khác
 */
object TXAFormat {

    private val decimalFormat = DecimalFormat("#.##")
    private val largeNumberFormat = DecimalFormat("#.#")
    
    // Date formats
    private val shortDateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private val dateTimeFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())

    /**
     * Format duration từ milliseconds thành readable string
     */
    fun formatDuration(ms: Long): String {
        return when {
            ms < 1000 -> "${ms}ms"
            ms < 60_000 -> String.format(Locale.getDefault(), "%d:%02d", 
                ms / 60_000, (ms % 60_000) / 1000)
            ms < 3_600_000 -> String.format(Locale.getDefault(), "%d:%02d", 
                ms / 60_000, (ms % 60_000) / 1000)
            ms < 86_400_000 -> String.format(Locale.getDefault(), "%d:%02d:%02d", 
                ms / 3_600_000, (ms % 3_600_000) / 60_000, (ms % 60_000) / 1000)
            else -> {
                val days = ms / 86_400_000
                val hours = (ms % 86_400_000) / 3_600_000
                val minutes = (ms % 3_600_000) / 60_000
                if (days > 0) String.format(Locale.getDefault(), "%dd %d:%02d:%02d", days, hours, minutes)
                else String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, 0)
            }
        }
    }

    /**
     * Format duration ngắn gọn cho UI
     */
    fun formatShortDuration(ms: Long): String {
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
    }

    /**
     * Format file size từ bytes thành readable string
     */
    fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "${bytes}B"
            bytes < 1024 * 1024 -> "${decimalFormat.format(bytes / 1024.0)}KB"
            bytes < 1024 * 1024 * 1024 -> "${decimalFormat.format(bytes / (1024.0 * 1024.0))}MB"
            bytes < 1024 * 1024 * 1024 * 1024L -> "${decimalFormat.format(bytes / (1024.0 * 1024.0 * 1024.0))}GB"
            else -> "${decimalFormat.format(bytes / (1024.0 * 1024.0 * 1024.0 * 1024.0))}TB"
        }
    }

    /**
     * Format file size ngắn gọn
     */
    fun formatShortFileSize(bytes: Long): String {
        return when {
            bytes < 1024 * 1024 -> "${(bytes / 1024.0).toInt()}KB"
            bytes < 1024 * 1024 * 1024 -> "${decimalFormat.format(bytes / (1024.0 * 1024.0))}MB"
            else -> "${decimalFormat.format(bytes / (1024.0 * 1024.0 * 1024.0))}GB"
        }
    }

    /**
     * Format percentage với precision tùy chọn
     */
    fun formatPercent(current: Long, total: Long): String {
        if (total == 0L) return "0%"
        val percent = (current * 100.0 / total).coerceIn(0.0, 100.0)
        return "${decimalFormat.format(percent)}%"
    }

    /**
     * Format percentage với decimal places
     */
    fun formatPercent(current: Long, total: Long, decimalPlaces: Int): String {
        if (total == 0L) return "0%"
        val percent = (current * 100.0 / total).coerceIn(0.0, 100.0)
        val format = when (decimalPlaces) {
            0 -> "#"
            1 -> "#.#"
            2 -> "#.##"
            3 -> "#.###"
            else -> "#.##"
        }
        val formatter = DecimalFormat(format)
        return "${formatter.format(percent)}%"
    }

    /**
     * Format download speed
     */
    fun formatDownloadSpeed(bytesPerSecond: Long): String {
        return "${formatFileSize(bytesPerSecond)}/s"
    }

    /**
     * Format bitrate
     */
    fun formatBitrate(bitrate: Int): String {
        return when {
            bitrate < 1000 -> "${bitrate}bps"
            bitrate < 1_000_000 -> "${decimalFormat.format(bitrate / 1000.0)}kbps"
            else -> "${decimalFormat.format(bitrate / 1_000_000.0)}Mbps"
        }
    }

    /**
     * Format sample rate
     */
    fun formatSampleRate(sampleRate: Int): String {
        return when {
            sampleRate < 1000 -> "${sampleRate}Hz"
            else -> "${decimalFormat.format(sampleRate / 1000.0)}kHz"
        }
    }

    /**
     * Format number với separator
     */
    fun formatNumber(number: Long): String {
        return String.format(Locale.getDefault(), "%,d", number)
    }

    /**
     * Format large number với K, M, B suffix
     */
    fun formatLargeNumber(number: Long): String {
        return when {
            number < 1000 -> number.toString()
            number < 1_000_000 -> "${largeNumberFormat.format(number / 1000.0)}K"
            number < 1_000_000_000 -> "${largeNumberFormat.format(number / 1_000_000.0)}M"
            number < 1_000_000_000_000L -> "${largeNumberFormat.format(number / 1_000_000_000.0)}B"
            else -> "${largeNumberFormat.format(number / 1_000_000_000_000.0)}T"
        }
    }

    /**
     * Format timestamp thành relative time
     */
    fun formatRelativeTime(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp
        
        return when {
            diff < 60_000 -> "Just now"
            diff < 3600_000 -> DateUtils.getRelativeTimeSpanString(timestamp, now, DateUtils.MINUTE_IN_MILLIS).toString()
            diff < 86400_000 -> DateUtils.getRelativeTimeSpanString(timestamp, now, DateUtils.HOUR_IN_MILLIS).toString()
            diff < 604800_000 -> DateUtils.getRelativeTimeSpanString(timestamp, now, DateUtils.DAY_IN_MILLIS).toString()
            else -> shortDateFormat.format(Date(timestamp))
        }
    }

    /**
     * Format date
     */
    fun formatDate(timestamp: Long): String {
        return shortDateFormat.format(Date(timestamp))
    }

    /**
     * Format time
     */
    fun formatTime(timestamp: Long): String {
        return timeFormat.format(Date(timestamp))
    }

    /**
     * Format date and time
     */
    fun formatDateTime(timestamp: Long): String {
        return dateTimeFormat.format(Date(timestamp))
    }

    /**
     * Format remaining time cho download/progress
     */
    fun formatRemainingTime(remainingMs: Long): String {
        return when {
            remainingMs < 60_000 -> "${remainingMs / 1000}s"
            remainingMs < 3600_000 -> "${remainingMs / 60_000}m"
            remainingMs < 86400_000 -> "${remainingMs / 3600_000}h"
            else -> "${remainingMs / 86400_000}d"
        }
    }

    /**
     * Format audio position với total duration
     */
    fun formatAudioPosition(position: Long, duration: Long): String {
        return "${formatShortDuration(position)} / ${formatShortDuration(duration)}"
    }

    /**
     * Format audio position với full duration
     */
    fun formatFullAudioPosition(position: Long, duration: Long): String {
        return "${formatDuration(position)} / ${formatDuration(duration)}"
    }

    /**
     * Format volume percentage
     */
    fun formatVolume(volume: Float): String {
        val percent = (volume * 100).coerceIn(0f, 100f)
        return "${decimalFormat.format(percent)}%"
    }

    /**
     * Format pitch adjustment
     */
    fun formatPitch(semitones: Float): String {
        val sign = if (semitones >= 0) "+" else ""
        return "$sign${decimalFormat.format(semitones)}"
    }

    /**
     * Format speed adjustment
     */
    fun formatSpeed(speed: Float): String {
        return "${decimalFormat.format(speed)}x"
    }

    /**
     * Format crossfade duration
     */
    fun formatCrossfadeDuration(ms: Long): String {
        return when {
            ms < 1000 -> "${ms}ms"
            else -> "${decimalFormat.format(ms / 1000.0)}s"
        }
    }

    /**
     * Format playlist duration
     */
    fun formatPlaylistDuration(totalMs: Long, songCount: Int): String {
        val duration = formatDuration(totalMs)
        val count = formatNumber(songCount.toLong())
        return "$count songs • $duration"
    }

    /**
     * Format audio codec info
     */
    fun formatAudioCodec(codec: String?, bitrate: Int?, sampleRate: Int?): String {
        val parts = mutableListOf<String>()
        
        codec?.let { parts.add(it.uppercase()) }
        bitrate?.let { parts.add(formatBitrate(it)) }
        sampleRate?.let { parts.add(formatSampleRate(it)) }
        
        return parts.joinToString(" • ")
    }

    /**
     * Format loudness (LUFS)
     */
    fun formatLoudness(lufs: Float): String {
        return "${decimalFormat.format(lufs)} LUFS"
    }

    /**
     * Format frequency cho equalizer
     */
    fun formatFrequency(hz: Int): String {
        return when {
            hz < 1000 -> "${hz}Hz"
            else -> "${decimalFormat.format(hz / 1000.0)}kHz"
        }
    }

    /**
     * Format percentage progress bar
     */
    fun formatProgressBar(current: Long, total: Long, width: Int = 20): String {
        if (total == 0L) return "[" + " ".repeat(width) + "]"
        
        val progress = (current * width / total).coerceIn(0L, width.toLong()).toInt()
        val filled = "█".repeat(progress)
        val empty = " ".repeat(width - progress)
        return "[$filled$empty]"
    }

    /**
     * Format version string
     */
    fun formatVersion(versionCode: Int, versionName: String): String {
        return "$versionName ($versionCode)"
    }

    /**
     * Format build info
     */
    fun formatBuildInfo(versionName: String, buildType: String, timestamp: Long): String {
        return "$versionName • $buildType • ${formatDate(timestamp)}"
    }
}
