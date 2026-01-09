package com.txapp.musicplayer.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import com.txapp.musicplayer.MusicApplication
import com.txapp.musicplayer.R
import com.txapp.musicplayer.data.MusicRepository
import com.txapp.musicplayer.model.Song
import com.txapp.musicplayer.ui.component.TXAFilePickerModal
import com.txapp.musicplayer.ui.component.TXATagEditorSheet
import com.txapp.musicplayer.ui.component.TagEditData
import com.txapp.musicplayer.util.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class SongsFragment : Fragment() {
    private val intentSenderLauncher =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                TXAToast.success(requireContext(), "txamusic_btn_save".txa())
            }
        }

    private lateinit var repository: MusicRepository
    private var allSongs by mutableStateOf(emptyList<Song>())
    private var playingId by mutableLongStateOf(-1L)

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
                    SongsScreen(
                        songs = allSongs,
                        onSongClick = { song -> playSong(song) },
                        currentlyPlayingId = playingId,
                        onSaveTags = { song, editData ->
                            viewLifecycleOwner.lifecycleScope.launch {
                                val success = repository.updateSongMetadata(
                                    context = requireContext(),
                                    songId = song.id,
                                    title = editData.title,
                                    artist = editData.artist,
                                    album = editData.album,
                                    albumArtist = editData.albumArtist,
                                    composer = editData.composer,
                                    year = editData.year.toIntOrNull() ?: 0,
                                    trackNumber = song.trackNumber
                                )
                                if (success) {
                                    TXAToast.success(context, "txamusic_tag_saved".txa())
                                } else {
                                    val intent = TXATagWriter.createWriteRequest(context, listOf(song.data))
                                    if (intent != null) {
                                        intentSenderLauncher.launch(
                                            IntentSenderRequest.Builder(intent.intentSender).build()
                                        )
                                    } else {
                                        TXAToast.error(context, "txamusic_tag_save_failed".txa())
                                    }
                                }
                            }
                        },
                        onSetAsRingtone = { song ->
                            if (TXARingtoneManager.setRingtone(requireContext(), song)) {
                                TXAToast.success(context, "txamusic_ringtone_set_success".txa())
                            } else {
                                TXAToast.error(context, "txamusic_ringtone_set_failed".txa())
                            }
                        },
                        onAddManualSongs = { uris ->
                            viewLifecycleOwner.lifecycleScope.launch {
                                val result = repository.addManualSongs(uris, requireContext())
                                val msg = "txamusic_manual_add_result".txa(result.added, result.skipped)
                                TXAToast.info(context, msg, ToastDuration.MEDIUM)
                            }
                        },
                        onDeleteFromApp = { song ->
                            val file = java.io.File(song.data)
                            if (file.exists()) {
                                // Show confirmation dialog
                                com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                                    .setTitle("txamusic_delete_confirm_title".txa())
                                    .setMessage("txamusic_delete_confirm_message".txa(song.title))
                                    .setPositiveButton("txamusic_btn_confirm".txa()) { _, _ ->
                                        viewLifecycleOwner.lifecycleScope.launch {
                                            repository.deleteSongFromApp(song.id)
                                            TXAToast.success(context, "txamusic_song_deleted".txa())
                                        }
                                    }
                                    .setNegativeButton("txamusic_btn_cancel".txa(), null)
                                    .show()
                            } else {
                                // File missing, delete directly and notify
                                viewLifecycleOwner.lifecycleScope.launch {
                                    repository.deleteSongFromApp(song.id)
                                    TXAToast.warning(context, "txamusic_error_file_not_found".txa(song.title))
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
            repository.allSongs.collectLatest { songs ->
                allSongs = songs
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            while (true) {
                val controller = (activity as? MainActivity)?.getPlayerController()
                if (controller != null) {
                    val currentItem = controller.currentMediaItem
                    playingId = currentItem?.mediaId?.toLongOrNull() ?: -1L
                }
                delay(1000)
            }
        }
    }

    private fun playSong(song: Song) {
        val mainActivity = activity as? MainActivity ?: return
        val controller = mainActivity.getPlayerController()

        if (controller != null && controller.isPlaying) {
            com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle("txamusic_play_options_title".txa())
                .setMessage("txamusic_play_options_desc".txa())
                .setPositiveButton("txamusic_play_now".txa()) { _, _ ->
                    mainActivity.playSongs(allSongs, allSongs.indexOf(song))
                }
                .setNegativeButton("txamusic_add_to_queue".txa()) { _, _ ->
                    mainActivity.addSongsToQueue(listOf(song))
                }
                .setNeutralButton("txamusic_btn_cancel".txa(), null)
                .show()
        } else {
            mainActivity.playSongs(allSongs, allSongs.indexOf(song))
        }
    }
}

@Suppress("FunctionName")
@Composable
fun SongsScreen(
    songs: List<Song>,
    onSongClick: (Song) -> Unit,
    currentlyPlayingId: Long = -1L,
    onSaveTags: (Song, TagEditData) -> Unit = { _, _ -> },
    onSetAsRingtone: (Song) -> Unit = {},
    onAddManualSongs: (List<android.net.Uri>) -> Unit = {},
    onDeleteFromApp: (Song) -> Unit = {}
) {
    var searchQuery by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    val filteredSongs = remember(songs, searchQuery) {
        if (searchQuery.isBlank()) songs
        else songs.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
                    it.artist.contains(searchQuery, ignoreCase = true) ||
                    it.album.contains(searchQuery, ignoreCase = true)
        }
    }

    var selectedSongForEdit by remember { mutableStateOf<Song?>(null) }
    var showTXAPicker by remember { mutableStateOf(false) }
    var showAddMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val systemPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            onAddManualSongs(uris)
        }
    }

    if (showTXAPicker) {
        TXAFilePickerModal(
            onDismiss = { showTXAPicker = false },
            onFilesSelected = { files ->
                showTXAPicker = false
                val uris = files.map { android.net.Uri.fromFile(it) }
                onAddManualSongs(uris)
            }
        )
    }

    if (selectedSongForEdit != null) {
        TXATagEditorSheet(
            song = selectedSongForEdit!!,
            onDismiss = { selectedSongForEdit = null },
            onSave = { editData ->
                onSaveTags(selectedSongForEdit!!, editData)
                selectedSongForEdit = null
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .focusRequester(focusRequester),
            placeholder = { Text("txamusic_search_placeholder".txa()) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
            )
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "txamusic_media_songs".txa(),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            
            Box {
                IconButton(onClick = { showAddMenu = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Add Songs")
                }
                DropdownMenu(
                    expanded = showAddMenu,
                    onDismissRequest = { showAddMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("txamusic_picker_system".txa()) },
                        onClick = { 
                            showAddMenu = false
                            try {
                                systemPicker.launch(arrayOf("audio/*"))
                            } catch (e: Exception) {
                                TXAToast.error(context, "txamusic_picker_system_error".txa())
                            }
                        },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.InsertDriveFile, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("txamusic_picker_manual".txa()) },
                        onClick = { 
                             showAddMenu = false
                             showTXAPicker = true 
                        },
                        leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null) }
                    )
                }
            }
        }

        if (filteredSongs.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (searchQuery.isNotEmpty()) {
                    Text(text = "txamusic_no_results".txa(searchQuery), color = Color.Gray)
                } else {
                    Text(text = "txamusic_home_no_songs".txa(), color = Color.Gray)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                items(filteredSongs) { song ->
                    SongListItem(
                        song = song,
                        onClick = onSongClick,
                        isPlaying = song.id == currentlyPlayingId,
                        searchQuery = searchQuery,
                        onEditClick = { selectedSongForEdit = it },
                        onSetAsRingtone = onSetAsRingtone,
                        onDeleteFromApp = onDeleteFromApp
                    )
                }
            }
        }
    }
}

@Suppress("FunctionName")
@Composable
fun SongListItem(
    song: Song,
    onClick: (Song) -> Unit,
    isPlaying: Boolean = false,
    searchQuery: String = "",
    onEditClick: (Song) -> Unit = {},
    onSetAsRingtone: (Song) -> Unit = {},
    onDeleteFromApp: (Song) -> Unit = {}
) {
    val textColor = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick(song) }
            .background(if (isPlaying) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f) else Color.Transparent)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SubcomposeAsyncImage(
            model = MusicUtil.getAlbumArtUri(song.albumId),
            contentDescription = null,
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop
        ) {
            val state = painter.state
            if (state is coil.compose.AsyncImagePainter.State.Loading || state is coil.compose.AsyncImagePainter.State.Error) {
                Box(
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_launcher),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                }
            } else {
                SubcomposeAsyncImageContent()
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            val titleText = if (searchQuery.isNotEmpty()) {
                highlightText(song.title, searchQuery, MaterialTheme.colorScheme.primary)
            } else {
                buildAnnotatedString { append(song.title) }
            }

            Text(
                text = titleText,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            val artistText = if (searchQuery.isNotEmpty()) {
                val fullText = "${song.artist} • ${song.album}"
                highlightText(fullText, searchQuery, MaterialTheme.colorScheme.primary)
            } else {
                buildAnnotatedString { append("${song.artist} • ${song.album}") }
            }

            Text(
                text = artistText,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (isPlaying) {
            Icon(
                painter = painterResource(R.drawable.ic_music_note),
                contentDescription = "Playing",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp).padding(end = 8.dp)
            )
        }

        var showMenu by remember { mutableStateOf(false) }
        Box {
            IconButton(onClick = { showMenu = true }) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = "More",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("txamusic_edit_tag".txa()) },
                    onClick = {
                        showMenu = false
                        onEditClick(song)
                    },
                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
                DropdownMenuItem(
                    text = { Text("txamusic_set_as_ringtone".txa()) },
                    onClick = {
                        showMenu = false
                        onSetAsRingtone(song)
                    },
                    leadingIcon = { Icon(Icons.Default.Notifications, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
                DropdownMenuItem(
                    text = { Text("txamusic_delete_from_app".txa()) },
                    onClick = {
                        showMenu = false
                        onDeleteFromApp(song)
                    },
                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp)) }
                )
            }
        }

        Text(
            text = TXAFormat.formatDuration(song.duration),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp)
        )
    }
}

@Composable
fun highlightText(text: String, query: String, color: Color): androidx.compose.ui.text.AnnotatedString {
    return buildAnnotatedString {
        val lowerText = text.lowercase()
        val lowerQuery = query.lowercase()
        var startIndex = 0
        while (startIndex < text.length) {
            val index = lowerText.indexOf(lowerQuery, startIndex)
            if (index == -1) {
                append(text.substring(startIndex))
                break
            }
            append(text.substring(startIndex, index))
            withStyle(style = SpanStyle(color = color, fontWeight = FontWeight.Bold)) {
                append(text.substring(index, index + query.length))
            }
            startIndex = index + query.length
        }
    }
}
