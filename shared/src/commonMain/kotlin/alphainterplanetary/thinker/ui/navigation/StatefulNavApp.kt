package alphainterplanetary.thinker.ui.navigation

import alphainterplanetary.thinker.di.AppComponent
import alphainterplanetary.thinker.ui.chrome.rememberAppChromeState
import alphainterplanetary.thinker.ui.components.GenerationTaskBar
import alphainterplanetary.thinker.ui.screens.ActivityLogScreen
import alphainterplanetary.thinker.ui.screens.ProjectDetailScreen
import alphainterplanetary.thinker.ui.screens.ProjectListScreen
import alphainterplanetary.thinker.ui.screens.SettingsScreen
import alphainterplanetary.thinker.ui.screens.TaskManagerScreen
import alphainterplanetary.thinker.ui.theme.Dimens
import alphainterplanetary.thinker.ui.viewmodel.ActivityLogViewModel
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

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
  val current = route
  val chrome = rememberAppChromeState(
    appComponent = appComponent,
    onOpenActivityLog = { route = AppRoute.ActivityLog },
    onOpenTaskManager = { route = AppRoute.TaskManager },
  )

  // A settings sheet belongs to the screen that opened it; navigating away
  // dismisses it rather than stranding it over the new screen.
  LaunchedEffect(current) {
    chrome.closeSheet()
  }

  Box(modifier = Modifier.fillMaxSize()) {
    when (current) {
      AppRoute.ProjectList -> {
        ProjectListScreen(
          appComponent = appComponent,
          chrome = chrome,
          onProjectClick = { route = AppRoute.ProjectDetail(it.id) },
          onProjectCreated = { route = AppRoute.ProjectDetail(it.id) },
          onTaskManagerClick = { route = AppRoute.TaskManager },
        )
      }

      is AppRoute.ProjectDetail -> {
        ProjectDetailScreen(
          appComponent = appComponent,
          chrome = chrome,
          projectId = current.projectId,
          onBack = { route = AppRoute.ProjectList },
        )
      }

      AppRoute.TaskManager -> {
        TaskManagerScreen(
          appComponent = appComponent,
          chrome = chrome,
          onBack = { route = AppRoute.ProjectList },
        )
      }

      AppRoute.Settings -> {
        SettingsScreen(
          appComponent = appComponent,
          chrome = chrome,
          onBack = { route = AppRoute.ProjectList },
        )
      }

      AppRoute.ActivityLog -> {
        val vm = remember { ActivityLogViewModel(appComponent.activityLogger, appComponent.projectRepository, appComponent.appScope) }
        ActivityLogScreen(
          viewModel = vm,
          chrome = chrome,
          onBack = { route = AppRoute.Settings },
        )
      }
    }

    // The floating task bar would sit on top of a settings sheet's content, so
    // it yields while one is open.
    if (current != AppRoute.TaskManager && !chrome.isSheetOpen) {
      GenerationTaskBar(
        taskRunner = appComponent.taskRunner,
        onTaskManagerClick = { route = AppRoute.TaskManager },
        modifier = Modifier
          .align(Alignment.BottomCenter)
          .padding(
            horizontal = Dimens.ScreenPadding,
            vertical = Dimens.ScreenPadding,
          ),
      )
    }
  }
}

internal sealed class AppRoute {
  object ProjectList : AppRoute()
  object TaskManager : AppRoute()
  object Settings : AppRoute()
  object ActivityLog : AppRoute()
  data class ProjectDetail(val projectId: String) : AppRoute()
}