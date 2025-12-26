package ms.txams.vv.core

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * TXA HTTP Client - Singleton HTTP client for TXA Music
 * 
 * Features:
 * - Singleton OkHttpClient with optimized timeouts
 * - Request builder helpers
 * 
 * @author TXA - fb.com/vlog.txa.2311 - txavlog7@gmail.com
 */
object TXAHttp {
    
    /**
     * Singleton OkHttpClient with optimized timeouts
     * - Connect: 15 seconds
     * - Read: 30 seconds
     * - Write: 30 seconds
     */
    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .build()
    }
    
    /**
     * Client without auto-redirect (for URL resolver)
     */
    val noRedirectClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .retryOnConnectionFailure(true)
            .build()
    }
    
    /**
     * Build GET request with standard headers
     */
    fun buildGet(url: String): Request {
        return Request.Builder()
            .url(url)
            .header("User-Agent", "TXAMusic/1.0 (Android)")
            .header("Accept", "application/json, text/plain, */*")
            .get()
            .build()
    }
    
    /**
     * Build GET request with desktop User-Agent (for URL resolving)
     */
    fun buildDesktopGet(url: String): Request {
        return Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .get()
            .build()
    }
    
    /**
     * Build POST request with JSON body
     */
    fun buildPost(url: String, jsonBody: String): Request {
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body = jsonBody.toRequestBody(mediaType)
        
        return Request.Builder()
            .url(url)
            .header("User-Agent", "TXAMusic/1.0 (Android)")
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .post(body)
            .build()
    }
}
