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
}
