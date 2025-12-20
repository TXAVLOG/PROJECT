package gc.txa.demo.update

import android.content.Context
import gc.txa.demo.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object TXAUpdateManager {

    // FORCE TEST MODE - Always return update available
    private const val FORCE_TEST_MODE = true
    private const val TEST_VERSION_NAME = "3.0.0_txa"
    private const val TEST_CHANGELOG = "Phiên bản thử nghiệm 3.0.0_txa. Hỗ trợ MediaFire Resolver & GitHub Blob & Google Drive & File Manager UI."
    
    // Test URLs for different resolvers (MediaFire, GitHub, Google Drive)
    private val TEST_DOWNLOAD_URLS = listOf(
        "https://www.mediafire.com/file/jdy9nl8o6uqoyvq/TXA_AUTHENTICATOR_3.0.0_txa.apk/file"
    )
    private const val TEST_DOWNLOAD_URL = TEST_DOWNLOAD_URLS[0] // Default to MediaFire

    /**
     * Check for updates
     */
    suspend fun checkForUpdate(context: Context): UpdateCheckResult = withContext(Dispatchers.IO) {
        try {
            if (FORCE_TEST_MODE) {
                // Force test mode - always return update available
                return@withContext UpdateCheckResult.UpdateAvailable(
                    UpdateInfo(
                        versionName = TEST_VERSION_NAME,
                        versionCode = 300,
                        downloadUrl = TEST_DOWNLOAD_URL,
                        changelog = TEST_CHANGELOG,
                        fileSize = 0L, // Unknown until resolved
                        isForced = false
                    )
                )
            }

            // Real implementation would call API here
            // For now, return no update in non-test mode
            return@withContext UpdateCheckResult.NoUpdate

        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext UpdateCheckResult.Error(e.message ?: "Unknown error")
        }
    }

    /**
     * Get current app version
     */
    fun getCurrentVersion(): String {
        return BuildConfig.VERSION_NAME
    }

    /**
     * Get current version code
     */
    fun getCurrentVersionCode(): Int {
        return BuildConfig.VERSION_CODE
    }

    /**
     * Update check result
     */
    sealed class UpdateCheckResult {
        data class UpdateAvailable(val updateInfo: UpdateInfo) : UpdateCheckResult()
        object NoUpdate : UpdateCheckResult()
        data class Error(val message: String) : UpdateCheckResult()
    }

    /**
     * Update information
     */
    data class UpdateInfo(
        val versionName: String,
        val versionCode: Int,
        val downloadUrl: String,
        val changelog: String,
        val fileSize: Long,
        val isForced: Boolean
    )
}
