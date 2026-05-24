package com.pforhan.alphathinker.navigation

sealed class Screen(val path: String) {
    object ProjectsList : Screen("projects_list")
    object NewProject : Screen("new_project")
    object ProjectDetail : Screen("project_detail") {
        fun createRoute(projectId: String) = "$path/$projectId"
    }

    companion object {
        fun fromRoute(route: String): Screen? {
            return when (route) {
                ProjectsList.path -> ProjectsList
                NewProject.path -> NewProject
                else -> null
            }
        }
    }
}
