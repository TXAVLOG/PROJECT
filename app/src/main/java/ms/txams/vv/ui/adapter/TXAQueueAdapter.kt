package ms.txams.vv.ui.adapter

import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import ms.txams.vv.R
import ms.txams.vv.core.TXAFormat
import ms.txams.vv.data.database.SongEntity
import ms.txams.vv.databinding.ItemQueueSongBinding
import java.util.Collections

class TXAQueueAdapter(
    private val onSongClick: (SongEntity) -> Unit,
    private val onStartDrag: (RecyclerView.ViewHolder) -> Unit
) : ListAdapter<SongEntity, TXAQueueAdapter.QueueViewHolder>(SongDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QueueViewHolder {
        val binding = ItemQueueSongBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return QueueViewHolder(binding)
    }

    override fun onBindViewHolder(holder: QueueViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    fun onItemMove(fromPosition: Int, toPosition: Int) {
        val currentList = currentList.toMutableList()
        if (fromPosition < toPosition) {
            for (i in fromPosition until toPosition) {
                Collections.swap(currentList, i, i + 1)
            }
        } else {
            for (i in fromPosition downTo toPosition + 1) {
                Collections.swap(currentList, i, i - 1)
            }
        }
        submitList(currentList)
    }
    
    fun onItemDismiss(position: Int) {
        val currentList = currentList.toMutableList()
        currentList.removeAt(position)
        submitList(currentList)
    }

    inner class QueueViewHolder(private val binding: ItemQueueSongBinding) : 
        RecyclerView.ViewHolder(binding.root) {
        
        fun bind(song: SongEntity) {
            binding.tvTitle.text = song.title
            binding.tvArtist.text = song.artist
            binding.tvDuration.text = TXAFormat.formatDuration(song.duration)
            
            binding.ivAlbumArt.load(song.path as? String ?: "") {
                 crossfade(true)
                 error(R.drawable.ic_music_note)
                 placeholder(R.drawable.ic_music_note)
            }
            
            binding.root.setOnClickListener { onSongClick(song) }
            
            // Handle drag handle touch
            binding.ivDragHandle.setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_DOWN) {
                    onStartDrag(this)
                }
                false
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
