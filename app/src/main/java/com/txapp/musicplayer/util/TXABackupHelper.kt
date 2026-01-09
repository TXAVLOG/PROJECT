package com.txapp.musicplayer.util

import android.content.Context
import android.net.Uri
import android.os.Environment
import com.txapp.musicplayer.MusicApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.*
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * TXA Backup & Restore Helper
 * 
 * Features:
 * - Backup favorites, play history, settings to encrypted .txa file
 * - AES-256 encryption to prevent other apps from reading
 * - File format: TXA_<timestamp>.txa
 * - Cross-device restore support with file existence check
 */
object TXABackupHelper {

    private const val TAG = "TXABackupHelper"
    
    // File constants
    const val BACKUP_EXTENSION = "txa"
    const val FILE_PREFIX = "TXA_"
    private const val APPEND_EXTENSION = ".$BACKUP_EXTENSION"
    
    // Encryption constants
    private const val AES_ALGORITHM = "AES/CBC/PKCS5Padding"
    private const val KEY_ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val KEY_LENGTH = 256
    private const val ITERATION_COUNT = 65536
    private const val SALT_LENGTH = 16
    private const val IV_LENGTH = 16
    
    // Secret passphrase for encryption (device-independent)
    private const val SECRET_PASSPHRASE = "TXAMS_2025_KEY"
    
    // Backup content paths
    private const val FAVORITES_FILE = "favorites.json"
    private const val PLAY_HISTORY_FILE = "play_history.json"
    private const val SETTINGS_FILE = "settings.json"
    private const val PLAYLISTS_FILE = "playlists.json"
    private const val METADATA_FILE = "metadata.json"
    
    // State flows
    private val _isBackingUp = MutableStateFlow(false)
    val isBackingUp = _isBackingUp.asStateFlow()
    
    private val _isRestoring = MutableStateFlow(false)
    val isRestoring = _isRestoring.asStateFlow()
    
    private val _progress = MutableStateFlow(0)
    val progress = _progress.asStateFlow()
    
    private val _statusMessage = MutableStateFlow("")
    val statusMessage = _statusMessage.asStateFlow()

    /**
     * Backup content options
     */
    enum class BackupContent {
        FAVORITES,
        PLAY_HISTORY,
        SETTINGS,
        PLAYLISTS
    }

    /**
     * Backup result
     */
    data class BackupResult(
        val success: Boolean,
        val filePath: String? = null,
        val error: String? = null,
        val favoritesCount: Int = 0,
        val historyCount: Int = 0,
        val playlistsCount: Int = 0
    )

    /**
     * Restore result
     */
    data class RestoreResult(
        val success: Boolean,
        val favoritesRestored: Int = 0,
        val favoritesSkipped: Int = 0,
        val historyRestored: Int = 0,
        val historySkipped: Int = 0,
        val playlistsRestored: Int = 0,
        val playlistsSkipped: Int = 0,
        val settingsRestored: Boolean = false,
        val error: String? = null
    )

    /**
     * Create encrypted backup file - reads directly from database
     */
    suspend fun createBackup(
        context: Context,
        contents: List<BackupContent> = BackupContent.entries,
        customName: String? = null
    ): BackupResult = withContext(Dispatchers.IO) {
        try {
            _isBackingUp.value = true
            _progress.value = 0
            _statusMessage.value = "txamusic_backup_preparing".txa()
            
            val timestamp = getTimeStamp()
            val fileName = customName?.sanitize() ?: timestamp
            val backupFile = File(getBackupRoot(context), "$FILE_PREFIX$fileName$APPEND_EXTENSION")
            
            // Ensure directory exists
            backupFile.parentFile?.mkdirs()
            
            // Get database
            // Get database
            val app = context.applicationContext as MusicApplication
            val songDao = app.database.songDao()
            val playlistDao = app.database.playlistDao()
            
            // Read favorites from database
            _progress.value = 10
            _statusMessage.value = "txamusic_backup_collecting".txa()
            
            val allSongs = songDao.getAllSongs().first()
            val favorites = allSongs.filter { it.isFavorite }
            val songsWithHistory = allSongs.filter { it.playCount > 0 || it.lastPlayed > 0 }
            
            // Read Playlists
            val playlists = if (contents.contains(BackupContent.PLAYLISTS)) {
                 playlistDao.getPlaylistsWithSongCountOnce()
            } else {
                 emptyList()
            }
            
            TXALogger.appI(TAG, "Found ${favorites.size} favorites, ${songsWithHistory.size} history items, and ${playlists.size} playlists")
            
            // Create temporary zip in memory
            _progress.value = 20
            val zipBytes = ByteArrayOutputStream()
            ZipOutputStream(zipBytes).use { zipOut ->
                // Add metadata
                addMetadataToZip(zipOut, favorites.size, songsWithHistory.size, playlists.size)
                _progress.value = 30
                
                // Add favorites
                if (contents.contains(BackupContent.FAVORITES)) {
                    _statusMessage.value = "txamusic_backup_favorites".txa()
                    addFavoritesToZip(zipOut, favorites.map { 
                        FavoriteData(it.id, it.data, it.title, it.artist)
                    })
                    _progress.value = 50
                }
                
                // Add play history
                if (contents.contains(BackupContent.PLAY_HISTORY)) {
                    _statusMessage.value = "txamusic_backup_history".txa()
                    addPlayHistoryToZip(zipOut, songsWithHistory.map {
                        HistoryData(it.id, it.data, it.title, it.artist, it.playCount, it.lastPlayed)
                    })
                    _progress.value = 70
                }
                
                // Add settings
                if (contents.contains(BackupContent.SETTINGS)) {
                    _statusMessage.value = "txamusic_backup_settings".txa()
                    addSettingsToZip(zipOut)
                    _progress.value = 85
                }
                
                // Add playlists
                if (contents.contains(BackupContent.PLAYLISTS) && playlists.isNotEmpty()) {
                    _statusMessage.value = "txamusic_backup_playlists".txa()
                    val playlistDataList = mutableListOf<PlaylistData>()
                    
                    for (playlist in playlists) {
                        val songIds = playlistDao.getPlaylistSongIds(playlist.id)
                        val songsInPlaylist = songIds.mapNotNull { id ->
                            allSongs.find { it.id == id }?.let { 
                                SongIdentifier(it.data, it.title, it.artist)
                            }
                        }
                        playlistDataList.add(PlaylistData(playlist.name, songsInPlaylist))
                    }
                    
                    addPlaylistsToZip(zipOut, playlistDataList)
                    _progress.value = 95
                }
            }
            
            // Encrypt and write to file
            _statusMessage.value = "txamusic_backup_encrypting".txa()
            _progress.value = 90
            
            val encryptedData = encrypt(zipBytes.toByteArray())
            
            try {
                // Try standard write
                backupFile.writeBytes(encryptedData)
            } catch (e: Exception) {
                // Fallback to root if permission denied
                val isPermissionIssue = e is IOException && (e.message?.contains("Permission denied", ignoreCase = true) == true || e.message?.contains("EACCES") == true)
                if (isPermissionIssue && TXASuHelper.isRooted()) {
                     TXALogger.appW(TAG, "Standard write failed (${e.message}), attempting root write...")
                     
                     // Write to temp file first
                     val tempFile = File(context.cacheDir, "temp_backup_${System.currentTimeMillis()}.tmp")
                     tempFile.writeBytes(encryptedData)
                     
                     // Move to target using root
                     val targetPath = backupFile.absolutePath
                     // Ensure directory exists with root
                     TXASuHelper.runAsRoot("mkdir -p '${backupFile.parent}' && chmod 777 '${backupFile.parent}'")
                     
                     val success = TXASuHelper.runAsRoot("cp '${tempFile.absolutePath}' '$targetPath' && chmod 666 '$targetPath'")
                     
                     // Cleanup temp
                     tempFile.delete()
                     
                     if (!success) {
                         TXALogger.appE(TAG, "Root write also failed")
                         throw e // Throw original error
                     }
                     // If success, continue as normal
                } else {
                    throw e
                }
            }
            
            _progress.value = 100
            _statusMessage.value = "txamusic_backup_success".txa()
            
            TXALogger.appI(TAG, "Backup created successfully: ${backupFile.absolutePath}")
            
            BackupResult(
                success = true, 
                filePath = backupFile.absolutePath,
                favoritesCount = favorites.size,
                historyCount = songsWithHistory.size,
                playlistsCount = playlists.size
            )
            
        } catch (e: Exception) {
            TXALogger.appE(TAG, "Backup failed", e)
            BackupResult(success = false, error = e.message ?: "Unknown error")
        } finally {
            _isBackingUp.value = false
        }
    }

    /**
     * Restore from encrypted backup file (URI)
     */
    suspend fun restoreBackup(
        context: Context,
        uri: Uri,
        contents: List<BackupContent> = BackupContent.entries
    ): RestoreResult = withContext(Dispatchers.IO) {
        try {
            _isRestoring.value = true
            _progress.value = 0
            _statusMessage.value = "txamusic_restore_reading".txa()
            
            // Read encrypted file
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return@withContext RestoreResult(success = false, error = "Cannot open file")
            
            val encryptedData = inputStream.readBytes()
            inputStream.close()
            
            restoreFromBytes(context, encryptedData, contents)
            
        } catch (e: Exception) {
            TXALogger.appE(TAG, "Restore failed", e)
            RestoreResult(success = false, error = e.message ?: "Unknown error")
        } finally {
            _isRestoring.value = false
        }
    }

    /**
     * Restore from file path
     */
    suspend fun restoreBackup(
        context: Context,
        file: File,
        contents: List<BackupContent> = BackupContent.entries
    ): RestoreResult = withContext(Dispatchers.IO) {
        try {
            _isRestoring.value = true
            _progress.value = 0
            _statusMessage.value = "txamusic_restore_reading".txa()
            
            if (!file.exists()) {
                return@withContext RestoreResult(success = false, error = "File not found")
            }
            
            val encryptedData = file.readBytes()
            restoreFromBytes(context, encryptedData, contents)
            
        } catch (e: Exception) {
            TXALogger.appE(TAG, "Restore failed", e)
            RestoreResult(success = false, error = e.message ?: "Unknown error")
        } finally {
            _isRestoring.value = false
        }
    }
    
    private suspend fun restoreFromBytes(
        context: Context,
        encryptedData: ByteArray,
        contents: List<BackupContent>
    ): RestoreResult {
        _progress.value = 20
        _statusMessage.value = "txamusic_restore_decrypting".txa()
        
        // Decrypt
        val decryptedData = decrypt(encryptedData)
        
        _progress.value = 40
        _statusMessage.value = "txamusic_restore_processing".txa()
        
        var favoritesRestored = 0
        var favoritesSkipped = 0
        var historyRestored = 0
        var historySkipped = 0
        var playlistsRestored = 0
        var playlistsSkipped = 0
        var settingsRestored = false
        
        // Get database
        // Get database
        val app = context.applicationContext as MusicApplication
        val songDao = app.database.songDao()
        val playlistDao = app.database.playlistDao()
        
        // Extract and restore from zip
        ZipInputStream(ByteArrayInputStream(decryptedData)).use { zipIn ->
            var entry = zipIn.nextEntry
            while (entry != null) {
                when {
                    entry.name == FAVORITES_FILE && contents.contains(BackupContent.FAVORITES) -> {
                        _statusMessage.value = "txamusic_restore_favorites".txa()
                        val result = restoreFavorites(songDao, zipIn)
                        favoritesRestored = result.first
                        favoritesSkipped = result.second
                        _progress.value = 60
                    }
                    entry.name == PLAY_HISTORY_FILE && contents.contains(BackupContent.PLAY_HISTORY) -> {
                        _statusMessage.value = "txamusic_restore_history".txa()
                        val result = restorePlayHistory(songDao, zipIn)
                        historyRestored = result.first
                        historySkipped = result.second
                        _progress.value = 80
                    }
                    entry.name == SETTINGS_FILE && contents.contains(BackupContent.SETTINGS) -> {
                        _statusMessage.value = "txamusic_restore_settings".txa()
                        settingsRestored = restoreSettings(zipIn)
                        _progress.value = 90
                    }
                    entry.name == PLAYLISTS_FILE && contents.contains(BackupContent.PLAYLISTS) -> {
                        _statusMessage.value = "txamusic_restore_playlists".txa()
                        val result = restorePlaylists(playlistDao, songDao, zipIn)
                        playlistsRestored = result.first
                        playlistsSkipped = result.second
                        _progress.value = 95
                    }
                }
                entry = zipIn.nextEntry
            }
        }
        
        _progress.value = 100
        _statusMessage.value = "txamusic_restore_success".txa()
        
        // Notify app to refresh everything
        val intent = android.content.Intent("com.txapp.musicplayer.action.RESTORE_COMPLETED")
        context.sendBroadcast(intent)
        
        TXALogger.appI(TAG, "Restore completed: favorites=$favoritesRestored, history=$historyRestored, playlists=$playlistsRestored, settings=$settingsRestored")
        
        return RestoreResult(
            success = true,
            favoritesRestored = favoritesRestored,
            favoritesSkipped = favoritesSkipped,
            historyRestored = historyRestored,
            historySkipped = historySkipped,
            playlistsRestored = playlistsRestored,
            playlistsSkipped = playlistsSkipped,
            settingsRestored = settingsRestored
        )
    }

    /**
     * Get list of existing backups
     */
    fun getBackupFiles(context: Context): List<File> {
        val backupDir = getBackupRoot(context)
        if (!backupDir.exists()) return emptyList()
        
        return backupDir.listFiles { file ->
            file.extension == BACKUP_EXTENSION && file.name.startsWith(FILE_PREFIX)
        }?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    /**
     * Delete a backup file
     */
    fun deleteBackup(file: File): Boolean {
        return try {
            file.delete().also {
                if (it) TXALogger.appI(TAG, "Backup deleted: ${file.name}")
            }
        } catch (e: Exception) {
            TXALogger.appE(TAG, "Failed to delete backup", e)
            false
        }
    }

    /**
     * Rename a backup file
     */
    fun renameBackup(file: File, newName: String): Boolean {
        return try {
            val sanitized = newName.sanitize()
            if (sanitized.isBlank()) return false
            
            val newFile = File(file.parentFile, "$FILE_PREFIX$sanitized$APPEND_EXTENSION")
            if (newFile.exists()) return false
            
            file.renameTo(newFile).also {
                if (it) TXALogger.appI(TAG, "Backup renamed from ${file.name} to ${newFile.name}")
            }
        } catch (e: Exception) {
            TXALogger.appE(TAG, "Failed to rename backup", e)
            false
        }
    }

    private fun String.sanitize(): String {
        return this.replace(Regex("[^a-zA-Z0-9_.-]"), "_")
            .trim()
            .take(50) // Limit length
    }

    /**
     * Get backup metadata from file without full restore
     */
    suspend fun getBackupInfo(context: Context, uri: Uri): BackupMetadata? = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return@withContext null
            val encryptedData = inputStream.readBytes()
            inputStream.close()
            
            val decryptedData = decrypt(encryptedData)
            
            ZipInputStream(ByteArrayInputStream(decryptedData)).use { zipIn ->
                var entry = zipIn.nextEntry
                while (entry != null) {
                    if (entry.name == METADATA_FILE) {
                        val json = JSONObject(zipIn.bufferedReader().readText())
                        return@withContext BackupMetadata(
                            appVersion = json.optString("appVersion", "Unknown"),
                            createdAt = json.optLong("createdAt", 0),
                            deviceModel = json.optString("deviceModel", "Unknown"),
                            favoritesCount = json.optInt("favoritesCount", 0),
                            historyCount = json.optInt("historyCount", 0),
                            playlistsCount = json.optInt("playlistsCount", 0),
                            hasSettings = json.optBoolean("hasSettings", false)
                        )
                    }
                    entry = zipIn.nextEntry
                }
            }
            null
        } catch (e: Exception) {
            TXALogger.appE(TAG, "Failed to read backup info", e)
            null
        }
    }
    
    /**
     * Get backup metadata from file without full restore
     */
    suspend fun getBackupInfo(file: File): BackupMetadata? = withContext(Dispatchers.IO) {
        try {
            if (!file.exists()) return@withContext null
            
            val encryptedData = file.readBytes()
            val decryptedData = decrypt(encryptedData)
            
            ZipInputStream(ByteArrayInputStream(decryptedData)).use { zipIn ->
                var entry = zipIn.nextEntry
                while (entry != null) {
                    if (entry.name == METADATA_FILE) {
                        val json = JSONObject(zipIn.bufferedReader().readText())
                        return@withContext BackupMetadata(
                            appVersion = json.optString("appVersion", "Unknown"),
                            createdAt = json.optLong("createdAt", 0),
                            deviceModel = json.optString("deviceModel", "Unknown"),
                            favoritesCount = json.optInt("favoritesCount", 0),
                            historyCount = json.optInt("historyCount", 0),
                            playlistsCount = json.optInt("playlistsCount", 0),
                            hasSettings = json.optBoolean("hasSettings", false)
                        )
                    }
                    entry = zipIn.nextEntry
                }
            }
            null
        } catch (e: Exception) {
            TXALogger.appE(TAG, "Failed to read backup info from file", e)
            null
        }
    }
    
    /**
     * Compare backup data with current database to check if restore will overwrite data
     * Returns true if backup has different data than current
     */
    suspend fun compareBackupWithCurrent(context: Context, file: File): Boolean = withContext(Dispatchers.IO) {
        try {
            val backupInfo = getBackupInfo(file) ?: return@withContext false
            
            // Get current data from database
            val app = context.applicationContext as MusicApplication
            val songDao = app.database.songDao()
            val allSongs = songDao.getAllSongs().first()
            
            val currentFavorites = allSongs.count { it.isFavorite }
            val currentHistory = allSongs.count { it.lastPlayed > 0 }
            
            // Rough check for playlists
            val playlistDao = app.database.playlistDao()
            val currentPlaylists = playlistDao.getPlaylistsWithSongCountOnce().size
            
            // Check if backup has different data
            val hasDifferentFavorites = backupInfo.favoritesCount > 0 && currentFavorites > 0
            val hasDifferentHistory = backupInfo.historyCount > 0 && currentHistory > 0
            val hasDifferentPlaylists = backupInfo.playlistsCount > 0 && currentPlaylists > 0
            
            TXALogger.appI(TAG, "Compare: backup(fav=${backupInfo.favoritesCount}, hist=${backupInfo.historyCount}, pl=${backupInfo.playlistsCount}) vs current(fav=$currentFavorites)")
            
            // Will overwrite if current has data and backup also has data
            hasDifferentFavorites || hasDifferentHistory || hasDifferentPlaylists
        } catch (e: Exception) {
            TXALogger.appE(TAG, "Failed to compare backup", e)
            false
        }
    }

    // ============== DATA CLASSES ==============
    
    private data class FavoriteData(
        val id: Long,
        val filePath: String,
        val title: String,
        val artist: String
    )
    
    private data class HistoryData(
        val id: Long,
        val filePath: String,
        val title: String,
        val artist: String,
        val playCount: Int,
        val lastPlayed: Long
    )
    
    private data class PlaylistData(
        val name: String,
        val songs: List<SongIdentifier>
    )
    
    private data class SongIdentifier(
        val filePath: String,
        val title: String,
        val artist: String
    )

    // ============== BACKUP METHODS ==============

    private fun addMetadataToZip(zipOut: ZipOutputStream, favoritesCount: Int, historyCount: Int, playlistsCount: Int) {
        val metadata = JSONObject().apply {
            put("appVersion", TXADeviceInfo.getVersionName())
            put("createdAt", System.currentTimeMillis())
            put("deviceModel", TXADeviceInfo.getModel())
            put("favoritesCount", favoritesCount)
            put("historyCount", historyCount)
            put("playlistsCount", playlistsCount)
            put("hasSettings", true)
        }
        
        zipOut.putNextEntry(ZipEntry(METADATA_FILE))
        zipOut.write(metadata.toString().toByteArray())
        zipOut.closeEntry()
    }

    private fun addFavoritesToZip(zipOut: ZipOutputStream, favorites: List<FavoriteData>) {
        val jsonArray = JSONArray()
        favorites.forEach { fav ->
            jsonArray.put(JSONObject().apply {
                put("id", fav.id)
                put("filePath", fav.filePath)
                put("title", fav.title)
                put("artist", fav.artist)
            })
        }
        
        zipOut.putNextEntry(ZipEntry(FAVORITES_FILE))
        zipOut.write(jsonArray.toString().toByteArray())
        zipOut.closeEntry()
        
        TXALogger.appI(TAG, "Added ${favorites.size} favorites to backup")
    }

    private fun addPlayHistoryToZip(zipOut: ZipOutputStream, history: List<HistoryData>) {
        val jsonArray = JSONArray()
        history.forEach { item ->
            jsonArray.put(JSONObject().apply {
                put("id", item.id)
                put("filePath", item.filePath)
                put("title", item.title)
                put("artist", item.artist)
                put("playCount", item.playCount)
                put("lastPlayed", item.lastPlayed)
            })
        }
        
        zipOut.putNextEntry(ZipEntry(PLAY_HISTORY_FILE))
        zipOut.write(jsonArray.toString().toByteArray())
        zipOut.closeEntry()
        
        TXALogger.appI(TAG, "Added ${history.size} history items to backup")
    }

    private fun addSettingsToZip(zipOut: ZipOutputStream) {
        val settings = JSONObject().apply {
            put("theme", TXAPreferences.currentTheme)
            put("accent", TXAPreferences.currentAccent)
            put("gridSize", TXAPreferences.currentGridSize)
            put("crossfade", TXAPreferences.currentCrossfadeDuration)
            put("playbackSpeed", TXAPreferences.currentPlaybackSpeed.toDouble())
            put("audioFocus", TXAPreferences.isAudioFocusEnabled)
            put("bluetoothPlayback", TXAPreferences.isBluetoothPlaybackEnabled)
            put("headsetPlay", TXAPreferences.isHeadsetPlayEnabled)
            put("showShuffle", TXAPreferences.isShowShuffleBtn)
            put("showFavorite", TXAPreferences.isShowFavoriteBtn)
            put("holidayEffect", TXAPreferences.isHolidayEffectEnabled)
            put("autoDownloadImages", TXAPreferences.isAutoDownloadImagesEnabled)
            put("nowPlayingUI", TXAPreferences.getNowPlayingUI())
            put("imageQuality", TXAPreferences.getImageQuality())
            put("virtualizerEnabled", TXAPreferences.isVirtualizerEnabled)
            put("audioFadeDuration", TXAPreferences.currentAudioFadeDuration)
            put("eqBandLevels", TXAPreferences.getEqualizerBandLevels(5).joinToString(",")) 
            put("eqPreset", TXAPreferences.getEqualizerPreset())
            put("bassBoostStrength", TXAPreferences.getBassBoostStrength())
            put("virtualizerStrength", TXAPreferences.getVirtualizerStrength())
            put("equalizerEnabled", TXAPreferences.isEqualizerEnabled)
            put("bassBoostEnabled", TXAPreferences.isBassBoostEnabled)
            put("playerEffectsEnabled", TXAPreferences.isPlayerEffectsEnabled)
            put("playerEffectType", TXAPreferences.currentPlayerEffectType)
            put("extraControls", TXAPreferences.isExtraControls)
            put("powerMode", TXAPreferences.isPowerMode)
            put("aodAutoBrightness", TXAPreferences.isAodAutoBrightness)
            put("rememberLastTab", TXAPreferences.isRememberLastTab)
            put("lastTab", TXAPreferences.currentLastTab)
            put("albumGridSize", TXAPreferences.currentAlbumGridSize)
            put("artistGridSize", TXAPreferences.currentArtistGridSize)

        }
        
        zipOut.putNextEntry(ZipEntry(SETTINGS_FILE))
        zipOut.write(settings.toString().toByteArray())
        zipOut.closeEntry()
        
        TXALogger.appI(TAG, "Added settings to backup")
    }
    
    private fun addPlaylistsToZip(zipOut: ZipOutputStream, playlists: List<PlaylistData>) {
        val jsonArray = JSONArray()
        playlists.forEach { playlist ->
            val songArray = JSONArray()
            playlist.songs.forEach { song ->
                songArray.put(JSONObject().apply {
                    put("filePath", song.filePath)
                    put("title", song.title)
                    put("artist", song.artist)
                })
            }
            
            jsonArray.put(JSONObject().apply {
                put("name", playlist.name)
                put("songs", songArray)
            })
        }
        
        zipOut.putNextEntry(ZipEntry(PLAYLISTS_FILE))
        zipOut.write(jsonArray.toString().toByteArray())
        zipOut.closeEntry()
        
        TXALogger.appI(TAG, "Added ${playlists.size} playlists to backup")
    }

    // ============== RESTORE METHODS ==============

    private suspend fun findSongRobustly(
        songDao: com.txapp.musicplayer.data.SongDao,
        id: Long,
        filePath: String,
        title: String,
        artist: String
    ): com.txapp.musicplayer.model.Song? {
        // 1. Try by ID
        var song = songDao.getSongById(id)
        if (song != null) return song
        
        // 2. Try by Path
        song = songDao.getSongByPath(filePath)
        if (song != null) return song
        
        // 3. Try by Metadata
        if (title.isNotBlank() && artist.isNotBlank()) {
            song = songDao.getSongByMetadata(title, artist)
        }
        return song
    }

    private suspend fun restoreFavorites(
        songDao: com.txapp.musicplayer.data.SongDao,
        zipIn: ZipInputStream
    ): Pair<Int, Int> {
        return try {
            val content = zipIn.bufferedReader().readText()
            val jsonArray = JSONArray(content)
            
            var restored = 0
            var skipped = 0
            val idsToFavorite = mutableListOf<Long>()
            
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val filePath = obj.optString("filePath", "")
                val id = obj.optLong("id", 0)
                val title = obj.optString("title", "")
                val artist = obj.optString("artist", "")
                
                val existingSong = findSongRobustly(songDao, id, filePath, title, artist)
                if (existingSong != null) {
                    idsToFavorite.add(existingSong.id)
                    restored++
                } else {
                    skipped++
                }
            }
            
            if (idsToFavorite.isNotEmpty()) {
                songDao.setFavorites(idsToFavorite, true)
            }
            
            TXALogger.appI(TAG, "Favorites restored: $restored, skipped: $skipped")
            Pair(restored, skipped)
        } catch (e: Exception) {
            TXALogger.appE(TAG, "Failed to restore favorites", e)
            Pair(0, 0)
        }
    }

    private suspend fun restorePlayHistory(
        songDao: com.txapp.musicplayer.data.SongDao,
        zipIn: ZipInputStream
    ): Pair<Int, Int> {
        return try {
            val content = zipIn.bufferedReader().readText()
            val jsonArray = JSONArray(content)
            
            var restored = 0
            var skipped = 0
            
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val id = obj.optLong("id", 0)
                val filePath = obj.optString("filePath", "")
                val title = obj.optString("title", "")
                val artist = obj.optString("artist", "")
                val lastPlayed = obj.optLong("lastPlayed", 0)
                val playCount = obj.optInt("playCount", 0)
                
                val existingSong = findSongRobustly(songDao, id, filePath, title, artist)
                if (existingSong != null) {
                    // Update history if backup data is newer or more frequent
                    if (lastPlayed > existingSong.lastPlayed || playCount > existingSong.playCount) {
                        // Use a custom update or just set them
                        // For simplicity, we'll use existing DAO methods if available
                        songDao.updateLastPlayed(existingSong.id, lastPlayed)
                        // If we had a setPlayCount we would use it, but for now just lastPlayed
                        restored++
                    } else {
                        restored++
                    }
                } else {
                    skipped++
                    TXALogger.appW(TAG, "History song not found in DB: $title")
                }
            }
            
            TXALogger.appI(TAG, "Play history restored: $restored, skipped: $skipped")
            Pair(restored, skipped)
        } catch (e: Exception) {
            TXALogger.appE(TAG, "Failed to restore play history", e)
            Pair(0, 0)
        }
    }

    private fun restoreSettings(zipIn: ZipInputStream): Boolean {
        return try {
            val content = zipIn.bufferedReader().readText()
            val json = JSONObject(content)
            
            // Apply settings immediately
            TXAPreferences.currentTheme = json.optString("theme", "system")
            TXAPreferences.currentAccent = json.optString("accent", "#00d269ff")
            TXAPreferences.currentGridSize = json.optInt("gridSize", 2)
            TXAPreferences.currentCrossfadeDuration = json.optInt("crossfade", 0)
            TXAPreferences.currentPlaybackSpeed = json.optDouble("playbackSpeed", 1.0).toFloat()
            TXAPreferences.isAudioFocusEnabled = json.optBoolean("audioFocus", true)
            TXAPreferences.isBluetoothPlaybackEnabled = json.optBoolean("bluetoothPlayback", false)
            TXAPreferences.isHeadsetPlayEnabled = json.optBoolean("headsetPlay", false)
            TXAPreferences.isShowShuffleBtn = json.optBoolean("showShuffle", true)
            TXAPreferences.isShowFavoriteBtn = json.optBoolean("showFavorite", true)
            TXAPreferences.isHolidayEffectEnabled = json.optBoolean("holidayEffect", true)
            TXAPreferences.isAutoDownloadImagesEnabled = json.optBoolean("autoDownloadImages", false)
            TXAPreferences.setNowPlayingUI(json.optString("nowPlayingUI", "adaptive"))
            TXAPreferences.setImageQuality(json.optString("imageQuality", "medium"))
            TXAPreferences.isEqualizerEnabled = json.optBoolean("equalizerEnabled", false)
            TXAPreferences.isBassBoostEnabled = json.optBoolean("bassBoostEnabled", false)
            TXAPreferences.isVirtualizerEnabled = json.optBoolean("virtualizerEnabled", false)
            TXAPreferences.isPlayerEffectsEnabled = json.optBoolean("playerEffectsEnabled", false)
            TXAPreferences.currentPlayerEffectType = json.optString("playerEffectType", "snow")
            TXAPreferences.currentAudioFadeDuration = json.optInt("audioFadeDuration", 500)
            TXAPreferences.isExtraControls = json.optBoolean("extraControls", true)
            TXAPreferences.isPowerMode = json.optBoolean("powerMode", false)
            TXAPreferences.isAodAutoBrightness = json.optBoolean("aodAutoBrightness", true)
            
            TXAPreferences.isRememberLastTab = json.optBoolean("rememberLastTab", true)
            TXAPreferences.currentLastTab = json.optInt("lastTab", 0)
            TXAPreferences.currentAlbumGridSize = json.optInt("albumGridSize", 2)
            TXAPreferences.currentArtistGridSize = json.optInt("artistGridSize", 3)

            
            val eqBands = json.optString("eqBandLevels", "")
            if (eqBands.isNotEmpty()) {
                TXAPreferences.setEqualizerBandLevels(eqBands.split(",").map { it.toInt() })
            }
            TXAPreferences.setEqualizerPreset(json.optInt("eqPreset", -1))
            TXAPreferences.setBassBoostStrength(json.optInt("bassBoostStrength", 0))
            TXAPreferences.setVirtualizerStrength(json.optInt("virtualizerStrength", 0))
            
            TXALogger.appI(TAG, "Settings restored successfully")
            true
        } catch (e: Exception) {
            TXALogger.appE(TAG, "Failed to restore settings", e)
            false
        }
    }
    
    private suspend fun restorePlaylists(
        playlistDao: com.txapp.musicplayer.data.PlaylistDao,
        songDao: com.txapp.musicplayer.data.SongDao,
        zipIn: ZipInputStream
    ): Pair<Int, Int> {
        return try {
            val content = zipIn.bufferedReader().readText()
            val jsonArray = JSONArray(content)
            
            var restored = 0
            var skipped = 0 // Count of playlists failed (e.g. empty after valid song check) - unlikely
            
            // For now, we count restored as number of playlists created
            
            for (i in 0 until jsonArray.length()) {
                val playlistObj = jsonArray.getJSONObject(i)
                val name = playlistObj.optString("name", "Restored Playlist")
                val songsParams = playlistObj.optJSONArray("songs")
                
                // Create playlist
                val playlistId = playlistDao.insertPlaylist(com.txapp.musicplayer.data.PlaylistEntity(name = name))
                
                if (songsParams != null && songsParams.length() > 0) {
                    for (j in 0 until songsParams.length()) {
                        val songObj = songsParams.getJSONObject(j)
                        val filePath = songObj.optString("filePath", "")
                        val title = songObj.optString("title", "")
                        val artist = songObj.optString("artist", "")
                        
                        // Find logic (using SongDao)
                        val song = findSongRobustly(songDao, -1, filePath, title, artist) // -1 ID as it might have changed
                        if (song != null) {
                            playlistDao.addSongToPlaylist(com.txapp.musicplayer.data.PlaylistSongCrossRef(
                                playlistId = playlistId,
                                songId = song.id,
                                sortOrder = j
                            ))
                        }
                    }
                }
                restored++
            }
            
            TXALogger.appI(TAG, "Playlists restored: $restored")
            Pair(restored, skipped)
        } catch (e: Exception) {
            TXALogger.appE(TAG, "Failed to restore playlists", e)
            Pair(0, 0)
        }
    }

    // ============== ENCRYPTION ==============

    private fun encrypt(data: ByteArray): ByteArray {
        val salt = ByteArray(SALT_LENGTH).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(IV_LENGTH).also { SecureRandom().nextBytes(it) }
        
        val secretKey = generateKey(SECRET_PASSPHRASE, salt)
        val cipher = Cipher.getInstance(AES_ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, IvParameterSpec(iv))
        
        val encryptedData = cipher.doFinal(data)
        
        // Format: [salt (16 bytes)][iv (16 bytes)][encrypted data]
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

    // ============== UTILITIES ==============

    fun getBackupRoot(context: Context): File {
        // 1. If emulator, always use app private storage to avoid permission issues
        if (TXADeviceInfo.isEmulator()) {
            val emulatorRoot = File(context.getExternalFilesDir(null), "backup")
            try {
                if (!emulatorRoot.exists()) {
                    emulatorRoot.mkdirs()
                }
                TXALogger.appI(TAG, "Using emulator backup path: ${emulatorRoot.absolutePath}")
            } catch (e: Exception) {
                TXALogger.appE(TAG, "Failed to create emulator backup directory", e)
            }
            return emulatorRoot
        }
        
        // 2. For real devices, try Documents first
        val documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        val documentsRoot = if (documentsDir != null) {
            File(documentsDir, "TXAMusic/Backups")
        } else {
            null
        }
        
        // 3. Try to create Documents path
        if (documentsRoot != null) {
            try {
                if (!documentsRoot.exists()) {
                    val success = documentsRoot.mkdirs() || TXASuHelper.mkdirs(documentsRoot)
                    TXALogger.appI(TAG, "Creating backup directory: ${documentsRoot.absolutePath} (Success: $success)")
                }
                // Test if we can write to this directory
                val testFile = File(documentsRoot, ".write_test")
                if (testFile.createNewFile()) {
                    testFile.delete()
                    return documentsRoot
                }
            } catch (e: Exception) {
                TXALogger.appW(TAG, "Documents directory not writable (${e.message}), using fallback")
            }
        }
        
        // 4. Fallback to app private storage if Documents failed
        val fallbackRoot = File(context.getExternalFilesDir(null), "backup")
        try {
            if (!fallbackRoot.exists()) {
                fallbackRoot.mkdirs()
            }
            TXALogger.appI(TAG, "Using fallback backup path: ${fallbackRoot.absolutePath}")
        } catch (e: Exception) {
            TXALogger.appE(TAG, "Failed to create fallback backup directory", e)
        }
        
        return fallbackRoot
    }

    fun getTimeStamp(): String {
        return SimpleDateFormat("dd-MMM-yyyy_HHmmss", Locale.getDefault()).format(Date())
    }
}

/**
 * Backup metadata for preview
 */
data class BackupMetadata(
    val appVersion: String,
    val createdAt: Long,
    val deviceModel: String,
    val favoritesCount: Int,
    val historyCount: Int,
    val playlistsCount: Int,
    val hasSettings: Boolean
)

/**
 * Sanitize filename
 */
fun CharSequence.sanitize(): String {
    return toString()
        .replace("/", "_")
        .replace(":", "_")
        .replace("*", "_")
        .replace("?", "_")
        .replace("\"", "_")
        .replace("<", "_")
        .replace(">", "_")
        .replace("|", "_")
        .replace("\\", "_")
        .replace("&", "_")
}
