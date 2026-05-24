package com.pforhan.alphathinker.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.pforhan.alphathinker.ui.MainViewModel
import com.pforhan.alphathinker.ui.screen.ProjectDetailScreen
import com.pforhan.alphathinker.ui.screen.ProjectsListScreen
import com.pforhan.alphathinker.ui.screen.NewProjectScreen

@Composable
fun AppNavGraph(
    viewModel: MainViewModel = viewModel(),
    navController: NavHostController
) {
    NavHost(
        navController = navController,
        startDestination = Screen.ProjectsList.path
    ) {
        composable(Screen.ProjectsList.path) {
            val state = viewModel.uiState
            if (state is MainViewModel.UiState.ProjectList) {
                ProjectsListScreen(
                    projects = state.projects,
                    onNewProjectClick = { navController.navigate(Screen.NewProject.path) },
                    onProjectClick = { projectId ->
                        navController.navigate(Screen.ProjectDetail.createRoute(projectId))
                    },
                    onGoToNewProject = { navController.navigate(Screen.NewProject.path) }
                )
            }
        }

        composable(Screen.NewProject.path) {
            NewProjectScreen(
                onBack = { navController.popBackStack() },
                onCreate = { synopsis ->
                    viewModel.createProject(synopsis)
                    navController.popBackStack()
                }
            )
        }

        composable(
            route = Screen.ProjectDetail.path + "/{projectId}",
            arguments = listOf(
                navArgument("projectId") { type = androidx.navigation.NavType.StringType }
            )
        ) { backStackEntry ->
            val state = viewModel.uiState
            if (state is MainViewModel.UiState.ProjectDetail) {
                ProjectDetailScreen(
                    project = state.project,
                    onViewAllClick = { /* TODO */ },
                    onViewArchivedClick = { /* TODO */ },
                    onToggleAutoArchive = { viewModel.toggleAutoArchive() },
                    onArchiveCurrentClick = { /* TODO */ },
                    onBack = { navController.popBackStack() },
                    onUpdateAnswer = { qId, text -> viewModel.updateAnswer(state.project.id, qId, text) },
                    onExportClick = { /* TODO */ },
                    autoArchive = viewModel.archiveSettings.autoArchiveAfterUpdate
                )
            }
        }
    }
}
