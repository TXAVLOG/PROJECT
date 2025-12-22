package ms.txams.vv.ui

import android.Manifest
import android.content.ContentUris
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.MediaStore.Audio.Media
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import gc.txa.demo.data.database.SongEntity
import gc.txa.demo.data.repository.MusicRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ms.txams.vv.core.TXAFormat
import ms.txams.vv.core.TXATranslation
import ms.txams.vv.databinding.ActivityTxaFileManagerBinding
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@AndroidEntryPoint
class TXAFileManagerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTxaFileManagerBinding
    private lateinit var adapter: MusicAdapter
    private val musicFiles = mutableListOf<SongEntity>()

    @Inject
    lateinit var musicRepository: MusicRepository

    companion object {
        private const val REQUEST_PERMISSION = 100
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTxaFileManagerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        setupRecyclerView()
        loadFiles()
    }

    private fun setupUI() {
        binding.apply {
            toolbar.title = TXATranslation.txa("txamusic_music_library_title")
            toolbar.setNavigationOnClickListener {
                finish()
            }

            tvStoragePath.text = TXATranslation.txa("txamusic_all_songs")
            btnRefresh.text = TXATranslation.txa("txamusic_refresh_library")
            btnCleanUp.text = TXATranslation.txa("txamusic_scan_library")

            btnRefresh.setOnClickListener {
                loadFiles()
            }

            btnCleanUp.setOnClickListener {
                scanMusicLibrary()
            }
        }
    }

    private fun setupRecyclerView() {
        adapter = MusicAdapter { song, action ->
            when (action) {
                "play" -> playSong(song)
                "add_to_playlist" -> addToPlaylist(song)
            }
        }
        
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(this@TXAFileManagerActivity)
            adapter = this@TXAFileManagerActivity.adapter
        }
    }

    private fun loadFiles() {
        if (!downloadDir.exists()) {
            downloadDir.mkdirs()
        }

        val files = downloadDir.listFiles { file ->
            file.isFile && file.name.endsWith(".apk")
        }?.sortedByDescending { it.lastModified() } ?: emptyList()

        if (files.isEmpty()) {
            binding.recyclerView.visibility = View.GONE
            binding.emptyState.visibility = View.VISIBLE
            binding.tvFileCount.text = TXATranslation.txa("txamusic_file_manager_files_count").format("0")
        } else {
            binding.recyclerView.visibility = View.VISIBLE
            binding.emptyState.visibility = View.GONE
            
            binding.tvFileCount.text = TXATranslation.txa("txamusic_file_manager_files_count").format(files.size.toString())
            
            adapter.updateFiles(files)
        }
    }

    private fun installFile(file: File) {
        val success = TXAInstall.installApk(this, file)
        if (success) {
            Toast.makeText(this, TXATranslation.txa("txamusic_file_manager_install_success"), Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, TXATranslation.txa("txamusic_file_manager_install_failed"), Toast.LENGTH_SHORT).show()
        }
    }

    private fun showDeleteDialog(file: File) {
        AlertDialog.Builder(this)
            .setTitle(TXATranslation.txa("txamusic_file_manager_delete_confirm"))
            .setMessage(TXATranslation.txa("txamusic_file_manager_delete_message").format(file.name))
            .setPositiveButton(TXATranslation.txa("txamusic_file_manager_delete")) { _, _ ->
                deleteFile(file)
            }
            .setNegativeButton(TXATranslation.txa("txamusic_action_cancel"), null)
            .show()
    }

    private fun deleteFile(file: File) {
        try {
            if (file.delete()) {
                Toast.makeText(this, TXATranslation.txa("txamusic_file_manager_delete_success"), Toast.LENGTH_SHORT).show()
                loadFiles()
            } else {
                Toast.makeText(this, TXATranslation.txa("txamusic_file_manager_delete_failed"), Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, TXATranslation.txa("txamusic_file_manager_delete_failed"), Toast.LENGTH_SHORT).show()
        }
    }

    private fun showCleanupDialog() {
        val oldFiles = downloadDir.listFiles { file ->
            file.isFile && file.name.endsWith(".apk")
        }?.filter { file ->
            val ageInDays = (System.currentTimeMillis() - file.lastModified()) / (1000 * 60 * 60 * 24)
            ageInDays > 7
        } ?: emptyList()

        if (oldFiles.isEmpty()) {
            Toast.makeText(this, "No old files to clean up", Toast.LENGTH_SHORT).show()
            return
        }

        val message = "Found ${oldFiles.size} old files (>7 days). Delete them all?"
        AlertDialog.Builder(this)
            .setTitle("Clean Up Old Files")
            .setMessage(message)
            .setPositiveButton("Delete All") { _, _ ->
                var deletedCount = 0
                oldFiles.forEach { file ->
                    try {
                        if (file.delete()) deletedCount++
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                Toast.makeText(this, "Deleted $deletedCount old files", Toast.LENGTH_SHORT).show()
                loadFiles()
            }
            .setNegativeButton(TXATranslation.txa("txamusic_action_cancel"), null)
            .show()
    }
}

// File data class
data class FileInfo(
    val file: File,
    val name: String,
    val path: String,
    val size: String,
    val date: String
)

// RecyclerView Adapter
class FileManagerAdapter(
    private val onFileAction: (File, String) -> Unit
) : androidx.recyclerview.widget.RecyclerView.Adapter<FileManagerAdapter.ViewHolder>() {

    private var files: List<FileInfo> = emptyList()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    fun updateFiles(newFiles: List<File>) {
        files = newFiles.map { file ->
            FileInfo(
                file = file,
                name = file.name,
                path = file.absolutePath,
                size = TXAFormat.formatBytes(file.length()),
                date = dateFormat.format(Date(file.lastModified()))
            )
        }
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
        val view = android.view.LayoutInflater.from(parent.context)
            .inflate(gc.txa.demo.R.layout.item_file_manager, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(files[position])
    }

    override fun getItemCount(): Int = files.size

    inner class ViewHolder(itemView: android.view.View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(itemView) {
        fun bind(fileInfo: FileInfo) {
            itemView.findViewById<android.widget.TextView>(gc.txa.demo.R.id.tvFileName).text = fileInfo.name
            itemView.findViewById<android.widget.TextView>(gc.txa.demo.R.id.tvFilePath).text = fileInfo.path
            itemView.findViewById<android.widget.TextView>(gc.txa.demo.R.id.tvFileSize).text = fileInfo.size
            itemView.findViewById<android.widget.TextView>(gc.txa.demo.R.id.tvFileDate).text = fileInfo.date

            itemView.findViewById<com.google.android.material.button.MaterialButton>(gc.txa.demo.R.id.btnInstall)
                .setOnClickListener { onFileAction(fileInfo.file, "install") }
            
            itemView.findViewById<com.google.android.material.button.MaterialButton>(gc.txa.demo.R.id.btnDelete)
                .setOnClickListener { onFileAction(fileInfo.file, "delete") }
        }
    }
}
