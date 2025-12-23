package ms.txams.vv.core.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index
import androidx.room.ColumnInfo
import kotlinx.parcelize.Parcelize
import android.os.Parcelable

/**
 * Entity representing a song in the local database
 * Chứa metadata, file path, và branding information
 */
@Entity(
    tableName = "songs",
    indices = [
        Index(value = ["file_path"], unique = true),
        Index(value = ["artist"]),
        Index(value = ["album"]),
        Index(value = ["title"]),
        Index(value = ["is_branded"])
    ]
)
@Parcelize
data class TXASongEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    @ColumnInfo(name = "title")
    val title: String,
    
    @ColumnInfo(name = "artist")
    val artist: String,
    
    @ColumnInfo(name = "album")
    val album: String,
    
    @ColumnInfo(name = "album_id")
    val albumId: String,
    
    @ColumnInfo(name = "artist_id")
    val artistId: String,
    
    @ColumnInfo(name = "duration")
    val duration: Long, // in milliseconds
    
    @ColumnInfo(name = "file_path")
    val filePath: String,
    
    @ColumnInfo(name = "file_size")
    val fileSize: Long, // in bytes
    
    @ColumnInfo(name = "file_modified")
    val fileModified: Long, // timestamp
    
    @ColumnInfo(name = "mime_type")
    val mimeType: String,
    
    @ColumnInfo(name = "year")
    val year: Int? = null,
    
    @ColumnInfo(name = "track_number")
    val trackNumber: Int? = null,
    
    @ColumnInfo(name = "genre")
    val genre: String? = null,
    
    @ColumnInfo(name = "composer")
    val composer: String? = null,
    
    @ColumnInfo(name = "album_art_path")
    val albumArtPath: String? = null,
    
    @ColumnInfo(name = "is_branded")
    val isBranded: Boolean = false,
    
    @ColumnInfo(name = "branding_timestamp")
    val brandingTimestamp: Long? = null,
    
    @ColumnInfo(name = "play_count")
    val playCount: Int = 0,
    
    @ColumnInfo(name = "skip_count")
    val skipCount: Int = 0,
    
    @ColumnInfo(name = "last_played")
    val lastPlayed: Long? = null,
    
    @ColumnInfo(name = "favorite")
    val favorite: Boolean = false,
    
    @ColumnInfo(name = "date_added")
    val dateAdded: Long = System.currentTimeMillis(),
    
    @ColumnInfo(name = "lyrics_file_path")
    val lyricsFilePath: String? = null,
    
    @ColumnInfo(name = "bitrate")
    val bitrate: Int? = null,
    
    @ColumnInfo(name = "sample_rate")
    val sampleRate: Int? = null,
    
    @ColumnInfo(name = "channels")
    val channels: Int? = null,
    
    @ColumnInfo(name = "format")
    val format: String? = null,
    
    @ColumnInfo(name = "is_available")
    val isAvailable: Boolean = true,
    
    @ColumnInfo(name = "checksum")
    val checksum: String? = null // MD5 hash for integrity check
) : Parcelable
