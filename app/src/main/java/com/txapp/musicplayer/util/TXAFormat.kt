package com.txapp.musicplayer.util

import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * TXA Format - Utility for formatting sizes, speeds, durations
 */
object TXAFormat {
    
    private val sizeFormat = DecimalFormat("#0.00")
    private val percentFormat = DecimalFormat("#0")
    private val decimalMinFormat = DecimalFormat("#0.00")
    
    /**
     * Format bytes to human readable string (KB, MB, GB)
     */
    fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${sizeFormat.format(bytes / 1024.0)} KB"
            bytes < 1024 * 1024 * 1024 -> "${sizeFormat.format(bytes / (1024.0 * 1024.0))} MB"
            else -> "${sizeFormat.format(bytes / (1024.0 * 1024.0 * 1024.0))} GB"
        }
    }
    
    /**
     * Format download speed (bytes/second to MB/s, KB/s, B/s)
     */
    fun formatSpeed(bytesPerSecond: Long): String {
        return when {
            bytesPerSecond <= 0 -> "0 B/s"
            bytesPerSecond < 1024 -> "$bytesPerSecond B/s"
            bytesPerSecond < 1024 * 1024 -> "${sizeFormat.format(bytesPerSecond / 1024.0)} KB/s"
            else -> "${sizeFormat.format(bytesPerSecond / (1024.0 * 1024.0))} MB/s"
        }
    }
    
    /**
     * Format an integer to at least 2 digits (e.g. 5 -> "05")
     */
    fun format2Digits(value: Long): String {
        return if (value < 10) "0$value" else "$value"
    }
    
    fun format2Digits(value: Int): String = format2Digits(value.toLong())

    /**
     * Format duration (milliseconds to mm:ss or hh:mm:ss)
     */
    fun formatDuration(millis: Long): String {
        if (millis <= 0) return "0:00"
        
        val totalSeconds = millis / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val secs = totalSeconds % 60
        
        return if (hours > 0) {
            "${format2Digits(hours)}:${format2Digits(minutes)}:${format2Digits(secs)}"
        } else {
            "${format2Digits(minutes)}:${format2Digits(secs)}"
        }
    }

    /**
     * Format duration (millis to human readable e.g. 1h 05m 20s)
     */
    fun formatDurationHuman(millis: Long): String {
        if (millis <= 0) return "0${"txamusic_unit_second".txa()}"

        val totalSeconds = millis / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val secs = totalSeconds % 60

        val h = "txamusic_unit_hour".txa()
        val m = "txamusic_unit_minute".txa()
        val s = "txamusic_unit_second".txa()

        return if (hours > 0) {
            "${format2Digits(hours)}$h ${format2Digits(minutes)}$m ${format2Digits(secs)}$s"
        } else { 
             if (minutes > 0) "${format2Digits(minutes)}$m ${format2Digits(secs)}$s" else "00$m ${format2Digits(secs)}$s"
        }
    }
    
    /**
     * Format sleep timer for icon display
     * Rules:
     * - > 1h: xx h xx m
     * - < 1h & > 1m: xx m
     * - < 1m: xx.xxm
     */
    fun formatSleepTimer(millis: Long): String {
        if (millis <= 0) return ""
        
        val totalSeconds = millis / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        
        val h = "txamusic_unit_hour".txa()
        val m = "txamusic_unit_minute".txa()
        
        return when {
            hours > 0 -> "${hours}:${format2Digits(minutes)}:${format2Digits(seconds)}"
            else -> "${format2Digits(minutes)}:${format2Digits(seconds)}"
        }
    }

    /**
     * Format ETA (remaining bytes / speed) to string
     */
    fun formatETA(remainingBytes: Long, speedBps: Long): String {
        if (speedBps <= 0) return "--"
        val seconds = remainingBytes / speedBps
        return formatETA(seconds)
    }

    /**
     * Format ETA (seconds to human readable)
     * Requested format: xx s, xx m xx s, xx h xx m xx s, xx d xx h
     */
    fun formatETA(seconds: Long): String {
        if (seconds <= 0) return "0${"txamusic_unit_second".txa()}"
        
        val days = (seconds / 86400).toInt()
        val hours = ((seconds % 86400) / 3600).toInt()
        val minutes = ((seconds % 3600) / 60).toInt()
        val secs = (seconds % 60).toInt()
        
        val d = "txamusic_unit_day".txa()
        val h = "txamusic_unit_hour".txa()
        val m = "txamusic_unit_minute".txa()
        val s = "txamusic_unit_second".txa()

        return when {
            days > 0 -> "${format2Digits(days)}$d ${format2Digits(hours)}$h"
            hours > 0 -> "${format2Digits(hours)}$h ${format2Digits(minutes)}$m ${format2Digits(secs)}$s"
            minutes > 0 -> "${format2Digits(minutes)}$m ${format2Digits(secs)}$s"
            else -> "${format2Digits(secs)}$s"
        }
    }
    
    /**
     * Format date to dd/MM/yy
     */
    fun formatDate(millis: Long): String {
        return try {
            SimpleDateFormat("dd/MM/yy", Locale.getDefault()).format(Date(millis))
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * Format date from string (pass-through fallback)
     */
    fun formatDate(dateStr: String?): String {
       // Keep existing signature if used mostly for raw strings, but user asked for dd/MM/yy.
       // We'll rely on the Long version for new usages.
       return dateStr ?: ""
    }

    /**
     * Format time to HH:mm:ss dd/MM/yy
     */
    fun formatTime(millis: Long): String {
        return try {
            SimpleDateFormat("HH:mm:ss dd/MM/yy", Locale.getDefault()).format(Date(millis))
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * Format time with timezone for Digital Clock: H:i:S d/M/YY (timezone)
     */
    fun formatFullTime(millis: Long): String {
        return try {
            val sdf = SimpleDateFormat("HH:mm:ss dd/MM/yy (z)", Locale.getDefault())
            sdf.format(Date(millis))
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * Parse a UTC date string (e.g. from server) and convert to local milliseconds.
     * Supports common formats: "yyyy-MM-dd HH:mm:ss", ISO 8601, and "yyyy-MM-dd"
     */
    fun parseUtcToMillis(utcDateStr: String?): Long {
        if (utcDateStr.isNullOrEmpty()) return 0L
        return try {
            if (utcDateStr.contains("T") && utcDateStr.endsWith("Z")) {
                // ISO 8601 format (e.g. 2025-12-30T18:33:00.068262Z)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    java.time.Instant.parse(utcDateStr).toEpochMilli()
                } else {
                    0L
                }
            } else if (utcDateStr.length == 10 && utcDateStr.contains("-")) {
                // Format: yyyy-MM-dd (GMT+0 as requested)
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                sdf.timeZone = java.util.TimeZone.getTimeZone("GMT")
                val date = sdf.parse(utcDateStr)
                date?.time ?: 0L
            } else {
                // legacy format: "yyyy-MM-dd HH:mm:ss"
                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
                val date = sdf.parse(utcDateStr)
                date?.time ?: 0L
            }
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * Convert UTC date string (yyyy-MM-dd or full timestamp) to local display format
     */
    fun formatUtcToLocal(utcDateStr: String?): String {
        val millis = parseUtcToMillis(utcDateStr)
        if (millis == 0L) return utcDateStr ?: ""
        
        // If it was just a date (yyyy-MM-dd), maybe we just want dd/MM/yy
        return if (utcDateStr?.length == 10) {
            formatDate(millis)
        } else {
            formatTime(millis)
        }
    }
    
    fun formatBytes(bytes: Long): String = formatSize(bytes)
}
