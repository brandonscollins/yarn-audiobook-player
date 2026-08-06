package io.github.brandonscollins.yarn.ui.onboarding

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.brandonscollins.yarn.data.plex.PlexAuthRepo
import io.github.brandonscollins.yarn.data.plex.PlexGraph
import io.github.brandonscollins.yarn.data.plex.PlexPin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface LoginUiState {
    data object Idle : LoginUiState

    data class AwaitingBrowser(val pin: PlexPin) : LoginUiState

    data object SignedIn : LoginUiState

    data object Error : LoginUiState
}

class LoginViewModel(app: Application) : AndroidViewModel(app) {
    private val authRepo = PlexAuthRepo(PlexGraph.prefs(app), PlexGraph.api(app))

    private val _state = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    fun startLogin() {
        viewModelScope.launch {
            val pin = runCatching { authRepo.requestPin() }.getOrNull()
            if (pin == null) {
                _state.value = LoginUiState.Error
                return@launch
            }
            _state.value = LoginUiState.AwaitingBrowser(pin)
            val signedIn = runCatching { authRepo.awaitToken(pin.id) }.getOrDefault(false)
            _state.value = if (signedIn) LoginUiState.SignedIn else LoginUiState.Error
        }
    }
}
