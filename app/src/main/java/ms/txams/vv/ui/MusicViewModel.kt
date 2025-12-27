package ms.txams.vv.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ms.txams.vv.data.database.SongEntity
import ms.txams.vv.data.repository.MusicRepository
import javax.inject.Inject

@HiltViewModel
class MusicViewModel @Inject constructor(
    private val repository: MusicRepository
) : ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // Expose flow from repository
    val songs = repository.allSongs

    fun scanLibrary() {
        viewModelScope.launch {
            _isLoading.value = true
            repository.scanMediaStore()
            _isLoading.value = false
        }
    }

    fun addManualSong(uri: android.net.Uri) {
        viewModelScope.launch {
            _isLoading.value = true
            repository.addManualSong(uri)
            _isLoading.value = false
        }
    }
}
