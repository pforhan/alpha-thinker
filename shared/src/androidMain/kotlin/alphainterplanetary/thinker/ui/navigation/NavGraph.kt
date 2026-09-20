package alphainterplanetary.thinker.ui.navigation

import alphainterplanetary.thinker.di.AppComponent
import alphainterplanetary.thinker.ui.screens.ProjectDetailScreen
import alphainterplanetary.thinker.ui.screens.ProjectListScreen
import alphainterplanetary.thinker.ui.screens.SettingsScreen
import alphainterplanetary.thinker.ui.screens.TaskManagerScreen
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
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
          navController.navigate(Screen.TaskManager.route)
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
        onBack = { navController.popBackStack() }
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
}
