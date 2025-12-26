package ms.txams.vv.update

import ms.txams.vv.core.TXAHttp
import ms.txams.vv.core.TXALogger

/**
 * TXA Download URL Resolver
 * 
 * Resolves direct download URLs from various sources:
 * - DIRECT: Direct .apk links
 * - MEDIAFIRE: Parse HTML to find download button
 * - GOOGLE_DRIVE: Extract fileId and build direct link
 * - GITHUB: Handle /blob/, /raw/, /releases/download/
 * - UNKNOWN: Follow redirects manually
 * 
 * Security Note: Does NOT use DownloadManager due to Google security warnings
 * 
 * @author TXA - fb.com/vlog.txa.2311 - txavlog7@gmail.com
 */
object TXADownloadUrlResolver {
    
    private const val MAX_REDIRECTS = 10
    
    /**
     * Link types
     */
    enum class LinkType {
        DIRECT,
        MEDIAFIRE,
        GOOGLE_DRIVE,
        GITHUB,
        UNKNOWN
    }
    
    /**
     * Resolve result
     */
    sealed class ResolveResult {
        data class Success(val directUrl: String, val fileName: String?) : ResolveResult()
        data class Error(val message: String, val originalUrl: String) : ResolveResult()
    }
    
    /**
     * Detect link type from URL
     */
    fun detectLinkType(url: String): LinkType {
        val normalized = url.lowercase()
        return when {
            normalized.contains("mediafire.com") -> LinkType.MEDIAFIRE
            normalized.contains("drive.google.com") -> LinkType.GOOGLE_DRIVE
            normalized.contains("github.com") && (
                normalized.contains("/releases/") ||
                normalized.contains("/blob/") ||
                normalized.contains("/raw/")
            ) -> LinkType.GITHUB
            normalized.endsWith(".apk") -> LinkType.DIRECT
            else -> LinkType.UNKNOWN
        }
    }
    
    /**
     * Resolve URL to direct download link
     */
    fun resolve(url: String): ResolveResult {
        TXALogger.downloadD("Resolving URL: $url")
        val linkType = detectLinkType(url)
        TXALogger.downloadD("Link type: $linkType")
        
        return when (linkType) {
            LinkType.DIRECT -> ResolveResult.Success(url, extractFileName(url))
            LinkType.MEDIAFIRE -> resolveMediaFire(url)
            LinkType.GOOGLE_DRIVE -> resolveGoogleDrive(url)
            LinkType.GITHUB -> resolveGitHub(url)
            LinkType.UNKNOWN -> followRedirects(url)
        }
    }
    
    /**
     * Resolve MediaFire link
     * Parse HTML to find download button
     */
    private fun resolveMediaFire(url: String): ResolveResult {
        TXALogger.downloadD("Resolving MediaFire: $url")
        
        return try {
            val request = TXAHttp.buildDesktopGet(url)
            TXAHttp.client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return ResolveResult.Error("HTTP ${response.code}", url)
                }
                
                val html = response.body?.string() ?: ""
                
                // Try multiple patterns to find download URL
                val patterns = listOf(
                    // Pattern 1: downloadButton aria-label
                    """aria-label="Download file"[^>]*href="([^"]+)"""".toRegex(),
                    // Pattern 2: id="downloadButton"
                    """id="downloadButton"[^>]*href="([^"]+)"""".toRegex(),
                    // Pattern 3: Direct download domain
                    """href="(https?://download\d*\.mediafire\.com[^"]+)"""".toRegex(),
                    // Pattern 4: Any .apk link
                    """href="([^"]+\.apk[^"]*)"""".toRegex()
                )
                
                for (pattern in patterns) {
                    val match = pattern.find(html)
                    if (match != null) {
                        val directUrl = match.groupValues[1]
                        TXALogger.downloadI("MediaFire resolved: $directUrl")
                        return ResolveResult.Success(directUrl, extractFileName(directUrl))
                    }
                }
                
                ResolveResult.Error("Could not find download link in MediaFire page", url)
            }
        } catch (e: Exception) {
            TXALogger.downloadE("MediaFire resolve error", e)
            ResolveResult.Error(e.message ?: "Unknown error", url)
        }
    }
    
    /**
     * Resolve Google Drive link
     * Extract fileId and build direct download link
     */
    private fun resolveGoogleDrive(url: String): ResolveResult {
        TXALogger.downloadD("Resolving Google Drive: $url")
        
        return try {
            // Extract file ID from various patterns
            val fileId = extractGoogleDriveFileId(url)
            
            if (fileId == null) {
                return ResolveResult.Error("Could not extract Google Drive file ID", url)
            }
            
            // Build direct download URL with confirm parameter
            val directUrl = "https://drive.google.com/uc?export=download&id=$fileId&confirm=t"
            TXALogger.downloadI("Google Drive resolved: $directUrl")
            
            ResolveResult.Success(directUrl, null)
        } catch (e: Exception) {
            TXALogger.downloadE("Google Drive resolve error", e)
            ResolveResult.Error(e.message ?: "Unknown error", url)
        }
    }
    
    /**
     * Extract Google Drive file ID from URL
     */
    private fun extractGoogleDriveFileId(url: String): String? {
        // Pattern 1: /file/d/{id}
        val pattern1 = """/file/d/([a-zA-Z0-9_-]+)""".toRegex()
        pattern1.find(url)?.let { return it.groupValues[1] }
        
        // Pattern 2: id={id}
        val pattern2 = """id=([a-zA-Z0-9_-]+)""".toRegex()
        pattern2.find(url)?.let { return it.groupValues[1] }
        
        return null
    }
    
    /**
     * Resolve GitHub link
     * Handle /blob/, /raw/, /releases/download/
     */
    private fun resolveGitHub(url: String): ResolveResult {
        TXALogger.downloadD("Resolving GitHub: $url")
        
        return try {
            val modifiedUrl = when {
                // /blob/ -> /raw/
                url.contains("/blob/") -> url.replace("/blob/", "/raw/")
                // Already /raw/ or /releases/download/
                else -> url
            }
            
            // Follow redirects to get final URL
            followRedirects(modifiedUrl, "GitHub")
        } catch (e: Exception) {
            TXALogger.downloadE("GitHub resolve error", e)
            ResolveResult.Error(e.message ?: "Unknown error", url)
        }
    }
    
    /**
     * Follow redirects manually (for UNKNOWN links)
     */
    private fun followRedirects(url: String, source: String = "Unknown"): ResolveResult {
        TXALogger.downloadD("Following redirects for $source: $url")
        
        var currentUrl = url
        var redirectCount = 0
        
        try {
            while (redirectCount < MAX_REDIRECTS) {
                val request = TXAHttp.buildDesktopGet(currentUrl)
                
                TXAHttp.noRedirectClient.newCall(request).execute().use { response ->
                    when {
                        response.isSuccessful -> {
                            // Final URL found
                            TXALogger.downloadI("Final URL after $redirectCount redirects: $currentUrl")
                            return ResolveResult.Success(currentUrl, extractFileName(currentUrl))
                        }
                        response.isRedirect -> {
                            val location = response.header("Location")
                            if (location == null) {
                                return ResolveResult.Error("Redirect without Location header", url)
                            }
                            
                            // Handle relative URLs
                            currentUrl = if (location.startsWith("http")) {
                                location
                            } else if (location.startsWith("/")) {
                                val baseUrl = currentUrl.substringBefore("/", currentUrl.take(currentUrl.indexOf("/", 8)))
                                "$baseUrl$location"
                            } else {
                                location
                            }
                            
                            redirectCount++
                            TXALogger.downloadD("Redirect $redirectCount: $currentUrl")
                        }
                        else -> {
                            return ResolveResult.Error("HTTP ${response.code}: ${response.message}", url)
                        }
                    }
                }
            }
            
            return ResolveResult.Error("Too many redirects ($MAX_REDIRECTS)", url)
        } catch (e: Exception) {
            TXALogger.downloadE("Redirect follow error", e)
            return ResolveResult.Error(e.message ?: "Unknown error", url)
        }
    }
    
    /**
     * Extract filename from URL
     */
    private fun extractFileName(url: String): String? {
        return try {
            val path = url.substringBefore("?").substringAfterLast("/")
            if (path.isNotEmpty() && path.contains(".")) {
                path
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}
