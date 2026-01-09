package com.txapp.musicplayer.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.util.regex.Pattern

/**
 * TXA Download URL Resolver
 * Resolves URLs from various sources to direct download links.
 */
object TXADownloadUrlResolver {

    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    suspend fun resolve(url: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            TXALogger.resolveI("UrlResolver", "Attempting to resolve: $url")
            
            val result = when {
                url.contains("drive.google.com") -> resolveGoogleDrive(url)
                url.contains("github.com") && url.contains("/releases/download/") -> Result.success(url)
                url.contains("github.com") && (url.contains("/blob/") || url.contains("/raw/")) -> resolveGitHub(url)
                url.contains("mediafire.com") -> resolveMediafire(url)
                else -> Result.success(url)
            }
            
            result.onSuccess { 
                TXALogger.resolveI("UrlResolver", "Resolved successfully: $it")
            }.onFailure {
                TXALogger.resolveE("UrlResolver", "Resolve failed for $url: ${it.message}")
            }
        } catch (e: Exception) {
            TXALogger.resolveE("UrlResolver", "Fatal resolve error for $url", e)
            Result.failure(e)
        }
    }

    private fun resolveGoogleDrive(url: String): Result<String> {
        val fileId = findMatch(url, "/d/([^/]+)") ?: findMatch(url, "id=([^&]+)")
        return if (fileId != null) {
            Result.success("https://docs.google.com/uc?export=download&id=$fileId")
        } else {
            Result.failure(Exception("Invalid Google Drive URL"))
        }
    }

    private fun resolveGitHub(url: String): Result<String> {
        val directUrl = url.replace("github.com", "raw.githubusercontent.com")
            .replace("/blob/", "/")
        return Result.success(directUrl)
    }

    private suspend fun resolveMediafire(url: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val document = Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .timeout(10000)
                .get()

            val downloadLink = document.select("a#downloadButton").attr("href")
                .ifEmpty { document.select(".input_btn_color").attr("href") }
                .ifEmpty { document.select("a[href^=https://download]").attr("href") }

            if (downloadLink.isNotEmpty() && downloadLink.startsWith("http")) {
                Result.success(downloadLink)
            } else {
                Result.failure(Exception("Could not find Mediafire download link in HTML"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Mediafire parse failed: ${e.message}"))
        }
    }

    private fun findMatch(url: String, regex: String): String? {
        val pattern = Pattern.compile(regex)
        val matcher = pattern.matcher(url)
        return if (matcher.find()) matcher.group(1) else null
    }
}
