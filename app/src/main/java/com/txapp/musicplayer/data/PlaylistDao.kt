package com.txapp.musicplayer.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Playlist Entity - Lưu trữ trong Room Database thay vì MediaStore
 * vì MediaStore.Audio.Playlists đã bị deprecated trên Android 10+
 */
@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Bảng liên kết giữa Playlist và Song
 */
@Entity(
    tableName = "playlist_songs",
    primaryKeys = ["playlistId", "songId"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class PlaylistSongCrossRef(
    val playlistId: Long,
    val songId: Long,
    val addedAt: Long = System.currentTimeMillis(),
    val sortOrder: Int = 0
)

@Dao
interface PlaylistDao {
    // Get all playlists
    @Query("SELECT * FROM playlists ORDER BY updatedAt DESC")
    fun getAllPlaylists(): Flow<List<PlaylistEntity>>

    // Get playlist by id
    @Query("SELECT * FROM playlists WHERE id = :playlistId")
    suspend fun getPlaylistById(playlistId: Long): PlaylistEntity?
    
    @Query("SELECT * FROM playlists WHERE name = :name LIMIT 1")
    suspend fun getPlaylistByNameSync(name: String): PlaylistEntity?

    // Create playlist
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    // Update playlist name
    @Query("UPDATE playlists SET name = :name, updatedAt = :updatedAt WHERE id = :playlistId")
    suspend fun updatePlaylistName(playlistId: Long, name: String, updatedAt: Long = System.currentTimeMillis())

    // Delete playlist
    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: Long)

    // Get song count for playlist
    @Query("SELECT COUNT(*) FROM playlist_songs WHERE playlistId = :playlistId")
    suspend fun getPlaylistSongCount(playlistId: Long): Int

    // Get all song IDs in playlist
    @Query("SELECT songId FROM playlist_songs WHERE playlistId = :playlistId ORDER BY sortOrder")
    suspend fun getPlaylistSongIds(playlistId: Long): List<Long>

    // Add song to playlist
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addSongToPlaylist(crossRef: PlaylistSongCrossRef)

    // Remove song from playlist
    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun removeSongFromPlaylist(playlistId: Long, songId: Long)

    // Check if song is in playlist
    @Query("SELECT EXISTS(SELECT 1 FROM playlist_songs WHERE playlistId = :playlistId AND songId = :songId)")
    suspend fun isSongInPlaylist(playlistId: Long, songId: Long): Boolean

    // Get playlists containing song
    @Query("SELECT playlistId FROM playlist_songs WHERE songId = :songId")
    fun getPlaylistsContainingSong(songId: Long): Flow<List<Long>>

    // Get playlists with song count
    @Query("""
        SELECT p.id, p.name, p.createdAt, p.updatedAt, COUNT(ps.songId) as songCount
        FROM playlists p
        LEFT JOIN playlist_songs ps ON p.id = ps.playlistId
        GROUP BY p.id
        ORDER BY p.updatedAt DESC
    """)
    fun getPlaylistsWithSongCount(): Flow<List<PlaylistWithCount>>
    
    /**
     * Rename playlist (convenience method)
     */
    suspend fun renamePlaylist(playlistId: Long, newName: String) {
        updatePlaylistName(playlistId, newName, System.currentTimeMillis())
    }
    
    // ============= ANDROID AUTO SUPPORT =============
    
    @Query("""
        SELECT p.id, p.name, p.createdAt, p.updatedAt, COUNT(ps.songId) as songCount
        FROM playlists p
        LEFT JOIN playlist_songs ps ON p.id = ps.playlistId
        GROUP BY p.id
        ORDER BY p.updatedAt DESC
    """)
    suspend fun getPlaylistsWithSongCountOnce(): List<PlaylistWithCount>
}

/**
 * Data class cho playlist + song count
 */
data class PlaylistWithCount(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val songCount: Int
)
