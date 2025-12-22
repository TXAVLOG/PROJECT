package gc.txa.demo.ui.albums

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import gc.txa.demo.R

class AlbumsFragment : Fragment() {
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return TextView(requireContext()).apply {
            text = "Albums Fragment - Coming Soon"
            textSize = 18f
            setPadding(32, 32, 32, 32)
        }
    }
}
