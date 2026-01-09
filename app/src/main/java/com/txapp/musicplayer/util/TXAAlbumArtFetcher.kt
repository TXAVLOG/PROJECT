package com.txapp.musicplayer.util

import android.content.Context
import android.net.Uri
import com.txapp.musicplayer.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * TXA Album Art Fetcher
 * Fetches album art from the web (iTunes API) when local art is missing.
 */
object TXAAlbumArtFetcher {

    private const val TAG = "TXAAlbumArtFetcher"
    private const val API_URL_TEMPLATE = "https://itunes.apple.com/search?term=%s&media=music&entity=album&limit=1"

    suspend fun fetchAlbumArtUrl(song: Song): String? = withContext(Dispatchers.IO) {
        if (!TXAPreferences.isAutoDownloadImagesEnabled) {
            TXALogger.albumArtD(TAG, "Auto download disabled, skipping")
            return@withContext null
        }

        val cleanQuery = cleanQuery("${song.artist} ${song.title}")
        TXALogger.albumArtI(TAG, "Searching album art for: \"$cleanQuery\" (Song: ${song.title})")

        // 1. Try iTunes (Highest Quality)
        TXALogger.albumArtD(TAG, "Trying iTunes...")
        val itunesUrl = fetchFromItunes(cleanQuery)
        if (itunesUrl != null) {
            TXALogger.albumArtI(TAG, "Found on iTunes: $itunesUrl")
            return@withContext itunesUrl
        }

        // 2. Try ZingMP3 (Good for VN songs)
        TXALogger.albumArtD(TAG, "Trying ZingMP3...")
        val zingUrl = fetchFromZingMp3(cleanQuery)
        if (zingUrl != null) {
            TXALogger.albumArtI(TAG, "Found on ZingMP3: $zingUrl")
            return@withContext zingUrl
        }
        
        // 3. Try Nhaccuatui (Fallback for VN songs)
        TXALogger.albumArtD(TAG, "Trying NhacCuaTui...")
        val nctUrl = fetchFromNCT(cleanQuery)
        if (nctUrl != null) {
            TXALogger.albumArtI(TAG, "Found on NCT: $nctUrl")
            return@withContext nctUrl
        }

        TXALogger.albumArtI(TAG, "No album art found for: \"$cleanQuery\"")
        return@withContext null
    }

    private suspend fun fetchFromItunes(query: String): String? {
        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val urlString = String.format(API_URL_TEMPLATE, encodedQuery)
            
            val json = getJsonFromUrl(urlString) ?: return null
            
            if (json.has("resultCount") && json.getInt("resultCount") > 0) {
                val results = json.getJSONArray("results")
                val item = results.getJSONObject(0)
                
                if (item.has("artworkUrl100")) {
                    return item.getString("artworkUrl100").replace("100x100bb", "600x600bb")
                }
            }
        } catch (e: Exception) {
            TXALogger.albumArtE(TAG, "Error fetching iTunes art: ${e.message}", e)
        }
        return null
    }

    private suspend fun fetchFromZingMp3(query: String): String? {
        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val urlString = "http://ac.mp3.zing.vn/complete?type=artist,song&num=3&query=$encodedQuery"
            
            val json = getJsonFromUrl(urlString) ?: return null

            if (json.has("data")) {
                val data = json.getJSONArray("data")
                if (data.length() > 0) {
                    val songItem = data.getJSONObject(0)
                    if (songItem.has("song")) {
                         val songs = songItem.getJSONArray("song")
                         if (songs.length() > 0) {
                             val song = songs.getJSONObject(0)
                             if (song.has("thumb")) {
                                 return song.getString("thumb").replace("w94", "w600")
                             }
                         }
                    }
                }
            }
        } catch (e: Exception) {
             TXALogger.albumArtE(TAG, "Error fetching ZingMP3 art: ${e.message}", e)
        }
        return null
    }
    
    private suspend fun fetchFromNCT(query: String): String? {
        try {
             val encodedQuery = URLEncoder.encode(query, "UTF-8")
             val urlString = "https://m.nhaccuatui.com/ajax/search_suggest?q=$encodedQuery"
             
             val json = getJsonFromUrl(urlString) ?: return null
             
             if (json.has("data")) {
                 val data = json.getJSONObject("data")
                 if (data.has("song")) {
                     val songs = data.getJSONArray("song")
                     if (songs.length() > 0) {
                         val song = songs.getJSONObject(0)
                         if (song.has("thumb")) {
                             return song.getString("thumb")
                         }
                     }
                 }
             }

        } catch (e: Exception) {
             TXALogger.albumArtE(TAG, "Error fetching NCT art: ${e.message}", e)
        }
        return null
    }

    private fun getJsonFromUrl(urlString: String): JSONObject? {
        try {
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            
            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                return JSONObject(response)
            } else {
                TXALogger.albumArtD(TAG, "HTTP ${connection.responseCode} from $urlString")
            }
        } catch (e: Exception) {
             TXALogger.albumArtE(TAG, "Network error for $urlString: ${e.message}")
        }
        return null
    }

    private fun cleanQuery(query: String): String {
        return query.replace(Regex("(?i)\\b(feat\\.|ft\\.|original|mix|remix|mv|official|video|lyric)\\b.*"), "")
            .replace(Regex("[^a-zA-Z0-9 \\p{L}]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}

