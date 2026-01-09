package com.txapp.musicplayer.ui

import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.txapp.musicplayer.MusicApplication
import com.txapp.musicplayer.data.MusicRepository
import com.txapp.musicplayer.model.Song
import com.txapp.musicplayer.util.TXAPreferences
import com.txapp.musicplayer.util.txa
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File

data class FolderInfo(
    val path: String,
    val name: String,
    val songCount: Int
)

class FoldersFragment : Fragment() {

    private lateinit var repository: MusicRepository
    private var folders by mutableStateOf(emptyList<FolderInfo>())
    private var allSongs by mutableStateOf(emptyList<Song>())

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val app = requireActivity().application as MusicApplication
        repository = MusicRepository(app.database, requireContext().contentResolver)
        
        return ComposeView(requireContext()).apply {
            setContent {
                MaterialTheme(
                    colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
                ) {
                    FoldersScreen(
                        folders = folders,
                        onFolderClick = { folder -> navigateToFolder(folder) }
                    )
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewLifecycleOwner.lifecycleScope.launch {
            repository.allSongs.collectLatest { songs ->
                allSongs = songs
                folders = songs.groupBy { song ->
                    File(song.data).parent ?: ""
                }.map { (path, songsInFolder) ->
                    FolderInfo(
                        path = path,
                        name = File(path).name,
                        songCount = songsInFolder.size
                    )
                }.sortedBy { it.name.lowercase() }
            }
        }
    }

    private fun navigateToFolder(folder: FolderInfo) {
        val songsInFolder = allSongs.filter { File(it.data).parent == folder.path }
        if (songsInFolder.isNotEmpty()) {
            (activity as? MainActivity)?.playSongs(songsInFolder, 0)
        }
    }
}

@Composable
fun FoldersScreen(
    folders: List<FolderInfo>,
    onFolderClick: (FolderInfo) -> Unit
) {
    val accentColor = Color(android.graphics.Color.parseColor(TXAPreferences.currentAccent))
    
    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Text(
            text = "txamusic_media_folders".txa(),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(16.dp)
        )
        
        if (folders.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "txamusic_home_no_songs".txa(), color = Color.Gray)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                items(folders) { folder ->
                    FolderItem(folder = folder, accentColor = accentColor, onClick = { onFolderClick(folder) })
                }
            }
        }
    }
}

@Composable
private fun FolderItem(
    folder: FolderInfo,
    accentColor: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp)),
            color = accentColor.copy(alpha = 0.15f),
            shape = RoundedCornerShape(12.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = folder.name,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = folder.path,
                fontSize = 12.sp,
                color = Color.Gray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = "${folder.songCount}",
            fontSize = 14.sp,
            color = accentColor,
            fontWeight = FontWeight.Bold
        )
    }
}
