package io.github.brandonscollins.yarn.ui.onboarding

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.brandonscollins.yarn.data.plex.LibrarySyncRepo
import io.github.brandonscollins.yarn.data.plex.PlexGraph
import io.github.brandonscollins.yarn.data.plex.PlexLibrary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface LibraryPickUiState {
    data object Loading : LibraryPickUiState

    data class Loaded(val libraries: List<PlexLibrary>) : LibraryPickUiState

    data object Syncing : LibraryPickUiState

    data object Done : LibraryPickUiState

    data object Error : LibraryPickUiState
}

class LibraryPickViewModel(app: Application) : AndroidViewModel(app) {
    private val syncRepo = LibrarySyncRepo(PlexGraph.prefs(app), PlexGraph.api(app), PlexGraph.db(app))

    private val _state = MutableStateFlow<LibraryPickUiState>(LibraryPickUiState.Loading)
    val state: StateFlow<LibraryPickUiState> = _state.asStateFlow()

    init {
        loadLibraries()
    }

    fun loadLibraries() {
        viewModelScope.launch {
            _state.value = LibraryPickUiState.Loading
            val libraries = runCatching { syncRepo.fetchLibraries() }.getOrNull()
            _state.value =
                if (libraries != null) LibraryPickUiState.Loaded(libraries) else LibraryPickUiState.Error
        }
    }

    fun choose(library: PlexLibrary) {
        viewModelScope.launch {
            syncRepo.chooseLibrary(library.id)
            _state.value = LibraryPickUiState.Syncing
            val ok = runCatching { syncRepo.sync() }.isSuccess
            _state.value = if (ok) LibraryPickUiState.Done else LibraryPickUiState.Error
        }
    }
}
