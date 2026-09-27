package alphainterplanetary.thinker.ui.chrome

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import alphainterplanetary.thinker.ui.theme.Dimens

/**
 * Hosts whichever settings subscreen [AppChromeState.sheet] names.
 *
 * A `ModalBottomSheet` rather than a route: the subscreens are chrome, so they
 * must not land on the back stack — the hand-rolled non-Android nav has no stack
 * to pop, and a route would leave the Activity Log and Task Manager stranded
 * behind four screens of settings. `skipPartiallyExpanded` because every
 * subscreen is a tall scroll, and a half-height peek is a worse default than
 * showing it outright.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChromeSheetHost(chrome: AppChromeState) {
  val target = chrome.sheet ?: return
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

  ModalBottomSheet(
    onDismissRequest = chrome::closeSheet,
    sheetState = sheetState,
  ) {
    ChromeSheetFrame(title = target.title) {
      when (target) {
        ChromeSheet.Status -> {
          val status = chrome.engineStatus()
          val activity by chrome.latestActivity.collectAsState()
          StatusSheetContent(
            status = status,
            latestActivity = activity,
            onOpenIntelligence = { chrome.openSheet(ChromeSheet.Intelligence) },
            onOpenActivityLog = { chrome.onOpenActivityLog() },
          )
        }

        ChromeSheet.PhaseColors -> PhaseColorsSheetContent(chrome = chrome)

        ChromeSheet.Intelligence -> IntelligenceSheetContent(chrome = chrome)

        ChromeSheet.Testing -> TestingSheetContent(chrome = chrome)
      }
    }
  }
}

/**
 * A sheet's fixed title over its scrolling body: the title stays put so a long
 * subscreen keeps saying what it is, and each body owns its own scroll because
 * the sheets have different heights.
 */
@Composable
private fun ChromeSheetFrame(
  title: String,
  content: @Composable ColumnScope.() -> Unit,
) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = Dimens.ScreenPadding),
    verticalArrangement = Arrangement.spacedBy(Dimens.SectionGap),
  ) {
    Text(
      text = title,
      style = MaterialTheme.typography.titleLarge,
    )
    content()
  }
}

/** The sheet's own title; the flyout rows and the host read the same name. */
internal val ChromeSheet.title: String
  get() = when (this) {
    ChromeSheet.Status -> "Status"
    ChromeSheet.PhaseColors -> "Phase colors"
    ChromeSheet.Intelligence -> "Intelligence"
    ChromeSheet.Testing -> "Testing"
  }
