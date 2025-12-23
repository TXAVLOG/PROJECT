package ms.txams.vv.core

import android.util.Log
import timber.log.Timber

/**
 * TXA Log utility class - minimal implementation for compilation
 */
object TXALog {
    
    fun d(tag: String, message: String) {
        Timber.d("[$tag] $message")
    }
    
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Timber.e(throwable, "[$tag] $message")
        } else {
            Timber.e("[$tag] $message")
        }
    }
    
    fun i(tag: String, message: String) {
        Timber.i("[$tag] $message")
    }
    
    fun w(tag: String, message: String) {
        Timber.w("[$tag] $message")
    }
}
