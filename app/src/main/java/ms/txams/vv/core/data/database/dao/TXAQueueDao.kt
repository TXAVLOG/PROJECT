package ms.txams.vv.core.data.database.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import ms.txams.vv.core.data.database.entity.TXAQueueEntity
import ms.txams.vv.core.data.database.entity.TXAQueueHistoryEntity

/**
 * Data Access Object for TXAQueueEntity and TXAQueueHistoryEntity
 * Cung cấp các phương thức CRUD và query phức tạp cho queue management
 */
@Dao
interface TXAQueueDao {

    // Queue CRUD operations
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQueueItem(queueItem: TXAQueueEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQueueItems(queueItems: List<TXAQueueEntity>): List<Long>

    @Update
    suspend fun updateQueueItem(queueItem: TXAQueueEntity)

    @Delete
    suspend fun deleteQueueItem(queueItem: TXAQueueEntity)

    @Query("DELETE FROM queue WHERE id = :id")
    suspend fun deleteQueueItemById(id: Long)

    @Query("DELETE FROM queue WHERE queue_name = :queueName")
    suspend fun clearQueue(queueName: String = "default"): Int

    // Queue retrieval operations
    @Query("SELECT * FROM queue WHERE queue_name = :queueName ORDER BY position ASC")
    fun getQueue(queueName: String = "default"): Flow<List<TXAQueueEntity>>

    @Query("SELECT * FROM queue WHERE queue_name = :queueName ORDER BY position ASC")
    suspend fun getQueueSnapshot(queueName: String = "default"): List<TXAQueueEntity>

    @Query("SELECT * FROM queue WHERE is_current = 1 AND queue_name = :queueName LIMIT 1")
    suspend fun getCurrentQueueItem(queueName: String = "default"): TXAQueueEntity?

    @Query("SELECT * FROM queue WHERE id = :id")
    suspend fun getQueueItemById(id: Long): TXAQueueEntity?

    @Query("SELECT COUNT(*) FROM queue WHERE queue_name = :queueName")
    suspend fun getQueueSize(queueName: String = "default"): Int

    // Current track management
    @Query("UPDATE queue SET is_current = 0 WHERE queue_name = :queueName")
    suspend fun clearCurrentTrack(queueName: String = "default")

    @Query("UPDATE queue SET is_current = 1 WHERE id = :id AND queue_name = :queueName")
    suspend fun setCurrentTrack(id: Long, queueName: String = "default")

    @Query("UPDATE queue SET is_current = 1, position = :newPosition WHERE id = :id AND queue_name = :queueName")
    suspend fun setCurrentTrackWithPosition(id: Long, newPosition: Int, queueName: String = "default")

    // Position management
    @Query("UPDATE queue SET position = position + 1 WHERE queue_name = :queueName AND position >= :position")
    suspend fun shiftQueueItemsDown(position: Int, queueName: String = "default")

    @Query("UPDATE queue SET position = position - 1 WHERE queue_name = :queueName AND position > :position")
    suspend fun shiftQueueItemsUp(position: Int, queueName: String = "default")

    @Query("UPDATE queue SET position = :newPosition WHERE id = :id AND queue_name = :queueName")
    suspend fun updateQueueItemPosition(id: Long, newPosition: Int, queueName: String = "default")

    // Queue manipulation operations
    @Transaction
    suspend fun addToQueue(songId: Long, position: Int? = null, queueName: String = "default"): Long {
        clearCurrentTrack(queueName)
        
        val actualPosition = position ?: getQueueSize(queueName)
        
        shiftQueueItemsDown(actualPosition, queueName)
        
        val queueItem = TXAQueueEntity(
            songId = songId,
            position = actualPosition,
            queueName = queueName,
            isCurrent = actualPosition == 0
        )
        
        return insertQueueItem(queueItem)
    }

    @Transaction
    suspend fun addToQueueNext(songId: Long, queueName: String = "default"): Long {
        val currentTrack = getCurrentQueueItem(queueName)
        val position = if (currentTrack != null) currentTrack.position + 1 else 0
        return addToQueue(songId, position, queueName)
    }

    @Transaction
    suspend fun removeFromQueue(id: Long, queueName: String = "default") {
        val itemToRemove = getQueueItemById(id)
        if (itemToRemove != null) {
            deleteQueueItem(itemToRemove)
            shiftQueueItemsUp(itemToRemove.position, queueName)
        }
    }

    @Transaction
    suspend fun moveToNext(currentId: Long, queueName: String = "default"): TXAQueueEntity? {
        val current = getQueueItemById(currentId)
        if (current == null) return null
        
        val nextPosition = current.position + 1
        val nextItems = getQueueSnapshot(queueName).filter { it.position == nextPosition }
        
        if (nextItems.isNotEmpty()) {
            val next = nextItems.first()
            clearCurrentTrack(queueName)
            setCurrentTrack(next.id, queueName)
            return next
        }
        
        return null
    }

    @Transaction
    suspend fun moveToPrevious(currentId: Long, queueName: String = "default"): TXAQueueEntity? {
        val current = getQueueItemById(currentId)
        if (current == null) return null
        
        val previousPosition = current.position - 1
        if (previousPosition < 0) return null
        
        val previousItems = getQueueSnapshot(queueName).filter { it.position == previousPosition }
        
        if (previousItems.isNotEmpty()) {
            val previous = previousItems.first()
            clearCurrentTrack(queueName)
            setCurrentTrack(previous.id, queueName)
            return previous
        }
        
        return null
    }

    // Shuffle operations
    @Transaction
    suspend fun shuffleQueue(queueName: String = "default") {
        val queueItems = getQueueSnapshot(queueName).toMutableList()
        val currentTrack = queueItems.find { it.isCurrent }
        
        // Fisher-Yates shuffle algorithm
        queueItems.shuffle()
        
        // Clear and reinsert in new order
        clearQueue(queueName)
        
        val shuffledItems = queueItems.mapIndexed { index, item ->
            item.copy(
                position = index,
                isCurrent = (currentTrack?.id == item.id)
            )
        }
        
        insertQueueItems(shuffledItems)
    }

    // Playback state management
    @Query("UPDATE queue SET playback_state = :state WHERE id = :id")
    suspend fun updatePlaybackState(id: Long, state: Int)

    @Query("UPDATE queue SET last_played_position = :position WHERE id = :id")
    suspend fun updateLastPlayedPosition(id: Long, position: Long)

    @Query("UPDATE queue SET play_count_in_queue = play_count_in_queue + 1 WHERE id = :id")
    suspend fun incrementQueuePlayCount(id: Long)

    @Query("UPDATE queue SET skip_count_in_queue = skip_count_in_queue + 1 WHERE id = :id")
    suspend fun incrementQueueSkipCount(id: Long)

    // Audio adjustments
    @Query("UPDATE queue SET volume_adjustment = :volume WHERE id = :id")
    suspend fun updateVolumeAdjustment(id: Long, volume: Float)

    @Query("UPDATE queue SET pitch_adjustment = :pitch WHERE id = :id")
    suspend fun updatePitchAdjustment(id: Long, pitch: Float)

    @Query("UPDATE queue SET speed_adjustment = :speed WHERE id = :id")
    suspend fun updateSpeedAdjustment(id: Long, speed: Float)

    @Query("UPDATE queue SET crossfade_duration = :duration WHERE id = :id")
    suspend fun updateCrossfadeDuration(id: Long, duration: Int)

    // Branding management
    @Query("UPDATE queue SET is_branded = :branded WHERE id = :id")
    suspend fun updateBrandingStatus(id: Long, branded: Boolean)

    @Query("SELECT * FROM queue WHERE is_branded = 1 AND queue_name = :queueName ORDER BY position ASC")
    fun getBrandedQueueItems(queueName: String = "default"): Flow<List<TXAQueueEntity>>

    // Queue history operations
    @Insert
    suspend fun insertQueueHistory(history: TXAQueueHistoryEntity)

    @Insert
    suspend fun insertQueueHistoryBatch(history: List<TXAQueueHistoryEntity>)

    @Query("SELECT * FROM queue_history WHERE session_id = :sessionId ORDER BY timestamp ASC")
    fun getQueueHistory(sessionId: String): Flow<List<TXAQueueHistoryEntity>>

    @Query("SELECT * FROM queue_history WHERE song_id = :songId ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getSongHistory(songId: Long, limit: Int = 10): List<TXAQueueHistoryEntity>

    @Query("DELETE FROM queue_history WHERE timestamp < :threshold")
    suspend fun cleanupHistory(threshold: Long): Int

    // Analytics and statistics
    @Query("SELECT COUNT(*) FROM queue WHERE playback_state = 1 AND queue_name = :queueName")
    suspend fun getCurrentlyPlayingCount(queueName: String = "default"): Int

    @Query("SELECT AVG(play_count_in_queue) FROM queue WHERE queue_name = :queueName")
    suspend fun getAveragePlayCount(queueName: String = "default"): Double?

    @Query("SELECT AVG(skip_count_in_queue) FROM queue WHERE queue_name = :queueName")
    suspend fun getAverageSkipCount(queueName: String = "default"): Double?

    // Queue templates and presets
    @Query("SELECT * FROM queue WHERE queue_name != 'default' ORDER BY queue_name ASC")
    fun getSavedQueues(): Flow<List<TXAQueueEntity>>

    @Transaction
    suspend fun saveQueue(templateName: String, queueName: String = "default") {
        val queueItems = getQueueSnapshot(queueName)
        val savedItems = queueItems.map { item ->
            item.copy(
                id = 0, // Reset ID for new insertion
                queueName = templateName,
                isCurrent = false // Reset current track for saved queue
            )
        }
        insertQueueItems(savedItems)
    }

    @Transaction
    suspend fun loadQueue(templateName: String, targetQueueName: String = "default") {
        clearQueue(targetQueueName)
        val templateItems = getQueueSnapshot(templateName)
        val loadedItems = templateItems.map { item ->
            item.copy(
                id = 0, // Reset ID for new insertion
                queueName = targetQueueName
            )
        }
        insertQueueItems(loadedItems)
    }

    // Batch operations
    @Query("DELETE FROM queue WHERE queue_name = :queueName AND position >= :startPosition AND position <= :endPosition")
    suspend fun removeQueueRange(startPosition: Int, endPosition: Int, queueName: String = "default")

    @Transaction
    suspend fun reorderQueue(fromPosition: Int, toPosition: Int, queueName: String = "default") {
        val queueItems = getQueueSnapshot(queueName).toMutableList()
        
        if (fromPosition < 0 || fromPosition >= queueItems.size || 
            toPosition < 0 || toPosition >= queueItems.size) {
            return
        }
        
        val movedItem = queueItems.removeAt(fromPosition)
        queueItems.add(toPosition, movedItem)
        
        // Update positions
        val updatedItems = queueItems.mapIndexed { index, item ->
            item.copy(position = index)
        }
        
        // Clear and reinsert
        clearQueue(queueName)
        insertQueueItems(updatedItems)
    }

    // Cleanup operations
    @Query("DELETE FROM queue WHERE queue_name != 'default' AND created_at < :threshold")
    suspend fun cleanupOldQueues(threshold: Long): Int

    @Query("DELETE FROM queue WHERE playback_state = 3 AND last_played_position < :threshold")
    suspend fun cleanupSkippedTracks(threshold: Long): Int

    // Service integration methods - Simplified for TXAMusicService
    @Transaction
    suspend fun getNextQueueItem(queueName: String = "default"): TXAQueueEntity? {
        val currentTrack = getCurrentQueueItem(queueName)
        if (currentTrack == null) {
            // If no current track, get first item
            val queueItems = getQueueSnapshot(queueName)
            return if (queueItems.isNotEmpty()) {
                queueItems.first()
            } else {
                null
            }
        }
        
        return moveToNext(currentTrack.id, queueName)
    }

    @Transaction
    suspend fun getPreviousQueueItem(queueName: String = "default"): TXAQueueEntity? {
        val currentTrack = getCurrentQueueItem(queueName)
        if (currentTrack == null) return null
        
        return moveToPrevious(currentTrack.id, queueName)
    }
}
