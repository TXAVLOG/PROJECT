package com.txapp.musicplayer.ui.adapters.artist

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
import com.txapp.musicplayer.interfaces.IArtistClickListener
import com.txapp.musicplayer.model.Artist

class ArtistAdapter(
    private val activity: FragmentActivity,
    private var dataSet: List<Artist>,
    private val itemLayoutRes: Int,
    private val listener: IArtistClickListener?
) : RecyclerView.Adapter<ArtistAdapter.ViewHolder>() {

    fun swapDataSet(dataSet: List<Artist>) {
        this.dataSet = dataSet
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val layoutRes = if (itemLayoutRes == 0) R.layout.item_album_card else itemLayoutRes
        val view = LayoutInflater.from(activity).inflate(layoutRes, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val artist = dataSet[position]
        holder.title.text = artist.name
        holder.text?.text = "${artist.albumCount} Albums"
        
        val song = artist.safeGetFirstAlbum().safeGetFirstSong()
        Glide.with(activity)
            .load(song.data)
            .placeholder(R.drawable.ic_music_note)
            .transition(DrawableTransitionOptions.withCrossFade())
            .into(holder.image)
    }

    override fun getItemCount() = dataSet.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val title: TextView = itemView.findViewById(R.id.title)
        val text: TextView? = itemView.findViewById(R.id.text)
        val image: ImageView = itemView.findViewById(R.id.image)

        init {
            itemView.setOnClickListener {
                val artist = dataSet[layoutPosition]
                listener?.onArtist(artist.id, artist.name, image)
            }
        }
    }
}
