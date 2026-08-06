package io.github.brandonscollins.yarn.ui.onboarding

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun ServerPickScreen(
    onConnected: () -> Unit,
    viewModel: ServerPickViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state) {
        if (state is ServerPickUiState.Connected) onConnected()
    }

    Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        when (val s = state) {
            is ServerPickUiState.Loading, is ServerPickUiState.Connected ->
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))

            is ServerPickUiState.Loaded ->
                Column {
                    Text("Choose your server", modifier = Modifier.padding(bottom = 16.dp))
                    LazyColumn {
                        items(s.servers) { server ->
                            ListItem(
                                headlineContent = { Text(server.name) },
                                modifier = Modifier.clickable { viewModel.choose(server) },
                            )
                        }
                    }
                }

            is ServerPickUiState.Connecting ->
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator(modifier = Modifier.padding(bottom = 16.dp))
                    Text("Connecting to ${s.serverName}…")
                }

            is ServerPickUiState.Error ->
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("Couldn't reach the server.")
                    Button(
                        onClick = { viewModel.loadServers() },
                        modifier = Modifier.padding(top = 16.dp),
                    ) { Text("Try again") }
                }
        }
    }
}
