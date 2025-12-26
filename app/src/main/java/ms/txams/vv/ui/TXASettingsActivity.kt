package ms.txams.vv.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import ms.txams.vv.core.TXATranslation
import ms.txams.vv.databinding.ActivitySettingsBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class TXASettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.tvTitle.text = TXATranslation.txa("txamusic_settings_title")
        binding.btnMusicLibrary.text = TXATranslation.txa("txamusic_settings_open_music_library")

        binding.btnMusicLibrary.setOnClickListener {
            startActivity(Intent(this, TXAMusicLibraryActivity::class.java))
        }
    }
}
