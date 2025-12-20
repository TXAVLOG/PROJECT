package gc.txa.demo.ui

import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import gc.txa.demo.core.TXAFormat
import gc.txa.demo.core.TXATranslation
import gc.txa.demo.databinding.ActivityTxaFileManagerBinding
import gc.txa.demo.update.TXAInstall
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class TXAFileManagerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTxaFileManagerBinding
    private lateinit var adapter: FileManagerAdapter
    private val downloadDir = File("/storage/emulated/0/Download/TXADEMO")

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
            toolbar.title = TXATranslation.txa("file_manager_title")
            toolbar.setNavigationOnClickListener {
                finish()
            }

            tvStoragePath.text = downloadDir.absolutePath
            btnRefresh.text = TXATranslation.txa("file_manager_refresh")
            btnCleanUp.text = TXATranslation.txa("file_manager_cleanup")

            btnRefresh.setOnClickListener {
                loadFiles()
            }

            btnCleanUp.setOnClickListener {
                showCleanupDialog()
            }
        }
    }

    private fun setupRecyclerView() {
        adapter = FileManagerAdapter { file, action ->
            when (action) {
                "install" -> installFile(file)
                "delete" -> showDeleteDialog(file)
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
            binding.tvFileCount.text = TXATranslation.txa("file_manager_files_count").format("0")
        } else {
            binding.recyclerView.visibility = View.VISIBLE
            binding.emptyState.visibility = View.GONE
            
            val totalSize = files.sumOf { it.length() }
            binding.tvFileCount.text = TXATranslation.txa("file_manager_files_count").format(files.size.toString())
            
            adapter.updateFiles(files)
        }
    }

    private fun installFile(file: File) {
        val success = TXAInstall.installApk(this, file)
        if (success) {
            Toast.makeText(this, TXATranslation.txa("file_manager_install_success"), Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, TXATranslation.txa("file_manager_install_failed"), Toast.LENGTH_SHORT).show()
        }
    }

    private fun showDeleteDialog(file: File) {
        AlertDialog.Builder(this)
            .setTitle(TXATranslation.txa("file_manager_delete_confirm"))
            .setMessage(TXATranslation.txa("file_manager_delete_message").format(file.name))
            .setPositiveButton(TXATranslation.txa("file_manager_delete")) { _, _ ->
                deleteFile(file)
            }
            .setNegativeButton(TXATranslation.txa("action_cancel"), null)
            .show()
    }

    private fun deleteFile(file: File) {
        try {
            if (file.delete()) {
                Toast.makeText(this, TXATranslation.txa("file_manager_delete_success"), Toast.LENGTH_SHORT).show()
                loadFiles()
            } else {
                Toast.makeText(this, TXATranslation.txa("file_manager_delete_failed"), Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, TXATranslation.txa("file_manager_delete_failed"), Toast.LENGTH_SHORT).show()
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
            .setNegativeButton(TXATranslation.txa("action_cancel"), null)
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
