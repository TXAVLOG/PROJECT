package ms.txams.vv.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import ms.txams.vv.data.database.SongEntity
import ms.txams.vv.databinding.ItemSongBinding
import ms.txams.vv.core.TXAFormat
import coil.load
import ms.txams.vv.R

class MusicAdapter(private val onItemClick: (SongEntity) -> Unit) : 
    ListAdapter<SongEntity, MusicAdapter.SongViewHolder>(SongDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SongViewHolder {
        val binding = ItemSongBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return SongViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SongViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class SongViewHolder(private val binding: ItemSongBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(song: SongEntity) {
            binding.tvTitle.text = song.title
            binding.tvArtist.text = song.artist
            binding.tvDuration.text = TXAFormat.formatDuration(song.duration)
            
            // Load album art using Coil
            // Note: Efficient way is using ContentUris for AlbumArt, but here using placeholders first
             binding.ivAlbumArt.load(song.path as? String ?: "") {
                 crossfade(true)
                 error(R.drawable.ic_music_note)
                 placeholder(R.drawable.ic_music_note)
             }
            
            binding.root.setOnClickListener {
                onItemClick(song)
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
}
