package alphainterplanetary.thinker.ui.navigation

import alphainterplanetary.thinker.di.AppComponent
import alphainterplanetary.thinker.ui.components.GenerationTaskBar
import alphainterplanetary.thinker.ui.screens.ActivityLogScreen
import alphainterplanetary.thinker.ui.screens.ProjectDetailScreen
import alphainterplanetary.thinker.ui.screens.ProjectListScreen
import alphainterplanetary.thinker.ui.screens.SettingsScreen
import alphainterplanetary.thinker.ui.screens.TaskManagerScreen
import alphainterplanetary.thinker.ui.viewmodel.ActivityLogViewModel
import alphainterplanetary.thinker.ui.theme.Dimens
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

/**
 * Android navigation backed by Jetpack Navigation — the analogue of
 * [StatefulNavApp] in commonMain. The screens/destinations in this graph
 * mirror the ones rendered for every other target in
 * commonMain/StatefulNavApp.kt. When adding or renaming a screen, update BOTH
 * this file and StatefulNavApp.kt so the platforms stay in sync.
 */
sealed class Screen(val route: String) {
  object ProjectList : Screen("project_list")
  object TaskManager : Screen("task_manager")
  object Settings : Screen("settings")
  object ActivityLog : Screen("activity_log")
  object ProjectDetail : Screen("project_detail/{projectId}") {
    fun createRoute(projectId: String) = "project_detail/$projectId"
  }
}

@Composable
actual fun NavApp(appComponent: AppComponent) {
  NavGraph(
    navController = rememberNavController(),
    appComponent = appComponent,
  )
}

@Composable
private fun NavGraph(
  navController: NavHostController = rememberNavController(),
  appComponent: AppComponent,
) {
  val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route

  Box(modifier = Modifier.fillMaxSize()) {
    NavHost(
      navController = navController,
      startDestination = Screen.ProjectList.route
    ) {
      composable(Screen.ProjectList.route) {
        ProjectListScreen(
          appComponent = appComponent,
          onProjectClick = { project ->
            navController.navigate(Screen.ProjectDetail.createRoute(project.id))
          },
          onProjectCreated = { project ->
            navController.navigate(Screen.ProjectDetail.createRoute(project.id))
          },
          onSettingsClick = {
            navController.navigate(Screen.Settings.route)
          },
          onTaskManagerClick = {
            navController.navigate(Screen.TaskManager.route) {
              launchSingleTop = true
            }
          },
        )
      }
      composable(Screen.TaskManager.route) {
        TaskManagerScreen(
          appComponent = appComponent,
          onBack = { navController.popBackStack() },
        )
      }
      composable(Screen.Settings.route) {
        SettingsScreen(
          appComponent = appComponent,
          onBack = { navController.popBackStack() },
          onOpenActivityLog = {
            navController.navigate(Screen.ActivityLog.route)
          },
        )
      }
      composable(Screen.ActivityLog.route) {
        val vm = remember { ActivityLogViewModel(appComponent.engineActivityLog, appComponent.appScope) }
        ActivityLogScreen(
          viewModel = vm,
          onBack = { navController.popBackStack() },
        )
      }
      composable(Screen.ProjectDetail.route) { backStackEntry ->
        val projectId = backStackEntry.arguments?.getString("projectId") ?: return@composable
        ProjectDetailScreen(
          appComponent = appComponent,
          projectId = projectId,
          onBack = { navController.popBackStack() }
        )
      }
    }

    if (currentRoute != Screen.TaskManager.route) {
      GenerationTaskBar(
        taskRunner = appComponent.taskRunner,
        onTaskManagerClick = {
          navController.navigate(Screen.TaskManager.route) {
            launchSingleTop = true
          }
        },
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
