package alphainterplanetary.thinker.ui.navigation

import alphainterplanetary.thinker.di.AppComponent
import alphainterplanetary.thinker.ui.chrome.rememberAppChromeState
import alphainterplanetary.thinker.ui.components.GenerationTaskBar
import alphainterplanetary.thinker.ui.screens.ActivityLogScreen
import alphainterplanetary.thinker.ui.screens.ProjectDetailScreen
import alphainterplanetary.thinker.ui.screens.ProjectListScreen
import alphainterplanetary.thinker.ui.screens.TaskManagerScreen
import alphainterplanetary.thinker.ui.theme.Dimens
import alphainterplanetary.thinker.ui.viewmodel.ActivityLogViewModel
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * The app's one navigation root, on every target.
 *
 * It reads the top of the back stack and renders that route, and every way out —
 * a screen's back arrow, the flyout's Tools rows, a sheet's close glyph, the
 * platform's own back event — goes through the same stack, so where "back" lands
 * is one decision in one place. Settings sheets are stack entries too, so back
 * closes a sheet before it leaves a screen.
 *
 * This replaced a hand-rolled single-slot route variable (every non-Android
 * target) *and* a Jetpack Navigation graph (Android), which between them meant
 * two roots to keep in step and a real stack on only one platform. Adding a
 * destination now means adding a branch here and nothing else.
 */
@Composable
internal fun NavApp(appComponent: AppComponent) {
  val chrome = rememberAppChromeState(appComponent)
  val route = chrome.route

  // Hardware/gesture back pops the same stack the app bar's arrow does, so a
  // back gesture closes a sheet when one is up and otherwise returns to the
  // screen the current one was opened from. Disabled at the root, where there is
  // nothing to pop, so the platform's own leave-the-app behavior still runs.
  PlatformBackHandler(enabled = chrome.canGoBack) { chrome.goBack() }

  Box(modifier = Modifier.fillMaxSize()) {
    when (route) {
      AppRoute.ProjectList -> {
        ProjectListScreen(
          appComponent = appComponent,
          chrome = chrome,
          onProjectClick = { chrome.navigate(AppRoute.ProjectDetail(it.id)) },
          onProjectCreated = { chrome.navigate(AppRoute.ProjectDetail(it.id)) },
        )
      }

      is AppRoute.ProjectDetail -> {
        ProjectDetailScreen(
          appComponent = appComponent,
          chrome = chrome,
          projectId = route.projectId,
          onBack = { chrome.goBack() },
        )
      }

      AppRoute.TaskManager -> {
        TaskManagerScreen(
          appComponent = appComponent,
          chrome = chrome,
          onBack = { chrome.goBack() },
        )
      }

      AppRoute.ActivityLog -> {
        val vm = remember { ActivityLogViewModel(appComponent.activityLogger, appComponent.projectRepository, appComponent.appScope) }
        ActivityLogScreen(
          viewModel = vm,
          chrome = chrome,
          onBack = { chrome.goBack() },
        )
      }
    }

    // The floating task bar would sit on top of a settings sheet's content, so
    // it yields while one is open.
    if (route != AppRoute.TaskManager && !chrome.isSheetOpen) {
      GenerationTaskBar(
        taskRunner = appComponent.taskRunner,
        onTaskManagerClick = { chrome.navigate(AppRoute.TaskManager) },
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
