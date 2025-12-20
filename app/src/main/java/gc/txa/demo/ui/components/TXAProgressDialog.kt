package gc.txa.demo.ui.components

import android.content.Context
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import gc.txa.demo.databinding.DialogProgressBinding

class TXAProgressDialog(private val context: Context) {

    private var dialog: AlertDialog? = null
    private var binding: DialogProgressBinding? = null

    fun show(
        message: String,
        cancellable: Boolean = false,
        indeterminate: Boolean = true
    ) {
        binding = DialogProgressBinding.inflate(LayoutInflater.from(context)).also { binding ->
            binding.tvMessage.text = message
            binding.progressBar.isIndeterminate = indeterminate
            if (!indeterminate) {
                binding.progressBar.max = 100
                binding.progressBar.progress = 0
            }
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
        indeterminate: Boolean? = null
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
    }

    fun dismiss() {
        dialog?.dismiss()
        dialog = null
        binding = null
    }
}
