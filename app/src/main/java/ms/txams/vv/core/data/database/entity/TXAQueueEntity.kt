package ms.txams.vv.core.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index
import androidx.room.ColumnInfo
import androidx.room.ForeignKey
import kotlinx.parcelize.Parcelize
import android.os.Parcelable

/**
 * Entity representing the playback queue
 * Lưu trạng thái hàng chờ thực tế để khôi phục khi app restart
 */
@Entity(
    tableName = "queue",
    foreignKeys = [
        ForeignKey(
            entity = TXASongEntity::class,
            parentColumns = ["id"],
            childColumns = ["song_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["queue_name"]),
        Index(value = ["position"]),
        Index(value = ["song_id"])
    ]
)
@Parcelize
data class TXAQueueEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    @ColumnInfo(name = "queue_name")
    val queueName: String = "default", // default, temporary, saved
    
    @ColumnInfo(name = "song_id")
    val songId: Long,
    
    @ColumnInfo(name = "position")
    val position: Int,
    
    @ColumnInfo(name = "added_at")
    val addedAt: Long = System.currentTimeMillis(),
    
    @ColumnInfo(name = "playback_state")
    val playbackState: Int = 0, // 0: Queued, 1: Playing, 2: Played, 3: Skipped
    
    @ColumnInfo(name = "is_current")
    val isCurrent: Boolean = false, // Currently playing track
    
    @ColumnInfo(name = "is_branded")
    val isBranded: Boolean = false, // Has branding intro injected
    
    @ColumnInfo(name = "start_position")
    val startPosition: Long = 0, // Resume position in milliseconds
    
    @ColumnInfo(name = "end_position")
    val endPosition: Long = -1, // Custom end position (-1 = full track)
    
    @ColumnInfo(name = "play_count_in_queue")
    val playCountInQueue: Int = 0,
    
    @ColumnInfo(name = "skip_count_in_queue")
    val skipCountInQueue: Int = 0,
    
    @ColumnInfo(name = "last_played_position")
    val lastPlayedPosition: Long = 0,
    
    @ColumnInfo(name = "crossfade_duration")
    val crossfadeDuration: Int = 3000, // milliseconds
    
    @ColumnInfo(name = "volume_adjustment")
    val volumeAdjustment: Float = 1.0f, // Volume multiplier
    
    @ColumnInfo(name = "pitch_adjustment")
    val pitchAdjustment: Float = 0.0f, // Semitones
    
    @ColumnInfo(name = "speed_adjustment")
    val speedAdjustment: Float = 1.0f, // Playback speed
    
    @ColumnInfo(name = "is_favorite_in_queue")
    val isFavoriteInQueue: Boolean = false,
    
    @ColumnInfo(name = "custom_tags")
    val customTags: String? = null, // JSON string for custom metadata
) : Parcelable

/**
 * Entity representing queue history for analytics
 */
@Entity(
    tableName = "queue_history",
    indices = [
        Index(value = ["session_id"]),
        Index(value = ["timestamp"]),
        Index(value = ["song_id"])
    ]
)
@Parcelize
data class TXAQueueHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    @ColumnInfo(name = "session_id")
    val sessionId: String,
    
    @ColumnInfo(name = "song_id")
    val songId: Long,
    
    @ColumnInfo(name = "position_in_queue")
    val positionInQueue: Int,
    
    @ColumnInfo(name = "timestamp")
    val timestamp: Long,
    
    @ColumnInfo(name = "action")
    val action: String, // "play", "skip", "complete", "pause", "resume"
    
    @ColumnInfo(name = "playback_duration")
    val playbackDuration: Long, // How long it was played
    
    @ColumnInfo(name = "completion_percentage")
    val completionPercentage: Float,
    
    @ColumnInfo(name = "source")
    val source: String, // "queue", "playlist", "album", "artist", "search"
) : Parcelable
