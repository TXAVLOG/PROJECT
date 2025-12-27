package ms.txams.vv.core

import android.content.Context
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.UUID

/**
 * TXA TTS Manager
 * Text-to-Speech Manager for app announcements
 * 
 * Features:
 * - Uses system TTS engine with device locale
 * - Synthesize to file for audio injection
 * - Countdown with voice
 * 
 * @author TXA - fb.com/vlog.txa.2311
 */
class TXATTSManager(private val context: Context) {
    
    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var initDeferred: CompletableDeferred<Boolean>? = null
    
    /**
     * Initialize TTS engine
     */
    suspend fun initialize(): Boolean = withContext(Dispatchers.Main) {
        if (isInitialized) return@withContext true
        
        initDeferred = CompletableDeferred()
        
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                // Set language to device locale
                val locale = Locale.getDefault()
                val result = tts?.setLanguage(locale)
                
                isInitialized = result != TextToSpeech.LANG_MISSING_DATA && 
                               result != TextToSpeech.LANG_NOT_SUPPORTED
                
                if (!isInitialized) {
                    // Fallback to English
                    tts?.setLanguage(Locale.US)
                    isInitialized = true
                }
                
                // Set speech rate to 0.8x (slower)
                tts?.setSpeechRate(0.8f)
                
                TXABackgroundLogger.d("TTS initialized with language: ${tts?.language}")
            } else {
                isInitialized = false
                TXABackgroundLogger.e("TTS initialization failed with status: $status")
            }
            initDeferred?.complete(isInitialized)
        }
        
        return@withContext initDeferred?.await() ?: false
    }
    
    /**
     * Speak text immediately
     */
    fun speak(text: String, utteranceId: String = UUID.randomUUID().toString()) {
        if (!isInitialized) {
            TXABackgroundLogger.e("TTS not initialized")
            return
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        } else {
            @Suppress("DEPRECATION")
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null)
        }
    }
    
    /**
     * Speak with countdown for music intro
     * "Áp biu bai TXA, app sẽ phát nhạc sau... 5... 4... 3... 2... 1"
     */
    suspend fun speakIntroWithCountdown(onCountdownComplete: () -> Unit): Boolean = withContext(Dispatchers.Main) {
        if (!isInitialized && !initialize()) {
            TXABackgroundLogger.e("Cannot speak intro - TTS not available")
            onCountdownComplete()
            return@withContext false
        }
        
        val speakDeferred = CompletableDeferred<Boolean>()
        
        // Build intro text based on locale
        val introText = buildIntroText()
        val utteranceId = "txa_intro_${System.currentTimeMillis()}"
        
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {
                TXABackgroundLogger.d("TTS started speaking: $id")
            }
            
            override fun onDone(id: String?) {
                TXABackgroundLogger.d("TTS finished: $id")
                if (id == utteranceId) {
                    speakDeferred.complete(true)
                    onCountdownComplete()
                }
            }
            
            override fun onError(id: String?) {
                TXABackgroundLogger.e("TTS error on: $id")
                speakDeferred.complete(false)
                onCountdownComplete()
            }
            
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?, errorCode: Int) {
                TXABackgroundLogger.e("TTS error code: $errorCode")
                speakDeferred.complete(false)
                onCountdownComplete()
            }
        })
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            tts?.speak(introText, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        } else {
            @Suppress("DEPRECATION")
            tts?.speak(introText, TextToSpeech.QUEUE_FLUSH, null)
            // Estimate time and call completion
            delay(calculateSpeakDuration(introText))
            speakDeferred.complete(true)
            onCountdownComplete()
        }
        
        return@withContext speakDeferred.await()
    }
    
    /**
     * Build intro text based on current locale
     */
    private fun buildIntroText(): String {
        val locale = tts?.language ?: Locale.getDefault()
        
        return when (locale.language) {
            "vi" -> "Áp biu bai tê ích a, ứng dụng sẽ phát nhạc sau... 5... 4... 3... 2... 1"
            "ja" -> "TXA アプリ、音楽が始まります... 5... 4... 3... 2... 1"
            "ko" -> "TXA 앱, 음악이 시작됩니다... 5... 4... 3... 2... 1"
            "zh" -> "TXA 应用程序即将播放音乐... 5... 4... 3... 2... 1"
            else -> "App by TXA, music will play in... 5... 4... 3... 2... 1"
        }
    }
    
    /**
     * Synthesize text to audio file (for merging with music)
     */
    suspend fun synthesizeToFile(text: String, outputFile: File): Boolean = withContext(Dispatchers.IO) {
        if (!isInitialized) {
            TXABackgroundLogger.e("TTS not initialized for synthesis")
            return@withContext false
        }
        
        val synthesizeDeferred = CompletableDeferred<Boolean>()
        val utteranceId = "txa_synth_${System.currentTimeMillis()}"
        
        withContext(Dispatchers.Main) {
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) {}
                
                override fun onDone(id: String?) {
                    if (id == utteranceId) {
                        TXABackgroundLogger.d("TTS synthesis complete: ${outputFile.absolutePath}")
                        synthesizeDeferred.complete(true)
                    }
                }
                
                override fun onError(id: String?) {
                    synthesizeDeferred.complete(false)
                }
            })
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                tts?.synthesizeToFile(text, null, outputFile, utteranceId)
            } else {
                @Suppress("DEPRECATION")
                val params = HashMap<String, String>()
                params[TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID] = utteranceId
                tts?.synthesizeToFile(text, params, outputFile.absolutePath)
            }
        }
        
        return@withContext synthesizeDeferred.await()
    }
    
    /**
     * Get intro text for synthesis
     */
    fun getIntroTextForSynthesis(): String = buildIntroText()
    
    /**
     * Estimate speak duration in milliseconds
     */
    private fun calculateSpeakDuration(text: String): Long {
        // Roughly 100ms per word + countdown pauses
        val wordCount = text.split(" ").size
        val countdownPause = 5 * 1000L // 5 seconds for countdown numbers
        return (wordCount * 300L) + countdownPause
    }
    
    /**
     * Stop speaking
     */
    fun stop() {
        tts?.stop()
    }
    
    /**
     * Release TTS resources
     */
    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
        TXABackgroundLogger.d("TTS shutdown")
    }
}
