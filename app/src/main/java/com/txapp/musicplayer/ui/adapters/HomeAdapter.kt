package com.txapp.musicplayer.ui.adapters

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.txapp.musicplayer.*
import com.txapp.musicplayer.interfaces.IAlbumClickListener
import com.txapp.musicplayer.interfaces.IArtistClickListener
import com.txapp.musicplayer.model.Album
import com.txapp.musicplayer.model.Artist
import com.txapp.musicplayer.model.Home
import com.txapp.musicplayer.model.Song
import com.txapp.musicplayer.ui.adapters.album.AlbumAdapter
import com.txapp.musicplayer.ui.adapters.artist.ArtistAdapter

class HomeAdapter(private val activity: AppCompatActivity) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>(), IArtistClickListener, IAlbumClickListener {

    private var playingId: Long = -1L
    private var isPlaying: Boolean = false
    private var list = listOf<Home>()

    fun updatePlayingState(songId: Long, playing: Boolean) {
        playingId = songId
        isPlaying = playing
        
        list.forEachIndexed { index, home ->
            if (home.homeSection == FAVOURITES) { // Assuming Favorites uses SongAdapter
                notifyItemChanged(index, PAYLOAD_PLAYING_STATE)
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        return list[position].homeSection
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val layout = LayoutInflater.from(activity).inflate(R.layout.section_recycler_view, parent, false)
        return when (viewType) {
            RECENT_ARTISTS, TOP_ARTISTS -> ArtistViewHolder(layout)
            FAVOURITES -> PlaylistViewHolder(layout)
            TOP_ALBUMS, RECENT_ALBUMS -> AlbumViewHolder(layout)
            else -> ArtistViewHolder(layout)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        onBindViewHolder(holder, position, mutableListOf())
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int, payloads: MutableList<Any>) {
        val home = list[position]
        if (payloads.contains(PAYLOAD_PLAYING_STATE)) {
             if (holder is PlaylistViewHolder) {
                 holder.updatePlayingStateOnly()
             }
        } else {
             when (holder) {
                is AlbumViewHolder -> holder.bindView(home)
                is ArtistViewHolder -> holder.bindView(home)
                is PlaylistViewHolder -> holder.bindView(home)
            }
        }
    }

    override fun getItemCount(): Int = list.size

    @SuppressLint("NotifyDataSetChanged")
    fun swapData(sections: List<Home>) {
        list = sections
        notifyDataSetChanged()
    }
    
    private inner class AlbumViewHolder(view: View) : AbsHomeViewItem(view) {
        fun bindView(home: Home) {
            title.setText(home.titleRes)
            clickableArea.setOnClickListener {
                (activity as? com.txapp.musicplayer.ui.MainActivity)?.navigateToLibraryTab(0) // Assuming 0 is Albums index or handle by ID
            }
            recyclerView.apply {
                layoutManager = LinearLayoutManager(activity, LinearLayoutManager.HORIZONTAL, false)
                adapter = AlbumAdapter(activity, home.arrayList.filterIsInstance<Album>(), R.layout.item_album_card, this@HomeAdapter)
            }
        }
    }

    private inner class ArtistViewHolder(view: View) : AbsHomeViewItem(view) {
        fun bindView(home: Home) {
            title.setText(home.titleRes)
            clickableArea.setOnClickListener {
                (activity as? com.txapp.musicplayer.ui.MainActivity)?.navigateToLibraryTab(1) // Assuming 1 is Artists
            }
            recyclerView.apply {
                layoutManager = LinearLayoutManager(activity, LinearLayoutManager.HORIZONTAL, false)
                adapter = ArtistAdapter(activity, home.arrayList.filterIsInstance<Artist>(), R.layout.item_album_card, this@HomeAdapter)
            }
        }
    }

    private inner class PlaylistViewHolder(view: View) : AbsHomeViewItem(view) {
        fun bindView(home: Home) {
            title.setText(home.titleRes)
            clickableArea.setOnClickListener {
                (activity as? com.txapp.musicplayer.ui.MainActivity)?.navigateToLibraryTab(2) // Playlists/Songs
            }
            recyclerView.apply {
                layoutManager = LinearLayoutManager(activity, LinearLayoutManager.HORIZONTAL, false)
                
                val songAdapter = (adapter as? SongAdapter) ?: SongAdapter { song -> 
                    val songList = home.arrayList.filterIsInstance<Song>()
                    (activity as? com.txapp.musicplayer.ui.MainActivity)?.playSongs(songList, songList.indexOf(song))
                }.also { adapter = it }
                
                songAdapter.updatePlayingState(playingId, isPlaying)
                songAdapter.submitList(home.arrayList.filterIsInstance<Song>())
            }
        }
        
        fun updatePlayingStateOnly() {
            (recyclerView.adapter as? SongAdapter)?.updatePlayingState(playingId, isPlaying)
        }
    }
    
    companion object {
        private const val PAYLOAD_PLAYING_STATE = "PAYLOAD_PLAYING_STATE"
    }

    open class AbsHomeViewItem(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val recyclerView: RecyclerView = itemView.findViewById(R.id.recyclerView)
        val title: TextView = itemView.findViewById(R.id.title)
        val clickableArea: View = itemView.findViewById(R.id.clickable_area)
    }

    override fun onArtist(artistId: Long, name: String, view: View) {
        val bundle = android.os.Bundle().apply { 
            putLong("extra_artist_id", artistId)
            putString("extra_artist_name", name)
        }
        try {
            val navController = androidx.navigation.Navigation.findNavController(activity, R.id.fragment_container)
            navController.navigate(R.id.artistDetailsFragment, bundle)
        } catch (e: Exception) {
            com.txapp.musicplayer.util.TXALogger.appE("HomeAdapter", "Navigation failed", e)
        }
    }

    override fun onAlbumClick(albumId: Long, view: View) {
        val bundle = android.os.Bundle().apply { putLong("extra_album_id", albumId) }
        try {
            val navController = androidx.navigation.Navigation.findNavController(activity, R.id.fragment_container)
            navController.navigate(R.id.albumDetailsFragment, bundle)
        } catch (e: Exception) {
            com.txapp.musicplayer.util.TXALogger.appE("HomeAdapter", "Navigation failed", e)
        }
    }
}
