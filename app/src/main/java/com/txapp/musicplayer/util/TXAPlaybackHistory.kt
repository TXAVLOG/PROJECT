package com.txapp.musicplayer.util

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import java.io.ByteArrayOutputStream

/**
 * Helper class to manage playback history (resume timestamp) for all songs.
 * Stores data in an encrypted file "txa.txa".
 */
object TXAPlaybackHistory {

    private const val FILE_NAME = "txa.txa"
    private const val TAG = "TXAPlaybackHistory"

    // Encryption Constants (Matching TXABackupHelper for consistency)
    private const val AES_ALGORITHM = "AES/CBC/PKCS5Padding"
    private const val KEY_ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val KEY_LENGTH = 256
    private const val ITERATION_COUNT = 65536
    private const val SALT_LENGTH = 16
    private const val IV_LENGTH = 16
    private const val SECRET_PASSPHRASE = "TXAMS_2025_KEY"

    // In-memory cache: Map<SongPath, PositionMs>
    private val historyCache = mutableMapOf<String, Long>()

    // Flag to ensure we don't read file multiple times if not needed
    private var isLoaded = false

    /**
     * Initialize/Load history from file
     */
    suspend fun load(context: Context) = withContext(Dispatchers.IO) {
        if (isLoaded) return@withContext
        
        try {
            val file = File(context.filesDir, FILE_NAME)
            if (!file.exists()) {
                isLoaded = true
                return@withContext
            }

            val encryptedData = file.readBytes()
            if (encryptedData.isEmpty()) return@withContext

            val decryptedData = decrypt(encryptedData)
            val jsonString = String(decryptedData)
            val jsonObject = JSONObject(jsonString)

            historyCache.clear()
            val keys = jsonObject.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val value = jsonObject.optLong(key, 0)
                if (value > 0) {
                    historyCache[key] = value
                }
            }
            isLoaded = true
            TXALogger.d(TAG, "Loaded playback history for ${historyCache.size} songs")
        } catch (e: Exception) {
            TXALogger.e(TAG, "Failed to load playback history", e)
            // If load fails (corruption/key change), just start fresh but don't crash
            isLoaded = true 
        }
    }

    /**
     * Save current progress for a song
     */
    fun saveProgress(context: Context, songPath: String, position: Long) {
        if (!TXAPreferences.isRememberPlaybackPositionEnabled) return
        if (songPath.isBlank()) return
        
        // Update cache
        if (position > 5000) { // Only save if played more than 5 seconds
             historyCache[songPath] = position
        } else {
             // If very close to start, maybe remove? keeping it simple for now
        }

        // Persist to file async
        // Note: In a real heavy app we might batch this, but for now specific save points (pause/change) are fine.
        // We will call persist() manually from Service to avoid too many IO ops.
    }

    /**
     * Persist cache to disk
     */
    suspend fun persist(context: Context) = withContext(Dispatchers.IO) {
        if (!TXAPreferences.isRememberPlaybackPositionEnabled) return@withContext
        
        try {
            val jsonObject = JSONObject()
            synchronized(historyCache) {
                 historyCache.forEach { (path, pos) ->
                     jsonObject.put(path, pos)
                 }
            }
            
            val jsonString = jsonObject.toString()
            val encryptedData = encrypt(jsonString.toByteArray())
            
            val file = File(context.filesDir, FILE_NAME)
            file.writeBytes(encryptedData)
            
            TXALogger.d(TAG, "Persisted playback history")
        } catch (e: Exception) {
            TXALogger.e(TAG, "Failed to persist playback history", e)
        }
    }

    /**
     * Get saved position for a song
     */
    fun getPosition(songPath: String): Long {
        if (!TXAPreferences.isRememberPlaybackPositionEnabled) return 0
        val pos = historyCache[songPath] ?: 0
        if (pos > 0) {
            TXALogger.d(TAG, "History lookup: SUCCESS for $songPath at $pos ms")
        } else {
            // Only log if it's a real path to avoid spamming
            if (songPath.startsWith("/")) {
                 TXALogger.d(TAG, "History lookup: NO DATA for $songPath")
            }
        }
        return pos
    }
    
    /**
     * Remove position for a song (e.g. when finished)
     */
    fun clearPosition(songPath: String) {
        historyCache.remove(songPath)
    }

    /**
     * Clear all history
     */
    suspend fun clearAll(context: Context) = withContext(Dispatchers.IO) {
        historyCache.clear()
        val file = File(context.filesDir, FILE_NAME)
        if (file.exists()) {
            file.delete()
        }
    }
    
    /**
     * Get all saved history as a Map for UI display
     */
    fun getHistoryMap(): Map<String, Long> {
        return historyCache.toMap()
    }

    /**
     * Get count of saved songs
     */
    fun getSavedCount(): Int {
        return historyCache.size
    }

    // ================== ENCRYPTION ==================

    private fun encrypt(data: ByteArray): ByteArray {
        val salt = ByteArray(SALT_LENGTH).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(IV_LENGTH).also { SecureRandom().nextBytes(it) }
        
        val secretKey = generateKey(SECRET_PASSPHRASE, salt)
        val cipher = Cipher.getInstance(AES_ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, IvParameterSpec(iv))
        
        val encryptedData = cipher.doFinal(data)
        
        return ByteArrayOutputStream().apply {
            write(salt)
            write(iv)
            write(encryptedData)
        }.toByteArray()
    }

    private fun decrypt(data: ByteArray): ByteArray {
        if (data.size < SALT_LENGTH + IV_LENGTH) {
            throw IllegalArgumentException("Invalid encrypted data format")
        }
        
        val salt = data.sliceArray(0 until SALT_LENGTH)
        val iv = data.sliceArray(SALT_LENGTH until SALT_LENGTH + IV_LENGTH)
        val encryptedData = data.sliceArray(SALT_LENGTH + IV_LENGTH until data.size)
        
        val secretKey = generateKey(SECRET_PASSPHRASE, salt)
        val cipher = Cipher.getInstance(AES_ALGORITHM)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, IvParameterSpec(iv))
        
        return cipher.doFinal(encryptedData)
    }

    private fun generateKey(passphrase: String, salt: ByteArray): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance(KEY_ALGORITHM)
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, ITERATION_COUNT, KEY_LENGTH)
        val secretKey = factory.generateSecret(spec)
        return SecretKeySpec(secretKey.encoded, "AES")
    }
}
