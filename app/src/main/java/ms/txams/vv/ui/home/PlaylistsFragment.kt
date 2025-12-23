package ms.txams.vv.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import ms.txams.vv.R
import ms.txams.vv.databinding.FragmentSectionBinding

class PlaylistsFragment : Fragment() {

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
        binding.tvSectionTitle.text = "Your Playlists"
        val adapter = MusicCardAdapter(getPlaylistData())
        binding.recyclerView.adapter = adapter
        binding.recyclerView.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
    }

    private fun getPlaylistData(): List<MusicCardItem> {
        return listOf(
            MusicCardItem("Liked Songs", "234 songs", R.drawable.ic_favorite),
            MusicCardItem("Chill Vibes", "45 songs", R.drawable.ic_playlist),
            MusicCardItem("Workout Mix", "67 songs", R.drawable.ic_music_note),
            MusicCardItem("Study Focus", "89 songs", R.drawable.ic_music_note),
            MusicCardItem("Road Trip", "56 songs", R.drawable.ic_music_note),
            MusicCardItem("Party Hits", "78 songs", R.drawable.ic_music_note)
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
