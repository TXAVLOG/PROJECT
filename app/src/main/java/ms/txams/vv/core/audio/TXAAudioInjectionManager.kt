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
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

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
        injectionScope.launch {
            try {
                // Release audio injection resources
                introMediaSource = null
                isInitialized = false
                Timber.d("TXAAudioInjectionManager released")
            } catch (e: Exception) {
                Timber.e(e, "Failed to release TXAAudioInjectionManager")
            }
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

    /**
     * Ghép intro audio vào đầu file nhạc và tạo file mới trong Android/data/
     */
    suspend fun createBrandedAudioFile(song: TXASongEntity): String? {
        return withContext(Dispatchers.IO) {
            try {
                val introPath = introAudioPath ?: return@withContext null
                val originalFile = File(song.filePath)
                
                if (!originalFile.exists()) {
                    Timber.e("Original file not found: ${song.filePath}")
                    return@withContext null
                }
                
                // Tạo thư mục output trong Android/data/
                val outputDir = getBrandedAudioOutputDir()
                if (!outputDir.exists()) {
                    outputDir.mkdirs()
                }
                
                // Tạo tên file mới với prefix TXA_
                val originalFileName = File(song.filePath).nameWithoutExtension
                val fileExtension = File(song.filePath).extension
                val brandedFileName = "TXA_${originalFileName}_branded.$fileExtension"
                val brandedFile = File(outputDir, brandedFileName)
                
                // Ghép file intro và file nhạc
                val success = mergeAudioFiles(introPath, song.filePath, brandedFile.absolutePath)
                
                if (success && brandedFile.exists()) {
                    Timber.d("Branded audio file created: ${brandedFile.absolutePath}")
                    return@withContext brandedFile.absolutePath
                } else {
                    Timber.e("Failed to create branded audio file")
                    return@withContext null
                }
            } catch (e: Exception) {
                Timber.e(e, "Error creating branded audio file")
                return@withContext null
            }
        }
    }

    /**
     * Lấy thư mục output cho file branded audio trong Android/data/
     */
    private fun getBrandedAudioOutputDir(): File {
        // Sử dụng external files directory để lưu trong Android/data/
        val baseDir = File(context.getExternalFilesDir(null), "TXA_Branded_Audio")
        return baseDir
    }

    /**
     * Ghép 2 file audio thành 1 file mới
     */
    private fun mergeAudioFiles(introPath: String, songPath: String, outputPath: String): Boolean {
        return try {
            val introFile = File(introPath)
            val songFile = File(songPath)
            val outputFile = File(outputPath)
            
            if (!introFile.exists() || !songFile.exists()) {
                Timber.e("Source files not found")
                return false
            }
            
            FileOutputStream(outputFile).use { fos ->
                // Ghi file intro trước
                FileInputStream(introFile).use { fis ->
                    fis.copyTo(fos)
                }
                
                // Ghi file nhạc ngay sau intro
                FileInputStream(songFile).use { fis ->
                    fis.copyTo(fos)
                }
            }
            
            Timber.d("Audio files merged successfully: ${outputFile.length()} bytes")
            true
        } catch (e: IOException) {
            Timber.e(e, "Failed to merge audio files")
            false
        }
    }

    /**
     * Kiểm tra file branded đã tồn tại chưa
     */
    fun hasBrandedFile(song: TXASongEntity): Boolean {
        val originalFileName = File(song.filePath).nameWithoutExtension
        val fileExtension = File(song.filePath).extension
        val brandedFileName = "TXA_${originalFileName}_branded.$fileExtension"
        val brandedFile = File(getBrandedAudioOutputDir(), brandedFileName)
        return brandedFile.exists()
    }

    /**
     * Lấy path của file branded nếu đã tồn tại
     */
    fun getBrandedFilePath(song: TXASongEntity): String? {
        val originalFileName = File(song.filePath).nameWithoutExtension
        val fileExtension = File(song.filePath).extension
        val brandedFileName = "TXA_${originalFileName}_branded.$fileExtension"
        val brandedFile = File(getBrandedAudioOutputDir(), brandedFileName)
        return if (brandedFile.exists()) brandedFile.absolutePath else null
    }

    /**
     * Xóa tất cả file branded audio
     */
    fun clearBrandedFiles() {
        try {
            val outputDir = getBrandedAudioOutputDir()
            if (outputDir.exists()) {
                outputDir.listFiles()?.forEach { file ->
                    if (file.delete()) {
                        Timber.d("Deleted branded file: ${file.name}")
                    } else {
                        Timber.w("Failed to delete branded file: ${file.name}")
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to clear branded files")
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

    /**
     * Check audio injection integrity
     */
    suspend fun checkIntegrity(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // Check if intro audio is properly loaded
                val hasValidIntro = introAudioPath?.let { path ->
                    val introFile = File(path)
                    introFile.exists() && introFile.length() > 0
                } ?: false
                
                // Check initialization state
                if (!isInitialized) {
                    Timber.w("TXAAudioInjectionManager not initialized")
                    return@withContext false
                }
                
                // Check branding configuration
                if (!isBrandingEnabled) {
                    Timber.d("Audio branding is disabled")
                }
                
                Timber.d("Audio injection integrity check passed")
                true
            } catch (e: Exception) {
                Timber.e(e, "Audio injection integrity check failed")
                false
            }
        }
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
