package com.txapp.musicplayer.ui

import android.os.Bundle
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
    private var allFolders by mutableStateOf(emptyList<FolderInfo>())
    private var allSongs by mutableStateOf(emptyList<Song>())
    private var currentPath by mutableStateOf<String?>(null)

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
                    val foldersToShow = if (currentPath == null) {
                        allFolders
                    } else {
                        emptyList()
                    }
                    
                    val songsToShow = if (currentPath != null) {
                        allSongs.filter { File(it.data).parent == currentPath }
                    } else {
                        emptyList()
                    }

                    FoldersScreen(
                        currentPath = currentPath,
                        folders = foldersToShow,
                        songs = songsToShow,
                        onFolderClick = { folder -> currentPath = folder.path },
                        onBackClick = { currentPath = null },
                        onBreadcrumbClick = { path -> currentPath = path },
                        onSongClick = { song, songs -> (activity as? MainActivity)?.playSongs(songs, songs.indexOf(song)) }
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
                allFolders = songs.groupBy { song ->
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
}

@Composable
fun FoldersScreen(
    currentPath: String?,
    folders: List<FolderInfo>,
    songs: List<Song>,
    onFolderClick: (FolderInfo) -> Unit,
    onBackClick: () -> Unit,
    onBreadcrumbClick: (String?) -> Unit,
    onSongClick: (Song, List<Song>) -> Unit
) {
    val accentColor = Color(android.graphics.Color.parseColor(TXAPreferences.currentAccent))
    
    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Breadcrumb
        Box(modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp), contentAlignment = Alignment.CenterStart) {
            androidx.compose.ui.viewinterop.AndroidView(
                factory = { context ->
                    com.txapp.musicplayer.ui.component.BreadCrumbLayout(context).apply {
                        setCallback(object : com.txapp.musicplayer.ui.component.BreadCrumbLayout.SelectionCallback {
                            override fun onCrumbSelection(crumb: com.txapp.musicplayer.ui.component.BreadCrumbLayout.Crumb, index: Int) {
                                if (index == 0) onBreadcrumbClick(null)
                                else onBreadcrumbClick(crumb.file.path)
                            }
                        })
                    }
                },
                update = { view ->
                    view.clearCrumbs()
                    // Root crumb
                    view.addCrumb(com.txapp.musicplayer.ui.component.BreadCrumbLayout.Crumb(File("Folders")), false)
                    
                    currentPath?.let { path ->
                        val file = File(path)
                        val components = mutableListOf<File>()
                        var p: File? = file
                        while (p != null && p.path != "/") {
                            components.add(0, p)
                            p = p.parentFile
                        }
                        
                        components.forEach { comp ->
                            view.addCrumb(com.txapp.musicplayer.ui.component.BreadCrumbLayout.Crumb(comp), false)
                        }
                    }
                    view.invalidate()
                },
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (currentPath == null && folders.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "txamusic_home_no_songs".txa(), color = Color.Gray)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                if (currentPath == null) {
                    items(folders) { folder ->
                        FolderItem(folder = folder, accentColor = accentColor, onClick = { onFolderClick(folder) })
                    }
                } else {
                    items(songs) { song ->
                        SongItem(song = song, accentColor = accentColor, onClick = { onSongClick(song, songs) })
                    }
                }
            }
        }
    }
}

@Composable
private fun SongItem(
    song: Song,
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
        Icon(
            painter = androidx.compose.ui.res.painterResource(id = com.txapp.musicplayer.R.drawable.ic_music_note),
            contentDescription = null,
            tint = accentColor,
            modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = song.artist,
                fontSize = 12.sp,
                color = Color.Gray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
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
