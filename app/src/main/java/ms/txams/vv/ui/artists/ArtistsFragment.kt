package ms.txams.vv.ui.artists

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import ms.txams.vv.R

class ArtistsFragment : Fragment() {
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return TextView(requireContext()).apply {
            text = "Artists Fragment - Coming Soon"
            textSize = 18f
            setPadding(32, 32, 32, 32)
        }
    }
}
