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
import ms.txams.vv.core.TXALogger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * TXA Music Repository
 * Scans and manages music files from device storage
 * 
 * Features:
 * - Scan all music files except ringtones folder
 * - Store in local database for fast access
 * - Detailed logging for debugging
 */
@Singleton
class MusicRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val songDao: SongDao
) {
    val allSongs: Flow<List<SongEntity>> = songDao.getAllSongs()

    // Folders to exclude from scanning
    private val excludedFolders = listOf(
        "/ringtones/",
        "/ringtone/",
        "/notifications/",
        "/alarms/",
        "/android/data/",
        "/android/obb/"
    )

    suspend fun scanMediaStore() {
        withContext(Dispatchers.IO) {
            TXALogger.appI("Starting music library scan...")
            val startTime = System.currentTimeMillis()
            
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
                MediaStore.Audio.Media.ALBUM_ID,
                MediaStore.Audio.Media.DATA // Path for filtering
            )

            // Select audio files with size > 0 (let app filter by duration/path later)
            val selection = "${MediaStore.Audio.Media.SIZE} > 0"

            try {
                var totalScanned = 0
                var skippedRingtones = 0
                var skippedShort = 0
                
                context.contentResolver.query(
                    collection,
                    projection,
                    selection,
                    null,
                    "${MediaStore.Audio.Media.DATE_ADDED} DESC" // Newest first
                )?.use { cursor ->
                    val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                    val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                    val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                    val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                    val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                    val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                    val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                    val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                    val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)

                    TXALogger.appD("Found ${cursor.count} audio files in MediaStore")

                    while (cursor.moveToNext()) {
                        totalScanned++
                        
                        val id = cursor.getLong(idColumn)
                        val title = cursor.getString(titleColumn) ?: "Unknown"
                        val artist = cursor.getString(artistColumn) ?: "Unknown"
                        val album = cursor.getString(albumColumn) ?: "Unknown"
                        val duration = cursor.getLong(durationColumn)
                        val size = cursor.getLong(sizeColumn)
                        val added = cursor.getLong(dateAddedColumn)
                        val albumId = cursor.getLong(albumIdColumn)
                        val path = cursor.getString(dataColumn) ?: ""

                        // Skip if in excluded folders (ringtones, notifications, etc.)
                        val pathLower = path.lowercase()
                        if (excludedFolders.any { pathLower.contains(it) }) {
                            skippedRingtones++
                            TXALogger.appD("Skipped ringtone/notification: $title ($path)")
                            continue
                        }

                        // Skip very short audio (less than 5 seconds - likely notifications or UI sounds)
                        if (duration < 5000) {
                            skippedShort++
                            TXALogger.appD("Skipped short audio (${duration}ms): $title ($path)")
                            continue
                        }

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
                // Using REPLACE strategy in DAO handles duplicates
                songDao.insertAll(songList)
                
                val elapsed = System.currentTimeMillis() - startTime
                TXALogger.appI("Music scan completed in ${elapsed}ms")
                TXALogger.appI("Results: ${songList.size} songs added")
                TXALogger.appI("Skipped: $skippedRingtones ringtones/notifications, $skippedShort short files")
                TXALogger.appI("Total scanned: $totalScanned files")
                
            } catch (e: Exception) {
                TXALogger.appE("Error scanning music library: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    suspend fun addManualSong(uri: android.net.Uri) {
        withContext(Dispatchers.IO) {
            TXALogger.appI("Adding manual song: $uri")
            try {
                // Remove existing if duplicate path
                songDao.deleteByPath(uri.toString())

                // Try to get info from ContentResolver
                val projection = arrayOf(
                    MediaStore.Audio.Media.TITLE,
                    MediaStore.Audio.Media.ARTIST,
                    MediaStore.Audio.Media.ALBUM,
                    MediaStore.Audio.Media.DURATION,
                    MediaStore.Audio.Media.SIZE,
                    MediaStore.Audio.Media.ALBUM_ID
                )
                
                context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                     if (cursor.moveToFirst()) {
                         val song = SongEntity(
                             id = uri.hashCode().toLong(), // Unique ID for manual
                             title = cursor.getString(0) ?: "Unknown",
                             artist = cursor.getString(1) ?: "Unknown",
                             album = cursor.getString(2) ?: "Unknown",
                             duration = cursor.getLong(3),
                             path = uri.toString(),
                             size = cursor.getLong(4),
                             addedAt = System.currentTimeMillis() / 1000,
                             albumId = cursor.getLong(5)
                         )
                         songDao.insert(song)
                         TXALogger.appI("Manual song added: ${song.title}")
                         return@withContext
                     }
                }
                
                // Fallback for simple files
                val song = SongEntity(
                    id = uri.hashCode().toLong(),
                    title = uri.lastPathSegment ?: "Unknown",
                    artist = "Unknown",
                    album = "Unknown",
                    duration = 0,
                    path = uri.toString(),
                    size = 0,
                    addedAt = System.currentTimeMillis() / 1000,
                    albumId = 0
                )
                songDao.insert(song)
                TXALogger.appI("Manual song added (fallback): ${song.title}")
                
            } catch (e: Exception) {
                TXALogger.appE("Failed to add manual song", e)
            }
        }
    }
}
