package com.txapp.musicplayer.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * DAO for managing blacklisted folders
 */
@Dao
interface BlackListDao {
    
    /**
     * Get all blacklisted paths as Flow for reactive updates
     */
    @Query("SELECT * FROM blacklist ORDER BY addedAt DESC")
    fun getAllBlacklistPaths(): Flow<List<BlackListEntity>>
    
    /**
     * Get all blacklisted paths (one-shot)
     */
    @Query("SELECT * FROM blacklist ORDER BY addedAt DESC")
    suspend fun getAllBlacklistPathsOnce(): List<BlackListEntity>
    
    /**
     * Get just the path strings for filtering
     */
    @Query("SELECT path FROM blacklist")
    suspend fun getBlacklistPathStrings(): List<String>
    
    /**
     * Check if a path is blacklisted
     */
    @Query("SELECT COUNT(*) > 0 FROM blacklist WHERE path = :path")
    suspend fun isPathBlacklisted(path: String): Boolean
    
    /**
     * Add a new blacklisted path
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addBlacklistPath(entity: BlackListEntity): Long
    
    /**
     * Add multiple blacklisted paths
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addBlacklistPaths(entities: List<BlackListEntity>)
    
    /**
     * Remove a blacklisted path
     */
    @Delete
    suspend fun removeBlacklistPath(entity: BlackListEntity)
    
    /**
     * Remove blacklist by path string
     */
    @Query("DELETE FROM blacklist WHERE path = :path")
    suspend fun removeBlacklistByPath(path: String)
    
    /**
     * Remove multiple blacklisted paths
     */
    @Query("DELETE FROM blacklist WHERE id IN (:ids)")
    suspend fun removeBlacklistByIds(ids: List<Long>)
    
    /**
     * Clear all blacklisted paths
     */
    @Query("DELETE FROM blacklist")
    suspend fun clearAll()
}
