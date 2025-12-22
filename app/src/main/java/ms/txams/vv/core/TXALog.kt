package ms.txams.vv.core

import android.util.Log

/**
 * Centralized logger that prefixes all tags for easier filtering.
 */
object TXALog {

    private const val TAG_PREFIX = "TXA"

    private fun buildTag(tag: String): String = "$TAG_PREFIX-$tag"

    fun d(tag: String, message: String) {
        Log.d(buildTag(tag), message)
    }

    fun i(tag: String, message: String) {
        Log.i(buildTag(tag), message)
    }

    fun w(tag: String, message: String) {
        Log.w(buildTag(tag), message)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.e(buildTag(tag), message, throwable)
        } else {
            Log.e(buildTag(tag), message)
        }
    }

    fun v(tag: String, message: String) {
        Log.v(buildTag(tag), message)
    }
}
