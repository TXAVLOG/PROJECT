package ms.txams.vv.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import ms.txams.vv.R
import ms.txams.vv.core.TXATranslation
import ms.txams.vv.core.TXABackgroundLogger
import java.io.File

/**
 * TXA Custom File Picker
 * A fallback file picker when system Document Provider is not available
 * 
 * Features:
 * - Browse local storage for audio files
 * - Shows file metadata (size, date)
 * - Copyright/License information
 * - Works on Android 9+ and emulators without Document UI
 * 
 * @author TXA - fb.com/vlog.txa.2311 - txavlog7@gmail.com
 * @license MIT License - Free to use with attribution
 * @copyright 2024 TXA Music - All Rights Reserved
 */
class TXAFilePickerDialog : BottomSheetDialogFragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var tvPath: TextView
    private lateinit var tvEmpty: TextView
    private lateinit var tvCopyright: TextView
    private lateinit var btnBack: ImageView
    
    private var currentPath: File = Environment.getExternalStorageDirectory()
    private var onFileSelected: ((File) -> Unit)? = null
    
    private val audioExtensions = listOf("mp3", "m4a", "wav", "flac", "ogg", "aac", "wma", "opus")
    
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            loadDirectory(currentPath)
        } else {
            Toast.makeText(requireContext(), TXATranslation.txa("txamusic_permission_denied"), Toast.LENGTH_SHORT).show()
            dismiss()
        }
    }
    
    companion object {
        const val TAG = "TXAFilePickerDialog"
        
        fun newInstance(onFileSelected: (File) -> Unit): TXAFilePickerDialog {
            return TXAFilePickerDialog().apply {
                this.onFileSelected = onFileSelected
            }
        }
    }
    
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.txa_dialog_file_picker, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        recyclerView = view.findViewById(R.id.rvFiles)
        tvPath = view.findViewById(R.id.tvCurrentPath)
        tvEmpty = view.findViewById(R.id.tvEmpty)
        tvCopyright = view.findViewById(R.id.tvCopyright)
        btnBack = view.findViewById(R.id.btnBack)
        val btnFacebook: ImageView = view.findViewById(R.id.btnFacebook)
        
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        
        btnBack.setOnClickListener {
            navigateUp()
        }
        
        // Copyright notice with dynamic year
        val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
        tvCopyright.text = """
            TXA Music File Browser
            © $currentYear TXA - All Rights Reserved
            
            ${TXATranslation.txa("txamusic_copyright_license")}
        """.trimIndent()
        
        // Facebook button click handler
        val openFacebookAction: () -> Unit = {
            try {
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW)
                intent.data = android.net.Uri.parse("https://fb.com/vlog.txa.2311")
                startActivity(intent)
            } catch (e: Exception) {
                TXABackgroundLogger.e("Failed to open Facebook", e)
            }
        }
        
        // Make copyright clickable to open Facebook
        tvCopyright.setOnClickListener { openFacebookAction() }
        btnFacebook.setOnClickListener { openFacebookAction() }
        
        checkPermissionAndLoad()
    }
    
    private fun checkPermissionAndLoad() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        
        if (ContextCompat.checkSelfPermission(requireContext(), permission) == PackageManager.PERMISSION_GRANTED) {
            // Try common music directories first
            val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
            currentPath = if (musicDir.exists() && musicDir.canRead()) musicDir else Environment.getExternalStorageDirectory()
            loadDirectory(currentPath)
        } else {
            permissionLauncher.launch(permission)
        }
    }
    
    private fun loadDirectory(directory: File) {
        currentPath = directory
        tvPath.text = directory.absolutePath
        
        TXABackgroundLogger.d("Loading directory: ${directory.absolutePath}")
        
        try {
            val files = directory.listFiles()?.filter { file ->
                if (file.isDirectory) {
                    // Show directories that are readable and not hidden
                    !file.isHidden && file.canRead()
                } else {
                    // Show audio files only
                    val extension = file.extension.lowercase()
                    audioExtensions.contains(extension)
                }
            }?.sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() })
            
            if (files.isNullOrEmpty()) {
                tvEmpty.visibility = View.VISIBLE
                recyclerView.visibility = View.GONE
                tvEmpty.text = TXATranslation.txa("txamusic_library_empty")
            } else {
                tvEmpty.visibility = View.GONE
                recyclerView.visibility = View.VISIBLE
                recyclerView.adapter = FileAdapter(files) { file ->
                    if (file.isDirectory) {
                        loadDirectory(file)
                    } else {
                        onFileSelected?.invoke(file)
                        dismiss()
                    }
                }
            }
            
            // Update back button visibility
            btnBack.visibility = if (directory != Environment.getExternalStorageDirectory()) {
                View.VISIBLE
            } else {
                View.INVISIBLE
            }
            
        } catch (e: Exception) {
            TXABackgroundLogger.e("Error loading directory", e)
            tvEmpty.visibility = View.VISIBLE
            tvEmpty.text = "Error: ${e.message}"
        }
    }
    
    private fun navigateUp() {
        val parent = currentPath.parentFile
        if (parent != null && parent.canRead()) {
            loadDirectory(parent)
        }
    }
    
    // File Adapter
    inner class FileAdapter(
        private val files: List<File>,
        private val onClick: (File) -> Unit
    ) : RecyclerView.Adapter<FileAdapter.ViewHolder>() {
        
        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val icon: ImageView = view.findViewById(R.id.ivFileIcon)
            val name: TextView = view.findViewById(R.id.tvFileName)
            val info: TextView = view.findViewById(R.id.tvFileInfo)
        }
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.txa_item_file, parent, false)
            return ViewHolder(view)
        }
        
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val file = files[position]
            
            holder.name.text = file.name
            
            if (file.isDirectory) {
                holder.icon.setImageResource(R.drawable.ic_folder)
                val childCount = file.listFiles()?.size ?: 0
                holder.info.text = "$childCount items"
            } else {
                holder.icon.setImageResource(R.drawable.ic_music_note)
                holder.info.text = formatFileSize(file.length())
            }
            
            holder.itemView.setOnClickListener { onClick(file) }
        }
        
        override fun getItemCount() = files.size
        
        private fun formatFileSize(bytes: Long): String {
            return when {
                bytes < 1024 -> "$bytes B"
                bytes < 1024 * 1024 -> "${bytes / 1024} KB"
                else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
            }
        }
    }
}
