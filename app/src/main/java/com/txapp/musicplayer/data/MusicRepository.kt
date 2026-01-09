package com.txapp.musicplayer.data

import android.content.ContentResolver
import android.content.Context
import android.provider.MediaStore
import com.txapp.musicplayer.model.Genre
import com.txapp.musicplayer.model.Playlist
import com.txapp.musicplayer.model.Song
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import com.txapp.musicplayer.util.TXALogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MusicRepository(
    private val database: MusicDatabase,
    private val contentResolver: ContentResolver
) {

    val genres: Flow<List<Genre>> = flow {
        val list = mutableListOf<Genre>()
        val projection = arrayOf(
            MediaStore.Audio.Genres._ID,
            MediaStore.Audio.Genres.NAME
        )
        contentResolver.query(
            MediaStore.Audio.Genres.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            MediaStore.Audio.Genres.NAME
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Genres._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Genres.NAME)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val name = cursor.getString(nameColumn) ?: "Unknown"
                list.add(Genre(id, name))
            }
        }
        emit(list)
    }

    // Sử dụng Room Database thay vì MediaStore (đã deprecated trên Android 10+)
    val playlists: Flow<List<Playlist>> = database.playlistDao().getPlaylistsWithSongCount()
        .map { list ->
            list.map { pwc ->
                Playlist(
                    id = pwc.id,
                    name = pwc.name,
                    songCount = pwc.songCount
                )
            }
        }

    fun refreshPlaylists() {
        // Không cần trigger vì Room tự động update khi có thay đổi
    }

    val allSongs: Flow<List<Song>> = database.songDao().getAllSongs()

    val topTracks: Flow<List<Song>> = database.songDao().getTopTracks()

    val favoriteSongs: Flow<List<Song>> = database.songDao().getFavoriteSongs()
    
    val recentlyPlayed: Flow<List<Song>> = database.songDao().getRecentlyPlayed()

    suspend fun updateHistory(songId: Long) {
        val timestamp = System.currentTimeMillis()
        database.songDao().updateLastPlayed(songId, timestamp)
        database.songDao().incrementPlayCount(songId)
    }

    fun getSuggestions(limit: Int): Flow<List<Song>> {
        return database.songDao().getRandomSongs(limit)
    }

    suspend fun toggleFavorite(songId: Long): Boolean {
        database.songDao().toggleFavorite(songId)
        return database.songDao().getSongById(songId)?.isFavorite ?: false
    }

    suspend fun setFavorite(songId: Long, isFavorite: Boolean) {
        database.songDao().setFavorite(songId, isFavorite)
    }

    suspend fun loadSongsFromMediaStore(): List<Song> {
        val songs = mutableListOf<Song>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.ARTIST_ID,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DATE_MODIFIED
        )
        val selection = "${MediaStore.Audio.Media.DURATION} >= 30000"
        val sortOrder = "${MediaStore.Audio.Media.DATE_ADDED} DESC"

        contentResolver.query(
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
            val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val artistIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST_ID)
            val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val dateModifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)

            while (cursor.moveToNext()) {
                val data = cursor.getString(dataColumn) ?: ""
                
                // Skip files that no longer exist (Only check on older Android versions)
                // On Android 10+ (API 29+), direct file access is restricted and often returns false negative
                if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) {
                    val file = java.io.File(data)
                    if (!file.exists()) continue
                }
                
                val id = cursor.getLong(idColumn)
                val title = cursor.getString(titleColumn) ?: "Unknown"
                val artist = cursor.getString(artistColumn) ?: "Unknown"
                val album = cursor.getString(albumColumn) ?: "Unknown"
                val duration = cursor.getLong(durationColumn)
                val albumId = cursor.getLong(albumIdColumn)
                val dateAdded = cursor.getLong(dateAddedColumn)
                val dateModified = cursor.getLong(dateModifiedColumn)

                songs.add(
                    Song(
                        id = id,
                        title = title,
                        artist = artist,
                        album = album,
                        data = data,
                        duration = duration,
                        albumId = albumId,
                        artistId = cursor.getLong(artistIdColumn),
                        dateAdded = dateAdded,
                        dateModified = dateModified
                    )
                )
            }
        }
        
        // Deduplicate songs by metadata (title + artist + album + duration)
        // This removes duplicates where same song exists in multiple locations
        return songs.distinctBy { 
            "${it.title.lowercase().trim()}|${it.artist.lowercase().trim()}|${it.album.lowercase().trim()}|${it.duration}" 
        }
    }
    
    /**
     * Normalize song title by removing common suffixes like (1), (2), [1], [2], etc.
     */
    private fun normalizeTitle(title: String): String {
        return title.lowercase()
            .trim()
            // Remove (1), (2), [1], [2], etc.
            .replace(Regex("\\s*[\\(\\[]\\d+[\\)\\]]\\s*$"), "")
            // Remove trailing numbers
            .replace(Regex("\\s+\\d+$"), "")
            .trim()
    }

    val albums: Flow<List<com.txapp.musicplayer.model.Album>> = allSongs.map { songs ->
        songs.groupBy { it.albumId }.map { (albumId, songs) ->
            com.txapp.musicplayer.model.Album(albumId, songs)
        }
    }

    val artists: Flow<List<com.txapp.musicplayer.model.Artist>> = albums.map { albums ->
        albums.groupBy { it.artistId }.map { (artistId, albums) ->
            com.txapp.musicplayer.model.Artist(artistId, albums)
        }
    }

    suspend fun saveSongs(songs: List<Song>) {
        database.songDao().upsertSongsPreservingUserData(songs)
    }

    suspend fun syncLibrary(songs: List<Song>, smart: Boolean = false) = withContext(Dispatchers.IO) {
        if (songs.isEmpty()) {
             // Safety check
             return@withContext
        }
        
        // Always load sync info to check for Manual songs protection
        val dbSyncInfo = database.songDao().getAllSongSyncInfo()
        val dbMap = dbSyncInfo.associateBy { it.id }
        
        var songsToUpsert = songs
        
        if (smart) {
             // Smart Sync: Only process differences
             // 1. Filter songs to upsert (New or Modified)
             songsToUpsert = songs.filter { song ->
                 val dbInfo = dbMap[song.id]
                 // Add if not in DB OR dateModified is different
                 dbInfo == null || dbInfo.dateModified != song.dateModified
             }
        }
        
        // 2. Identify Deleted songs (Logic shared for both Smart and Full)
        val scannedIds = songs.map { it.id }.toSet()
        
        // Candidates for deletion: In DB but not in current System Scan
        val candidates = dbSyncInfo.filter { !scannedIds.contains(it.id) }
        
        // Filter out Manual songs that still exist
        val idsToDelete = candidates.filter { info ->
            if (info.isManual) {
                // PROTECT MANUAL SONGS: Only delete if file physically missing
                try {
                    val file = java.io.File(info.data)
                    !file.exists() // Delete if file gone
                } catch (e: Exception) {
                    true // Delete if error checking
                }
            } else {
                true // System song missing from scan -> Delete
            }
        }.map { it.id }
        
        if (smart) {
             TXALogger.appI("MusicRepository", "Smart Sync: Found ${songsToUpsert.size} changes, ${idsToDelete.size} deletions.")
        } else {
             TXALogger.appI("MusicRepository", "Full Sync: Upserting ${songsToUpsert.size} songs, Deleting ${idsToDelete.size}.")
        }
        
        // Execute Batch Database Operations
        if (songsToUpsert.isNotEmpty()) {
            songsToUpsert.chunked(500).forEach { batch ->
                database.songDao().upsertSongsPreservingUserData(batch)
            }
        }
        
        if (idsToDelete.isNotEmpty()) {
            idsToDelete.chunked(500).forEach { batch ->
                database.songDao().deleteSongsByIds(batch)
            }
        }
    }

    suspend fun addManualSongs(uris: List<android.net.Uri>, context: Context): ManualAddResult = withContext(Dispatchers.IO) {
        val songsToAdd = mutableListOf<Song>()
        var skippedCount = 0
        
        uris.forEach { uri ->
            try {
                val path = com.txapp.musicplayer.util.TXAFilePickerUtil.getPath(context, uri) ?: uri.toString()
                
                // Check if exists in DB by Path
                val existingPath = database.songDao().getSongByPath(path)
                
                if (existingPath != null) {
                    skippedCount++
                    return@forEach
                }
                
                // Smart Deduplication: Check by Metadata (Title + Artist + Duration)
                // This prevents adding 'content://' duplicates if the file is already scanned via MediaStore
                val meta = com.txapp.musicplayer.util.TXAFilePickerUtil.extractMetadata(context, uri)
                val titleToCheck = meta.title ?: "Unknown"
                val artistToCheck = meta.artist ?: "Unknown Artist"
                
                if (titleToCheck != "Unknown") {
                     val existingMeta = database.songDao().getSongBySmartMetadata(titleToCheck, artistToCheck, meta.duration)
                     if (existingMeta != null) {
                         TXALogger.appI("MusicRepository", "Skipped duplicate manual add (Metadata match): $titleToCheck")
                         skippedCount++
                         return@forEach
                     }
                }
                
                // Generate safe negative ID to avoid collision with System IDs (usually positive)
                // Use Hash of path for consistency
                var id = path.hashCode().toLong()
                
                // Ensure ID is unique
                if (database.songDao().getSongById(id) != null) {
                     id = System.nanoTime() // Fallback to random
                }

                songsToAdd.add(Song(
                    id = id,
                    title = meta.title ?: "Unknown",
                    artist = meta.artist ?: "Unknown Artist",
                    album = meta.album ?: "Unknown Album",
                    data = path,
                    duration = meta.duration,
                    albumId = -1L, 
                    isManual = true,
                    dateAdded = System.currentTimeMillis() / 1000,
                    dateModified = System.currentTimeMillis() / 1000
                ))
            } catch (e: Exception) {
                TXALogger.appE("MusicRepository", "Failed manual add: $uri", e)
            }
        }
        
        if (songsToAdd.isNotEmpty()) {
            database.songDao().insertSongs(songsToAdd)
        }
        
        ManualAddResult(songsToAdd.size, skippedCount)
    }

    data class ManualAddResult(val added: Int, val skipped: Int)

    suspend fun incrementPlayCount(songId: Long) {
        database.songDao().incrementPlayCount(songId)
    }

    suspend fun getSongById(songId: Long): Song? {
        return database.songDao().getSongById(songId)
    }

    suspend fun getSongsByIds(songIds: LongArray): List<Song> {
        return database.songDao().getSongsByIds(songIds)
    }

    // ============= ROOM-BASED PLAYLIST MANAGEMENT =============
    // Sử dụng Room Database thay vì MediaStore (deprecated từ Android 10+)

    suspend fun createPlaylist(name: String): Long = withContext(Dispatchers.IO) {
        try {
            val playlistEntity = PlaylistEntity(name = name)
            database.playlistDao().insertPlaylist(playlistEntity)
        } catch (e: Exception) {
            TXALogger.appE("MusicRepository", "Failed to create playlist", e)
            -1L
        }
    }

    suspend fun getPlaylistById(playlistId: Long): Playlist? = withContext(Dispatchers.IO) {
        try {
            database.playlistDao().getPlaylistById(playlistId)?.let { entity ->
                val songCount = database.playlistDao().getPlaylistSongCount(playlistId)
                Playlist(
                    id = entity.id,
                    name = entity.name,
                    songCount = songCount
                )
            }
        } catch (e: Exception) {
            TXALogger.appE("MusicRepository", "Failed to get playlist by id", e)
            null
        }
    }

    fun getPlaylistSongs(playlistId: Long): Flow<List<Song>> = flow {
        try {
            val songIds = database.playlistDao().getPlaylistSongIds(playlistId)
            if (songIds.isNotEmpty()) {
                val songs = database.songDao().getSongsByIds(songIds.toLongArray())
                // Giữ thứ tự đúng theo sortOrder
                val sortedSongs = songIds.mapNotNull { id -> songs.find { it.id == id } }
                emit(sortedSongs)
            } else {
                emit(emptyList())
            }
        } catch (e: Exception) {
            TXALogger.appE("MusicRepository", "Failed to get playlist songs", e)
            emit(emptyList())
        }
    }

    fun getPlaylistsContainingSong(songId: Long): Flow<List<Long>> {
        return database.playlistDao().getPlaylistsContainingSong(songId)
    }

    suspend fun isSongInPlaylist(playlistId: Long, songId: Long): Boolean = withContext(Dispatchers.IO) {
        database.playlistDao().isSongInPlaylist(playlistId, songId)
    }

    suspend fun addSongToPlaylist(playlistId: Long, songId: Long): Boolean = withContext(Dispatchers.IO) {
        try {
            val currentCount = database.playlistDao().getPlaylistSongCount(playlistId)
            val crossRef = PlaylistSongCrossRef(
                playlistId = playlistId,
                songId = songId,
                sortOrder = currentCount
            )
            database.playlistDao().addSongToPlaylist(crossRef)
            true
        } catch (e: Exception) {
            TXALogger.appE("MusicRepository", "Failed to add song to playlist", e)
            false
        }
    }

    suspend fun removeSongFromPlaylist(playlistId: Long, songId: Long): Boolean = withContext(Dispatchers.IO) {
        try {
            database.playlistDao().removeSongFromPlaylist(playlistId, songId)
            true
        } catch (e: Exception) {
            TXALogger.appE("MusicRepository", "Failed to remove song from playlist", e)
            false
        }
    }

    suspend fun deletePlaylist(playlistId: Long): Boolean = withContext(Dispatchers.IO) {
        try {
            database.playlistDao().deletePlaylist(playlistId)
            true
        } catch (e: Exception) {
            TXALogger.appE("MusicRepository", "Failed to delete playlist", e)
            false
        }
    }

    suspend fun deleteSong(songId: Long) = withContext(Dispatchers.IO) {
        database.songDao().deleteById(songId)
    }
    
    /**
     * Delete song from app only (keeps the file on device)
     */
    suspend fun deleteSongFromApp(songId: Long) = deleteSong(songId)

    /**
     * Remove songs by extension from Database ONLY (Does not delete physical file)
     */
    suspend fun removeSongsByExtension(extension: String) = withContext(Dispatchers.IO) {
        try {
            val allSongs = database.songDao().getAllSongSyncInfo()
            val idsToDelete = allSongs.filter {
                it.data.endsWith(".$extension", ignoreCase = true)
            }.map { it.id }

            if (idsToDelete.isNotEmpty()) {
                TXALogger.appI("MusicRepository", "Cleaning up: Removing ${idsToDelete.size} songs with extension .$extension")
                idsToDelete.chunked(500).forEach { batch ->
                    database.songDao().deleteSongsByIds(batch)
                }
            }
        } catch (e: Exception) {
            TXALogger.appE("MusicRepository", "Failed to remove songs by extension", e)
        }
    }
    
    // ============= ANDROID AUTO SUPPORT =============
    
    /**
     * Get all songs as a suspend function (for Android Auto)
     */
    suspend fun getAllSongs(): List<Song> = withContext(Dispatchers.IO) {
        database.songDao().getAllSongsOnce()
    }
    
    /**
     * Get all albums (for Android Auto browsing)
     */
    suspend fun getAlbums(): List<com.txapp.musicplayer.model.Album> = withContext(Dispatchers.IO) {
        val songs = database.songDao().getAllSongsOnce()
        songs.groupBy { it.albumId }.map { (albumId, albumSongs) ->
            com.txapp.musicplayer.model.Album(albumId, albumSongs)
        }
    }
    
    /**
     * Get all artists (for Android Auto browsing)
     */
    suspend fun getArtists(): List<com.txapp.musicplayer.model.Artist> = withContext(Dispatchers.IO) {
        val songs = database.songDao().getAllSongsOnce()
        val albums = songs.groupBy { it.albumId }.map { (albumId, albumSongs) ->
            com.txapp.musicplayer.model.Album(albumId, albumSongs)
        }
        albums.groupBy { it.artistId }.map { (artistId, artistAlbums) ->
            com.txapp.musicplayer.model.Artist(artistId, artistAlbums)
        }
    }
    
    /**
     * Get favorite songs (for Android Auto)
     */
    suspend fun getFavorites(): List<Song> = withContext(Dispatchers.IO) {
        database.songDao().getFavoriteSongsOnce()
    }
    
    /**
     * Get recently played songs (for Android Auto)
     */
    suspend fun getRecentlyPlayed(limit: Int = 50): List<Song> = withContext(Dispatchers.IO) {
        database.songDao().getRecentlyPlayedOnce(limit)
    }
    
    /**
     * Get top tracks (for Android Auto)
     */
    suspend fun getTopTracks(limit: Int = 50): List<Song> = withContext(Dispatchers.IO) {
        database.songDao().getTopTracksOnce(limit)
    }
    
    /**
     * Search songs by title/artist (for Android Auto voice search)
     */
    suspend fun searchSongs(query: String): List<Song> = withContext(Dispatchers.IO) {
        database.songDao().searchSongs("%$query%")
    }
    
    /**
     * Get playlists as a one-shot list (for Android Auto)
     */
    suspend fun getPlaylistsOnce(): List<Playlist> = withContext(Dispatchers.IO) {
        database.playlistDao().getPlaylistsWithSongCountOnce().map { pwc ->
            Playlist(
                id = pwc.id,
                name = pwc.name,
                songCount = pwc.songCount
            )
        }
    }
    
    /**
     * Get songs by album ID (for Android Auto browsing)
     */
    suspend fun getSongsByAlbum(albumId: Long): List<Song> = withContext(Dispatchers.IO) {
        database.songDao().getSongsByAlbum(albumId)
    }
    
    /**
     * Get songs by artist ID (for Android Auto browsing)
     */
    suspend fun getSongsByArtist(artistId: Long): List<Song> = withContext(Dispatchers.IO) {
        // Artist ID is derived from album, so we get all songs and filter
        val songs = database.songDao().getAllSongsOnce()
        val albums = songs.groupBy { it.albumId }.map { (albumId, albumSongs) ->
            com.txapp.musicplayer.model.Album(albumId, albumSongs)
        }
        val artist = albums.filter { it.artistId == artistId }
        artist.flatMap { it.songs }
    }
    
    /**
     * Get playlist songs as suspend function (for Android Auto)
     */
    suspend fun getPlaylistSongsForAuto(playlistId: Long): List<Song> = withContext(Dispatchers.IO) {
        try {
            val songIds = database.playlistDao().getPlaylistSongIds(playlistId)
            if (songIds.isNotEmpty()) {
                val songs = database.songDao().getSongsByIds(songIds.toLongArray())
                songIds.mapNotNull { id -> songs.find { it.id == id } }
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            TXALogger.appE("MusicRepository", "Failed to get playlist songs", e)
            emptyList()
        }
    }
    
    // ============= TAG EDITOR SUPPORT =============
    
    /**
     * Update song metadata in database AND file (Tag Editor feature)
     */
    suspend fun updateSongMetadata(
        context: Context,
        songId: Long,
        title: String,
        artist: String,
        album: String,
        albumArtist: String?,
        composer: String?,
        year: Int,
        trackNumber: Int
    ): com.txapp.musicplayer.util.TXATagWriter.WriteResult = withContext(Dispatchers.IO) {
        try {
            // 1. Get current song data to get file path
            val song = database.songDao().getSongById(songId) ?: return@withContext com.txapp.musicplayer.util.TXATagWriter.WriteResult.Failure
            val filePath = song.data

            // 2. Try to write to physical file FIRST. 
            // If it needs permission, we stop here and return PermissionRequired.
            val result = com.txapp.musicplayer.util.TXATagWriter.writeTags(
                context,
                com.txapp.musicplayer.model.SongTagInfo(
                    songId = songId,
                    filePaths = listOf(filePath),
                    title = title,
                    artist = artist,
                    album = album,
                    albumArtist = albumArtist,
                    composer = composer,
                    year = year.toString(),
                    trackNumber = trackNumber.toString()
                )
            )

            if (result is com.txapp.musicplayer.util.TXATagWriter.WriteResult.Success) {
                // 3. Update Database ONLY if file write was successful
                database.songDao().updateSongMetadata(
                    songId = songId,
                    title = title,
                    artist = artist,
                    album = album,
                    albumArtist = albumArtist,
                    composer = composer,
                    year = year,
                    trackNumber = trackNumber
                )
                TXALogger.appI("MusicRepository", "Successfully updated metadata for song: $title")
            }

            return@withContext result
        } catch (e: Exception) {
            TXALogger.appE("MusicRepository", "Failed to update song metadata", e)
            com.txapp.musicplayer.util.TXATagWriter.WriteResult.Failure
        }
    }

}
