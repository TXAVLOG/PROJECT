package gc.txa.demo.update

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import gc.txa.demo.core.TXAHttp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TXAUpdateWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            TXAHttp.logInfo(applicationContext, "TXAUpdateWorker", "Checking for updates in background")

            val result = TXAUpdateManager.checkForUpdate(applicationContext)
            
            when (result) {
                is TXAUpdateManager.UpdateCheckResult.UpdateAvailable -> {
                    TXAHttp.logInfo(
                        applicationContext,
                        "TXAUpdateWorker",
                        "Update available: ${result.updateInfo.versionName}"
                    )
                    // Could show notification here
                }
                is TXAUpdateManager.UpdateCheckResult.NoUpdate -> {
                    TXAHttp.logInfo(applicationContext, "TXAUpdateWorker", "No update available")
                }
                is TXAUpdateManager.UpdateCheckResult.Error -> {
                    TXAHttp.logInfo(
                        applicationContext,
                        "TXAUpdateWorker",
                        "Update check failed: ${result.message}"
                    )
                }
            }

            return@withContext Result.success()
        } catch (e: Exception) {
            TXAHttp.logError(applicationContext, "TXAUpdateWorker", e)
            return@withContext Result.retry()
        }
    }
}
