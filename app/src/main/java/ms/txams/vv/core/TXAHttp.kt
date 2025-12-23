package ms.txams.vv.core

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * TXA HTTP Client - HTTP wrapper cho OTA translations và API calls
 * Sử dụng OkHttp với timeout và retry logic
 */
object TXAHttp {

    private const val TAG = "TXAHttp"
    private const val DEFAULT_TIMEOUT = 30_000L // 30 seconds
    private const val MAX_RETRIES = 3
    private const val RETRY_DELAY_MS = 1000L

    private val client = OkHttpClient.Builder()
        .connectTimeout(DEFAULT_TIMEOUT, TimeUnit.MILLISECONDS)
        .readTimeout(DEFAULT_TIMEOUT, TimeUnit.MILLISECONDS)
        .writeTimeout(DEFAULT_TIMEOUT, TimeUnit.MILLISECONDS)
        .addInterceptor(UserAgentInterceptor())
        .addInterceptor(LoggingInterceptor())
        .build()

    /**
     * GET request với retry logic
     */
    suspend fun get(url: String, headers: Map<String, String> = emptyMap()): String {
        return withContext(Dispatchers.IO) {
            var lastException: Exception? = null
            
            repeat(MAX_RETRIES) { attempt ->
                try {
                    val request = Request.Builder()
                        .url(url)
                        .apply {
                            headers.forEach { (key, value) ->
                                addHeader(key, value)
                            }
                        }
                        .get()
                        .build()

                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            throw IOException("HTTP ${response.code}: ${response.message}")
                        }
                        
                        val body = response.body?.string()
                            ?: throw IOException("Empty response body")
                        
                        Log.d(TAG, "GET $url - Success (attempt ${attempt + 1})")
                        return@withContext body
                    }
                } catch (e: Exception) {
                    lastException = e
                    Log.w(TAG, "GET $url - Attempt ${attempt + 1} failed", e)
                    
                    if (attempt < MAX_RETRIES - 1) {
                        kotlinx.coroutines.delay(RETRY_DELAY_MS * (attempt + 1))
                    }
                }
            }
            
            throw lastException ?: IOException("All retry attempts failed")
        }
    }

    /**
     * POST request với JSON body
     */
    suspend fun post(url: String, jsonBody: String, headers: Map<String, String> = emptyMap()): String {
        return withContext(Dispatchers.IO) {
            var lastException: Exception? = null
            
            repeat(MAX_RETRIES) { attempt ->
                try {
                    val mediaType = "application/json; charset=utf-8".toMediaType()
                    val requestBody = jsonBody.toRequestBody(mediaType)
                    
                    val request = Request.Builder()
                        .url(url)
                        .apply {
                            headers.forEach { (key, value) ->
                                addHeader(key, value)
                            }
                        }
                        .post(requestBody)
                        .build()

                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            throw IOException("HTTP ${response.code}: ${response.message}")
                        }
                        
                        val body = response.body?.string()
                            ?: throw IOException("Empty response body")
                        
                        Log.d(TAG, "POST $url - Success (attempt ${attempt + 1})")
                        return@withContext body
                    }
                } catch (e: Exception) {
                    lastException = e
                    Log.w(TAG, "POST $url - Attempt ${attempt + 1} failed", e)
                    
                    if (attempt < MAX_RETRIES - 1) {
                        kotlinx.coroutines.delay(RETRY_DELAY_MS * (attempt + 1))
                    }
                }
            }
            
            throw lastException ?: IOException("All retry attempts failed")
        }
    }

    /**
     * Download file với progress tracking
     */
    suspend fun downloadFile(
        url: String,
        outputFile: java.io.File,
        onProgress: (bytesRead: Long, contentLength: Long) -> Unit = { _, _ -> }
    ): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .get()
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("HTTP ${response.code}: ${response.message}")
                    }
                    
                    val contentLength = response.body?.contentLength() ?: -1L
                    val inputStream = response.body?.byteStream()
                        ?: throw IOException("Empty response body")
                    
                    outputFile.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Long = 0
                        
                        while (true) {
                            val count = inputStream.read(buffer)
                            if (count == -1) break
                            
                            output.write(buffer, 0, count)
                            bytesRead += count
                            
                            onProgress(bytesRead, contentLength)
                        }
                    }
                    
                    Log.d(TAG, "Download completed: $url -> ${outputFile.absolutePath}")
                    true
                }
            } catch (e: Exception) {
                Log.e(TAG, "Download failed: $url", e)
                false
            }
        }
    }

    /**
     * Check if URL is reachable
     */
    suspend fun isUrlReachable(url: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .head()
                    .build()

                client.newCall(request).execute().use { response ->
                    response.isSuccessful
                }
            } catch (e: Exception) {
                Log.w(TAG, "URL not reachable: $url", e)
                false
            }
        }
    }

    /**
     * Get file size from URL headers
     */
    suspend fun getFileSize(url: String): Long {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .head()
                    .build()

                client.newCall(request).execute().use { response ->
                    response.header("Content-Length")?.toLongOrNull() ?: -1L
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to get file size: $url", e)
                -1L
            }
        }
    }

    /**
     * Custom interceptor for User-Agent
     */
    private class UserAgentInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val originalRequest = chain.request()
            val requestWithUserAgent = originalRequest.newBuilder()
                .header("User-Agent", "TXA-Music/1.0.0 (Android)")
                .build()
            return chain.proceed(requestWithUserAgent)
        }
    }

    /**
     * Custom interceptor for logging
     */
    private class LoggingInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            val startTime = System.currentTimeMillis()
            
            val response = chain.proceed(request)
            val endTime = System.currentTimeMillis()
            
            Log.d(TAG, "${request.method} ${request.url} - ${response.code} (${endTime - startTime}ms)")
            
            return response
        }
    }

    /**
     * Cancel all ongoing requests
     */
    fun cancelAll() {
        client.dispatcher.cancelAll()
    }

    /**
     * Get connection pool info
     */
    fun getConnectionInfo(): String {
        return "Connection Pool: ${client.connectionPool.connectionCount()} connections, " +
                "${client.connectionPool.idleConnectionCount()} idle"
    }
}
