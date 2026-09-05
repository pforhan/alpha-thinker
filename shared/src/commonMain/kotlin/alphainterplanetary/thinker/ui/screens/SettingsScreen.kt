package alphainterplanetary.thinker.ui.screens

import alphainterplanetary.thinker.data.ThinkerRepository
import alphainterplanetary.thinker.di.AppComponent
import alphainterplanetary.thinker.ui.viewmodel.SettingsUiState
import alphainterplanetary.thinker.ui.viewmodel.SettingsViewModel
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
  appComponent: AppComponent,
  onBack: () -> Unit,
) {
  val repository = remember {
    ThinkerRepository(appComponent.projectRepository, appComponent.sampleProjectGenerator)
  }
  val viewModel = remember { SettingsViewModel(repository) }
  val uiState by viewModel.uiState.collectAsState()

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Settings") },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        }
      )
    }
  ) { paddingValues ->
    Column(
      modifier = Modifier
        .padding(paddingValues)
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text(
        text = "Tools",
        style = MaterialTheme.typography.titleMedium,
      )
      ToolItem(
        title = "Generate sample projects",
        description = "Creates three projects to explore the UI: one sparse, one mostly " +
          "complete, and one with very long text in every field to stress-test the layout.",
        isLoading = uiState == SettingsUiState.Generating,
        onClick = { viewModel.generateSampleProjects() },
      )
      when (val state = uiState) {
        SettingsUiState.Idle, SettingsUiState.Generating -> {
          Unit
        }

        is SettingsUiState.Success -> {
          Text(
            text = state.message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
          )
        }

        is SettingsUiState.Error -> {
          Text(
            text = state.message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
          )
        }
      }
    }
  }
}

@Composable
private fun ToolItem(
  title: String,
  description: String,
  isLoading: Boolean,
  onClick: () -> Unit,
) {
  Card(modifier = Modifier.fillMaxWidth()) {
    Column(modifier = Modifier.padding(16.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
          imageVector = Icons.Filled.Build,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
          text = title,
          style = MaterialTheme.typography.titleSmall,
          modifier = Modifier.weight(1f),
        )
      }
      Spacer(modifier = Modifier.height(8.dp))
      Text(
        text = description,
        style = MaterialTheme.typography.bodyMedium,
      )
      Spacer(modifier = Modifier.height(12.dp))
      Button(onClick = onClick, enabled = !isLoading) {
        Text(text = if (isLoading) "Generating..." else "Generate")
      }
    }
  }
}