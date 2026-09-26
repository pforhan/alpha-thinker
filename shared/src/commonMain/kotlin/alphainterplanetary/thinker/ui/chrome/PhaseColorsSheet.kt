package alphainterplanetary.thinker.ui.chrome

import alphainterplanetary.thinker.ui.theme.Dimens
import alphainterplanetary.thinker.ui.theme.PhaseTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier

/**
 * The phase-color theme picker, one [ThemeOption] card per theme. The cards
 * preview each palette on the surfaces it renders on, so the choice is made on
 * the thing being changed rather than on a swatch.
 */
@Composable
internal fun PhaseColorsSheetContent(chrome: AppChromeState) {
  val phaseTheme by chrome.settings.phaseTheme.collectAsState()

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .verticalScroll(rememberScrollState())
      .padding(bottom = Dimens.ScreenPadding),
    verticalArrangement = Arrangement.spacedBy(Dimens.SectionGap),
  ) {
    PhaseTheme.All.forEach { theme ->
      ThemeOption(
        theme = theme,
        selected = phaseTheme == theme,
        onClick = { chrome.settings.selectPhaseTheme(theme) },
      )
    }
  }
}
