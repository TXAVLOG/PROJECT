package ms.txams.vv.core

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class TXAApp : Application() {
    
    override fun onCreate() {
        super.onCreate()
        // Initialize logger first for crash logging
        TXALogger.init(this)
        TXALogger.appI("TXA Music App starting...")
        
        TXATranslation.init(this)
    }
}
