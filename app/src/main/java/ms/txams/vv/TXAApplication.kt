package ms.txams.vv

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * TXA Application class
 * Khởi tạo Hilt dependency injection và WorkManager configuration
 */
@HiltAndroidApp
class TXAApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override fun onCreate() {
        super.onCreate()
        
        // Initialize crash reporting if needed
        // Initialize analytics if needed
        // Initialize debug tools in debug builds
    }

    override fun getWorkManagerConfiguration(): Configuration {
        return Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
    }
}
