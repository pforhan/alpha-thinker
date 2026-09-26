package alphainterplanetary.thinker.ui.screens

import alphainterplanetary.thinker.di.AppComponent
import alphainterplanetary.thinker.engine.EngineMode
import alphainterplanetary.thinker.ui.chrome.AppChromeState
import alphainterplanetary.thinker.ui.chrome.AppScaffold
import alphainterplanetary.thinker.ui.chrome.DelayControlItem
import alphainterplanetary.thinker.ui.chrome.EngineModeOption
import alphainterplanetary.thinker.ui.chrome.RemoteConnectionItem
import alphainterplanetary.thinker.ui.chrome.ThemeOption
import alphainterplanetary.thinker.ui.theme.Dimens
import alphainterplanetary.thinker.ui.theme.PhaseTheme
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
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
  appComponent: AppComponent,
  chrome: AppChromeState,
  onBack: () -> Unit,
) {
  val viewModel = remember {
    SettingsViewModel(
      settingsRepository = appComponent.settingsRepository,
      sampleProjectGenerator = appComponent.sampleProjectGenerator,
      scope = appComponent.appScope,
    )
  }
  val uiState by viewModel.uiState.collectAsState()
  val phaseTheme by viewModel.phaseTheme.collectAsState()
  val engineMode by viewModel.engineMode.collectAsState()
  val remoteLlmBaseUrl by viewModel.remoteLlmBaseUrl.collectAsState()
  val remoteLlmApiKey by viewModel.remoteLlmApiKey.collectAsState()
  val remoteLlmModel by viewModel.remoteLlmModel.collectAsState()
  val engineDelay by viewModel.engineDelay.collectAsState()
  val scrollState = rememberScrollState()
  var delayExpandedHeight by remember { mutableIntStateOf(0) }
  var previousDelayEnabled by remember { mutableStateOf(engineDelay.enabled) }

  // Turning the slow-down on adds the per-interaction controls below the
  // toggle; scroll just enough to bring the newly revealed rows into view.
  LaunchedEffect(engineDelay.enabled) {
    if (engineDelay.enabled && !previousDelayEnabled) {
      // Wait for the expanded controls to be measured: their onSizeChanged
      // fires during the layout pass right after the toggle turns them on.
      while (delayExpandedHeight == 0) withFrameNanos {}
      scrollState.animateScrollTo(scrollState.value + delayExpandedHeight)
    }
    previousDelayEnabled = engineDelay.enabled
  }

  AppScaffold(
    title = { Text("Settings") },
    chrome = chrome,
    onBack = onBack,
  ) { paddingValues ->
    Column(
      modifier = Modifier
        .padding(paddingValues)
        .fillMaxSize()
        .verticalScroll(scrollState)
        .padding(Dimens.ScreenPadding),
      verticalArrangement = Arrangement.spacedBy(Dimens.SectionGap),
    ) {
      Text(
        text = "Phase colors",
        style = MaterialTheme.typography.titleMedium,
      )
      PhaseTheme.All.forEach { theme ->
        ThemeOption(
          theme = theme,
          selected = phaseTheme == theme,
          onClick = { viewModel.selectPhaseTheme(theme) },
        )
      }
      Text(
        text = "Intelligence",
        style = MaterialTheme.typography.titleMedium,
      )
      EngineMode.entries.forEach { mode ->
        EngineModeOption(
          mode = mode,
          selected = engineMode == mode,
          selectable = mode.available(),
          onClick = { viewModel.selectEngineMode(mode) },
        )
      }
      if (engineMode == EngineMode.Remote) {
        RemoteConnectionItem(
          baseUrl = remoteLlmBaseUrl,
          apiKey = remoteLlmApiKey,
          model = remoteLlmModel,
          onBaseUrlChange = viewModel::setRemoteLlmBaseUrl,
          onApiKeyChange = viewModel::setRemoteLlmApiKey,
          onModelChange = viewModel::setRemoteLlmModel,
        )
      }
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
      ToolItem(
        title = "Activity Log",
        description = "View the activity log: generation tasks, planning-engine interactions, tool calls and diagnostics.",
        isLoading = false,
        buttonLabel = "Open",
        onClick = chrome.onOpenActivityLog,
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
      Text(
        text = "Testing",
        style = MaterialTheme.typography.titleMedium,
      )
      DelayControlItem(
        config = engineDelay,
        onEnabledChange = { viewModel.setEngineDelayEnabled(it) },
        onDelayChange = { interaction, seconds ->
          viewModel.setEngineDelay(interaction, seconds)
        },
        onExpandedHeightChange = { delayExpandedHeight = it },
      )
    }
  }
}

@Composable
private fun ToolItem(
  title: String,
  description: String,
  isLoading: Boolean,
  buttonLabel: String = "Generate",
  onClick: () -> Unit,
) {
  Card(modifier = Modifier.fillMaxWidth()) {
    Column(modifier = Modifier.padding(Dimens.CardPadding)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
          imageVector = Icons.Filled.Build,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.width(Dimens.ToolIconLabelGap))
        Text(
          text = title,
          style = MaterialTheme.typography.titleSmall,
          modifier = Modifier.weight(1f),
        )
      }
      Spacer(modifier = Modifier.height(Dimens.ContentGap))
      Text(
        text = description,
        style = MaterialTheme.typography.bodyMedium,
      )
      Spacer(modifier = Modifier.height(Dimens.SectionGap))
      Button(onClick = onClick, enabled = !isLoading) {
        Text(text = if (isLoading) "Generating..." else buttonLabel)
      }
    }
  }
}
