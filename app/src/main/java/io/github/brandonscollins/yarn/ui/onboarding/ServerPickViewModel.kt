package io.github.brandonscollins.yarn.ui.onboarding

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.brandonscollins.yarn.data.plex.PlexAuthRepo
import io.github.brandonscollins.yarn.data.plex.PlexGraph
import io.github.brandonscollins.yarn.data.plex.model.PlexResource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ServerPickUiState {
    data object Loading : ServerPickUiState

    data class Loaded(val servers: List<PlexResource>) : ServerPickUiState

    data class Connecting(val serverName: String) : ServerPickUiState

    data object Connected : ServerPickUiState

    data object Error : ServerPickUiState
}

class ServerPickViewModel(app: Application) : AndroidViewModel(app) {
    private val authRepo = PlexAuthRepo(PlexGraph.prefs(app), PlexGraph.api(app))
    private val connections = PlexGraph.connections(app)

    private val _state = MutableStateFlow<ServerPickUiState>(ServerPickUiState.Loading)
    val state: StateFlow<ServerPickUiState> = _state.asStateFlow()

    init {
        loadServers()
    }

    fun loadServers() {
        viewModelScope.launch {
            _state.value = ServerPickUiState.Loading
            val servers = runCatching { authRepo.fetchServers() }.getOrNull()
            _state.value =
                if (servers != null) ServerPickUiState.Loaded(servers) else ServerPickUiState.Error
        }
    }

    fun choose(server: PlexResource) {
        viewModelScope.launch {
            authRepo.chooseServer(server)
            _state.value = ServerPickUiState.Connecting(server.name)
            val uri = runCatching { connections.connect() }.getOrNull()
            _state.value = if (uri != null) ServerPickUiState.Connected else ServerPickUiState.Error
        }
    }
}
