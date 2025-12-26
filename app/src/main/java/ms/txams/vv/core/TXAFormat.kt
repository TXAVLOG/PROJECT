package ms.txams.vv.core

import java.util.Locale

object TXAFormat {
    
    /**
     * Format number with 2 digits (leading zero if needed)
     * @param number The number to format
     * @return Formatted string with leading zero (e.g., "05", "12")
     */
    fun formatTwoDigits(number: Int): String {
        return String.format(Locale.US, "%02d", number)
    }

    /**
     * Format number with 3 digits (leading zeros if needed)
     */
    fun formatThreeDigits(number: Int): String {
        return String.format(Locale.US, "%03d", number)
    }

    /**
     * Format duration from milliseconds to MM:SS:SSS format
     */
    fun formatDuration(durationMs: Long, includeMillis: Boolean = true): String {
        val seconds = durationMs / 1000
        val remainingMillis = (durationMs % 1000).toInt()
        val minutes = seconds / 60
        val remainingSeconds = (seconds % 60).toInt()
        
        return if (includeMillis) {
            "${formatTwoDigits(minutes.toInt())}:${formatTwoDigits(remainingSeconds)}.${formatThreeDigits(remainingMillis)}"
        } else {
            "${formatTwoDigits(minutes.toInt())}:${formatTwoDigits(remainingSeconds)}"
        }
    }

    /**
     * Format bytes to human readable format (KB/MB)
     */
    fun formatBytes(bytes: Long): String {
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val result = if (mb >= 1) {
            String.format(Locale.US, "%.2f MB", mb)
        } else {
            String.format(Locale.US, "%.0f KB", kb)
        }
        return result
    }

    /**
     * Format progress percentage
     * @param current Current bytes downloaded
     * @param total Total bytes to download
     * @return Formatted percentage string with 2 digits (e.g., "05%", "45%")
     */
    fun formatPercent(current: Long, total: Long): String {
        if (total <= 0) return "${formatTwoDigits(0)}%"
        val percent = (current * 100.0 / total).toInt().coerceIn(0, 100)
        return "${formatTwoDigits(percent)}%"
    }

    /**
     * Format progress with detail
     * @param current Current bytes
     * @param total Total bytes
     * @return Formatted string like "12.50 MB / 25.00 MB (50%)"
     */
    fun formatProgressDetail(current: Long, total: Long): String {
        val currentFormatted = formatBytes(current)
        val totalFormatted = formatBytes(total)
        val percent = formatPercent(current, total)
        return "$currentFormatted / $totalFormatted ($percent)"
    }

    /**
     * Format time remaining (ETA)
     * @param remainingMs Remaining time in milliseconds
     * @return Formatted string like "02:35" or "01:02:35"
     */
    fun formatTimeRemaining(remainingMs: Long): String {
        val totalSeconds = remainingMs / 1000
        val hours = totalSeconds / 3600
        val minutes = ((totalSeconds % 3600) / 60).toInt()
        val seconds = (totalSeconds % 60).toInt()
        
        return if (hours > 0) {
            "${hours}:${formatTwoDigits(minutes)}:${formatTwoDigits(seconds)}"
        } else {
            "${formatTwoDigits(minutes)}:${formatTwoDigits(seconds)}"
        }
    }

    /**
     * Format speed (bytes per second)
     * @param bytesPerSecond Speed in bytes per second
     * @return Formatted string like "1.25 MB/s"
     */
    fun formatSpeed(bytesPerSecond: Long): String {
        val formattedBytes = formatBytes(bytesPerSecond)
        return "$formattedBytes/s"
    }
}
