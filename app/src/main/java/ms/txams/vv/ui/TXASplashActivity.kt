package ms.txams.vv.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ms.txams.vv.core.TXATranslation
import ms.txams.vv.data.manager.TXAAudioInjectionManager
import ms.txams.vv.databinding.ActivitySplashBinding
import javax.inject.Inject

@AndroidEntryPoint
class TXASplashActivity : AppCompatActivity() {

    @Inject lateinit var audioInjectionManager: TXAAudioInjectionManager
    private lateinit var binding: ActivitySplashBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (Build.VERSION.SDK_INT < 33) {
            showIncompatibleDialog()
            return
        }

        if (!audioInjectionManager.checkIntegrity()) {
            binding.tvStatus.text = TXATranslation.txa("txamusic_msg_error")
            return
        }

        startLoadingSequence()
    }

    private fun showIncompatibleDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(TXATranslation.txa("txamusic_msg_error"))
            .setMessage(TXATranslation.txa("txamusic_app_incompatible"))
            .setPositiveButton(TXATranslation.txa("txamusic_action_ok")) { _, _ ->
                finishAffinity()
            }
            .setCancelable(false)
            .show()
    }

    private fun startLoadingSequence() {
        lifecycleScope.launch {
            val success = TXATranslation.loadLanguageWithProgress("en") { status ->
                 runOnUiThread { binding.tvStatus.text = status }
            }
            
            if (success) {
                 binding.tvStatus.text = TXATranslation.txa("txamusic_splash_initializing")
                 delay(1000)
                 startActivity(Intent(this@TXASplashActivity, TXAMainActivity::class.java))
                 finish()
            } else {
                 binding.tvStatus.text = TXATranslation.txa("txamusic_msg_error")
            }
        }
    }
}
