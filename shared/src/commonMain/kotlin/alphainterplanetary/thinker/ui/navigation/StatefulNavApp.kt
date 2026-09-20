package alphainterplanetary.thinker.ui.navigation

import alphainterplanetary.thinker.di.AppComponent
import alphainterplanetary.thinker.ui.screens.ProjectDetailScreen
import alphainterplanetary.thinker.ui.screens.ProjectListScreen
import alphainterplanetary.thinker.ui.screens.SettingsScreen
import alphainterplanetary.thinker.ui.screens.TaskManagerScreen
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

@Composable
public expect fun NavApp(appComponent: AppComponent)

/**
 * Stack-based navigation shared by every non-Android target. Android uses
 * Jetpack Navigation instead (see androidMain/NavGraph.kt).
 *
 * The set of screens rendered here mirrors the destinations declared in
 * androidMain/NavGraph.kt. When adding or renaming a screen, update BOTH this
 * file and NavGraph.kt so the platforms stay in sync.
 */
@Composable
internal fun StatefulNavApp(appComponent: AppComponent) {
  var route by remember { mutableStateOf<AppRoute>(AppRoute.ProjectList) }

  when (val current = route) {
    AppRoute.ProjectList -> {
      ProjectListScreen(
        appComponent = appComponent,
        onProjectClick = { route = AppRoute.ProjectDetail(it.id) },
        onProjectCreated = { route = AppRoute.ProjectDetail(it.id) },
        onSettingsClick = { route = AppRoute.Settings },
        onTaskManagerClick = { route = AppRoute.TaskManager },
      )
    }

    is AppRoute.ProjectDetail -> {
      ProjectDetailScreen(
        appComponent = appComponent,
        projectId = current.projectId,
        onBack = { route = AppRoute.ProjectList },
      )
    }

    AppRoute.TaskManager -> {
      TaskManagerScreen(
        appComponent = appComponent,
        onBack = { route = AppRoute.ProjectList },
      )
    }

    AppRoute.Settings -> {
      SettingsScreen(
        appComponent = appComponent,
        onBack = { route = AppRoute.ProjectList },
      )
    }
  }
}

internal sealed class AppRoute {
  object ProjectList : AppRoute()
  object TaskManager : AppRoute()
  object Settings : AppRoute()
  data class ProjectDetail(val projectId: String) : AppRoute()
}