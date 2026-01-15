package com.txapp.musicplayer.util

import com.txapp.musicplayer.model.Song

/**
 * Advanced search query helper
 * Supports smart matching and filters
 */
object SearchQueryHelper {
    private const val TAG = "SearchQuery"
    
    /**
     * Search songs with advanced matching
     * @param songs List of songs to search
     * @param query Search query
     * @return Filtered and ranked list of songs
     */
    fun searchSongs(songs: List<Song>, query: String): List<Song> {
        if (query.isBlank()) return songs
        
        val normalizedQuery = normalizeString(query)
        val queryTokens = normalizedQuery.split(" ").filter { it.isNotBlank() }
        
        return songs
            .mapNotNull { song ->
                val score = calculateMatchScore(song, queryTokens, normalizedQuery)
                if (score > 0) song to score else null
            }
            .sortedByDescending { it.second }
            .map { it.first }
    }
    
    /**
     * Calculate match score for a song
     */
    private fun calculateMatchScore(
        song: Song,
        queryTokens: List<String>,
        fullQuery: String
    ): Int {
        var score = 0
        
        val normalizedTitle = normalizeString(song.title)
        val normalizedArtist = normalizeString(song.artist)
        val normalizedAlbum = normalizeString(song.album)
        
        // Exact matches (highest priority)
        if (normalizedTitle == fullQuery) score += 100
        if (normalizedArtist == fullQuery) score += 80
        if (normalizedAlbum == fullQuery) score += 70
        
        // Starts with query
        if (normalizedTitle.startsWith(fullQuery)) score += 50
        if (normalizedArtist.startsWith(fullQuery)) score += 40
        if (normalizedAlbum.startsWith(fullQuery)) score += 30
        
        // Contains query
        if (fullQuery in normalizedTitle) score += 25
        if (fullQuery in normalizedArtist) score += 20
        if (fullQuery in normalizedAlbum) score += 15
        
        // Token matching (for multi-word queries)
        queryTokens.forEach { token ->
            if (token in normalizedTitle) score += 10
            if (token in normalizedArtist) score += 8
            if (token in normalizedAlbum) score += 5
        }
        
        return score
    }
    
    /**
     * Normalize string for better matching
     * - Remove diacritics
     * - Lowercase
     * - Trim whitespace
     */
    private fun normalizeString(text: String): String {
        return text
            .lowercase()
            .trim()
            .replace(Regex("[àáạảãâầấậẩẫăằắặẳẵ]"), "a")
            .replace(Regex("[èéẹẻẽêềếệểễ]"), "e")
            .replace(Regex("[ìíịỉĩ]"), "i")
            .replace(Regex("[òóọỏõôồốộổỗơờớợởỡ]"), "o")
            .replace(Regex("[ùúụủũưừứựửữ]"), "u")
            .replace(Regex("[ỳýỵỷỹ]"), "y")
            .replace(Regex("[đ]"), "d")
            // Add more character replacements as needed
    }
    
    /**
     * Check if a song matches the query
     */
    fun matches(song: Song, query: String): Boolean {
        if (query.isBlank()) return true
        
        val normalizedQuery = normalizeString(query)
        val normalizedTitle = normalizeString(song.title)
        val normalizedArtist = normalizeString(song.artist)
        val normalizedAlbum = normalizeString(song.album)
        
        return normalizedQuery in normalizedTitle ||
               normalizedQuery in normalizedArtist ||
               normalizedQuery in normalizedAlbum
    }
    
    /**
     * Group search results by category
     */
    fun groupResults(songs: List<Song>): SearchResults {
        val allResults = songs.distinctBy { it.id }
        
        return SearchResults(
            all = allResults,
            byArtist = allResults.groupBy { it.artist }.map { (artist, songs) ->
                ArtistGroup(artist, songs)
            }.sortedByDescending { it.songs.size },
            byAlbum = allResults.groupBy { it.album }.map { (album, songs) ->
                AlbumGroup(album, songs)
            }.sortedByDescending { it.songs.size }
        )
    }
    
    data class SearchResults(
        val all: List<Song>,
        val byArtist: List<ArtistGroup>,
        val byAlbum: List<AlbumGroup>
    )
    
    data class ArtistGroup(
        val artist: String,
        val songs: List<Song>
    )
    
    data class AlbumGroup(
        val album: String,
        val songs: List<Song>
    )
}
