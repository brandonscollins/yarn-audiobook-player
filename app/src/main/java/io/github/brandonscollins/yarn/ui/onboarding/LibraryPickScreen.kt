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
import androidx.compose.material3.MaterialTheme
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
fun LibraryPickScreen(
    onDone: () -> Unit,
    viewModel: LibraryPickViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state) {
        if (state is LibraryPickUiState.Done) onDone()
    }

    Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        when (val s = state) {
            is LibraryPickUiState.Loading, is LibraryPickUiState.Done ->
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))

            is LibraryPickUiState.Loaded ->
                Column {
                    Text(
                        "Choose your audiobook library",
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(bottom = 16.dp),
                    )
                    LazyColumn {
                        items(s.libraries) { library ->
                            ListItem(
                                headlineContent = {
                                    Text(library.title, style = MaterialTheme.typography.titleMedium)
                                },
                                modifier = Modifier.clickable { viewModel.choose(library) },
                            )
                        }
                    }
                }

            is LibraryPickUiState.Syncing ->
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator(modifier = Modifier.padding(bottom = 16.dp))
                    Text("Syncing your library…")
                }

            is LibraryPickUiState.Error ->
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("Something went wrong.")
                    Button(
                        onClick = { viewModel.loadLibraries() },
                        modifier = Modifier.padding(top = 16.dp),
                    ) { Text("Try again") }
                }
        }
    }
}
