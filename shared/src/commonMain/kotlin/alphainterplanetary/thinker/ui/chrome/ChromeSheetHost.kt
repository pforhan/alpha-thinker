package alphainterplanetary.thinker.ui.chrome

import alphainterplanetary.thinker.ui.navigation.AppRoute
import alphainterplanetary.thinker.ui.theme.Dimens
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow

/**
 * Hosts whichever settings subscreen the back stack's open sheet names.
 *
 * A `ModalBottomSheet` rather than a route: a sheet is modal over the screen
 * beneath it, and it is a back-stack entry ([AppChromeState.goBack] closes it) rather
 * than a destination that changes which screen is showing. `skipPartiallyExpanded`
 * because every subscreen is a tall scroll, and a half-height peek is a worse
 * default than showing it outright.
 *
 * The drag handle is off. It was the only way out of a sheet other than a
 * tap-off, which is a gesture the user cannot see and a touchpad user may not
 * have; the header's close glyph is always there instead, and turns into a back
 * arrow for a sheet that replaced another one (Status handing off to
 * Intelligence), so each sheet offers the one control that matches where it sits
 * in the stack.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChromeSheetHost(
  chrome: AppChromeState,
  status: EngineStatus,
) {
  val target = chrome.sheet ?: return
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val nested = chrome.hasSheetBelow

  ModalBottomSheet(
    onDismissRequest = { chrome.goBack() },
    sheetState = sheetState,
    dragHandle = null,
  ) {
    ChromeSheetFrame(
      title = target.title,
      isNested = nested,
      onClose = { chrome.goBack() },
    ) {
      when (target) {
        ChromeSheet.Status -> {
          val activity by chrome.latestActivity.collectAsState()
          StatusSheetContent(
            status = status,
            latestActivity = activity,
            onOpenIntelligence = { chrome.openSheet(ChromeSheet.Intelligence) },
            // The log is a screen, so this navigates: the sheet is dropped from
            // the stack rather than left hanging over the screen beneath it.
            onOpenActivityLog = { chrome.navigate(AppRoute.ActivityLog) },
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
 * A sheet's fixed title and its way out, over its scrolling body: the title stays
 * put so a long subscreen keeps saying what it is, and each body owns its own
 * scroll because the sheets have different heights.
 *
 * The trailing glyph is a back arrow when there is another sheet underneath (go
 * back to it) and a close otherwise (go back to the screen). Both pop exactly one
 * entry, which is also what dragging the sheet down or tapping the scrim does.
 */
@Composable
private fun ChromeSheetFrame(
  title: String,
  isNested: Boolean,
  onClose: () -> Unit,
  content: @Composable ColumnScope.() -> Unit,
) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = Dimens.ScreenPadding),
    verticalArrangement = Arrangement.spacedBy(Dimens.SectionGap),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f),
      )
      IconButton(onClick = onClose) {
        Icon(
          imageVector = if (isNested) {
            Icons.AutoMirrored.Filled.ArrowBack
          } else {
            Icons.Filled.Close
          },
          contentDescription = if (isNested) "Back" else "Close",
        )
      }
    }
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
