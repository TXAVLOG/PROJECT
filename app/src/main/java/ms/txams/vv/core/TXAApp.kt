package ms.txams.vv.core

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class TXAApp : Application() {
    
    override fun onCreate() {
        super.onCreate()
        TXATranslation.init(this)
    }
}
