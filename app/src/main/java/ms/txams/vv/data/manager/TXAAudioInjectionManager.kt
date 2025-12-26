package ms.txams.vv.data.manager

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import dagger.hilt.android.qualifiers.ApplicationContext
import ms.txams.vv.data.database.SongEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TXAAudioInjectionManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val introAssetName = "intro_txa.mp3"
    
    fun checkIntegrity(): Boolean {
        return try {
            context.assets.open(introAssetName).close()
            true
        } catch (e: Exception) {
            false
        }
    }

    fun getIntroUri(): Uri {
        return Uri.parse("asset:///$introAssetName")
    }

    // Simplified as we now use List<MediaItem> in Service
    fun buildConcatenatedSource(song: SongEntity, mediaSourceFactory: DefaultMediaSourceFactory): MediaSource {
        // Legacy method if using manual source construction
        return mediaSourceFactory.createMediaSource(MediaItem.fromUri(song.path))
    }
}
