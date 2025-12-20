package gc.txa.demo.update

import gc.txa.demo.core.TXAHttp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

object TXADownloadUrlResolver {

    /**
     * Resolve MediaFire download URL from page URL
     */
    suspend fun resolveMediaFireUrl(pageUrl: String): String? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(pageUrl)
                .get()
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()

            val response = TXAHttp.getClient().newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext null
            }

            val html = response.body?.string() ?: return@withContext null

            // Pattern to find direct download link
            // MediaFire uses: https://download####.mediafire.com/.../*.apk
            val pattern = Regex("""https://download[0-9]+\.mediafire\.com/[^"'\s>]*\.apk""")
            val matchResult = pattern.find(html)

            return@withContext matchResult?.value
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        }
    }

    /**
     * Resolve download URL based on URL type
     */
    suspend fun resolveUrl(url: String): String? {
        return when {
            url.contains("mediafire.com/file/") -> resolveMediaFireUrl(url)
            url.endsWith(".apk") -> url // Direct APK link
            else -> null
        }
    }
}
