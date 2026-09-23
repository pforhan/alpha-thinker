package alphainterplanetary.thinker.ui.screens

import alphainterplanetary.thinker.di.AppComponent
import alphainterplanetary.thinker.engine.EngineDelayConfig
import alphainterplanetary.thinker.engine.EngineInteraction
import alphainterplanetary.thinker.engine.EngineMode
import alphainterplanetary.thinker.phases.BuiltInPhase
import alphainterplanetary.thinker.ui.components.PhaseBadge
import alphainterplanetary.thinker.ui.theme.BadgeShape
import alphainterplanetary.thinker.ui.theme.DarkColorScheme
import alphainterplanetary.thinker.ui.theme.Dimens
import alphainterplanetary.thinker.ui.theme.LightColorScheme
import alphainterplanetary.thinker.ui.theme.LocalDarkTheme
import alphainterplanetary.thinker.ui.theme.LocalPhaseTheme
import alphainterplanetary.thinker.ui.theme.PhaseTheme
import alphainterplanetary.thinker.ui.viewmodel.SettingsUiState
import alphainterplanetary.thinker.ui.viewmodel.SettingsViewModel
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.input.PasswordVisualTransformation

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
  appComponent: AppComponent,
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
  val llmEnabled by viewModel.llmEnabled.collectAsState()
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
      IntelligenceLlmToggleItem(
        enabled = llmEnabled,
        onEnabledChange = { viewModel.setLlmEnabled(it) },
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

/**
 * One selectable phase theme: names and describes the palette, previews each
 * phase's numbered badge on the surfaces it renders on (light and dark), and
 * marks the currently selected theme.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeOption(
  theme: PhaseTheme,
  selected: Boolean,
  onClick: () -> Unit,
) {
  Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
    Column(modifier = Modifier.padding(Dimens.CardPadding)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = theme.label,
            style = MaterialTheme.typography.titleSmall,
          )
          Spacer(modifier = Modifier.height(Dimens.TightGap))
          Text(
            text = theme.description,
            style = MaterialTheme.typography.bodyMedium,
          )
        }
        Spacer(modifier = Modifier.width(Dimens.ContentGap))
        Box(
          modifier = Modifier
            .size(Dimens.ScrollControlSize)
            .clip(BadgeShape)
            .background(
              if (selected) MaterialTheme.colorScheme.primary
              else MaterialTheme.colorScheme.outlineVariant,
            ),
        )
      }
      Spacer(modifier = Modifier.height(Dimens.ContentGap))
      ThemePreviewRow(
        theme = theme,
        dark = false,
        surface = LightColorScheme.surfaceContainerLow,
      )
      Spacer(modifier = Modifier.height(Dimens.TightGap))
      ThemePreviewRow(
        theme = theme,
        dark = true,
        surface = DarkColorScheme.surfaceContainerLow,
      )
    }
  }
}

/**
 * One preview strip for [theme]: the real numbered [PhaseBadge]s on the
 * surface color they sit on in the given light/dark mode.
 */
@Composable
private fun ThemePreviewRow(
  theme: PhaseTheme,
  dark: Boolean,
  surface: Color,
) {
  CompositionLocalProvider(
    LocalPhaseTheme provides theme,
    LocalDarkTheme provides dark,
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .clip(MaterialTheme.shapes.medium)
        .background(surface)
        .padding(Dimens.ThemePreviewPadding),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = if (dark) "Dark" else "Light",
        style = MaterialTheme.typography.labelSmall,
        color =
          if (dark) DarkColorScheme.onSurfaceVariant else LightColorScheme.onSurfaceVariant,
      )
      Spacer(modifier = Modifier.width(Dimens.ToolIconLabelGap))
      BuiltInPhase.entries.forEachIndexed { index, phase ->
        if (index > 0) {
          Spacer(modifier = Modifier.width(Dimens.ThemeSwatchGap))
        }
        PhaseBadge(phase = phase)
      }
    }
  }
}

/**
 * The master LLM switch for the "Intelligence" section: while on, LLM
 * backends may generate planning content; while off, the built-in Lite engine
 * runs instead (per PRD the toggle sits alongside the future lookup/search one).
 */
@Composable
private fun IntelligenceLlmToggleItem(
  enabled: Boolean,
  onEnabledChange: (Boolean) -> Unit,
) {
  Card(modifier = Modifier.fillMaxWidth()) {
    Column(modifier = Modifier.padding(Dimens.CardPadding)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Use the planning LLM",
            style = MaterialTheme.typography.titleSmall,
          )
          Spacer(modifier = Modifier.height(Dimens.TightGap))
          Text(
            text = "Lets Alpha Thinker generate titles and questions with a language model. " +
              "When off, the built-in Lite engine runs instead.",
            style = MaterialTheme.typography.bodyMedium,
          )
        }
        Spacer(modifier = Modifier.width(Dimens.ControlLabelGap))
        Switch(
          checked = enabled,
          onCheckedChange = onEnabledChange,
        )
      }
    }
  }
}

/**
 * One selectable planning backend in the "Intelligence" section: names and
 * describes the mode, marks the selected one, and gates unavailable backends
 * off (only Lite is shipped today — the LLM backends land in Phase 3).
 */
@Composable
private fun EngineModeOption(
  mode: EngineMode,
  selected: Boolean,
  selectable: Boolean,
  onClick: () -> Unit,
) {
  Card(
    onClick = onClick,
    enabled = selectable,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(modifier = Modifier.padding(Dimens.CardPadding)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = mode.label,
            style = MaterialTheme.typography.titleSmall,
          )
          Spacer(modifier = Modifier.height(Dimens.TightGap))
          Text(
            text = mode.description,
            style = MaterialTheme.typography.bodyMedium,
          )
        }
        Spacer(modifier = Modifier.width(Dimens.ContentGap))
        Box(
          modifier = Modifier
            .size(Dimens.ScrollControlSize)
            .clip(BadgeShape)
            .background(
              if (selected) MaterialTheme.colorScheme.primary
              else MaterialTheme.colorScheme.outlineVariant,
            ),
        )
      }
      if (!selectable) {
        Spacer(modifier = Modifier.height(Dimens.TightGap))
        Text(
          text = "Not available on this device yet.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

/**
 * The Remote backend's connection settings: any OpenAI-compatible endpoint,
 * its API key (empty for a local Ollama), and the model name. Shown while
 * [EngineMode.Remote] is the selected backend; changes take effect from the
 * next planning interaction.
 */
@Composable
private fun RemoteConnectionItem(
  baseUrl: String,
  apiKey: String,
  model: String,
  onBaseUrlChange: (String) -> Unit,
  onApiKeyChange: (String) -> Unit,
  onModelChange: (String) -> Unit,
) {
  Card(modifier = Modifier.fillMaxWidth()) {
    Column(modifier = Modifier.padding(Dimens.CardPadding)) {
      Text(
        text = "Remote connection",
        style = MaterialTheme.typography.titleSmall,
      )
      Spacer(modifier = Modifier.height(Dimens.TightGap))
      Text(
        text = "Defaults to a local Ollama install; a trailing /v1 endpoint " +
          "root (as Ollama's docs print) works too.",
        style = MaterialTheme.typography.bodyMedium,
      )
      Spacer(modifier = Modifier.height(Dimens.ContentGap))
      OutlinedTextField(
        value = baseUrl,
        onValueChange = onBaseUrlChange,
        label = { Text("Base URL") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        isError = baseUrl.isBlank(),
      )
      Spacer(modifier = Modifier.height(Dimens.ContentGap))
      OutlinedTextField(
        value = apiKey,
        onValueChange = onApiKeyChange,
        label = { Text("API key") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        supportingText = {
          Text("Leave empty for a local Ollama that needs no key.")
        },
      )
      Spacer(modifier = Modifier.height(Dimens.ContentGap))
      OutlinedTextField(
        value = model,
        onValueChange = onModelChange,
        label = { Text("Model") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        isError = model.isBlank(),
      )
    }
  }
}

/**
 * The Task-Manager testing controls: a master switch that, while enabled,
 * expands into a per-interaction picker choosing each PlanningEngine
 * delay from the 0s (off) / 2s / 5s / 30s options.
 */
@Composable
private fun DelayControlItem(
  config: EngineDelayConfig,
  onEnabledChange: (Boolean) -> Unit,
  onDelayChange: (EngineInteraction, Int) -> Unit,
  onExpandedHeightChange: (Int) -> Unit,
) {
  Card(modifier = Modifier.fillMaxWidth()) {
    Column(modifier = Modifier.padding(Dimens.CardPadding)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Slow down the planning engine",
            style = MaterialTheme.typography.titleSmall,
          )
          Spacer(modifier = Modifier.height(Dimens.TightGap))
          Text(
            text = "Adds an artificial delay to each PlanningEngine interaction so " +
              "Task Manager tasks stay visible long enough to observe them.",
            style = MaterialTheme.typography.bodyMedium,
          )
        }
        Spacer(modifier = Modifier.width(Dimens.ControlLabelGap))
        Switch(
          checked = config.enabled,
          onCheckedChange = onEnabledChange,
        )
      }
      if (config.enabled) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .onSizeChanged { onExpandedHeightChange(it.height) },
        ) {
          Column(modifier = Modifier.fillMaxWidth()) {
            Spacer(modifier = Modifier.height(Dimens.ContentGap))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(Dimens.ContentGap))
            EngineInteraction.entries.forEachIndexed { index, interaction ->
              if (index > 0) {
                Spacer(modifier = Modifier.height(Dimens.ContentGap))
              }
              DelayChoiceRow(
                interaction = interaction,
                secondsByInteraction = config.secondsByInteraction,
                onDelayChange = onDelayChange,
              )
            }
          }
        }
      }
    }
  }
}

/** One PlanningEngine interaction: its label plus a 0s / 2s / 5s / 30s choice. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DelayChoiceRow(
  interaction: EngineInteraction,
  secondsByInteraction: Map<EngineInteraction, Int>,
  onDelayChange: (EngineInteraction, Int) -> Unit,
) {
  Column(modifier = Modifier.fillMaxWidth()) {
    Text(
      text = interaction.label,
      style = MaterialTheme.typography.bodyMedium,
    )
    Spacer(modifier = Modifier.height(Dimens.TightGap))
    FlowRow(
      horizontalArrangement = Arrangement.spacedBy(Dimens.ChipGap),
      verticalArrangement = Arrangement.spacedBy(Dimens.TightGap),
    ) {
      EngineDelayConfig.DelayOptionsSeconds.forEach { seconds ->
        FilterChip(
          selected = secondsByInteraction[interaction] == seconds,
          onClick = { onDelayChange(interaction, seconds) },
          label = { Text("${seconds}s") },
          elevation = null,
        )
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
        Text(text = if (isLoading) "Generating..." else "Generate")
      }
    }
  }
}