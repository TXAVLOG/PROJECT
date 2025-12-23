package ms.txams.vv.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import ms.txams.vv.R
import ms.txams.vv.databinding.FragmentSectionBinding

class ArtistsFragment : Fragment() {

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
        binding.tvSectionTitle.text = "Top Artists"
        val adapter = MusicCardAdapter(getArtistData())
        binding.recyclerView.adapter = adapter
        binding.recyclerView.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
    }

    private fun getArtistData(): List<MusicCardItem> {
        return listOf(
            MusicCardItem("The Weeknd", "45M followers", R.drawable.ic_music_note),
            MusicCardItem("Dua Lipa", "38M followers", R.drawable.ic_music_note),
            MusicCardItem("Olivia Rodrigo", "29M followers", R.drawable.ic_music_note),
            MusicCardItem("The Kid LAROI", "22M followers", R.drawable.ic_music_note),
            MusicCardItem("Glass Animals", "18M followers", R.drawable.ic_music_note),
            MusicCardItem("Lil Nas X", "31M followers", R.drawable.ic_music_note)
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
