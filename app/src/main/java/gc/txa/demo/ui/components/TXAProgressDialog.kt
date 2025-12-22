package ms.txams.vv.ui.components

import android.content.Context
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import ms.txams.vv.databinding.DialogProgressBinding
import ms.txams.vv.core.TXATranslation

class TXAProgressDialog(private val context: Context) {

    private var dialog: AlertDialog? = null
    private var binding: DialogProgressBinding? = null
    private var onInstallClick: (() -> Unit)? = null

    fun show(
        title: String = "Loading...",
        message: String = "Please wait...",
        icon: Int? = null,
        cancellable: Boolean = false,
        indeterminate: Boolean = true
    ) {
        binding = DialogProgressBinding.inflate(LayoutInflater.from(context)).also { binding ->
            binding.tvTitle.text = title
            binding.tvMessage.text = message
            icon?.let { binding.ivIcon.setImageResource(it) }
            binding.progressBar.isIndeterminate = indeterminate
            if (!indeterminate) {
                binding.progressBar.max = 100
                binding.progressBar.progress = 0
            }
            
            // Initialize detailed fields
            binding.tvSize.text = "0 B / 0 B"
            binding.tvSpeed.text = "0 B/s"
            binding.tvEtaLabel.text = TXATranslation.txa("txamusic_update_download_eta")
            binding.tvEta.text = "--:--"
            binding.tvPercent.text = "0%"
            binding.btnInstall.isVisible = false
            binding.btnInstall.setOnClickListener { onInstallClick?.invoke() }
        }

        dialog?.dismiss()
        dialog = AlertDialog.Builder(context)
            .setView(binding?.root)
            .setCancelable(cancellable)
            .create()
            .also { it.show() }
    }

    fun update(
        message: String? = null,
        progressPercent: Int? = null,
        indeterminate: Boolean? = null,
        sizeText: String? = null,
        speedText: String? = null,
        etaText: String? = null,
        percentText: String? = null
    ) {
        val binding = binding ?: return

        message?.let { binding.tvMessage.text = it }

        indeterminate?.let { isIndeterminate ->
            binding.progressBar.isIndeterminate = isIndeterminate
            if (!isIndeterminate && binding.progressBar.max != 100) {
                binding.progressBar.max = 100
            }
        }

        progressPercent?.let { percent ->
            binding.progressBar.isIndeterminate = false
            binding.progressBar.progress = percent.coerceIn(0, 100)
        }

        sizeText?.let { binding.tvSize.text = it }
        speedText?.let { binding.tvSpeed.text = it }
        etaText?.let { binding.tvEta.text = it }
        percentText?.let { binding.tvPercent.text = it }
    }

    fun showCompleted(onInstall: () -> Unit) {
        val binding = binding ?: return
        binding.tvMessage.text = TXATranslation.txa("txamusic_download_completed")
        binding.progressBar.isIndeterminate = false
        binding.progressBar.progress = 100
        binding.tvPercent.text = "100%"
        binding.btnInstall.text = TXATranslation.txa("txamusic_update_install")
        binding.btnInstall.isVisible = true
        onInstallClick = onInstall
        
        // Make dialog cancelable after completion
        dialog?.setCancelable(true)
    }

    fun dismiss() {
        dialog?.dismiss()
        dialog = null
        binding = null
    }
    
    fun getBinding(): DialogProgressBinding? = binding
}
