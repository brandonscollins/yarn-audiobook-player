package io.github.brandonscollins.yarn.ui.player

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.brandonscollins.yarn.player.PlayerController

/**
 * Activity-scoped (created once at the NavHost root, threaded down as a parameter) so the mini
 * player and the full Player screen share one [PlayerController] and one [MediaController]
 * connection, matching the single-session nature of the app.
 */
class PlayerViewModel(app: Application) : AndroidViewModel(app) {
    val controller = PlayerController(app, viewModelScope)

    init {
        controller.connect()
    }

    override fun onCleared() {
        controller.release()
    }
}
