package ms.txams.vv.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import ms.txams.vv.databinding.ItemMusicCardBinding

class MusicCardAdapter(
    private val items: List<MusicCardItem>,
    private val onItemClick: ((MusicCardItem) -> Unit)? = null
) : RecyclerView.Adapter<MusicCardAdapter.MusicCardViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MusicCardViewHolder {
        val binding = ItemMusicCardBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return MusicCardViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MusicCardViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class MusicCardViewHolder(
        private val binding: ItemMusicCardBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: MusicCardItem) {
            binding.tvSongTitle.text = item.title
            binding.tvArtistName.text = item.subtitle
            binding.ivAlbumArt.setImageResource(item.imageRes)
            
            binding.root.setOnClickListener {
                onItemClick?.invoke(item)
            }
            
            binding.btnPlay.setOnClickListener {
                onItemClick?.invoke(item)
            }
        }
    }
}
