package com.txapp.musicplayer.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
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
import com.txapp.musicplayer.R
import com.txapp.musicplayer.data.MusicRepository
import com.txapp.musicplayer.model.Album
import com.txapp.musicplayer.model.Artist
import com.txapp.musicplayer.model.Song
import com.txapp.musicplayer.util.TXAFormat
import com.txapp.musicplayer.util.TXAPreferences
import com.txapp.musicplayer.util.txa
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SearchFragment : Fragment() {

    private lateinit var repository: MusicRepository
    private var allSongs by mutableStateOf(emptyList<Song>())
    private var allAlbums by mutableStateOf(emptyList<Album>())
    private var allArtists by mutableStateOf(emptyList<Artist>())

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
                    SearchScreen(
                        allSongs = allSongs,
                        allAlbums = allAlbums,
                        allArtists = allArtists,
                        onBack = { findNavController().navigateUp() },
                        onSongClick = { song -> playSong(song, allSongs) },
                        onAlbumClick = { album -> navigateToAlbum(album.id) },
                        onArtistClick = { artist -> navigateToArtist(artist.id) }
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
            repository.allSongs.collectLatest { songs ->
                allSongs = songs
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            repository.albums.collectLatest { albums ->
                allAlbums = albums
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            repository.artists.collectLatest { artists ->
                allArtists = artists
            }
        }
    }

    private fun playSong(song: Song, list: List<Song>) {
        val index = list.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
        (activity as? MainActivity)?.checkAndPlayOptions(list, index)
    }

    private fun navigateToAlbum(albumId: Long) {
        val bundle = Bundle().apply { putLong("extra_album_id", albumId) }
        findNavController().navigate(R.id.albumDetailsFragment, bundle)
    }

    private fun navigateToArtist(artistId: Long) {
        val bundle = Bundle().apply { putLong("extra_artist_id", artistId) }
        findNavController().navigate(R.id.artistDetailsFragment, bundle)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    allSongs: List<Song>,
    allAlbums: List<Album>,
    allArtists: List<Artist>,
    onBack: () -> Unit,
    onSongClick: (Song) -> Unit,
    onAlbumClick: (Album) -> Unit,
    onArtistClick: (Artist) -> Unit
) {
    val accentColor = Color(android.graphics.Color.parseColor(TXAPreferences.currentAccent))
    var query by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    // Filter results based on query
    val filteredSongs = remember(query, allSongs) {
        if (query.isBlank()) emptyList()
        else allSongs.filter {
            it.title.contains(query, ignoreCase = true) ||
            it.artist.contains(query, ignoreCase = true)
        }.take(20)
    }

    val filteredAlbums = remember(query, allAlbums) {
        if (query.isBlank()) emptyList()
        else allAlbums.filter {
            it.title.contains(query, ignoreCase = true) ||
            it.artistName.contains(query, ignoreCase = true)
        }.take(10)
    }

    val filteredArtists = remember(query, allArtists) {
        if (query.isBlank()) emptyList()
        else allArtists.filter {
            it.name.contains(query, ignoreCase = true)
        }.take(10)
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BasicTextField(
                            value = query,
                            onValueChange = { query = it },
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(focusRequester),
                            textStyle = TextStyle(
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 18.sp
                            ),
                            cursorBrush = SolidColor(accentColor),
                            singleLine = true,
                            decorationBox = { innerTextField ->
                                Box {
                                    if (query.isEmpty()) {
                                        Text(
                                            text = "txamusic_search_hint".txa(),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 18.sp
                                        )
                                    }
                                    innerTextField()
                                }
                            }
                        )
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "txamusic_btn_back".txa())
                    }
                }
            )
        }
    ) { padding ->
        if (query.isEmpty()) {
            // Show hint when no query
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "txamusic_search_hint".txa(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else if (filteredSongs.isEmpty() && filteredAlbums.isEmpty() && filteredArtists.isEmpty()) {
            // No results
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.SearchOff,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "txamusic_no_results".txa(),
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
                // Artists Section
                if (filteredArtists.isNotEmpty()) {
                    item {
                        Text(
                            text = "txamusic_media_artists".txa(),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                    items(filteredArtists) { artist ->
                        SearchArtistItem(artist, query, accentColor) { onArtistClick(artist) }
                    }
                }

                // Albums Section
                if (filteredAlbums.isNotEmpty()) {
                    item {
                        Text(
                            text = "txamusic_media_albums".txa(),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                    items(filteredAlbums) { album ->
                        SearchAlbumItem(album, query, accentColor) { onAlbumClick(album) }
                    }
                }

                // Songs Section
                if (filteredSongs.isNotEmpty()) {
                    item {
                        Text(
                            text = "txamusic_media_songs".txa(),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                    items(filteredSongs) { song ->
                        SearchSongItem(song, query, accentColor) { onSongClick(song) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchSongItem(song: Song, query: String, accentColor: Color, onClick: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val albumUri = remember(song.albumId) { com.txapp.musicplayer.util.MusicUtil.getAlbumArtUri(song.albumId) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
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

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = highlightText(song.title, query, accentColor),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = highlightText(song.artist, query, accentColor),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Icon(
            Icons.Default.MusicNote,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = accentColor.copy(alpha = 0.5f)
        )
    }
}

@Composable
private fun SearchAlbumItem(album: Album, query: String, accentColor: Color, onClick: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val albumUri = remember(album.id) { com.txapp.musicplayer.util.MusicUtil.getAlbumArtUri(album.id) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
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

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = highlightText(album.title, query, accentColor),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = highlightText(album.artistName, query, accentColor),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Icon(
            Icons.Default.Album,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = accentColor.copy(alpha = 0.5f)
        )
    }
}

@Composable
private fun SearchArtistItem(artist: Artist, query: String, accentColor: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(48.dp),
            shape = CircleShape,
            color = accentColor.copy(alpha = 0.15f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = artist.name.firstOrNull()?.uppercase() ?: "?",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = accentColor
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = highlightText(artist.name, query, accentColor),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${artist.albumCount} ${"txamusic_media_albums".txa()}",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }

        Icon(
            Icons.Default.Person,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = accentColor.copy(alpha = 0.5f)
        )
    }
}
