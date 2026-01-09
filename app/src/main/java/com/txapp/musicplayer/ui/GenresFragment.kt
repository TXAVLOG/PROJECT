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
import androidx.navigation.fragment.findNavController
import com.txapp.musicplayer.MusicApplication
import com.txapp.musicplayer.R
import com.txapp.musicplayer.data.MusicRepository
import com.txapp.musicplayer.model.Genre
import com.txapp.musicplayer.util.TXAPreferences
import com.txapp.musicplayer.util.txa
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class GenresFragment : Fragment() {

    private lateinit var repository: MusicRepository
    private var genres by mutableStateOf(emptyList<Genre>())

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
                    GenresScreen(
                        genres = genres,
                        onGenreClick = { genre -> navigateToGenre(genre) }
                    )
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewLifecycleOwner.lifecycleScope.launch {
            repository.genres.collectLatest { list: List<Genre> ->
                genres = list
            }
        }
    }

    private fun navigateToGenre(genre: Genre) {
        val bundle = Bundle().apply { 
            putLong("extra_genre_id", genre.id)
            putString("extra_genre_name", genre.name)
        }
        findNavController().navigate(R.id.genreDetailsFragment, bundle)
    }
}

@Composable
fun GenresScreen(
    genres: List<Genre>,
    onGenreClick: (Genre) -> Unit
) {
    val gridSize by TXAPreferences.gridSize.collectAsState()
    
    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Text(
            text = "txamusic_media_genres".txa(),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(16.dp)
        )
        
        if (genres.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "txamusic_home_no_songs".txa(), color = Color.Gray)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(gridSize.coerceAtLeast(1)),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth().weight(1f)
            ) {
                items(genres) { genre ->
                    GenreGridItem(genre, onGenreClick)
                }
            }
        }
    }
}

@Composable
fun GenreGridItem(genre: Genre, onClick: (Genre) -> Unit) {
    val colors = listOf(
        Color(0xFF6366F1),
        Color(0xFFEC4899),
        Color(0xFF14B8A6),
        Color(0xFFF59E0B),
        Color(0xFF8B5CF6),
        Color(0xFF10B981)
    )
    val bgColor = colors[genre.name.hashCode().mod(colors.size).let { if (it < 0) it + colors.size else it }]
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick(genre) }
    ) {
        Surface(
            modifier = Modifier
                .aspectRatio(1f)
                .clip(RoundedCornerShape(16.dp)),
            color = bgColor,
            shape = RoundedCornerShape(16.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                contentAlignment = Alignment.BottomStart
            ) {
                Text(
                    text = genre.name,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "${genre.songCount} ${"txamusic_media_songs".txa()}",
            fontSize = 12.sp,
            color = Color.Gray
        )
    }
}
