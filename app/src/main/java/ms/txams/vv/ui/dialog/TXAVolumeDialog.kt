package ms.txams.vv.ui.dialog

import android.content.Context
import android.media.AudioManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import ms.txams.vv.R
import ms.txams.vv.core.TXATranslation
import ms.txams.vv.core.TXABackgroundLogger

/**
 * TXA Volume Control Dialog
 * Bottom sheet with dual volume sliders:
 * - Media volume (app playback)
 * - System volume (device)
 * 
 * Syncs with system volume in real-time
 * 
 * @author TXA - fb.com/vlog.txa.2311
 */
class TXAVolumeDialog : BottomSheetDialogFragment() {
    
    private lateinit var audioManager: AudioManager
    
    // UI elements
    private lateinit var tvTitle: TextView
    private lateinit var tvMediaLabel: TextView
    private lateinit var tvSystemLabel: TextView
    private lateinit var seekMediaVolume: SeekBar
    private lateinit var seekSystemVolume: SeekBar
    private lateinit var ivMediaIcon: ImageView
    private lateinit var ivSystemIcon: ImageView
    private lateinit var tvMediaValue: TextView
    private lateinit var tvSystemValue: TextView
    
    // Volume ranges
    private var maxMediaVolume: Int = 15
    private var maxSystemVolume: Int = 15
    
    // Callback for media volume change (app internal)
    var onMediaVolumeChange: ((Float) -> Unit)? = null
    
    // Current app volume (0.0 to 1.0)
    private var currentAppVolume: Float = 1.0f
    
    companion object {
        private const val ARG_APP_VOLUME = "app_volume"
        
        fun newInstance(appVolume: Float = 1.0f): TXAVolumeDialog {
            return TXAVolumeDialog().apply {
                arguments = Bundle().apply {
                    putFloat(ARG_APP_VOLUME, appVolume)
                }
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        audioManager = requireContext().getSystemService(Context.AUDIO_SERVICE) as AudioManager
        maxMediaVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        maxSystemVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_SYSTEM)
        
        currentAppVolume = arguments?.getFloat(ARG_APP_VOLUME, 1.0f) ?: 1.0f
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_txa_volume, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Find views
        tvTitle = view.findViewById(R.id.tvTitle)
        tvMediaLabel = view.findViewById(R.id.tvMediaLabel)
        tvSystemLabel = view.findViewById(R.id.tvSystemLabel)
        seekMediaVolume = view.findViewById(R.id.seekMediaVolume)
        seekSystemVolume = view.findViewById(R.id.seekSystemVolume)
        ivMediaIcon = view.findViewById(R.id.ivMediaIcon)
        ivSystemIcon = view.findViewById(R.id.ivSystemIcon)
        tvMediaValue = view.findViewById(R.id.tvMediaValue)
        tvSystemValue = view.findViewById(R.id.tvSystemValue)
        
        // Set translations
        tvTitle.text = TXATranslation.txa("txamusic_volume")
        tvMediaLabel.text = TXATranslation.txa("txamusic_volume_media")
        tvSystemLabel.text = TXATranslation.txa("txamusic_volume_system")
        
        setupMediaVolumeSeekbar()
        setupSystemVolumeSeekbar()
    }
    
    private fun setupMediaVolumeSeekbar() {
        // Media volume uses 0-100 for finer control
        seekMediaVolume.max = 100
        seekMediaVolume.progress = (currentAppVolume * 100).toInt()
        updateMediaVolumeDisplay((currentAppVolume * 100).toInt())
        
        seekMediaVolume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    currentAppVolume = progress / 100f
                    onMediaVolumeChange?.invoke(currentAppVolume)
                    updateMediaVolumeDisplay(progress)
                }
            }
            
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }
    
    private fun setupSystemVolumeSeekbar() {
        // System volume uses actual device range
        seekSystemVolume.max = maxMediaVolume
        
        // Get current system music volume
        val currentSystemVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        seekSystemVolume.progress = currentSystemVolume
        updateSystemVolumeDisplay(currentSystemVolume)
        
        seekSystemVolume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    // Update system volume
                    audioManager.setStreamVolume(
                        AudioManager.STREAM_MUSIC,
                        progress,
                        0 // No flags - silent
                    )
                    updateSystemVolumeDisplay(progress)
                    TXABackgroundLogger.d("System volume changed to: $progress")
                }
            }
            
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }
    
    private fun updateMediaVolumeDisplay(progress: Int) {
        tvMediaValue.text = "$progress%"
        
        // Update icon based on volume level
        val iconRes = when {
            progress == 0 -> R.drawable.ic_volume_off
            progress < 50 -> R.drawable.ic_volume_down
            else -> R.drawable.ic_volume_up
        }
        ivMediaIcon.setImageResource(iconRes)
    }
    
    private fun updateSystemVolumeDisplay(progress: Int) {
        val percentage = (progress * 100 / maxMediaVolume)
        tvSystemValue.text = "$percentage%"
        
        // Update icon based on volume level
        val iconRes = when {
            progress == 0 -> R.drawable.ic_volume_off
            progress < maxMediaVolume / 2 -> R.drawable.ic_volume_down
            else -> R.drawable.ic_volume_up
        }
        ivSystemIcon.setImageResource(iconRes)
    }
    
    override fun onResume() {
        super.onResume()
        // Sync with current system volume when dialog resumes
        val currentSystemVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        seekSystemVolume.progress = currentSystemVolume
        updateSystemVolumeDisplay(currentSystemVolume)
    }
}
