package alphainterplanetary.thinker.ui.chrome

import alphainterplanetary.thinker.ui.theme.Dimens
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier

/**
 * The testing controls: the artificial planning-engine delay, so Task Manager
 * tasks stay visible long enough to watch.
 *
 * Turning the delay on reveals the per-interaction pickers below the toggle, and
 * this scrolls just enough to bring them into view. The reveal is measured
 * rather than assumed because the pickers' height depends on the screen width
 * (they wrap), so a fixed scroll distance would be wrong on some sizes.
 */
@Composable
internal fun TestingSheetContent(chrome: AppChromeState) {
  val engineDelay by chrome.settings.engineDelay.collectAsState()
  val scrollState = rememberScrollState()
  var expandedHeight by remember { mutableIntStateOf(0) }
  var previousEnabled by remember { mutableStateOf(engineDelay.enabled) }

  LaunchedEffect(engineDelay.enabled) {
    if (engineDelay.enabled && !previousEnabled) {
      // Wait for the expanded controls to be measured: their onSizeChanged fires
      // during the layout pass right after the toggle turns them on.
      while (expandedHeight == 0) withFrameNanos {}
      scrollState.animateScrollTo(scrollState.value + expandedHeight)
    }
    previousEnabled = engineDelay.enabled
  }

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .verticalScroll(scrollState)
      .padding(bottom = Dimens.ScreenPadding),
    verticalArrangement = Arrangement.spacedBy(Dimens.SectionGap),
  ) {
    DelayControlItem(
      config = engineDelay,
      onEnabledChange = chrome.settings::setEngineDelayEnabled,
      onDelayChange = chrome.settings::setEngineDelay,
      onExpandedHeightChange = { expandedHeight = it },
    )
  }
}
