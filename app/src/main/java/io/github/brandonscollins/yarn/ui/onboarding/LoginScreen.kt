package io.github.brandonscollins.yarn.ui.onboarding

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.unit.sp
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
        Text("Yarn", style = MaterialTheme.typography.displayMedium)
        Text(
            "An audiobook player for your Plex server.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, bottom = 40.dp),
        )
        when (val s = state) {
            is LoginUiState.Idle ->
                Button(
                    onClick = { viewModel.startLogin() },
                    shape = CircleShape,
                    contentPadding = PaddingValues(horizontal = 28.dp, vertical = 16.dp),
                ) {
                    Text("Sign in with Plex", style = MaterialTheme.typography.titleMedium)
                }

            is LoginUiState.AwaitingBrowser -> {
                Text(
                    "Finish signing in at plex.tv/link",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
                // The code is the whole job of this screen — set it like a chapter number.
                Text(
                    s.pin.code,
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 6.sp,
                    modifier = Modifier.padding(top = 12.dp, bottom = 24.dp),
                )
                CircularProgressIndicator()
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
