package io.github.brandonscollins.yarn.ui.onboarding

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun LoginScreen(
    onSignedIn: () -> Unit,
    viewModel: LoginViewModel = viewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state) {
        val awaiting = state as? LoginUiState.AwaitingBrowser ?: return@LaunchedEffect
        context.startActivity(
            Intent(Intent.ACTION_VIEW, awaiting.pin.authUrl.toUri())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
    LaunchedEffect(state) {
        if (state is LoginUiState.SignedIn) onSignedIn()
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Yarn", style = MaterialTheme.typography.displaySmall)
        Text(
            "An audiobook player for your Plex server.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, bottom = 32.dp),
        )
        when (val s = state) {
            is LoginUiState.Idle ->
                Button(onClick = { viewModel.startLogin() }) { Text("Sign in with Plex") }

            is LoginUiState.AwaitingBrowser -> {
                CircularProgressIndicator(modifier = Modifier.padding(bottom = 16.dp))
                Text("Finish signing in at plex.tv/link", textAlign = TextAlign.Center)
                Text(
                    "Code: ${s.pin.code}",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            is LoginUiState.SignedIn ->
                CircularProgressIndicator()

            is LoginUiState.Error -> {
                Text("Sign-in failed or timed out.")
                Button(
                    onClick = { viewModel.startLogin() },
                    modifier = Modifier.padding(top = 16.dp),
                ) { Text("Try again") }
            }
        }
    }
}
