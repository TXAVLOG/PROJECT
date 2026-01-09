package com.txapp.musicplayer.ui.adapters

import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.h6ah4i.android.widget.advrecyclerview.utils.AbstractDraggableSwipeableItemViewHolder
import com.txapp.musicplayer.R

/**
 * Base ViewHolder for draggable and swipeable items.
 * Extends AbstractDraggableSwipeableItemViewHolder from AdvRecyclerView library
 * for smooth drag-drop and swipe animations.
 */
open class DraggableViewHolder(itemView: View) : AbstractDraggableSwipeableItemViewHolder(itemView) {
    
    val albumArt: ImageView? = itemView.findViewById(R.id.albumArt)
    val songTitle: TextView? = itemView.findViewById(R.id.songTitle)
    val songArtist: TextView? = itemView.findViewById(R.id.songArtist)
    val songDuration: TextView? = itemView.findViewById(R.id.songDuration)
    val dragHandle: View? = itemView.findViewById(R.id.dragHandle)
    
    // Container for swipe background (required for swipe animation)
    val container: View? = itemView.findViewById(R.id.container)
    
    override fun getSwipeableContainerView(): View {
        return container ?: itemView
    }
}
