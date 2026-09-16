package alphainterplanetary.thinker.ui.screens

import alphainterplanetary.thinker.di.AppComponent
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color

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