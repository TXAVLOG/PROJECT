package com.txapp.musicplayer.ui.adapters.album

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.txapp.musicplayer.R
import com.txapp.musicplayer.interfaces.IAlbumClickListener
import com.txapp.musicplayer.model.Album

class AlbumAdapter(
    private val activity: FragmentActivity,
    private var dataSet: List<Album>,
    private val itemLayoutRes: Int,
    private val listener: IAlbumClickListener?
) : RecyclerView.Adapter<AlbumAdapter.ViewHolder>() {

    fun swapDataSet(dataSet: List<Album>) {
        this.dataSet = dataSet
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(activity).inflate(itemLayoutRes, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val album = dataSet[position]
        holder.title.text = album.title
        holder.text.text = album.artistName
        
        val song = album.safeGetFirstSong()
        com.txapp.musicplayer.glide.TXAGlideExtension.loadAlbumArt(holder.image, song)
    }

    override fun getItemCount() = dataSet.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val title: TextView = itemView.findViewById(R.id.title)
        val text: TextView = itemView.findViewById(R.id.text)
        val image: ImageView = itemView.findViewById(R.id.image)

        init {
            itemView.setOnClickListener {
                listener?.onAlbumClick(dataSet[layoutPosition].id, image)
            }
        }
    }
}
