package alphainterplanetary.thinker.ui.chrome

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The app's one screen chrome: the `Scaffold` and `TopAppBar` every screen
 * shares, so the header looks and behaves identically everywhere and the
 * app-wide pieces (status cluster, settings flyout, settings sheets) have a
 * single place to live.
 *
 * A screen supplies only what is specific to it: its [title] slot, an optional
 * [onBack] (null on the root screen, which then has no navigation icon), any
 * [actions] of its own, a [floatingActionButton] if it has one, and its content.
 *
 * The snackbar host is the chrome's ([AppChromeState.snackbarHostState]), not the
 * screen's, so an app has exactly one snackbar and a message outlives the screen
 * that raised it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScaffold(
  title: @Composable () -> Unit,
  chrome: AppChromeState,
  modifier: Modifier = Modifier,
  onBack: (() -> Unit)? = null,
  actions: @Composable RowScope.() -> Unit = {},
  floatingActionButton: @Composable () -> Unit = {},
  content: @Composable (PaddingValues) -> Unit,
) {
  val status = chrome.engineStatus()

  Scaffold(
    modifier = modifier,
    topBar = {
      TopAppBar(
        title = title,
        navigationIcon = {
          if (onBack != null) {
            IconButton(onClick = onBack) {
              Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
          }
        },
        actions = {
          // Ahead of the screen's own actions: the status is the one thing every
          // screen shares, so it sits in the same place on all of them.
          StatusCluster(
            status = status,
            onClick = { chrome.openSheet(ChromeSheet.Status) },
          )
          actions()
        },
      )
    },
    snackbarHost = { SnackbarHost(chrome.snackbarHostState) },
    floatingActionButton = floatingActionButton,
    content = content,
  )

  // A sibling of the Scaffold rather than a child: the sheet is an overlay, and
  // the navigation root's Box is what stacks the two.
  ChromeSheetHost(chrome = chrome)
}
