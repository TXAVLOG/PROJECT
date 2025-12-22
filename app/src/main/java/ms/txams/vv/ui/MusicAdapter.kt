package ms.txams.vv.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import ms.txams.vv.data.database.SongEntity
import ms.txams.vv.core.TXAFormat
import ms.txams.vv.databinding.ItemMusicBinding
import java.util.concurrent.TimeUnit

class MusicAdapter(
    private val onSongAction: (SongEntity, String) -> Unit
) : ListAdapter<SongEntity, MusicAdapter.ViewHolder>(SongDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemMusicBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    fun updateSongs(songs: List<SongEntity>) {
        submitList(songs)
    }

    inner class ViewHolder(private val binding: ItemMusicBinding) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(song: SongEntity) {
            binding.apply {
                tvSongTitle.text = song.title
                tvArtistName.text = song.artist
                tvAlbumName.text = song.album
                tvDuration.text = formatDuration(song.duration)
                tvFileSize.text = TXAFormat.formatBytes(java.io.File(song.filePath).length())
                
                // Load album art if available
                if (!song.albumArt.isNullOrEmpty()) {
                    // TODO: Load album art using Glide or Coil
                    // Glide.with(itemView.context)
                    //     .load(song.albumArt)
                    //     .placeholder(R.drawable.ic_music_note)
                    //     .into(ivAlbumArt)
                } else {
                    // TODO: Set default album art
                    // ivAlbumArt.setImageResource(R.drawable.ic_music_note)
                }
                
                btnPlay.setOnClickListener {
                    onSongAction(song, "play")
                }
                
                btnAddToPlaylist.setOnClickListener {
                    onSongAction(song, "add_to_playlist")
                }
                
                itemView.setOnClickListener {
                    onSongAction(song, "play")
                }
            }
        }
        
        private fun formatDuration(durationMs: Long): String {
            val minutes = TimeUnit.MILLISECONDS.toMinutes(durationMs)
            val seconds = TimeUnit.MILLISECONDS.toSeconds(durationMs) % 60
            return String.format("%d:%02d", minutes, seconds)
        }
    }
}

class SongDiffCallback : DiffUtil.ItemCallback<SongEntity>() {
    override fun areItemsTheSame(oldItem: SongEntity, newItem: SongEntity): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: SongEntity, newItem: SongEntity): Boolean {
        return oldItem == newItem
    }
}
