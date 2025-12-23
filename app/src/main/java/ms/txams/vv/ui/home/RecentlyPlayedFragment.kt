package ms.txams.vv.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import ms.txams.vv.R
import ms.txams.vv.databinding.FragmentSectionBinding

class RecentlyPlayedFragment : Fragment() {

    private var _binding: FragmentSectionBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSectionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
    }

    private fun setupRecyclerView() {
        val adapter = MusicCardAdapter(getMockData())
        binding.recyclerView.adapter = adapter
        binding.recyclerView.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
    }

    private fun getMockData(): List<MusicCardItem> {
        return listOf(
            MusicCardItem("Blinding Lights", "The Weeknd", R.drawable.ic_music_note),
            MusicCardItem("Levitating", "Dua Lipa", R.drawable.ic_music_note),
            MusicCardItem("Good 4 U", "Olivia Rodrigo", R.drawable.ic_music_note),
            MusicCardItem("Stay", "The Kid LAROI", R.drawable.ic_music_note),
            MusicCardItem("Heat Waves", "Glass Animals", R.drawable.ic_music_note),
            MusicCardItem("Industry Baby", "Lil Nas X", R.drawable.ic_music_note)
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

data class MusicCardItem(
    val title: String,
    val subtitle: String,
    val imageRes: Int
)
