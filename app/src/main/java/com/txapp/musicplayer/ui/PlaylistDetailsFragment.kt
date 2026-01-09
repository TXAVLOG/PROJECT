package com.txapp.musicplayer.ui

import android.app.Activity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.IntentSenderRequest
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.txapp.musicplayer.MusicApplication
import com.txapp.musicplayer.data.MusicRepository
import com.txapp.musicplayer.model.Playlist
import com.txapp.musicplayer.model.Song
import com.txapp.musicplayer.util.TXAFormat
import com.txapp.musicplayer.util.TXAPreferences
import com.txapp.musicplayer.util.TXAActiveMP3
import com.txapp.musicplayer.util.txa
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import com.txapp.musicplayer.ui.component.TXATagEditorSheet
import com.txapp.musicplayer.ui.component.TagEditData
import com.txapp.musicplayer.util.TXATagWriter
import com.txapp.musicplayer.util.TXARingtoneManager


class PlaylistDetailsFragment : Fragment() {
    private val intentSenderLauncher =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                Toast.makeText(requireContext(), "txamusic_btn_save".txa(), Toast.LENGTH_SHORT).show()
            }
        }

    private val playlistId: Long by lazy {
        arguments?.getLong("extra_playlist_id") ?: -1L
    }

    private lateinit var repository: MusicRepository
    private var playlist by mutableStateOf<Playlist?>(null)
    private var songs by mutableStateOf(emptyList<Song>())

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
                    val currentPlayingSongId = TXAActiveMP3.currentPlayingSongId.collectAsState().value
                    val isPlaying = TXAActiveMP3.isPlaying.collectAsState().value
                    var selectedSongForEdit by remember { mutableStateOf<Song?>(null) }
                    val scope = rememberCoroutineScope()
                    val context = androidx.compose.ui.platform.LocalContext.current

                    if (selectedSongForEdit != null) {
                        TXATagEditorSheet(
                            song = selectedSongForEdit!!,
                            onDismiss = { selectedSongForEdit = null },
                            onSave = { editData ->
                                scope.launch {
                                    val song = selectedSongForEdit!!
                                    val result = repository.updateSongMetadata(
                                        context = context,
                                        songId = song.id,
                                        title = editData.title,
                                        artist = editData.artist,
                                        album = editData.album,
                                        albumArtist = editData.albumArtist,
                                        composer = editData.composer,
                                        year = editData.year.toIntOrNull() ?: 0,
                                        trackNumber = song.trackNumber,
                                        artwork = editData.artworkBitmap
                                    )
                                    when (result) {
                                        is TXATagWriter.WriteResult.Success -> {
                                            Toast.makeText(context, "txamusic_tag_saved".txa(), Toast.LENGTH_SHORT).show()
                                            selectedSongForEdit = null
                                        }
                                        is TXATagWriter.WriteResult.PermissionRequired -> {
                                            intentSenderLauncher.launch(
                                                IntentSenderRequest.Builder(result.intent.intentSender).build()
                                            )
                                        }
                                        else -> {
                                            Toast.makeText(
                                                context,
                                                "txamusic_tag_save_failed".txa(),
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }

                                }
                            }
                        )
                    }

                    PlaylistDetailsScreen(
                        playlist = playlist,
                        songs = songs,
                        currentPlayingSongId = currentPlayingSongId,
                        isPlaying = isPlaying,
                        onBack = { findNavController().navigateUp() },
                        onPlayAll = { playSongs(songs, 0) },
                        onShuffleAll = { shuffleSongs(songs) },
                        onSongClick = { song -> playSong(song, songs) },
                        onRemoveSong = { song -> removeSongFromPlaylist(song) },
                        onDeletePlaylist = { deletePlaylist() },
                        onEditSong = { selectedSongForEdit = it },
                        onSetRingtone = { song ->
                            if (com.txapp.musicplayer.util.TXARingtoneManager.setRingtone(context, song)) {
                                Toast.makeText(context, "txamusic_ringtone_set_success".txa(), Toast.LENGTH_SHORT)
                                    .show()
                            }
                        }
                    )
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadPlaylistData()
    }

    private fun loadPlaylistData() {
        viewLifecycleOwner.lifecycleScope.launch {
            // Load playlist info
            repository.getPlaylistById(playlistId)?.let { pl ->
                playlist = pl
            }
            // Load songs
            repository.getPlaylistSongs(playlistId).collectLatest { songList ->
                songs = songList
            }
        }
    }

    private fun playSong(song: Song, list: List<Song>) {
        val index = list.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
        playSongs(list, index)
    }

    private fun playSongs(list: List<Song>, index: Int) {
        (activity as? MainActivity)?.playSongs(list, index)
    }

    private fun shuffleSongs(list: List<Song>) {
        (activity as? MainActivity)?.playSongs(list, 0, shuffle = true)
    }

    private fun removeSongFromPlaylist(song: Song) {
        viewLifecycleOwner.lifecycleScope.launch {
            repository.removeSongFromPlaylist(playlistId, song.id)
            com.txapp.musicplayer.util.TXAToast.success(requireContext(), "txamusic_removed_from_playlist".txa())
        }
    }

    private fun deletePlaylist() {
        viewLifecycleOwner.lifecycleScope.launch {
            repository.deletePlaylist(playlistId)
            com.txapp.musicplayer.util.TXAToast.success(requireContext(), "txamusic_playlist_deleted".txa())
            findNavController().navigateUp()
        }
    }
}

@Suppress("FunctionName")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailsScreen(
    playlist: Playlist?,
    songs: List<Song>,
    currentPlayingSongId: Long = -1L,
    isPlaying: Boolean = false,
    onBack: () -> Unit,
    onPlayAll: () -> Unit,
    onShuffleAll: () -> Unit,
    onSongClick: (Song) -> Unit,
    onRemoveSong: (Song) -> Unit,
    onDeletePlaylist: () -> Unit,
    onEditSong: (Song) -> Unit = {},
    onSetRingtone: (Song) -> Unit = {}
) {
    val accentColor = Color(android.graphics.Color.parseColor(TXAPreferences.currentAccent))
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(playlist?.name ?: "txamusic_loading".txa()) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "txamusic_btn_back".txa())
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "txamusic_more".txa())
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("txamusic_delete_playlist".txa()) },
                                onClick = {
                                    showMenu = false
                                    showDeleteConfirm = true
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Delete,
                                        null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (songs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.MusicNote,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "txamusic_playlist_empty".txa(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(bottom = 100.dp)
            ) {
                // Header with actions
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        // Playlist Info
                        Surface(
                            modifier = Modifier
                                .size(120.dp)
                                .align(Alignment.CenterHorizontally),
                            shape = RoundedCornerShape(16.dp),
                            color = accentColor.copy(alpha = 0.2f)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = playlist?.name?.take(2)?.uppercase() ?: "",
                                    fontSize = 40.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = accentColor
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "${songs.size} ${"txamusic_media_songs".txa()}",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Action Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = onPlayAll,
                                colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                            ) {
                                Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("txamusic_action_play_all".txa())
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            OutlinedButton(onClick = onShuffleAll) {
                                Icon(Icons.Default.Shuffle, null, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("txamusic_action_shuffle".txa())
                            }
                        }
                    }
                }

                // Song List
                itemsIndexed(songs) { index, song ->
                    val isSongActive = song.id == currentPlayingSongId && isPlaying
                    val isSongCurrent = song.id == currentPlayingSongId

                    PlaylistSongItem(
                        song = song,
                        index = index + 1,
                        isActive = isSongActive,
                        isCurrent = isSongCurrent,
                        onClick = { onSongClick(song) },
                        onRemove = { onRemoveSong(song) },
                        onEdit = { onEditSong(song) },
                        onSetRingtone = { onSetRingtone(song) },
                        accentColor = accentColor
                    )
                }
            }
        }
    }

    // Delete Confirmation Dialog
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("txamusic_delete_playlist".txa()) },
            text = { Text("txamusic_delete_playlist_confirm".txa()) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDeletePlaylist()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("txamusic_btn_delete".txa())
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("txamusic_btn_cancel".txa())
                }
            }
        )
    }
}

@Suppress("FunctionName")
@Composable
private fun PlaylistSongItem(
    song: Song,
    index: Int,
    isActive: Boolean = false,
    isCurrent: Boolean = false,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    onEdit: () -> Unit,
    onSetRingtone: () -> Unit,
    accentColor: Color
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val albumUri = remember(song.albumId) { com.txapp.musicplayer.util.MusicUtil.getAlbumArtUri(song.albumId) }

    var showMenu by remember { mutableStateOf(false) }

    // Colors based on active state
    val textColor = if (isCurrent) accentColor else MaterialTheme.colorScheme.onSurface
    val bgColor = when {
        isActive -> accentColor.copy(alpha = 0.12f)
        isCurrent -> accentColor.copy(alpha = 0.06f)
        else -> Color.Transparent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Index or Equalizer Indicator
        Box(
            modifier = Modifier.width(32.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isActive) {
                TXAActiveMP3.PlaylistNowPlayingIndicator(
                    barColor = accentColor,
                    maxHeight = 18.dp
                )
            } else {
                Text(
                    text = TXAFormat.format2Digits(index),
                    color = if (isCurrent) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp
                )
            }
        }

        // Album Art
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(albumUri)
                .crossfade(true)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
        )

        Spacer(modifier = Modifier.width(12.dp))

        // Song Info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title.ifBlank { "txamusic_unknown_title".txa() },
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = song.artist.ifBlank { "txamusic_unknown_artist".txa() },
                fontSize = 13.sp,
                color = if (isCurrent) accentColor.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Duration
        Text(
            text = TXAFormat.formatDurationHuman(song.duration),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // More Options
        Box {
            IconButton(onClick = { showMenu = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = null)
            }
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(
                    text = { Text("txamusic_remove_from_playlist".txa()) },
                    onClick = {
                        showMenu = false
                        onRemove()
                    },
                    leadingIcon = { Icon(Icons.Default.Remove, null) }
                )
                DropdownMenuItem(
                    text = { Text("txamusic_edit_tag".txa()) },
                    onClick = {
                        showMenu = false
                        onEdit()
                    },
                    leadingIcon = { Icon(Icons.Default.Edit, null) }
                )
                DropdownMenuItem(
                    text = { Text("txamusic_set_as_ringtone".txa()) },
                    onClick = {
                        showMenu = false
                        onSetRingtone()
                    },
                    leadingIcon = { Icon(Icons.Default.Notifications, null) }
                )
            }
        }
    }
}
