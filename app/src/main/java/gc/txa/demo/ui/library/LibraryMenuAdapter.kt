package gc.txa.demo.ui.library

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import gc.txa.demo.R

class LibraryMenuAdapter(
    private val onItemClick: (LibraryMenuItem) -> Unit
) : ListAdapter<LibraryMenuItem, LibraryMenuAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_library_menu, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivIcon: ImageView = itemView.findViewById(R.id.iv_menu_icon)
        private val tvTitle: TextView = itemView.findViewById(R.id.tv_menu_title)

        fun bind(item: LibraryMenuItem) {
            ivIcon.setImageResource(item.iconResId)
            tvTitle.text = item.title
            itemView.setOnClickListener {
                onItemClick(item)
            }
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<LibraryMenuItem>() {
        override fun areItemsTheSame(oldItem: LibraryMenuItem, newItem: LibraryMenuItem): Boolean {
            return oldItem.type == newItem.type
        }

        override fun areContentsTheSame(oldItem: LibraryMenuItem, newItem: LibraryMenuItem): Boolean {
            return oldItem == newItem
        }
    }
}
