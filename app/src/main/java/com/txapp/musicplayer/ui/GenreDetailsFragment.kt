package com.txapp.musicplayer.ui

import android.app.Activity
import android.content.ContentResolver
import android.os.Bundle
import android.provider.MediaStore
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
import coil.request.ImageRequest
import com.txapp.musicplayer.MusicApplication
import com.txapp.musicplayer.data.MusicRepository
import com.txapp.musicplayer.model.Song
import com.txapp.musicplayer.util.TXAFormat
import com.txapp.musicplayer.util.TXAPreferences
import com.txapp.musicplayer.util.TXAActiveMP3
import com.txapp.musicplayer.util.txa
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GenreDetailsFragment : Fragment() {

    private val genreId: Long by lazy {
        arguments?.getLong("extra_genre_id") ?: -1L
    }

    private val genreName: String by lazy {
        arguments?.getString("extra_genre_name") ?: ""
    }

    private lateinit var repository: MusicRepository
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

                    GenreDetailsScreen(
                        genreName = genreName,
                        songs = songs,
                        currentPlayingSongId = currentPlayingSongId,
                        isPlaying = isPlaying,
                        onBack = { findNavController().navigateUp() },
                        onPlayAll = { playSongs(songs, 0) },
                        onShuffleAll = { shuffleSongs(songs) },
                        onSongClick = { song -> playSong(song, songs) }
                    )
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadGenreSongs()
    }

    private fun loadGenreSongs() {
        viewLifecycleOwner.lifecycleScope.launch {
            songs = withContext(Dispatchers.IO) {
                getGenreSongs(requireContext().contentResolver, genreId)
            }
        }
    }

    private suspend fun getGenreSongs(contentResolver: ContentResolver, genreId: Long): List<Song> {
        val result = mutableListOf<Song>()
        val uri = MediaStore.Audio.Genres.Members.getContentUri("external", genreId)
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.ARTIST_ID,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DATE_MODIFIED
        )

        contentResolver.query(uri, projection, null, null, MediaStore.Audio.Media.TITLE)?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val artistIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST_ID)
            val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val dateModifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)

            while (cursor.moveToNext()) {
                result.add(
                    Song(
                        id = cursor.getLong(idColumn),
                        title = cursor.getString(titleColumn) ?: "Unknown",
                        artist = cursor.getString(artistColumn) ?: "Unknown",
                        album = cursor.getString(albumColumn) ?: "Unknown",
                        data = cursor.getString(dataColumn) ?: "",
                        duration = cursor.getLong(durationColumn),
                        albumId = cursor.getLong(albumIdColumn),
                        artistId = cursor.getLong(artistIdColumn),
                        dateAdded = cursor.getLong(dateAddedColumn),
                        dateModified = cursor.getLong(dateModifiedColumn)
                    )
                )
            }
        }
        return result
    }

    private fun playSong(song: Song, list: List<Song>) {
        val index = list.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
        (activity as? MainActivity)?.checkAndPlayOptions(list, index)
    }

    private fun playSongs(list: List<Song>, index: Int) {
        (activity as? MainActivity)?.playSongs(list, index)
    }

    private fun shuffleSongs(list: List<Song>) {
        (activity as? MainActivity)?.playSongs(list, 0, shuffle = true)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GenreDetailsScreen(
    genreName: String,
    songs: List<Song>,
    currentPlayingSongId: Long = -1L,
    isPlaying: Boolean = false,
    onBack: () -> Unit,
    onPlayAll: () -> Unit,
    onShuffleAll: () -> Unit,
    onSongClick: (Song) -> Unit
) {
    val accentColor = Color(android.graphics.Color.parseColor(TXAPreferences.currentAccent))
    val colors = listOf(
        Color(0xFF6366F1),
        Color(0xFFEC4899),
        Color(0xFF14B8A6),
        Color(0xFFF59E0B),
        Color(0xFF8B5CF6),
        Color(0xFF10B981)
    )
    val genreColor = colors[genreName.hashCode().mod(colors.size).let { if (it < 0) it + colors.size else it }]

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(genreName) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "txamusic_btn_back".txa())
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
                        text = "txamusic_home_no_songs".txa(),
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
                // Header with Genre Info
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Genre Icon
                        Surface(
                            modifier = Modifier.size(120.dp),
                            shape = RoundedCornerShape(16.dp),
                            color = genreColor
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Category,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = Color.White
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "${songs.size} ${"txamusic_media_songs".txa()}",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
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

                    GenreSongItem(
                        song = song,
                        index = index + 1,
                        isActive = isSongActive,
                        isCurrent = isSongCurrent,
                        onClick = { onSongClick(song) },
                        accentColor = accentColor
                    )
                }
            }
        }
    }
}

@Composable
private fun GenreSongItem(
    song: Song,
    index: Int,
    isActive: Boolean = false,
    isCurrent: Boolean = false,
    onClick: () -> Unit,
    accentColor: Color
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val albumUri = remember(song.albumId) { com.txapp.musicplayer.util.MusicUtil.getAlbumArtUri(song.albumId) }

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
        // Index or Now Playing Indicator
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
    }
}
