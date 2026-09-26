package alphainterplanetary.thinker.ui.chrome

import alphainterplanetary.thinker.phases.BuiltInPhase
import alphainterplanetary.thinker.ui.components.PhaseBadge
import alphainterplanetary.thinker.ui.theme.BadgeShape
import alphainterplanetary.thinker.ui.theme.DarkColorScheme
import alphainterplanetary.thinker.ui.theme.Dimens
import alphainterplanetary.thinker.ui.theme.LightColorScheme
import alphainterplanetary.thinker.ui.theme.LocalDarkTheme
import alphainterplanetary.thinker.ui.theme.LocalPhaseTheme
import alphainterplanetary.thinker.ui.theme.PhaseTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color

/**
 * One selectable phase theme: names and describes the palette, previews each
 * phase's numbered badge on the surfaces it renders on (light and dark), and
 * marks the currently selected theme.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ThemeOption(
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
