package alphainterplanetary.thinker

import alphainterplanetary.thinker.di.createAppComponent
import alphainterplanetary.thinker.ui.navigation.NavGraph
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController

@Composable
fun App() {
  MaterialTheme {
    Surface(
      modifier = Modifier,
      color = MaterialTheme.colorScheme.background
    ) {
      val navController = rememberNavController()
      val appComponent = remember { createAppComponent() }
      NavGraph(
        navController = navController,
        appComponent = appComponent,
      )
    }
  }
}
