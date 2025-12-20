package gc.txa.demo.core

import kotlin.math.pow

object TXAFormat {

    /**
     * Format bytes to human-readable size (MB, GB)
     */
    fun formatBytes(bytes: Long): String {
        if (bytes < 0) return "0 B"
        
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var size = bytes.toDouble()
        var unitIndex = 0
        
        while (size >= 1024 && unitIndex < units.size - 1) {
            size /= 1024
            unitIndex++
        }
        
        return if (unitIndex == 0) {
            "${size.toInt()} ${units[unitIndex]}"
        } else {
            "%.2f ${units[unitIndex]}".format(size)
        }
    }

    /**
     * Format speed in bytes/second to MB/s or KB/s
     */
    fun formatSpeed(bytesPerSecond: Long): String {
        if (bytesPerSecond < 0) return "0 B/s"
        
        return when {
            bytesPerSecond >= 1024 * 1024 -> {
                val mbps = bytesPerSecond.toDouble() / (1024 * 1024)
                "%.2f MB/s".format(mbps)
            }
            bytesPerSecond >= 1024 -> {
                val kbps = bytesPerSecond.toDouble() / 1024
                "%.2f KB/s".format(kbps)
            }
            else -> "$bytesPerSecond B/s"
        }
    }

    /**
     * Format ETA (Estimated Time of Arrival) in seconds to hh:mm:ss
     */
    fun formatETA(seconds: Long): String {
        if (seconds < 0) return "00:00:00"
        
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        
        return "%02d:%02d:%02d".format(hours, minutes, secs)
    }

    /**
     * Format percentage with 2 decimal places
     */
    fun formatPercent(value: Double): String {
        return "%.2f%%".format(value)
    }

    /**
     * Format percentage from downloaded/total bytes
     */
    fun formatPercent(downloaded: Long, total: Long): String {
        if (total <= 0) return "0.00%"
        val percent = (downloaded.toDouble() / total.toDouble()) * 100
        return formatPercent(percent)
    }
}
