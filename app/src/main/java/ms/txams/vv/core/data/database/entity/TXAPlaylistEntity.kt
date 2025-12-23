package ms.txams.vv.core.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index
import androidx.room.ColumnInfo
import androidx.room.ForeignKey
import androidx.room.Relation
import kotlinx.parcelize.Parcelize
import android.os.Parcelable

/**
 * Entity representing a playlist in the local database
 */
@Entity(
    tableName = "playlists",
    indices = [
        Index(value = ["name"], unique = true),
        Index(value = ["created_at"]),
        Index(value = ["is_system"])
    ]
)
@Parcelize
data class TXAPlaylistEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    @ColumnInfo(name = "name")
    val name: String,
    
    @ColumnInfo(name = "description")
    val description: String? = null,
    
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    
    @ColumnInfo(name = "modified_at")
    val modifiedAt: Long = System.currentTimeMillis(),
    
    @ColumnInfo(name = "song_count")
    val songCount: Int = 0,
    
    @ColumnInfo(name = "total_duration")
    val totalDuration: Long = 0, // in milliseconds
    
    @ColumnInfo(name = "cover_art_path")
    val coverArtPath: String? = null,
    
    @ColumnInfo(name = "is_system")
    val isSystem: Boolean = false, // System playlists like Favorites, Recently Played
    
    @ColumnInfo(name = "is_favorite")
    val isFavorite: Boolean = false,
    
    @ColumnInfo(name = "play_count")
    val playCount: Int = 0,
    
    @ColumnInfo(name = "last_played")
    val lastPlayed: Long? = null,
    
    @ColumnInfo(name = "sort_order")
    val sortOrder: Int = 0, // 0: Default, 1: A-Z, 2: Z-A, 3: Date Added, 4: Most Played
    
    @ColumnInfo(name = "is_visible")
    val isVisible: Boolean = true,
    
    @ColumnInfo(name = "color")
    val color: String? = null, // Hex color for playlist UI
) : Parcelable

/**
 * Entity representing songs in playlists (Many-to-Many relationship)
 */
@Entity(
    tableName = "playlist_songs",
    primaryKeys = ["playlist_id", "song_id"],
    foreignKeys = [
        ForeignKey(
            entity = TXAPlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlist_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = TXASongEntity::class,
            parentColumns = ["id"],
            childColumns = ["song_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["playlist_id", "position"]),
        Index(value = ["song_id"])
    ]
)
@Parcelize
data class TXAPlaylistSongEntity(
    @ColumnInfo(name = "playlist_id")
    val playlistId: Long,
    
    @ColumnInfo(name = "song_id")
    val songId: Long,
    
    @ColumnInfo(name = "position")
    val position: Int,
    
    @ColumnInfo(name = "added_at")
    val addedAt: Long = System.currentTimeMillis(),
) : Parcelable
