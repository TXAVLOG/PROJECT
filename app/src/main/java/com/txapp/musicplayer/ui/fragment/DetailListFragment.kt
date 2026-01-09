package com.txapp.musicplayer.ui.fragment

// cms

import android.app.Activity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
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
import com.txapp.musicplayer.model.Song
import com.txapp.musicplayer.util.txa
import com.txapp.musicplayer.util.TXAFormat
import com.txapp.musicplayer.util.TXARingtoneManager
import com.txapp.musicplayer.ui.MainActivity
import com.txapp.musicplayer.util.TXATagWriter
import com.txapp.musicplayer.util.TXAPreferences
import com.txapp.musicplayer.util.MusicUtil
import com.txapp.musicplayer.util.TXAActiveMP3
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import com.txapp.musicplayer.ui.component.TXATagEditorSheet
import android.widget.Toast
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.ui.platform.LocalContext
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.IntentSenderRequest

/**
 * DetailListFragment - Hiển thị danh sách chi tiết cho History, Last Added, Top Played
 * Type constants:
 * - 0: HISTORY_PLAYLIST
 * - 1: LAST_ADDED_PLAYLIST  
 * - 2: TOP_PLAYED_PLAYLIST
 * - 3: FAVOURITES
 */
class DetailListFragment : Fragment() {
    private val intentSenderLauncher =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                Toast.makeText(requireContext(), "txamusic_btn_save".txa(), Toast.LENGTH_SHORT).show()
            }
        }

    private var listType: Int = 0
    private lateinit var repository: MusicRepository
    private var songs by mutableStateOf(emptyList<Song>())
    private var title by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        listType = arguments?.getInt("type") ?: 0
    }

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
                    val playingId by TXAActiveMP3.currentPlayingSongId.collectAsState()
                    var selectedSongForEdit by remember { mutableStateOf<Song?>(null) }
                    val scope = rememberCoroutineScope()
                    val context = LocalContext.current

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

                    DetailListScreen(
                        title = title,
                        songs = songs,
                        listType = listType,
                        playingId = playingId,
                        onBack = { findNavController().navigateUp() },
                        onSongClick = { song -> playSong(song) },
                        onShuffleClick = { shuffleAll() },
                        onEditClick = { selectedSongForEdit = it },
                        onSetAsRingtone = { song ->
                            if (com.txapp.musicplayer.util.TXARingtoneManager.setRingtone(requireContext(), song)) {
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
        loadData()
    }

    private fun loadData() {
        viewLifecycleOwner.lifecycleScope.launch {
            when (listType) {
                HISTORY_PLAYLIST -> {
                    title = "txamusic_home_history".txa()
                    repository.allSongs.collectLatest { allSongs ->
                        // Get history from play count, sorted by last played
                        songs = allSongs.filter { it.playCount > 0 }
                            .sortedByDescending { it.dateModified }
                            .take(50)
                    }
                }

                LAST_ADDED_PLAYLIST -> {
                    title = "txamusic_home_last_added".txa()
                    repository.allSongs.collectLatest { allSongs ->
                        songs = allSongs.sortedByDescending { it.dateAdded }.take(50)
                    }
                }

                TOP_PLAYED_PLAYLIST -> {
                    title = "txamusic_home_top_played".txa()
                    repository.allSongs.collectLatest { allSongs ->
                        songs = allSongs.sortedByDescending { it.playCount }.take(50)
                    }
                }

                FAVOURITES -> {
                    title = "txamusic_home_favorite_title".txa()
                    repository.allSongs.collectLatest { allSongs ->
                        songs = allSongs.filter { it.isFavorite }
                    }
                }
            }
        }
    }

    private fun playSong(song: Song) {
        val index = songs.indexOf(song).coerceAtLeast(0)
        (activity as? MainActivity)?.playSongs(songs, index)
    }

    private fun shuffleAll() {
        if (songs.isNotEmpty()) {
            (activity as? MainActivity)?.playSongs(songs.shuffled(), 0)
        }
    }

    companion object {
        const val HISTORY_PLAYLIST = 0
        const val LAST_ADDED_PLAYLIST = 1
        const val TOP_PLAYED_PLAYLIST = 2
        const val FAVOURITES = 3
    }
}

@Suppress("FunctionName")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailListScreen(
    title: String,
    songs: List<Song>,
    listType: Int,
    playingId: Long,
    onBack: () -> Unit,
    onSongClick: (Song) -> Unit,
    onShuffleClick: () -> Unit,
    onEditClick: (Song) -> Unit = {},
    onSetAsRingtone: (Song) -> Unit = {}
) {
    val accentColor = Color(android.graphics.Color.parseColor(TXAPreferences.currentAccent))

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    if (songs.isNotEmpty()) {
                        IconButton(onClick = onShuffleClick) {
                            Icon(Icons.Default.Shuffle, contentDescription = null, tint = accentColor)
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        if (songs.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "txamusic_home_no_songs".txa(),
                    color = Color.Gray,
                    fontSize = 16.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                if (songs.isNotEmpty()) {
                    item {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .clickable(onClick = onShuffleClick),
                            color = accentColor.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Shuffle, contentDescription = null, tint = accentColor)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "txamusic_home_shuffle".txa(),
                                    fontWeight = FontWeight.Bold,
                                    color = accentColor
                                )
                            }
                        }
                    }
                }

                itemsIndexed(songs) { index, song ->
                    DetailSongItem(
                        song = song,
                        index = index + 1,
                        listType = listType,
                        isActive = song.id == playingId,
                        onClick = { onSongClick(song) },
                        onEditClick = onEditClick,
                        onSetAsRingtone = onSetAsRingtone,
                        accentColor = accentColor
                    )
                }
            }
        }
    }
}

@Suppress("FunctionName")
@Composable
private fun DetailSongItem(
    song: Song,
    index: Int,
    listType: Int,
    isActive: Boolean,
    onClick: () -> Unit,
    onEditClick: (Song) -> Unit = {},
    onSetAsRingtone: (Song) -> Unit = {},
    accentColor: Color
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val albumUri = remember(song.albumId) { com.txapp.musicplayer.util.MusicUtil.getAlbumArtUri(song.albumId) }
    // Determine Index/Medal UI
    val indexUi: @Composable () -> Unit = {
        if (listType == DetailListFragment.TOP_PLAYED_PLAYLIST && index <= 3) {
            val medalColor = when (index) {
                1 -> Color(0xFFFFD700) // Gold
                2 -> Color(0xFFC0C0C0) // Silver
                3 -> Color(0xFFCD7F32) // Bronze
                else -> Color.Gray
            }
            Box(
                modifier = Modifier.size(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_txa_medal),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(medalColor),
                    modifier = Modifier.fillMaxSize()
                )
                Text(
                    text = index.toString(),
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.offset(y = (-2).dp)
                )
            }
        } else {
            // Hiển thị số thứ tự cho tất cả các loại list khác (History, Last Added)
            Text(
                text = String.format("%02d", index),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isActive) MaterialTheme.colorScheme.primary else Color.Gray,
                modifier = Modifier.width(32.dp), // Fixed width for alignment
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }

    // Active CSS Logic - TXAActiveStyle
    val rowModifier = if (isActive) {
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp) // Reduced padding for container
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 8.dp, vertical = 8.dp) // Inner padding
    } else {
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    }

    Row(
        modifier = rowModifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Index / Medal
        Box(contentAlignment = Alignment.Center) {
            indexUi()
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Album Art
        Box {
            AsyncImage(
                model = albumUri,
                contentDescription = null,
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )

            // Active Indicator on Art (Equalizer Bars)
            if (isActive) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    TXAActiveMP3.PlaylistNowPlayingIndicator(
                        maxHeight = 16.dp,
                        barColor = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                fontSize = 16.sp,
                color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${song.artist} • ${song.album}",
                fontSize = 12.sp,
                color = if (isActive) MaterialTheme.colorScheme.onSurfaceVariant else Color.Gray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Play Count or Duration
        Text(
            text = if (listType == DetailListFragment.TOP_PLAYED_PLAYLIST)
                "${TXAFormat.format2Digits(song.playCount.toLong())} 🎧"
            else
                TXAFormat.formatDuration(song.duration),
            fontSize = 12.sp,
            color = if (isActive) MaterialTheme.colorScheme.primary else Color.Gray,
            fontWeight = FontWeight.Medium
        )

        var showMenu by remember { mutableStateOf(false) }
        Box {
            IconButton(onClick = { showMenu = true }) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = "More",
                    tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
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
                    leadingIcon = {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                )
                DropdownMenuItem(
                    text = { Text("txamusic_set_as_ringtone".txa()) },
                    onClick = {
                        showMenu = false
                        onSetAsRingtone(song)
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Notifications, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                )
            }
        }
    }
}
