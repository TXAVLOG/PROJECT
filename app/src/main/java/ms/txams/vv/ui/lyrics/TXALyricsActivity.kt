package ms.txams.vv.ui.lyrics

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import dagger.hilt.android.AndroidEntryPoint
import ms.txams.vv.R
import timber.log.Timber

/**
 * TXA Lyrics Activity - Placeholder implementation
 * TODO: Implement lyrics display with synchronized scrolling
 */
@AndroidEntryPoint
class TXALyricsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_txa_lyrics)
        
        Timber.d("TXALyricsActivity created - TODO: Implement lyrics functionality")
        
        setupUI()
    }

    private fun setupUI() {
        // TODO: Setup lyrics display, synchronization, and UI interactions
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
    }

    companion object {
        private const val TAG = "TXALyricsActivity"
    }
}
