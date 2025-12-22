package ms.txams.vv.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "songs")
data class SongEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val filePath: String,
    val albumArt: String?,
    val trackNumber: Int = 0,
    val year: Int = 0,
    val genre: String = "",
    val dateAdded: Long = System.currentTimeMillis(),
    val dateModified: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false,
    val playCount: Long = 0
)
