package com.txapp.musicplayer.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.txapp.musicplayer.R
import com.txapp.musicplayer.databinding.ActivityTxaScanResultBinding
import com.txapp.musicplayer.databinding.ItemTxaScanTreeBinding
import com.txapp.musicplayer.util.TXAMusicScanner
import com.txapp.musicplayer.util.txa

class TXAScanResultActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTxaScanResultBinding

    companion object {
        var lastResult: TXAMusicScanner.ScanResult? = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTxaScanResultBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.title = "txamusic_scan_result_title".txa()
        setSupportActionBar(binding.toolbar)

        val result = lastResult ?: finish().run { return }

        val flattenedItems = mutableListOf<TreeItem>()
        flatten(result.scannedFolders, 0, flattenedItems)

        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = TreeAdapter(flattenedItems)

        binding.btnClose.text = "txamusic_btn_ok".txa()
        binding.btnClose.setOnClickListener { finish() }
    }

    private fun flatten(node: TXAMusicScanner.FolderNode, depth: Int, list: MutableList<TreeItem>) {
        list.add(TreeItem(node.name, depth, isFolder = true, isSuccess = !node.isExcluded, reason = node.reason))
        
        // Children folders
        node.children.forEach { flatten(it, depth + 1, list) }
        
        // Files in this folder
        node.files.forEach { file ->
            list.add(TreeItem(file.name, depth + 1, isFolder = false, isSuccess = file.isSuccess, reason = file.reason))
        }
    }

    data class TreeItem(
        val name: String,
        val depth: Int,
        val isFolder: Boolean,
        val isSuccess: Boolean,
        val reason: String?
    )

    class TreeAdapter(val items: List<TreeItem>) : RecyclerView.Adapter<TreeAdapter.ViewHolder>() {
        class ViewHolder(val binding: ItemTxaScanTreeBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val b = ItemTxaScanTreeBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(b)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.binding.nameText.text = item.name
            holder.binding.root.setPadding(item.depth * 32, 4, 16, 4)
            
            val iconRes = if (item.isFolder) R.drawable.ic_folder else R.drawable.ic_music_note
            holder.binding.icon.setImageResource(iconRes)
            
            if (!item.isSuccess) {
                holder.binding.icon.setImageResource(if (item.isFolder) R.drawable.ic_folder else R.drawable.ic_close)
                holder.binding.icon.setColorFilter(android.graphics.Color.RED)
                holder.binding.reasonText.text = "- ${item.reason}"
                holder.binding.reasonText.visibility = android.view.View.VISIBLE
            } else {
                holder.binding.icon.setColorFilter(if (item.isFolder) 0xFFFFA000.toInt() else 0xFF2196F3.toInt())
                holder.binding.reasonText.visibility = android.view.View.GONE
            }
        }

        override fun getItemCount() = items.size
    }
}
