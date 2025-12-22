package ms.txams.vv.data.repository

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import ms.txams.vv.data.database.SongDao
import ms.txams.vv.data.database.SongEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MusicRepository @Inject constructor(
    private val songDao: SongDao,
    @ApplicationContext private val context: Context
) {

    fun getAllSongs(): Flow<List<SongEntity>> = songDao.getAllSongs()
    
    fun getFavoriteSongs(): Flow<List<SongEntity>> = songDao.getFavoriteSongs()
    
    fun getSongsByArtist(artist: String): Flow<List<SongEntity>> = songDao.getSongsByArtist(artist)
    
    fun getSongsByAlbum(album: String): Flow<List<SongEntity>> = songDao.getSongsByAlbum(album)
    
    fun getMostPlayedSongs(limit: Int = 20): Flow<List<SongEntity>> = songDao.getMostPlayedSongs(limit)
    
    fun getRecentlyAddedSongs(limit: Int = 20): Flow<List<SongEntity>> = songDao.getRecentlyAddedSongs(limit)
    
    fun searchSongs(query: String): Flow<List<SongEntity>> = songDao.searchSongs(query)
    
    fun getAllArtists(): Flow<List<String>> = songDao.getAllArtists()
    
    fun getAllAlbums(): Flow<List<String>> = songDao.getAllAlbums()

    suspend fun scanMusicLibrary(): Int = withContext(Dispatchers.IO) {
        val songs = scanMediaStore()
        
        songDao.deleteAllSongs()
        songDao.insertSongs(songs)
        
        songs.size
    }

    private fun scanMediaStore(): List<SongEntity> {
        val songs = mutableListOf<SongEntity>()
        
        try {
            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.ALBUM_ID,
                MediaStore.Audio.Media.TRACK,
                MediaStore.Audio.Media.YEAR,
                MediaStore.Audio.Media.GENRE,
                MediaStore.Audio.Media.DATE_ADDED,
                MediaStore.Audio.Media.DATE_MODIFIED
            )

            val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
            val sortOrder = "${MediaStore.Audio.Media.DATE_ADDED} DESC"

            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                sortOrder
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val trackColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
                val yearColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
                val genreColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.GENRE)
                val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                val dateModifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)

                while (cursor.moveToNext()) {
                    try {
                        val id = cursor.getLong(idColumn)
                        val title = cursor.getString(titleColumn) ?: "Unknown"
                        val artist = cursor.getString(artistColumn) ?: "Unknown Artist"
                        val album = cursor.getString(albumColumn) ?: "Unknown Album"
                        val duration = cursor.getLong(durationColumn)
                        val filePath = cursor.getString(dataColumn)
                        val albumId = cursor.getLong(albumIdColumn)
                        val track = cursor.getInt(trackColumn)
                        val year = cursor.getInt(yearColumn)
                        val genre = cursor.getString(genreColumn) ?: ""
                        val dateAdded = cursor.getLong(dateAddedColumn) * 1000
                        val dateModified = cursor.getLong(dateModifiedColumn) * 1000

                        val albumArtUri = ContentUris.withAppendedId(
                            Uri.parse("content://media/external/audio/albumart"),
                            albumId
                        ).toString()

                        val song = SongEntity(
                            id = id.toString(),
                            title = title,
                            artist = artist,
                            album = album,
                            duration = duration,
                            filePath = filePath,
                            albumArt = albumArtUri,
                            trackNumber = track,
                            year = year,
                            genre = genre,
                            dateAdded = dateAdded,
                            dateModified = dateModified
                        )
                        songs.add(song)
                    } catch (e: Exception) {
                        // Skip problematic songs but continue scanning
                        continue
                    }
                }
            }
        } catch (e: SecurityException) {
            // Permission denied - will be handled by caller
            throw e
        } catch (e: Exception) {
            // Other errors during scanning
            throw e
        }

        return songs
    }

    suspend fun getSongById(songId: String): SongEntity? {
        return songDao.getSongById(songId)
    }

    suspend fun updateFavoriteStatus(songId: String, isFavorite: Boolean) {
        songDao.updateFavoriteStatus(songId, isFavorite)
    }

    suspend fun incrementPlayCount(songId: String) {
        songDao.incrementPlayCount(songId)
    }

    suspend fun getSongsCount(): Int {
        return songDao.getSongsCount()
    }
}
