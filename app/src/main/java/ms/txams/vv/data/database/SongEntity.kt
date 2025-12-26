package ms.txams.vv.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "songs")
data class SongEntity(
    @PrimaryKey
    val id: Long, // MediaStore ID
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val path: String,
    val size: Long,
    val addedAt: Long,
    val albumId: Long
)
