package ms.txams.vv.ui.nowplaying

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import ms.txams.vv.R

class NowPlayingFragment : Fragment() {
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return TextView(requireContext()).apply {
            text = "Now Playing Fragment - Coming Soon\n\nUse the now bar at the bottom for playback controls"
            textSize = 16f
            setPadding(32, 32, 32, 32)
        }
    }
}
