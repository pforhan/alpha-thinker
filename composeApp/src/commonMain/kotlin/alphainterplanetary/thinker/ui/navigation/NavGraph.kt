package alphainterplanetary.thinker.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import alphainterplanetary.thinker.model.Project
import alphainterplanetary.thinker.ui.screens.ProjectDetailScreen
import alphainterplanetary.thinker.ui.screens.ProjectListScreen

sealed class Screen(val route: String) {
    object ProjectList : Screen("project_list")
    object ProjectDetail : Screen("project_detail/{projectId}") {
        fun createRoute(projectId: String) = "project_detail/$projectId"
    }
}

@Composable
fun NavGraph(
    navController: NavHostController = rememberNavController(),
    onProjectCreated: (Project) -> Unit
) {
    NavHost(
        navController = navController,
        startDestination = Screen.ProjectList.route
    ) {
        composable(Screen.ProjectList.route) {
            ProjectListScreen(
                onProjectClick = { project ->
                    navController.navigate(Screen.ProjectDetail.createRoute(project.id))
                },
                onProjectCreated = onProjectCreated
            )
        }
        composable(Screen.ProjectDetail.route) { backStackEntry ->
            val projectId = backStackEntry.arguments?.getString("projectId") ?: return@composable
            ProjectDetailScreen(
                projectId = projectId,
                onBack = { navController.popBackStack() },
                onProjectUpdated = {
                    navController.popBackStack()
                    navController.navigate(Screen.ProjectList.route) {
                        popUpTo(Screen.ProjectList.route) { inclusive = true }
                    }
                }
            )
        }
    }
}
