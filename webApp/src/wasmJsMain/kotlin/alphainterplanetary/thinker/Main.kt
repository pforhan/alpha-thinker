package alphainterplanetary.thinker

import alphainterplanetary.thinker.di.WebPlatformContext
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
  ComposeViewport(viewportContainerId = "ComposeApp") {
    App(WebPlatformContext())
  }
}
