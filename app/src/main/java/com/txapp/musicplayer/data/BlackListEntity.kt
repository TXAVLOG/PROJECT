package com.txapp.musicplayer.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity representing a blacklisted folder path
 * Songs in these folders will be excluded from library
 */
@Entity(tableName = "blacklist")
data class BlackListEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    /**
     * Absolute path to the blacklisted folder
     */
    val path: String,
    
    /**
     * Timestamp when this folder was blacklisted
     */
    val addedAt: Long = System.currentTimeMillis()
)
