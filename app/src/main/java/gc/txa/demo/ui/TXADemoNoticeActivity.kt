package gc.txa.demo.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import gc.txa.demo.core.TXATranslation
import gc.txa.demo.databinding.ActivityTxaDemoNoticeBinding

class TXADemoNoticeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTxaDemoNoticeBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTxaDemoNoticeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
    }

    private fun setupUI() {
        binding.apply {
            tvTitle.text = TXATranslation.txa("demo_notice_title")
            tvDescription.text = TXATranslation.txa("demo_notice_description")
            tvWarning.text = TXATranslation.txa("demo_notice_warning")
            
            tvFeature1.text = TXATranslation.txa("demo_notice_feature_1")
            tvFeature2.text = TXATranslation.txa("demo_notice_feature_2")
            tvFeature3.text = TXATranslation.txa("demo_notice_feature_3")
            tvFeature4.text = TXATranslation.txa("demo_notice_feature_4")
            tvFeature5.text = TXATranslation.txa("demo_notice_feature_5")
            
            btnConfirm.text = TXATranslation.txa("demo_notice_confirm")
            btnConfirm.setOnClickListener {
                navigateToSettings()
            }
        }
    }

    private fun navigateToSettings() {
        val intent = Intent(this, TXASettingsActivity::class.java)
        startActivity(intent)
        finish()
    }
}
