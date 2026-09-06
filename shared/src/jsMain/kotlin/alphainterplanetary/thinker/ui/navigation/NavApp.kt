package alphainterplanetary.thinker.ui.navigation

import alphainterplanetary.thinker.di.AppComponent
import alphainterplanetary.thinker.ui.screens.ProjectDetailScreen
import alphainterplanetary.thinker.ui.screens.ProjectListScreen
import alphainterplanetary.thinker.ui.screens.SettingsScreen
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

@Composable
public actual fun NavApp(appComponent: AppComponent) {
  var route by remember { mutableStateOf<Screen>(Screen.ProjectList) }

  when (val current = route) {
    Screen.ProjectList -> {
      ProjectListScreen(
        appComponent = appComponent,
        onProjectClick = { route = Screen.ProjectDetail(it.id) },
        onProjectCreated = { route = Screen.ProjectDetail(it.id) },
        onSettingsClick = { route = Screen.Settings },
      )
    }

    is Screen.ProjectDetail -> {
      ProjectDetailScreen(
        appComponent = appComponent,
        projectId = current.projectId,
        onBack = { route = Screen.ProjectList },
      )
    }

    Screen.Settings -> {
      SettingsScreen(
        appComponent = appComponent,
        onBack = { route = Screen.ProjectList },
      )
    }
  }
}

private sealed class Screen {
  object ProjectList : Screen()
  object Settings : Screen()
  data class ProjectDetail(val projectId: String) : Screen()
}
