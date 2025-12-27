package ms.txams.vv.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import ms.txams.vv.data.database.SongEntity
import ms.txams.vv.databinding.ItemSongBinding
import ms.txams.vv.core.TXAFormat
import ms.txams.vv.core.TXATranslation
import ms.txams.vv.R
import coil.load
import android.content.ContentUris
import android.net.Uri

/**
 * TXA Music Adapter
 * Inspired by Namida - supports selection mode, long press, and actions menu
 * 
 * Features:
 * - Click: Play song immediately
 * - Long press: Toggle selection mode
 * - Menu button: Show actions popup
 * - Multi-selection: Select/deselect tracks
 */
class MusicAdapter(
    private val onItemClick: (SongEntity) -> Unit,
    private val onItemLongClick: (SongEntity, Int) -> Unit = { _, _ -> },
    private val onMenuClick: (SongEntity, View, Int) -> Unit = { _, _, _ -> }
) : ListAdapter<SongEntity, MusicAdapter.SongViewHolder>(SongDiffCallback()) {

    // Selection state
    private val selectedItems = mutableSetOf<Long>()
    var isSelectionMode = false
        private set

    // Selection listener
    var onSelectionChanged: ((Set<Long>) -> Unit)? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SongViewHolder {
        val binding = ItemSongBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return SongViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SongViewHolder, position: Int) {
        holder.bind(getItem(position), position)
    }

    fun toggleSelection(songId: Long) {
        if (selectedItems.contains(songId)) {
            selectedItems.remove(songId)
        } else {
            selectedItems.add(songId)
        }
        
        // Exit selection mode if nothing selected
        if (selectedItems.isEmpty()) {
            isSelectionMode = false
        }
        
        notifyDataSetChanged()
        onSelectionChanged?.invoke(selectedItems.toSet())
    }

    fun enterSelectionMode() {
        isSelectionMode = true
        notifyDataSetChanged()
    }

    fun exitSelectionMode() {
        isSelectionMode = false
        selectedItems.clear()
        notifyDataSetChanged()
        onSelectionChanged?.invoke(emptySet())
    }

    fun selectAll() {
        currentList.forEach { selectedItems.add(it.id) }
        notifyDataSetChanged()
        onSelectionChanged?.invoke(selectedItems.toSet())
    }

    fun getSelectedSongs(): List<SongEntity> {
        return currentList.filter { selectedItems.contains(it.id) }
    }

    fun getSelectedCount(): Int = selectedItems.size

    fun isSelected(songId: Long): Boolean = selectedItems.contains(songId)

    inner class SongViewHolder(private val binding: ItemSongBinding) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(song: SongEntity, position: Int) {
            binding.tvTitle.text = song.title
            binding.tvArtist.text = song.artist
            binding.tvDuration.text = TXAFormat.formatDuration(song.duration, false)
            
            // Load album art using ContentUris
            val albumArtUri = try {
                ContentUris.withAppendedId(
                    Uri.parse("content://media/external/audio/albumart"),
                    song.albumId
                )
            } catch (e: Exception) {
                null
            }

            binding.ivAlbumArt.load(albumArtUri) {
                crossfade(true)
                error(R.drawable.ic_music_note)
                placeholder(R.drawable.ic_music_note)
            }

            // Selection state
            val isSelected = selectedItems.contains(song.id)
            binding.cbSelect.visibility = if (isSelectionMode) View.VISIBLE else View.GONE
            binding.cbSelect.isChecked = isSelected
            binding.selectionOverlay.visibility = if (isSelected) View.VISIBLE else View.GONE

            // Adjust album art margin when in selection mode
            val albumParams = binding.ivAlbumArt.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
            if (isSelectionMode) {
                albumParams.startToEnd = binding.cbSelect.id
                albumParams.startToStart = -1
                albumParams.marginStart = 12
            } else {
                albumParams.startToEnd = -1
                albumParams.startToStart = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
                albumParams.marginStart = 0
            }
            binding.ivAlbumArt.layoutParams = albumParams

            // Click handlers
            binding.root.setOnClickListener {
                if (isSelectionMode) {
                    toggleSelection(song.id)
                } else {
                    onItemClick(song)
                }
            }

            binding.root.setOnLongClickListener {
                if (!isSelectionMode) {
                    enterSelectionMode()
                }
                toggleSelection(song.id)
                onItemLongClick(song, position)
                true
            }

            binding.cbSelect.setOnClickListener {
                toggleSelection(song.id)
            }

            // Menu button
            binding.btnMenu.setOnClickListener { view ->
                onMenuClick(song, view, position)
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
