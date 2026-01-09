package com.txapp.musicplayer.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.txapp.musicplayer.R
import com.txapp.musicplayer.databinding.ItemSongBinding
import com.txapp.musicplayer.databinding.ItemSongVerticalBinding
import com.txapp.musicplayer.model.Song
import com.txapp.musicplayer.glide.TXAGlideExtension

class SongAdapter(
    private val isVertical: Boolean = false,
    private val onSongClick: (Song) -> Unit
) : ListAdapter<Song, RecyclerView.ViewHolder>(SongDiffCallback()) {

    init {
        // Enable stable IDs for smoother RecyclerView performance
        setHasStableIds(true)
    }

    var currentSongId: Long = -1
        private set
    var isPlaying: Boolean = false
        private set
    
    fun updatePlayingState(songId: Long, playing: Boolean) {
        val oldSongId = currentSongId
        val stateChanged = songId != oldSongId || playing != isPlaying
        
        if (stateChanged) {
            currentSongId = songId
            isPlaying = playing
            
            // Only notify affected items instead of full dataset
            val currentList = currentList
            val oldIndex = currentList.indexOfFirst { it.id == oldSongId }
            val newIndex = currentList.indexOfFirst { it.id == songId }
            
            if (oldIndex >= 0) notifyItemChanged(oldIndex, PAYLOAD_PLAYING_STATE)
            if (newIndex >= 0 && newIndex != oldIndex) notifyItemChanged(newIndex, PAYLOAD_PLAYING_STATE)
        }
    }
    
    override fun getItemId(position: Int): Long {
        return getItem(position).id
    }

    private var searchQuery: String = ""

    fun setSearchQuery(query: String) {
        searchQuery = query
        // We don't call notifyDataSetChanged here because usually submitList follows
    }

    private fun highlightText(fullText: String, query: String, color: Int): CharSequence {
        if (query.isEmpty() || !fullText.contains(query, ignoreCase = true)) {
            return fullText
        }
        val spannable = android.text.SpannableString(fullText)
        val start = fullText.indexOf(query, ignoreCase = true)
        if (start >= 0) {
            val end = start + query.length
            spannable.setSpan(
                android.text.style.ForegroundColorSpan(color),
                start,
                end,
                android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            spannable.setSpan(
                android.text.style.StyleSpan(android.graphics.Typeface.BOLD),
                start,
                end,
                android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        return spannable
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (isVertical) {
            val binding = ItemSongVerticalBinding.inflate(inflater, parent, false)
            VerticalViewHolder(binding)
        } else {
            val binding = ItemSongBinding.inflate(inflater, parent, false)
            HorizontalViewHolder(binding)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        onBindViewHolder(holder, position, mutableListOf())
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.contains(PAYLOAD_PLAYING_STATE)) {
            val song = getItem(position)
            if (holder is HorizontalViewHolder) holder.updatePlayingState(song)
            else if (holder is VerticalViewHolder) holder.updatePlayingState(song)
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    inner class HorizontalViewHolder(private val binding: ItemSongBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(song: Song) {
            updatePlayingState(song)
            
            val highlightColor = android.graphics.Color.parseColor(com.txapp.musicplayer.util.TXAPreferences.currentAccent)
            val artistText = if (song.playCount > 0) "${song.artist} • ${song.playCount} plays" else song.artist
            binding.songArtist.text = highlightText(artistText, searchQuery, highlightColor)
            
            // Use optimized Glide loading
            TXAGlideExtension.loadAlbumArt(binding.albumArt, song)

            binding.root.setOnClickListener { onSongClick(song) }
        }

        fun updatePlayingState(song: Song) {
            val highlightColor = android.graphics.Color.parseColor(com.txapp.musicplayer.util.TXAPreferences.currentAccent)
            val defaultTextColor = com.google.android.material.color.MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorOnSurface)
            
            val isCurrent = song.id == currentSongId
            
            if (isCurrent) {
                binding.songTitle.setTextColor(highlightColor)
                binding.songTitle.typeface = android.graphics.Typeface.DEFAULT_BOLD
                // Show animated playing indicator or "▶ " ?
                // For CSS active state request: Color + Bold + Icon
                binding.songTitle.text = if (isPlaying) "Now Playing • ${song.title}" else song.title
            } else {
                binding.songTitle.setTextColor(defaultTextColor)
                binding.songTitle.typeface = android.graphics.Typeface.DEFAULT
                binding.songTitle.text = highlightText(song.title, searchQuery, highlightColor)
            }
        }
    }

    inner class VerticalViewHolder(private val binding: ItemSongVerticalBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(song: Song) {
            updatePlayingState(song)
            
            val highlightColor = android.graphics.Color.parseColor(com.txapp.musicplayer.util.TXAPreferences.currentAccent)
            val artistText = if (song.playCount > 0) "${song.artist} • ${song.playCount} plays" else song.artist
            binding.songArtist.text = highlightText(artistText, searchQuery, highlightColor)

            // Use optimized Glide loading
            TXAGlideExtension.loadAlbumArt(binding.albumArt, song)

            binding.root.setOnClickListener { onSongClick(song) }
        }

        fun updatePlayingState(song: Song) {
            val highlightColor = android.graphics.Color.parseColor(com.txapp.musicplayer.util.TXAPreferences.currentAccent)
            val defaultTextColor = com.google.android.material.color.MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorOnSurface)
            
            val isCurrent = song.id == currentSongId
            if (isCurrent) {
                binding.songTitle.setTextColor(highlightColor)
                binding.songTitle.typeface = android.graphics.Typeface.DEFAULT_BOLD
                binding.songTitle.text = if (isPlaying) "Now Playing • ${song.title}" else song.title
            } else {
                binding.songTitle.setTextColor(defaultTextColor)
                binding.songTitle.typeface = android.graphics.Typeface.DEFAULT
                binding.songTitle.text = highlightText(song.title, searchQuery, highlightColor)
            }
        }
    }

    // Clear Glide request when view is recycled to prevent memory leaks
    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)
        when (holder) {
            is HorizontalViewHolder -> Glide.with(holder.itemView).clear(holder.itemView.findViewById<ImageView>(R.id.albumArt))
            is VerticalViewHolder -> Glide.with(holder.itemView).clear(holder.itemView.findViewById<ImageView>(R.id.albumArt))
        }
    }


    class SongDiffCallback : DiffUtil.ItemCallback<Song>() {
        override fun areItemsTheSame(oldItem: Song, newItem: Song): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Song, newItem: Song): Boolean = oldItem == newItem
    }
    
    companion object {
        private const val PAYLOAD_PLAYING_STATE = "playing_state"
    }
}
