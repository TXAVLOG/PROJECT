package com.txapp.musicplayer.auto

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.txapp.musicplayer.R
import com.txapp.musicplayer.data.MusicRepository
import com.txapp.musicplayer.model.Song
import com.txapp.musicplayer.util.txa

/**
 * Android Auto Music Provider
 * Cung cấp dữ liệu nhạc cho Android Auto browsing
 */
class AutoMusicProvider(
    private val context: Context,
    private val repository: MusicRepository
) {
    
    /**
     * Lấy danh sách children cho một media ID
     */
    suspend fun getChildren(parentId: String): List<MediaItem> {
        return when (parentId) {
            AutoMediaIDHelper.MEDIA_ID_ROOT -> getRootChildren()
            AutoMediaIDHelper.MEDIA_ID_RECENT -> getRecentChildren()
            AutoMediaIDHelper.MEDIA_ID_MUSICS_BY_SONGS -> getSongsChildren()
            AutoMediaIDHelper.MEDIA_ID_MUSICS_BY_ALBUM -> getAlbumsChildren()
            AutoMediaIDHelper.MEDIA_ID_MUSICS_BY_ARTIST -> getArtistsChildren()
            AutoMediaIDHelper.MEDIA_ID_MUSICS_BY_PLAYLIST -> getPlaylistsChildren()
            AutoMediaIDHelper.MEDIA_ID_MUSICS_BY_FAVORITES -> getFavoritesChildren()
            AutoMediaIDHelper.MEDIA_ID_MUSICS_BY_HISTORY -> getHistoryChildren()
            AutoMediaIDHelper.MEDIA_ID_MUSICS_BY_TOP_TRACKS -> getTopTracksChildren()
            else -> {
                // Check if it's a subcategory (e.g., album/123)
                getSubCategoryChildren(parentId)
            }
        }
    }
    
    /**
     * Root menu items hiển thị trên Android Auto
     */
    private fun getRootChildren(): List<MediaItem> {
        val items = mutableListOf<MediaItem>()
        
        // Shuffle All
        items.add(
            createBrowsableItem(
                id = AutoMediaIDHelper.MEDIA_ID_MUSICS_BY_SHUFFLE,
                title = "txamusic_shuffle_all".txa(),
                iconUri = getResourceUri(R.drawable.ic_shuffle),
                isPlayable = true
            )
        )
        
        // All Songs
        items.add(
            createBrowsableItem(
                id = AutoMediaIDHelper.MEDIA_ID_MUSICS_BY_SONGS,
                title = "txamusic_songs".txa(),
                iconUri = getResourceUri(R.drawable.ic_audiotrack)
            )
        )
        
        // Albums
        items.add(
            createBrowsableItem(
                id = AutoMediaIDHelper.MEDIA_ID_MUSICS_BY_ALBUM,
                title = "txamusic_albums".txa(),
                iconUri = getResourceUri(R.drawable.ic_album)
            )
        )
        
        // Artists
        items.add(
            createBrowsableItem(
                id = AutoMediaIDHelper.MEDIA_ID_MUSICS_BY_ARTIST,
                title = "txamusic_artists".txa(),
                iconUri = getResourceUri(R.drawable.ic_artist)
            )
        )
        
        // Playlists
        items.add(
            createBrowsableItem(
                id = AutoMediaIDHelper.MEDIA_ID_MUSICS_BY_PLAYLIST,
                title = "txamusic_playlists".txa(),
                iconUri = getResourceUri(R.drawable.ic_playlist_play)
            )
        )
        
        // Favorites
        items.add(
            createBrowsableItem(
                id = AutoMediaIDHelper.MEDIA_ID_MUSICS_BY_FAVORITES,
                title = "txamusic_favorites".txa(),
                iconUri = getResourceUri(R.drawable.ic_favorite)
            )
        )
        
        // History
        items.add(
            createBrowsableItem(
                id = AutoMediaIDHelper.MEDIA_ID_MUSICS_BY_HISTORY,
                title = "txamusic_history".txa(),
                iconUri = getResourceUri(R.drawable.ic_audiotrack)
            )
        )
        
        // Top Tracks
        items.add(
            createBrowsableItem(
                id = AutoMediaIDHelper.MEDIA_ID_MUSICS_BY_TOP_TRACKS,
                title = "txamusic_top_tracks".txa(),
                iconUri = getResourceUri(R.drawable.ic_favorite)
            )
        )
        
        return items
    }
    
    /**
     * Recent items - hiện trên màn hình chính của xe
     */
    private suspend fun getRecentChildren(): List<MediaItem> {
        val songs = repository.getRecentlyPlayed(10)
        return songs.map { song -> createPlayableSong(song, AutoMediaIDHelper.MEDIA_ID_RECENT) }
    }
    
    private suspend fun getSongsChildren(): List<MediaItem> {
        val songs = repository.getAllSongs()
        return songs.take(100).map { song -> 
            createPlayableSong(song, AutoMediaIDHelper.MEDIA_ID_MUSICS_BY_SONGS)
        }
    }
    
    private suspend fun getAlbumsChildren(): List<MediaItem> {
        val albums = repository.getAlbums()
        return albums.map { album ->
            createBrowsableItem(
                id = AutoMediaIDHelper.createMediaID(null, AutoMediaIDHelper.MEDIA_ID_MUSICS_BY_ALBUM, album.id.toString()),
                title = album.title,
                subtitle = album.artistName,
                iconUri = getAlbumArtUri(album.id)
            )
        }
    }
    
    private suspend fun getArtistsChildren(): List<MediaItem> {
        val artists = repository.getArtists()
        return artists.map { artist ->
            createBrowsableItem(
                id = AutoMediaIDHelper.createMediaID(null, AutoMediaIDHelper.MEDIA_ID_MUSICS_BY_ARTIST, artist.id.toString()),
                title = artist.name,
                subtitle = "${artist.songCount} ${"txamusic_songs".txa()}",
                iconUri = getResourceUri(R.drawable.ic_artist)
            )
        }
    }
    
    private suspend fun getPlaylistsChildren(): List<MediaItem> {
        val playlists = repository.getPlaylistsOnce()
        return playlists.map { playlist ->
            createBrowsableItem(
                id = AutoMediaIDHelper.createMediaID(null, AutoMediaIDHelper.MEDIA_ID_MUSICS_BY_PLAYLIST, playlist.id.toString()),
                title = playlist.name,
                subtitle = "${playlist.songCount} ${"txamusic_songs".txa()}",
                iconUri = getResourceUri(R.drawable.ic_playlist_play)
            )
        }
    }
    
    private suspend fun getFavoritesChildren(): List<MediaItem> {
        val songs = repository.getFavorites()
        return songs.map { song -> createPlayableSong(song, AutoMediaIDHelper.MEDIA_ID_MUSICS_BY_FAVORITES) }
    }
    
    private suspend fun getHistoryChildren(): List<MediaItem> {
        val songs = repository.getRecentlyPlayed(50)
        return songs.map { song -> createPlayableSong(song, AutoMediaIDHelper.MEDIA_ID_MUSICS_BY_HISTORY) }
    }
    
    private suspend fun getTopTracksChildren(): List<MediaItem> {
        val songs = repository.getTopTracks(50)
        return songs.map { song -> createPlayableSong(song, AutoMediaIDHelper.MEDIA_ID_MUSICS_BY_TOP_TRACKS) }
    }
    
    /**
     * Lấy bài hát trong album/artist/playlist cụ thể
     */
    private suspend fun getSubCategoryChildren(parentId: String): List<MediaItem> {
        val category = AutoMediaIDHelper.extractCategory(parentId)
        
        // Parse: __BY_ALBUM____/__123 -> album ID = 123
        val parts = parentId.split("__/__")
        if (parts.size < 2) return emptyList()
        
        val categoryType = parts[0]
        val itemId = parts[1].toLongOrNull() ?: return emptyList()
        
        val songs: List<Song> = when (categoryType) {
            AutoMediaIDHelper.MEDIA_ID_MUSICS_BY_ALBUM -> repository.getSongsByAlbum(itemId)
            AutoMediaIDHelper.MEDIA_ID_MUSICS_BY_ARTIST -> repository.getSongsByArtist(itemId)
            AutoMediaIDHelper.MEDIA_ID_MUSICS_BY_PLAYLIST -> repository.getPlaylistSongsForAuto(itemId)
            else -> emptyList()
        }
        
        return songs.map { song -> createPlayableSong(song, parentId) }
    }
    
    /**
     * Tạo browseable item (folder)
     */
    private fun createBrowsableItem(
        id: String,
        title: String,
        subtitle: String? = null,
        iconUri: Uri? = null,
        isPlayable: Boolean = false
    ): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setArtworkUri(iconUri)
            .setIsBrowsable(true)
            .setIsPlayable(isPlayable)
            .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
            .build()
        
        return MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(metadata)
            .build()
    }
    
    /**
     * Tạo playable song item
     */
    private fun createPlayableSong(song: Song, parentId: String): MediaItem {
        val mediaId = AutoMediaIDHelper.createMediaID(song.id.toString(), parentId)
        
        val metadata = MediaMetadata.Builder()
            .setTitle(song.title)
            .setArtist(song.artist)
            .setAlbumTitle(song.album)
            .setArtworkUri(getAlbumArtUri(song.albumId))
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
            .setExtras(Bundle().apply {
                putLong("song_id", song.id)
                putLong("album_id", song.albumId)
                putLong("duration", song.duration)
            })
            .build()
        
        return MediaItem.Builder()
            .setMediaId(mediaId)
            .setUri(song.data)
            .setMediaMetadata(metadata)
            .build()
    }
    
    private fun getAlbumArtUri(albumId: Long): Uri {
        return Uri.parse("content://media/external/audio/albumart/$albumId")
    }
    
    private fun getResourceUri(resId: Int): Uri {
        return Uri.parse("android.resource://${context.packageName}/$resId")
    }
}
