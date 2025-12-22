package gc.txa.demo.ui.songs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import gc.txa.demo.R

class SongsFragment : Fragment() {
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return TextView(requireContext()).apply {
            text = "Songs Fragment - Coming Soon"
            textSize = 18f
            setPadding(32, 32, 32, 32)
        }
    }
}
