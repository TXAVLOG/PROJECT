package com.txapp.musicplayer.network

import android.content.Context
import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder
import com.txapp.musicplayer.util.TXALogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cache
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Service để tải ảnh nghệ sĩ từ Deezer API
 * Fallback về album art local nếu không tìm thấy
 */
object ArtistImageService {
    
    private const val TAG = "ArtistImageService"
    private const val DEEZER_BASE_URL = "https://api.deezer.com/"
    
    private var deezerApi: DeezerApi? = null
    
    /**
     * Initialize service với context
     */
    fun init(context: Context) {
        if (deezerApi == null) {
            val client = createOkHttpClient(context)
            val gson = GsonBuilder()
                .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
                .create()
                
            deezerApi = Retrofit.Builder()
                .baseUrl(DEEZER_BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build()
                .create(DeezerApi::class.java)
            TXALogger.appI(TAG, "Deezer API initialized")
        }
    }
    
    private fun createOkHttpClient(context: Context): OkHttpClient {
        val cacheDir = File(context.cacheDir, "okhttp-deezer")
        if (!cacheDir.exists()) cacheDir.mkdirs()
        
        return OkHttpClient.Builder()
            .cache(Cache(cacheDir, 10 * 1024 * 1024)) // 10MB cache
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .addInterceptor(createCacheInterceptor())
            .build()
    }
    
    private fun createCacheInterceptor(): Interceptor {
        return Interceptor { chain ->
            val request = chain.request().newBuilder()
                .addHeader(
                    "Cache-Control",
                    String.format(Locale.getDefault(), "max-age=31536000, max-stale=31536000")
                )
                .build()
            chain.proceed(request)
        }
    }
    
    /**
     * Get artist image URL from Deezer
     * Returns null if not found
     */
    suspend fun getArtistImageUrl(artistName: String): String? = withContext(Dispatchers.IO) {
        if (artistName.isBlank() || artistName.equals("Unknown", ignoreCase = true)) {
            return@withContext null
        }
        
        try {
            // Handle multiple artists (e.g., "Artist1 & Artist2")
            val cleanName = artistName.split(",", "&", "feat.", "ft.")
                .firstOrNull()?.trim() ?: artistName
            
            val response = deezerApi?.searchArtist(cleanName)
            
            if (response != null && response.data.isNotEmpty()) {
                val artist = response.data[0]
                
                // Check if it's a placeholder image
                val imageUrl = getHighestQualityImage(artist)
                if (!imageUrl.contains("/images/artist//")) {
                    TXALogger.appD(TAG, "Found artist image for '$artistName': $imageUrl")
                    return@withContext imageUrl
                }
            }
            
            TXALogger.appD(TAG, "No image found for artist: $artistName")
            null
        } catch (e: Exception) {
            TXALogger.appE(TAG, "Error fetching artist image for '$artistName'", e)
            null
        }
    }
    
    private fun getHighestQualityImage(artist: DeezerArtistData): String {
        return when {
            !artist.pictureXl.isNullOrEmpty() -> artist.pictureXl
            !artist.pictureBig.isNullOrEmpty() -> artist.pictureBig
            !artist.pictureMedium.isNullOrEmpty() -> artist.pictureMedium
            !artist.pictureSmall.isNullOrEmpty() -> artist.pictureSmall
            !artist.picture.isNullOrEmpty() -> artist.picture
            else -> ""
        }
    }
}

// Deezer API Interface
private interface DeezerApi {
    @GET("search/artist?limit=1")
    suspend fun searchArtist(@Query("q") artistName: String): DeezerApiResponse
}

// Response models - using snake_case field names that match Gson FieldNamingPolicy
data class DeezerApiResponse(
    val data: List<DeezerArtistData> = emptyList()
)

data class DeezerArtistData(
    val id: Long = 0,
    val name: String = "",
    val picture: String? = null,
    val pictureSmall: String? = null,
    val pictureMedium: String? = null,
    val pictureBig: String? = null,
    val pictureXl: String? = null
)

