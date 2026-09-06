package alphainterplanetary.thinker

import alphainterplanetary.thinker.di.PlatformContext
import alphainterplanetary.thinker.di.createAppComponent
import alphainterplanetary.thinker.ui.navigation.NavApp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

@Composable
fun App(platformContext: PlatformContext) {
  MaterialTheme {
    Surface(
      modifier = Modifier,
      color = MaterialTheme.colorScheme.background
    ) {
      val appComponent = remember { createAppComponent(platformContext) }
      NavApp(appComponent)
    }
  }
}
