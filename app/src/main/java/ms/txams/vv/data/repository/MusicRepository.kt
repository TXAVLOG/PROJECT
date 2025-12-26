package ms.txams.vv.data.repository

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import ms.txams.vv.data.database.SongDao
import ms.txams.vv.data.database.SongEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MusicRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val songDao: SongDao
) {
    val allSongs: Flow<List<SongEntity>> = songDao.getAllSongs()

    suspend fun scanMediaStore() {
        withContext(Dispatchers.IO) {
            val songList = mutableListOf<SongEntity>()
            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } else {
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            }

            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.SIZE,
                MediaStore.Audio.Media.DATE_ADDED,
                MediaStore.Audio.Media.ALBUM_ID
            )

            // Select only music files
            val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"

            try {
                context.contentResolver.query(
                    collection,
                    projection,
                    selection,
                    null,
                    "${MediaStore.Audio.Media.TITLE} ASC"
                )?.use { cursor ->
                    val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                    val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                    val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                    val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                    val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                    val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                    val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                    val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)

                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idColumn)
                        val title = cursor.getString(titleColumn) ?: "Unknown"
                        val artist = cursor.getString(artistColumn) ?: "Unknown"
                        val album = cursor.getString(albumColumn) ?: "Unknown"
                        val duration = cursor.getLong(durationColumn)
                        val size = cursor.getLong(sizeColumn)
                        val added = cursor.getLong(dateAddedColumn)
                        val albumId = cursor.getLong(albumIdColumn)

                        val contentUri = ContentUris.withAppendedId(
                            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                            id
                        )

                        songList.add(
                            SongEntity(
                                id = id,
                                title = title,
                                artist = artist,
                                album = album,
                                duration = duration,
                                path = contentUri.toString(),
                                size = size,
                                addedAt = added,
                                albumId = albumId
                            )
                        )
                    }
                }
                
                // Update Database
                songDao.clearAll()
                songDao.insertAll(songList)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
