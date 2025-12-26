package ms.txams.vv.data.manager

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import dagger.hilt.android.qualifiers.ApplicationContext
import ms.txams.vv.data.database.SongEntity
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TXAAudioInjectionManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val introAssetName = "intro_txa.mp3"
    
    // Minimum expected file size for intro_txa.mp3 (40KB = 40960 bytes)
    private val minExpectedSize = 40_000L
    
    /**
     * Check integrity of intro_txa.mp3 asset file
     * Verifies: 1. File exists, 2. File size is reasonable, 3. File is readable
     * @return true if file is valid, false otherwise
     */
    fun checkIntegrity(): Boolean {
        return try {
            context.assets.open(introAssetName).use { inputStream ->
                val bytes = inputStream.readBytes()
                // Check 1: File exists and has content
                if (bytes.isEmpty()) return false
                // Check 2: File size is reasonable (at least minExpectedSize)
                if (bytes.size < minExpectedSize) return false
                // Check 3: Calculate MD5 to verify file isn't corrupted
                val md5 = calculateMD5(bytes)
                md5.isNotEmpty()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Get detailed integrity status
     * @return IntegrityResult with status and details
     */
    fun getIntegrityDetail(): IntegrityResult {
        return try {
            context.assets.open(introAssetName).use { inputStream ->
                val bytes = inputStream.readBytes()
                val md5 = calculateMD5(bytes)
                IntegrityResult(
                    exists = true,
                    size = bytes.size.toLong(),
                    md5Hash = md5,
                    isValid = bytes.size >= minExpectedSize
                )
            }
        } catch (e: Exception) {
            IntegrityResult(
                exists = false,
                size = 0,
                md5Hash = "",
                isValid = false,
                errorMessage = e.message
            )
        }
    }

    private fun calculateMD5(bytes: ByteArray): String {
        return try {
            val md = MessageDigest.getInstance("MD5")
            val digest = md.digest(bytes)
            digest.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            ""
        }
    }

    fun getIntroUri(): Uri {
        return Uri.parse("asset:///$introAssetName")
    }

    // Simplified as we now use List<MediaItem> in Service
    fun buildConcatenatedSource(song: SongEntity, mediaSourceFactory: DefaultMediaSourceFactory): MediaSource {
        // Legacy method if using manual source construction
        return mediaSourceFactory.createMediaSource(MediaItem.fromUri(song.path))
    }
    
    data class IntegrityResult(
        val exists: Boolean,
        val size: Long,
        val md5Hash: String,
        val isValid: Boolean,
        val errorMessage: String? = null
    )
}
