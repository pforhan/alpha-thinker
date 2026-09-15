package alphainterplanetary.thinker

import alphainterplanetary.thinker.di.PlatformContext
import alphainterplanetary.thinker.di.createAppComponent
import alphainterplanetary.thinker.ui.navigation.NavApp
import alphainterplanetary.thinker.ui.theme.AlphaThinkerTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

@Composable
fun App(platformContext: PlatformContext) {
  val appComponent = remember { createAppComponent(platformContext) }
  val phaseTheme by appComponent.settingsRepository.phaseTheme.collectAsState()

  AlphaThinkerTheme(phaseTheme = phaseTheme) {
    Surface(
      modifier = Modifier,
      color = MaterialTheme.colorScheme.background
    ) {
      NavApp(appComponent)
    }
  }
}