package com.txapp.musicplayer.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import coil.compose.AsyncImage
import com.txapp.musicplayer.MusicApplication
import com.txapp.musicplayer.R
import com.txapp.musicplayer.data.MusicRepository
import com.txapp.musicplayer.model.Playlist
import com.txapp.musicplayer.util.TXAPreferences
import com.txapp.musicplayer.util.txa
import com.txapp.musicplayer.ui.component.TXAIcons
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import com.txapp.musicplayer.ui.component.ImportPlaylistDialog
import androidx.compose.material.icons.filled.Input
import androidx.compose.material.icons.filled.Download

class PlaylistsFragment : Fragment() {

    private lateinit var repository: MusicRepository
    private var playlists by mutableStateOf(emptyList<Playlist>())

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
                    PlaylistsScreen(
                        playlists = playlists,
                        onPlaylistClick = { id -> navigateToPlaylist(id) },
                        onCreatePlaylist = { name ->
                            lifecycleScope.launch {
                                val id = repository.createPlaylist(name)
                                if (id > 0) {
                                    com.txapp.musicplayer.util.TXAToast.success(context, "txamusic_playlist_create_success".txa())
                                    repository.refreshPlaylists()
                                } else {
                                    com.txapp.musicplayer.util.TXALogger.appE("CreatePlaylist", "Failed to create playlist: $name")
                                    com.txapp.musicplayer.util.TXAToast.error(context, "txamusic_error_unknown".txa())
                                }
                            }
                        },
                        onImportPlaylist = { path ->
                            lifecycleScope.launch {
                                val count = repository.importPlaylistFromM3U(context, path)
                                if (count > 0) {
                                    com.txapp.musicplayer.util.TXAToast.success(context, "Imported $count songs")
                                    repository.refreshPlaylists()
                                } else {
                                    com.txapp.musicplayer.util.TXAToast.error(context, "Import failed")
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewLifecycleOwner.lifecycleScope.launch {
            repository.playlists.collectLatest { list: List<Playlist> ->
                playlists = list
            }
        }
    }

    private fun navigateToPlaylist(id: Long) {
        val bundle = Bundle().apply { putLong("extra_playlist_id", id) }
        findNavController().navigate(R.id.playlistDetailsFragment, bundle)
    }
}

@Composable
fun PlaylistsScreen(
    playlists: List<Playlist>,
    onPlaylistClick: (Long) -> Unit,
    onCreatePlaylist: (String) -> Unit,
    onImportPlaylist: (String) -> Unit
) {
    val gridSize by TXAPreferences.gridSize.collectAsState()
    val accentColor = Color(android.graphics.Color.parseColor(TXAPreferences.currentAccent))
    var showCreateDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    
    // Override onCreateClick to open our local dialog
    val onInternalCreateClick = { showCreateDialog = true }

    val surfaceColor = MaterialTheme.colorScheme.surface
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Header with Title and Add Button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "txamusic_media_playlists".txa(),
                fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold,
                color = onSurfaceColor
            )
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { showImportDialog = true }) {
                    Icon(
                         Icons.Default.Download, // Import icon
                         contentDescription = "Import Playlist",
                         tint = MaterialTheme.colorScheme.primary 
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
            
            if (playlists.isNotEmpty()) {
                IconButton(
                    onClick = onInternalCreateClick,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(accentColor.copy(alpha = 0.15f))
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Create Playlist",
                        tint = accentColor
                    )
                }
            }


            } // End of inner Row
        }
        
        if (playlists.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .clip(RoundedCornerShape(60.dp))
                            .background(accentColor.copy(alpha = 0.05f)),
                        contentAlignment = Alignment.Center
                    ) {
                         Icon(
                            TXAIcons.List,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = Color.Gray.copy(alpha = 0.3f)
                        )
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(text = "txamusic_home_no_songs".txa(), color = Color.Gray, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(32.dp))
                    Button(
                        onClick = onInternalCreateClick,
                        colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("txamusic_btn_create_playlist".txa(), fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 160.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 100.dp, top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.fillMaxWidth().weight(1f)
            ) {
                items(playlists) { playlist ->
                    PlaylistGridItem(playlist, onPlaylistClick, isLarge = false)
                }
            }
        }
    }
    
    val scope = rememberCoroutineScope()
    
    if (showCreateDialog) {
        var playlistName by remember { mutableStateOf("") }
        val context = androidx.compose.ui.platform.LocalContext.current
        
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("txamusic_btn_create_playlist".txa()) },
            text = {
                OutlinedTextField(
                    value = playlistName,
                    onValueChange = { playlistName = it },
                    label = { Text("txamusic_playlist_name".txa()) }, 
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (playlistName.isNotBlank()) {
                            onCreatePlaylist(playlistName)
                            showCreateDialog = false
                        }
                    }
                ) {
                    Text("txamusic_btn_ok".txa())
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("txamusic_btn_cancel".txa())
                }
            }
        )
    }
    
    if (showImportDialog) {
        ImportPlaylistDialog(
            onDismiss = { showImportDialog = false },
            onImport = onImportPlaylist
        )
    }
}

@Composable
fun PlaylistGridItem(playlist: Playlist, onClick: (Long) -> Unit, isLarge: Boolean = false) {
    val accentColor = Color(android.graphics.Color.parseColor(TXAPreferences.currentAccent))
    
    if (isLarge) {
        // Horizontal layout for single item to keep it "Large but balanced"
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick(playlist.id) },
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    color = accentColor.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = playlist.name.take(2).uppercase(),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = accentColor
                        )
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = playlist.name,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${playlist.songCount} ${"txamusic_media_songs".txa()}",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    } else {
        // Standard Grid Item (Square)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick(playlist.id) }
        ) {
            Surface(
                modifier = Modifier
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(12.dp)),
                color = accentColor.copy(alpha = 0.15f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = playlist.name.take(1).uppercase(),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = accentColor
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = playlist.name,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "${playlist.songCount} ${"txamusic_media_songs".txa()}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
