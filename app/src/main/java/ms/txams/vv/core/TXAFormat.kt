package ms.txams.vv.core

import java.util.Locale

object TXAFormat {
    fun formatDuration(durationMs: Long): String {
        val seconds = durationMs / 1000
        val minutes = seconds / 60
        val remainingSeconds = seconds % 60
        return String.format(Locale.US, "%d:%02d", minutes, remainingSeconds)
    }

    fun formatBytes(bytes: Long): String {
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        return if (mb >= 1) {
            String.format(Locale.US, "%.2f MB", mb)
        } else {
            String.format(Locale.US, "%.0f KB", kb)
        }
    }

    /**
     * Format progress percentage
     * @param current Current bytes downloaded
     * @param total Total bytes to download
     * @return Formatted percentage string (e.g., "45%")
     */
    fun formatPercent(current: Long, total: Long): String {
        if (total <= 0) return "0%"
        val percent = (current * 100.0 / total).toInt().coerceIn(0, 100)
        return "$percent%"
    }

    /**
     * Format progress with detail
     * @param current Current bytes
     * @param total Total bytes
     * @return Formatted string like "12.5 MB / 25.0 MB (50%)"
     */
    fun formatProgressDetail(current: Long, total: Long): String {
        val currentFormatted = formatBytes(current)
        val totalFormatted = formatBytes(total)
        val percent = formatPercent(current, total)
        return "$currentFormatted / $totalFormatted ($percent)"
    }
}
