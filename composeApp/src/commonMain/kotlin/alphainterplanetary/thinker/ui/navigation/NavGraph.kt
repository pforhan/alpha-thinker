package alphainterplanetary.thinker.ui.navigation

import alphainterplanetary.thinker.di.AppComponent
import alphainterplanetary.thinker.ui.screens.ProjectDetailScreen
import alphainterplanetary.thinker.ui.screens.ProjectListScreen
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

sealed class Screen(val route: String) {
  object ProjectList : Screen("project_list")
  object ProjectDetail : Screen("project_detail/{projectId}") {
    fun createRoute(projectId: String) = "project_detail/$projectId"
  }
}

@Composable
fun NavGraph(
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
        }
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
