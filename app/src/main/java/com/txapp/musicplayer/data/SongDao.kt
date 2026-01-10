package com.txapp.musicplayer.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.txapp.musicplayer.model.Song
import kotlinx.coroutines.flow.Flow

@Dao
interface SongDao {
    @Query("SELECT * FROM songs ORDER BY dateAdded DESC")
    fun getAllSongs(): Flow<List<Song>>

    @Query("SELECT * FROM songs WHERE playCount >= 2 ORDER BY playCount DESC LIMIT 50")
    fun getTopTracks(): Flow<List<Song>>

    @Query("SELECT * FROM songs WHERE id = :songId")
    suspend fun getSongById(songId: Long): Song?

    @Query("SELECT * FROM songs WHERE id IN (:songIds)")
    suspend fun getSongsByIds(songIds: LongArray): List<Song>

    @Query("SELECT * FROM songs WHERE data = :path")
    suspend fun getSongByPath(path: String): Song?

    @Query("SELECT * FROM songs WHERE title = :title AND artist = :artist LIMIT 1")
    suspend fun getSongByMetadata(title: String, artist: String): Song?

    @Query("SELECT * FROM songs WHERE title = :title AND artist = :artist AND ABS(duration - :duration) < 5000 LIMIT 1")
    suspend fun getSongBySmartMetadata(title: String, artist: String, duration: Long): Song?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSongs(songs: List<Song>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSong(song: Song)
    
    @Query("SELECT id, data, isFavorite, playCount, lastPlayed, isManual FROM songs WHERE id IN (:ids)")
    suspend fun getExistingMetadataByIds(ids: List<Long>): List<SongMetadata>
    
    @Query("SELECT id, data, isFavorite, playCount, lastPlayed, isManual FROM songs")
    suspend fun getAllMetadata(): List<SongMetadata>
    
    /**
     * Normalize path for comparison (handles case-insensitivity on Windows)
     */
    private fun normalizePath(path: String): String {
        return path.trim().lowercase().replace("\\", "/")
    }
    
    /**
     * Upsert songs while preserving user data (favorite, playCount, lastPlayed)
     * Matches by ID first, then by normalized Path.
     * Prevents duplicates by deleting ALL old records with same path before insert.
     */
    @androidx.room.Transaction
    suspend fun upsertSongsPreservingUserData(songs: List<Song>) {
        if (songs.isEmpty()) return
        
        // Match by both ID and Paths for robustness
        val allMetadata = getAllMetadata()
        val metadataById = allMetadata.associateBy { it.id }
        // Group by NORMALIZED path to handle case variations and duplicates
        val metadataByNormalizedPath = allMetadata.groupBy { normalizePath(it.data) }
        
        val idsToRemoveIfColliding = mutableSetOf<Long>()
        
        // Merge: preserve user data from existing records
        val mergedSongs = songs.map { song ->
            val normalizedPath = normalizePath(song.data)
            
            // Try to find existing record by ID
            val existingById = metadataById[song.id]
            // Find ALL records with same normalized path
            val existingsByPath = metadataByNormalizedPath[normalizedPath] ?: emptyList()
            
            // Pick the best existing record to preserve user data from
            val existing = existingById ?: existingsByPath.firstOrNull()
            
            // Mark ALL records with same path (but different ID) for deletion to prevent duplicates
            existingsByPath.filter { it.id != song.id }.forEach { dup ->
                idsToRemoveIfColliding.add(dup.id)
            }
            
            if (existing != null) {
                song.copy(
                    isFavorite = existing.isFavorite,
                    playCount = existing.playCount,
                    lastPlayed = existing.lastPlayed,
                    // Preserve manual status if any existing record was manual
                    isManual = existingById?.isManual ?: existingsByPath.any { it.isManual }
                )
            } else {
                song
            }
        }
        
        // Delete ALL duplicate records BEFORE inserting to guarantee uniqueness
        if (idsToRemoveIfColliding.isNotEmpty()) {
            deleteSongsByIds(idsToRemoveIfColliding.toList())
        }
        
        insertSongs(mergedSongs)
    }

    @Query("UPDATE songs SET playCount = playCount + 1 WHERE id = :songId")
    suspend fun incrementPlayCount(songId: Long)

    @Query("UPDATE songs SET isFavorite = NOT isFavorite WHERE id = :songId")
    suspend fun toggleFavorite(songId: Long)

    @Query("UPDATE songs SET isFavorite = :isFavorite WHERE id = :songId")
    suspend fun setFavorite(songId: Long, isFavorite: Boolean)

    @Query("UPDATE songs SET isFavorite = :isFavorite WHERE id IN (:songIds)")
    suspend fun setFavorites(songIds: List<Long>, isFavorite: Boolean)

    @Query("SELECT * FROM songs WHERE isFavorite = 1 ORDER BY dateAdded DESC")
    fun getFavoriteSongs(): Flow<List<Song>>

    @Query("SELECT * FROM songs WHERE lastPlayed > 0 ORDER BY lastPlayed DESC LIMIT 20")
    fun getRecentlyPlayed(): Flow<List<Song>>

    @Query("UPDATE songs SET lastPlayed = :timestamp WHERE id = :songId")
    suspend fun updateLastPlayed(songId: Long, timestamp: Long)

    @Query("SELECT * FROM songs ORDER BY RANDOM() LIMIT :limit")
    fun getRandomSongs(limit: Int): Flow<List<Song>>

    @Query("DELETE FROM songs WHERE id = :songId")
    suspend fun deleteById(songId: Long)

    // Helper queries for safe syncing
    @Query("SELECT id FROM songs")
    suspend fun getAllSongIds(): List<Long>

    @Query("DELETE FROM songs WHERE id IN (:ids)")
    suspend fun deleteSongsByIds(ids: List<Long>)

    @Query("DELETE FROM songs")
    suspend fun deleteAll()
    
    // ============= ANDROID AUTO SUPPORT =============
    
    @Query("SELECT * FROM songs ORDER BY dateAdded DESC")
    suspend fun getAllSongsOnce(): List<Song>
    
    @Query("SELECT * FROM songs WHERE isFavorite = 1 ORDER BY dateAdded DESC")
    suspend fun getFavoriteSongsOnce(): List<Song>
    
    @Query("SELECT * FROM songs WHERE lastPlayed > 0 ORDER BY lastPlayed DESC LIMIT :limit")
    suspend fun getRecentlyPlayedOnce(limit: Int): List<Song>
    
    @Query("SELECT * FROM songs WHERE playCount >= 2 ORDER BY playCount DESC LIMIT :limit")
    suspend fun getTopTracksOnce(limit: Int): List<Song>
    
    @Query("SELECT * FROM songs WHERE title LIKE :query OR artist LIKE :query ORDER BY playCount DESC LIMIT 50")
    suspend fun searchSongs(query: String): List<Song>
    
    @Query("SELECT * FROM songs WHERE albumId = :albumId ORDER BY title ASC")
    suspend fun getSongsByAlbum(albumId: Long): List<Song>
    
    @Query("SELECT * FROM songs WHERE artist = :artistName ORDER BY title ASC")
    suspend fun getSongsByArtist(artistName: String): List<Song>
    
    // ============= TAG EDITOR SUPPORT =============
    
    @Query("""
        UPDATE songs SET 
            title = :title,
            artist = :artist,
            album = :album,
            albumArtist = :albumArtist,
            composer = :composer,
            year = :year,
            trackNumber = :trackNumber,
            dateModified = :dateModified
        WHERE id = :songId
    """)
    suspend fun updateSongMetadata(
        songId: Long,
        title: String,
        artist: String,
        album: String,
        albumArtist: String?,
        composer: String?,
        year: Int,
        trackNumber: Int,
        dateModified: Long
    )

    
    @Query("SELECT id, dateModified, isManual, data FROM songs")
    suspend fun getAllSongSyncInfo(): List<SongSyncInfo>
}

data class SongSyncInfo(
    val id: Long, 
    val dateModified: Long,
    val isManual: Boolean,
    val data: String
)
