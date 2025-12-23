package ms.txams.vv.core.audio

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.source.ConcatenatingMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ms.txams.vv.core.data.database.entity.TXASongEntity
import timber.log.Timber
import java.io.File

/**
 * TXA Audio Injection Manager - Xử lý branding audio injection
 * Features: Intro audio injection, metadata masking, gapless playback
 */
@UnstableApi
class TXAAudioInjectionManager(private val context: Context) {

    private val injectionScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isInitialized = false

    // Intro audio configuration
    private var introAudioPath: String? = null
    private var isBrandingEnabled: Boolean = true
    private var metadataMaskingEnabled: Boolean = true

    // Audio processing
    private var introMediaSource: MediaSource? = null

    fun initialize() {
        try {
            if (isInitialized) {
                Timber.w("TXAAudioInjectionManager already initialized")
                return
            }

            // Load intro audio from assets
            loadIntroAudio()
            
            // Setup audio processing components
            setupAudioComponents()
            
            isInitialized = true
            Timber.d("TXAAudioInjectionManager initialized successfully")
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize TXAAudioInjectionManager")
            throw e
        }
    }

    fun release() {
        try {
            if (!isInitialized) {
                return
            }

            // Release audio resources
            introMediaSource = null
            
            isInitialized = false
            Timber.d("TXAAudioInjectionManager released")
        } catch (e: Exception) {
            Timber.e(e, "Failed to release TXAAudioInjectionManager")
        }
    }

    private fun loadIntroAudio() {
        try {
            // Load intro audio from assets folder
            val introFileName = "intro_txa.mp3"
            val introFile = File(context.cacheDir, introFileName)
            
            if (!introFile.exists()) {
                // Copy intro audio from assets to cache
                copyIntroAudioFromAssets(introFileName, introFile)
            }
            
            introAudioPath = introFile.absolutePath
            Timber.d("Intro audio loaded from: $introAudioPath")
        } catch (e: Exception) {
            Timber.e(e, "Failed to load intro audio")
            introAudioPath = null
        }
    }

    private fun copyIntroAudioFromAssets(fileName: String, targetFile: File) {
        try {
            context.assets.open(fileName).use { inputStream ->
                targetFile.outputStream().use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to copy intro audio from assets")
            throw e
        }
    }

    private fun setupAudioComponents() {
        // Setup audio processing components for injection
        introAudioPath?.let { path ->
            val introUri = Uri.parse(path)
            introMediaSource = ProgressiveMediaSource.Factory(
                androidx.media3.datasource.DefaultDataSource.Factory(context)
            ).createMediaSource(MediaItem.fromUri(introUri))
        }
    }

    /**
     * Tạo branded media source với intro audio injection
     */
    fun createBrandedMediaSource(
        song: TXASongEntity,
        dataSourceFactory: DataSource.Factory
    ): MediaSource {
        if (!isInitialized || !isBrandingEnabled || introMediaSource == null) {
            return createSimpleMediaSource(song, dataSourceFactory)
        }

        return try {
            val songMediaSource = createSimpleMediaSource(song, dataSourceFactory)
            val concatenatedSource = ConcatenatingMediaSource()
            
            // Add intro audio first
            concatenatedSource.addMediaSource(introMediaSource!!)
            
            // Add main song with metadata masking
            val maskedSongSource = createMetadataMaskedSource(song, dataSourceFactory)
            concatenatedSource.addMediaSource(maskedSongSource)
            
            concatenatedSource
        } catch (e: Exception) {
            Timber.e(e, "Failed to create branded media source, falling back to simple source")
            createSimpleMediaSource(song, dataSourceFactory)
        }
    }

    private fun createSimpleMediaSource(
        song: TXASongEntity,
        dataSourceFactory: DataSource.Factory
    ): MediaSource {
        return ProgressiveMediaSource.Factory(dataSourceFactory)
            .createMediaSource(MediaItem.fromUri(Uri.parse(song.filePath)))
    }

    private fun createMetadataMaskedSource(
        song: TXASongEntity,
        dataSourceFactory: DataSource.Factory
    ): MediaSource {
        val mediaItem = MediaItem.Builder()
            .setUri(Uri.parse(song.filePath))
            .setMediaId(song.id.toString())
            .apply {
                if (metadataMaskingEnabled) {
                    // Mask metadata to show main song info during intro playback
                    setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(song.title)
                            .setArtist(song.artist)
                            .setAlbumTitle(song.album)
                            .build()
                    )
                }
            }
            .build()
            
        return ProgressiveMediaSource.Factory(dataSourceFactory)
            .createMediaSource(mediaItem)
    }

    /**
     * Configuration methods
     */
    fun setBrandingEnabled(enabled: Boolean) {
        isBrandingEnabled = enabled
        Timber.d("Branding enabled: $enabled")
    }

    fun setMetadataMaskingEnabled(enabled: Boolean) {
        metadataMaskingEnabled = enabled
        Timber.d("Metadata masking enabled: $enabled")
    }

    fun setIntroAudioPath(path: String?) {
        introAudioPath = path
        // Reload intro media source
        setupAudioComponents()
        Timber.d("Intro audio path set to: $path")
    }

    /**
     * Utility methods
     */
    fun isBrandingEnabled(): Boolean = isBrandingEnabled

    fun hasIntroAudio(): Boolean = introAudioPath != null && introMediaSource != null

    fun getIntroDuration(): Long {
        return try {
            // Would need to actually load and get duration of intro audio
            3000L // 3 seconds placeholder
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * Gapless playback preparation
     */
    fun prepareGaplessTransition(
        currentPlayer: androidx.media3.exoplayer.ExoPlayer,
        nextSong: TXASongEntity,
        dataSourceFactory: DataSource.Factory
    ) {
        injectionScope.launch {
            try {
                val nextMediaSource = createBrandedMediaSource(nextSong, dataSourceFactory)
                // Prepare next media source for gapless playback
                withContext(Dispatchers.Main) {
                    currentPlayer.prepare(nextMediaSource)
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to prepare gapless transition")
            }
        }
    }

    /**
     * Audio validation
     */
    fun validateAudioFile(filePath: String): Boolean {
        return try {
            val file = File(filePath)
            file.exists() && file.canRead() && file.length() > 0
        } catch (e: Exception) {
            Timber.e(e, "Failed to validate audio file: $filePath")
            false
        }
    }

    /**
     * Cache management
     */
    fun clearCache() {
        try {
            val cacheDir = context.cacheDir
            cacheDir.listFiles()?.forEach { file ->
                if (file.name.endsWith(".mp3") || file.name.endsWith(".m4a")) {
                    file.delete()
                }
            }
            Timber.d("Audio injection cache cleared")
        } catch (e: Exception) {
            Timber.e(e, "Failed to clear audio injection cache")
        }
    }

    /**
     * Statistics and monitoring
     */
    fun getInjectionStats(): InjectionStats {
        return InjectionStats(
            isInitialized = isInitialized,
            brandingEnabled = isBrandingEnabled,
            metadataMaskingEnabled = metadataMaskingEnabled,
            hasIntroAudio = hasIntroAudio(),
            introDuration = getIntroDuration()
        )
    }

    data class InjectionStats(
        val isInitialized: Boolean,
        val brandingEnabled: Boolean,
        val metadataMaskingEnabled: Boolean,
        val hasIntroAudio: Boolean,
        val introDuration: Long
    )

    companion object {
        private const val TAG = "TXAAudioInjectionManager"
        
        // Intro audio configuration
        private const val DEFAULT_INTRO_FILE = "intro_txa.mp3"
        private const val INTRO_DURATION_MS = 3000L
    }
}
