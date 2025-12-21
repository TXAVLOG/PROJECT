package gc.txa.demo.core

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
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
     * Format ETA (Estimated Time of Arrival) in seconds to dynamic format
     * Uses adaptive time units: s, m s, h m s, d h m, M d h, y M d
     */
    fun formatETA(seconds: Long): String {
        if (seconds <= 0) return TXATranslation.txa("txademo_time_now")
        if (seconds < 60) return String.format(TXATranslation.txa("txademo_time_seconds"), seconds)
        
        val minutes = seconds / 60
        val secs = seconds % 60
        if (seconds < 3600) return String.format(TXATranslation.txa("txademo_time_minutes"), minutes, secs)
        
        val hours = seconds / 3600
        val remainingMinutes = (seconds % 3600) / 60
        if (seconds < 86400) return String.format(TXATranslation.txa("txademo_time_hours"), hours, remainingMinutes, secs)
        
        val days = seconds / 86400
        val remainingHours = (seconds % 86400) / 3600
        val remainingMinutesFromDays = ((seconds % 86400) % 3600) / 60
        if (seconds < 2592000) return String.format(TXATranslation.txa("txademo_time_days"), days, remainingHours, remainingMinutesFromDays)
        
        val months = seconds / 2592000 // ~30 days
        val remainingDays = (seconds % 2592000) / 86400
        val remainingHoursFromMonths = ((seconds % 2592000) % 86400) / 3600
        if (seconds < 31536000) return String.format(TXATranslation.txa("txademo_time_months"), months, remainingDays, remainingHoursFromMonths)
        
        // For very long periods (years+)
        val years = seconds / 31536000
        val remainingMonths = (seconds % 31536000) / 2592000
        val remainingDaysFromYears = ((seconds % 31536000) % 2592000) / 86400
        return String.format(TXATranslation.txa("txademo_time_years"), years, remainingMonths, remainingDaysFromYears)
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

    /**
     * Format ISO8601 update timestamp into localized readable string.
     */
    fun formatUpdateTime(isoString: String?): String {
        if (isoString.isNullOrBlank()) return "--"

        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault()).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val date = inputFormat.parse(isoString) ?: return isoString

            val outputFormat = SimpleDateFormat("HH:mm:ss dd/MM/yyyy", Locale.getDefault()).apply {
                timeZone = TimeZone.getDefault()
            }
            outputFormat.format(date)
        } catch (e: Exception) {
            isoString
        }
    }
}
