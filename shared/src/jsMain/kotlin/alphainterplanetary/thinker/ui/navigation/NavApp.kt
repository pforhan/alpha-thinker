package alphainterplanetary.thinker.ui.navigation

import alphainterplanetary.thinker.di.AppComponent
import androidx.compose.runtime.Composable

@Composable
public actual fun NavApp(appComponent: AppComponent) {
  StatefulNavApp(appComponent)
}