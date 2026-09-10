package alphainterplanetary.thinker

import alphainterplanetary.thinker.di.PlatformContext
import alphainterplanetary.thinker.di.createAppComponent
import alphainterplanetary.thinker.ui.navigation.NavApp
import alphainterplanetary.thinker.ui.theme.AlphaThinkerTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

@Composable
fun App(platformContext: PlatformContext) {
  AlphaThinkerTheme {
    Surface(
      modifier = Modifier,
      color = MaterialTheme.colorScheme.background
    ) {
      val appComponent = remember { createAppComponent(platformContext) }
      NavApp(appComponent)
    }
  }
}
