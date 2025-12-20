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

            val response = TXAHttp.client.newCall(request).execute()
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
     * Resolve GitHub blob URL to raw URL
     */
    suspend fun resolveGitHubUrl(pageUrl: String): String? = withContext(Dispatchers.IO) {
        try {
            // Convert blob URL to raw URL
            // https://github.com/user/repo/blob/main/file.apk
            // -> https://raw.githubusercontent.com/user/repo/main/file.apk
            val rawUrl = pageUrl
                .replace("github.com", "raw.githubusercontent.com")
                .replace("/blob/", "/")
            
            // Test if the raw URL is accessible
            val request = Request.Builder()
                .url(rawUrl)
                .head()
                .build()

            val response = TXAHttp.client.newCall(request).execute()
            return@withContext if (response.isSuccessful) rawUrl else null
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        }
    }

    /**
     * Resolve Google Drive URL to direct download URL
     */
    suspend fun resolveGoogleDriveUrl(pageUrl: String): String? = withContext(Dispatchers.IO) {
        try {
            // Extract file ID from URL
            val idPattern = Regex("""drive\.google\.com/file/d/([a-zA-Z0-9_-]+)""")
            val matchResult = idPattern.find(pageUrl)
            val fileId = matchResult?.groupValues?.get(1) ?: return@withContext null

            // Try direct download URL first
            val directUrl = "https://drive.google.com/uc?export=download&id=$fileId"
            
            val request = Request.Builder()
                .url(directUrl)
                .get()
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()

            val response = TXAHttp.client.newCall(request).execute()
            
            if (response.isSuccessful) {
                val contentType = response.header("Content-Type")
                val hasApkContentType = contentType?.contains("application/vnd.android.package-archive") == true
                val hasApkDisposition = response.header("Content-Disposition")?.contains(".apk") == true
                if (hasApkContentType || hasApkDisposition) {
                    return@withContext directUrl
                }
            }

            // If direct URL doesn't work, parse confirmation page
            if (response.isSuccessful) {
                val html = response.body?.string() ?: return@withContext null
                
                // Look for download link in confirmation page
                val downloadPattern = Regex("""https://download[0-9]*\.googleusercontent\.com/[^"'\s>]*\.apk""")
                val downloadMatch = downloadPattern.find(html)
                
                if (downloadMatch != null) {
                    return@withContext downloadMatch.value
                }
                
                // Alternative pattern for large files
                val confirmPattern = Regex("""id="uc-download-link"[^>]*href="([^"]+)"""")
                val confirmMatch = confirmPattern.find(html)
                
                if (confirmMatch != null) {
                    val confirmUrl = confirmMatch.groupValues[1]
                    return@withContext if (confirmUrl.startsWith("http")) confirmUrl else "https://drive.google.com$confirmUrl"
                }
            }

            return@withContext null
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
            url.contains("github.com") && url.contains("/blob/") -> resolveGitHubUrl(url)
            url.contains("raw.githubusercontent.com") -> url // Direct raw GitHub URL
            url.contains("drive.google.com/file/") -> resolveGoogleDriveUrl(url)
            url.contains("drive.google.com/uc?export=download") -> url // Direct Google Drive URL
            url.endsWith(".apk") -> url // Direct APK link
            else -> null
        }
    }
}
