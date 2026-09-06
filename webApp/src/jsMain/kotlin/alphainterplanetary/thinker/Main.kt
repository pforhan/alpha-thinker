package alphainterplanetary.thinker

import alphainterplanetary.thinker.di.WebPlatformContext
import org.jetbrains.compose.web.renderComposableInBody

fun main() {
  renderComposableInBody {
    App(WebPlatformContext())
}
}
