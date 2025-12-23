package ms.txams.vv.core.data.database.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import ms.txams.vv.core.data.database.entity.TXASongEntity

/**
 * Data Access Object for TXASongEntity
 * Cung cấp các phương thức CRUD và query phức tạp cho songs
 */
@Dao
interface TXASongDao {

    // Basic CRUD operations
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSong(song: TXASongEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSongs(songs: List<TXASongEntity>): List<Long>

    @Update
    suspend fun updateSong(song: TXASongEntity)

    @Delete
    suspend fun deleteSong(song: TXASongEntity)

    @Query("DELETE FROM songs WHERE id = :id")
    suspend fun deleteSongById(id: Long)

    @Query("DELETE FROM songs")
    suspend fun deleteAllSongs()

    // Query operations
    @Query("SELECT * FROM songs ORDER BY title ASC")
    fun getAllSongs(): Flow<List<TXASongEntity>>

    @Query("SELECT * FROM songs WHERE id = :id")
    suspend fun getSongById(id: Long): TXASongEntity?

    @Query("SELECT * FROM songs WHERE file_path = :filePath")
    suspend fun getSongByFilePath(filePath: String): TXASongEntity?

    @Query("SELECT * FROM songs WHERE is_available = 1 ORDER BY title ASC")
    fun getAvailableSongs(): Flow<List<TXASongEntity>>

    // Search operations
    @Query("""
        SELECT * FROM songs 
        WHERE (title LIKE '%' || :query || '%' 
        OR artist LIKE '%' || :query || '%' 
        OR album LIKE '%' || :query || '%')
        AND is_available = 1
        ORDER BY 
            CASE WHEN title LIKE :query || '%' THEN 1 ELSE 2 END,
            CASE WHEN artist LIKE :query || '%' THEN 1 ELSE 2 END,
            title ASC
        LIMIT :limit
    """)
    suspend fun searchSongs(query: String, limit: Int = 50): List<TXASongEntity>

    @Query("""
        SELECT * FROM songs 
        WHERE artist = :artist AND is_available = 1 
        ORDER BY album, track_number, title ASC
    """)
    fun getSongsByArtist(artist: String): Flow<List<TXASongEntity>>

    @Query("""
        SELECT * FROM songs 
        WHERE album = :album AND artist = :artist AND is_available = 1 
        ORDER BY track_number, title ASC
    """)
    fun getSongsByAlbum(album: String, artist: String): Flow<List<TXASongEntity>>

    @Query("SELECT * FROM songs WHERE genre = :genre AND is_available = 1 ORDER BY title ASC")
    fun getSongsByGenre(genre: String): Flow<List<TXASongEntity>>

    // Favorites and statistics
    @Query("SELECT * FROM songs WHERE favorite = 1 AND is_available = 1 ORDER BY last_played DESC")
    fun getFavoriteSongs(): Flow<List<TXASongEntity>>

    @Query("SELECT * FROM songs WHERE play_count > 0 AND is_available = 1 ORDER BY play_count DESC, last_played DESC")
    fun getMostPlayedSongs(): Flow<List<TXASongEntity>>

    @Query("SELECT * FROM songs WHERE last_played IS NOT NULL AND is_available = 1 ORDER BY last_played DESC LIMIT :limit")
    fun getRecentlyPlayedSongs(limit: Int = 20): Flow<List<TXASongEntity>>

    @Query("SELECT * FROM songs ORDER BY date_added DESC LIMIT :limit")
    fun getRecentlyAddedSongs(limit: Int = 20): Flow<List<TXASongEntity>>

    // Branded songs
    @Query("SELECT * FROM songs WHERE is_branded = 1 AND is_available = 1 ORDER BY branding_timestamp DESC")
    fun getBrandedSongs(): Flow<List<TXASongEntity>>

    // Statistics and analytics
    @Query("SELECT COUNT(*) FROM songs WHERE is_available = 1")
    suspend fun getAvailableSongCount(): Int

    @Query("SELECT COUNT(*) FROM songs WHERE favorite = 1 AND is_available = 1")
    suspend fun getFavoriteSongCount(): Int

    @Query("SELECT SUM(duration) FROM songs WHERE is_available = 1")
    suspend fun getTotalDuration(): Long?

    @Query("SELECT AVG(duration) FROM songs WHERE is_available = 1")
    suspend fun getAverageDuration(): Long?

    // Batch operations
    @Query("UPDATE songs SET is_available = 0 WHERE file_path IN (:filePaths)")
    suspend fun markSongsUnavailable(filePaths: List<String>)

    @Query("UPDATE songs SET is_available = 1 WHERE file_path IN (:filePaths)")
    suspend fun markSongsAvailable(filePaths: List<String>)

    @Query("UPDATE songs SET play_count = play_count + 1, last_played = :timestamp WHERE id = :id")
    suspend fun incrementPlayCount(id: Long, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE songs SET skip_count = skip_count + 1 WHERE id = :id")
    suspend fun incrementSkipCount(id: Long)

    @Query("UPDATE songs SET favorite = :favorite WHERE id = :id")
    suspend fun updateFavorite(id: Long, favorite: Boolean)

    // Integrity and maintenance
    @Query("SELECT * FROM songs WHERE checksum IS NULL OR checksum != :expectedChecksum")
    suspend fun getSongsWithInvalidChecksum(expectedChecksum: String): List<TXASongEntity>

    @Query("DELETE FROM songs WHERE is_available = 0 AND last_played IS NULL AND date_added < :threshold")
    suspend fun cleanupUnavailableSongs(threshold: Long): Int

    // Advanced queries for UI
    @Query("""
        SELECT DISTINCT artist FROM songs 
        WHERE is_available = 1 AND artist != '' 
        ORDER BY artist ASC
    """)
    fun getAllArtists(): Flow<List<String>>

    @Query("""
        SELECT DISTINCT album FROM songs 
        WHERE is_available = 1 AND album != '' 
        ORDER BY album ASC
    """)
    fun getAllAlbums(): Flow<List<String>>

    @Query("""
        SELECT DISTINCT genre FROM songs 
        WHERE is_available = 1 AND genre IS NOT NULL AND genre != '' 
        ORDER BY genre ASC
    """)
    fun getAllGenres(): Flow<List<String>>

    // Random selection for shuffle
    @Query("SELECT * FROM songs WHERE is_available = 1 ORDER BY RANDOM() LIMIT :limit")
    suspend fun getRandomSongs(limit: Int): List<TXASongEntity>

    // Pagination for large datasets
    @Query("SELECT * FROM songs WHERE is_available = 1 ORDER BY title ASC LIMIT :limit OFFSET :offset")
    suspend fun getSongsPaginated(limit: Int, offset: Int): List<TXASongEntity>
}
