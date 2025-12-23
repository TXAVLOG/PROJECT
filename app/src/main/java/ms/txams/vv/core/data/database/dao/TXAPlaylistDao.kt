package ms.txams.vv.core.data.database.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import ms.txams.vv.core.data.database.entity.TXAPlaylistEntity
import ms.txams.vv.core.data.database.entity.TXAPlaylistSongEntity

/**
 * Data Access Object for TXAPlaylistEntity and TXAPlaylistSongEntity
 * Cung cấp các phương thức CRUD và query phức tạp cho playlists
 */
@Dao
interface TXAPlaylistDao {

    // Playlist CRUD operations
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: TXAPlaylistEntity): Long

    @Update
    suspend fun updatePlaylist(playlist: TXAPlaylistEntity)

    @Delete
    suspend fun deletePlaylist(playlist: TXAPlaylistEntity)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deletePlaylistById(id: Long)

    @Query("SELECT * FROM playlists ORDER BY is_system DESC, name ASC")
    fun getAllPlaylists(): Flow<List<TXAPlaylistEntity>>

    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun getPlaylistById(id: Long): TXAPlaylistEntity?

    @Query("SELECT * FROM playlists WHERE name = :name LIMIT 1")
    suspend fun getPlaylistByName(name: String): TXAPlaylistEntity?

    @Query("SELECT * FROM playlists WHERE is_system = 1 ORDER BY name ASC")
    fun getSystemPlaylists(): Flow<List<TXAPlaylistEntity>>

    @Query("SELECT * FROM playlists WHERE is_system = 0 ORDER BY name ASC")
    fun getUserPlaylists(): Flow<List<TXAPlaylistEntity>>

    @Query("SELECT * FROM playlists WHERE favorite = 1 ORDER BY name ASC")
    fun getFavoritePlaylists(): Flow<List<TXAPlaylistEntity>>

    // Playlist-Song relationship operations
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addSongToPlaylist(playlistSong: TXAPlaylistSongEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addSongsToPlaylist(playlistSongs: List<TXAPlaylistSongEntity>)

    @Delete
    suspend fun removeSongFromPlaylist(playlistSong: TXAPlaylistSongEntity)

    @Query("DELETE FROM playlist_songs WHERE playlist_id = :playlistId AND song_id = :songId")
    suspend fun removeSongFromPlaylist(playlistId: Long, songId: Long)

    @Query("DELETE FROM playlist_songs WHERE playlist_id = :playlistId")
    suspend fun clearPlaylist(playlistId: Long)

    @Query("DELETE FROM playlist_songs WHERE song_id = :songId")
    suspend fun removeSongFromAllPlaylists(songId: Long)

    // Get songs in playlists
    @Query("""
        SELECT s.* FROM songs s 
        INNER JOIN playlist_songs ps ON s.id = ps.song_id 
        WHERE ps.playlist_id = :playlistId 
        ORDER BY ps.position ASC
    """)
    fun getSongsInPlaylist(playlistId: Long): Flow<List<ms.txams.vv.core.data.database.entity.TXASongEntity>>

    @Query("""
        SELECT ps.* FROM playlist_songs ps 
        WHERE ps.playlist_id = :playlistId 
        ORDER BY ps.position ASC
    """)
    fun getPlaylistSongs(playlistId: Long): Flow<List<TXAPlaylistSongEntity>>

    @Query("SELECT COUNT(*) FROM playlist_songs WHERE playlist_id = :playlistId")
    suspend fun getPlaylistSongCount(playlistId: Long): Int

    @Query("SELECT EXISTS(SELECT 1 FROM playlist_songs WHERE playlist_id = :playlistId AND song_id = :songId)")
    suspend fun isSongInPlaylist(playlistId: Long, songId: Long): Boolean

    // Playlist management operations
    @Query("""
        UPDATE playlists 
        SET song_count = (
            SELECT COUNT(*) FROM playlist_songs WHERE playlist_id = :playlistId
        ),
        modified_at = :timestamp
        WHERE id = :playlistId
    """)
    suspend fun updatePlaylistSongCount(playlistId: Long, timestamp: Long = System.currentTimeMillis())

    @Query("""
        UPDATE playlists 
        SET total_duration = (
            SELECT COALESCE(SUM(s.duration), 0) 
            FROM songs s 
            INNER JOIN playlist_songs ps ON s.id = ps.song_id 
            WHERE ps.playlist_id = :playlistId
        ),
        modified_at = :timestamp
        WHERE id = :playlistId
    """)
    suspend fun updatePlaylistDuration(playlistId: Long, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE playlists SET play_count = play_count + 1, last_played = :timestamp WHERE id = :id")
    suspend fun incrementPlaylistPlayCount(id: Long, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE playlists SET favorite = :favorite WHERE id = :id")
    suspend fun updatePlaylistFavorite(id: Long, favorite: Boolean)

    // Position management for drag & drop
    @Query("UPDATE playlist_songs SET position = position + 1 WHERE playlist_id = :playlistId AND position >= :position")
    suspend fun shiftPlaylistSongsDown(playlistId: Long, position: Int)

    @Query("UPDATE playlist_songs SET position = position - 1 WHERE playlist_id = :playlistId AND position > :position")
    suspend fun shiftPlaylistSongsUp(playlistId: Long, position: Int)

    @Query("UPDATE playlist_songs SET position = :newPosition WHERE playlist_id = :playlistId AND song_id = :songId")
    suspend fun updateSongPosition(playlistId: Long, songId: Long, newPosition: Int)

    // Reorder entire playlist
    @Transaction
    suspend fun reorderPlaylist(playlistId: Long, songIds: List<Long>) {
        // Clear existing songs
        clearPlaylist(playlistId)
        
        // Add songs in new order
        val playlistSongs = songIds.mapIndexed { index, songId ->
            TXAPlaylistSongEntity(
                playlistId = playlistId,
                songId = songId,
                position = index
            )
        }
        addSongsToPlaylist(playlistSongs)
        updatePlaylistSongCount(playlistId)
    }

    // Smart playlist operations
    @Query("""
        SELECT * FROM playlists 
        WHERE name LIKE '%Favorites%' AND is_system = 1 
        LIMIT 1
    """)
    suspend fun getFavoritesPlaylist(): TXAPlaylistEntity?

    @Query("""
        SELECT * FROM playlists 
        WHERE name LIKE '%Recently Played%' AND is_system = 1 
        LIMIT 1
    """)
    suspend fun getRecentlyPlayedPlaylist(): TXAPlaylistEntity?

    @Query("""
        SELECT * FROM playlists 
        WHERE name LIKE '%Most Played%' AND is_system = 1 
        LIMIT 1
    """)
    suspend fun getMostPlayedPlaylist(): TXAPlaylistEntity?

    // Analytics and statistics
    @Query("SELECT COUNT(*) FROM playlists WHERE is_system = 0")
    suspend fun getUserPlaylistCount(): Int

    @Query("SELECT SUM(song_count) FROM playlists")
    suspend fun getTotalSongsInPlaylists(): Int

    @Query("SELECT AVG(song_count) FROM playlists WHERE song_count > 0")
    suspend fun getAveragePlaylistSize(): Double?

    // Search and filter
    @Query("""
        SELECT * FROM playlists 
        WHERE (name LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%')
        ORDER BY is_system DESC, name ASC
    """)
    suspend fun searchPlaylists(query: String): List<TXAPlaylistEntity>

    // Batch operations
    @Query("UPDATE playlists SET modified_at = :timestamp WHERE id IN (:playlistIds)")
    suspend fun updatePlaylistsModified(playlistIds: List<Long>, timestamp: Long = System.currentTimeMillis())

    // Cleanup operations
    @Query("DELETE FROM playlists WHERE is_system = 0 AND song_count = 0 AND created_at < :threshold")
    suspend fun cleanupEmptyPlaylists(threshold: Long): Int

    // Transaction for complex operations
    @Transaction
    suspend fun createPlaylistWithSongs(
        playlist: TXAPlaylistEntity,
        songIds: List<Long>
    ): Long {
        val playlistId = insertPlaylist(playlist)
        
        val playlistSongs = songIds.mapIndexed { index, songId ->
            TXAPlaylistSongEntity(
                playlistId = playlistId,
                songId = songId,
                position = index
            )
        }
        
        addSongsToPlaylist(playlistSongs)
        updatePlaylistSongCount(playlistId)
        updatePlaylistDuration(playlistId)
        
        return playlistId
    }
}
