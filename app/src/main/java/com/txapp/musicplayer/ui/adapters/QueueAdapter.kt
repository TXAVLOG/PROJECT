package com.txapp.musicplayer.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.LayoutRes
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.h6ah4i.android.widget.advrecyclerview.draggable.DraggableItemAdapter
import com.h6ah4i.android.widget.advrecyclerview.draggable.DraggableItemState
import com.h6ah4i.android.widget.advrecyclerview.draggable.ItemDraggableRange
import com.h6ah4i.android.widget.advrecyclerview.draggable.annotation.DraggableItemStateFlags
import com.h6ah4i.android.widget.advrecyclerview.swipeable.SwipeableItemAdapter
import com.h6ah4i.android.widget.advrecyclerview.swipeable.SwipeableItemConstants
import com.h6ah4i.android.widget.advrecyclerview.swipeable.action.SwipeResultAction
import com.h6ah4i.android.widget.advrecyclerview.swipeable.action.SwipeResultActionDefault
import com.h6ah4i.android.widget.advrecyclerview.swipeable.action.SwipeResultActionRemoveItem
import com.h6ah4i.android.widget.advrecyclerview.utils.AbstractDraggableSwipeableItemViewHolder
import com.txapp.musicplayer.R
import com.txapp.musicplayer.glide.TXAGlideExtension
import com.txapp.musicplayer.model.Song
import com.txapp.musicplayer.util.TXAFormat

/**
 * Queue Adapter with AdvRecyclerView support for smooth drag-drop and swipe animations.
 * Implements DraggableItemAdapter and SwipeableItemAdapter for advanced RecyclerView features.
 */
class QueueAdapter(
    private var songs: MutableList<Song>,
    private var currentPosition: Int,
    private val onItemClick: (Int) -> Unit,
    private val onItemMove: (fromPosition: Int, toPosition: Int) -> Unit,
    private val onItemRemove: (position: Int) -> Unit
) : RecyclerView.Adapter<QueueAdapter.QueueViewHolder>(),
    DraggableItemAdapter<QueueAdapter.QueueViewHolder>,
    SwipeableItemAdapter<QueueAdapter.QueueViewHolder> {

    init {
        setHasStableIds(true)
    }

    fun swapData(newSongs: List<Song>, newPosition: Int) {
        songs = newSongs.toMutableList()
        currentPosition = newPosition
        notifyDataSetChanged()
    }

    fun updateCurrentPosition(position: Int) {
        val oldPosition = currentPosition
        currentPosition = position
        if (oldPosition >= 0 && oldPosition < songs.size) notifyItemChanged(oldPosition)
        if (position >= 0 && position < songs.size) notifyItemChanged(position)
    }

    override fun getItemId(position: Int): Long = songs[position].id

    override fun getItemCount(): Int = songs.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QueueViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_queue, parent, false)
        return QueueViewHolder(view)
    }

    override fun onBindViewHolder(holder: QueueViewHolder, position: Int) {
        val song = songs[position]
        val isCurrentSong = position == currentPosition
        
        holder.songTitle?.text = song.title
        holder.songArtist?.text = song.artist
        holder.songDuration?.text = TXAFormat.formatDuration(song.duration)
        
        // Highlight current playing song
        val alpha = when {
            position < currentPosition -> 0.5f  // History
            position == currentPosition -> 1.0f  // Current
            else -> 0.85f  // Up next
        }
        holder.itemView.alpha = alpha
        
        // Load album art
        holder.albumArt?.let { imageView ->
            TXAGlideExtension.loadAlbumArt(imageView, song)
        }
        
        holder.itemView.setOnClickListener { onItemClick(position) }
    }

    // ========== DraggableItemAdapter Implementation ==========

    override fun onCheckCanStartDrag(holder: QueueViewHolder, position: Int, x: Int, y: Int): Boolean {
        // Allow drag from anywhere on the item (or could restrict to drag handle)
        return true
    }

    override fun onGetItemDraggableRange(holder: QueueViewHolder, position: Int): ItemDraggableRange? {
        return null // Allow drag anywhere in the list
    }

    override fun onMoveItem(fromPosition: Int, toPosition: Int) {
        if (fromPosition == toPosition) return
        
        val item = songs.removeAt(fromPosition)
        songs.add(toPosition, item)
        
        // Update current position if needed
        currentPosition = when {
            fromPosition == currentPosition -> toPosition
            fromPosition < currentPosition && toPosition >= currentPosition -> currentPosition - 1
            fromPosition > currentPosition && toPosition <= currentPosition -> currentPosition + 1
            else -> currentPosition
        }
        
        onItemMove(fromPosition, toPosition)
    }

    override fun onCheckCanDrop(draggingPosition: Int, dropPosition: Int): Boolean = true

    override fun onItemDragStarted(position: Int) {
        notifyDataSetChanged()
    }

    override fun onItemDragFinished(fromPosition: Int, toPosition: Int, result: Boolean) {
        notifyDataSetChanged()
    }

    // ========== SwipeableItemAdapter Implementation ==========

    override fun onGetSwipeReactionType(holder: QueueViewHolder, position: Int, x: Int, y: Int): Int {
        return SwipeableItemConstants.REACTION_CAN_SWIPE_BOTH_H
    }

    override fun onSwipeItemStarted(holder: QueueViewHolder, position: Int) {}

    override fun onSetSwipeBackground(holder: QueueViewHolder, position: Int, type: Int) {}

    override fun onSwipeItem(holder: QueueViewHolder, position: Int, result: Int): SwipeResultAction {
        return if (result == SwipeableItemConstants.RESULT_CANCELED) {
            SwipeResultActionDefault()
        } else {
            SwipedRemoveAction(this, position)
        }
    }

    // ========== ViewHolder ==========

    inner class QueueViewHolder(itemView: View) : AbstractDraggableSwipeableItemViewHolder(itemView) {
        val albumArt: ImageView? = itemView.findViewById(R.id.albumArt)
        val songTitle: TextView? = itemView.findViewById(R.id.songTitle)
        val songArtist: TextView? = itemView.findViewById(R.id.songArtist)
        val songDuration: TextView? = itemView.findViewById(R.id.songDuration)
        val container: View? = itemView.findViewById(R.id.container)
        
        @DraggableItemStateFlags
        private var dragStateFlags: Int = 0
        
        override fun getSwipeableContainerView(): View = container ?: itemView
        
        @DraggableItemStateFlags
        override fun getDragStateFlags(): Int = dragStateFlags
        
        override fun setDragStateFlags(@DraggableItemStateFlags flags: Int) {
            dragStateFlags = flags
        }
    }

    // ========== Swipe Remove Action ==========

    private class SwipedRemoveAction(
        private val adapter: QueueAdapter,
        private val position: Int
    ) : SwipeResultActionRemoveItem() {
        
        override fun onPerformAction() {
            // Delay removal until animation completes
        }

        override fun onSlideAnimationEnd() {
            if (position >= 0 && position < adapter.songs.size) {
                adapter.songs.removeAt(position)
                adapter.notifyItemRemoved(position)
                
                // Adjust current position
                if (position < adapter.currentPosition) {
                    adapter.currentPosition--
                } else if (position == adapter.currentPosition && adapter.songs.isNotEmpty()) {
                    // Current song was removed, update UI
                }
                
                adapter.onItemRemove(position)
            }
        }
    }

    companion object {
        private const val VIEW_TYPE_HISTORY = 0
        private const val VIEW_TYPE_CURRENT = 1
        private const val VIEW_TYPE_UP_NEXT = 2
    }
}
