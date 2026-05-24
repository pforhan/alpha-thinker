package com.pforhan.alphathinker.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pforhan.alphathinker.navigation.AppNavGraph
import com.pforhan.alphathinker.ui.theme.AlphaThinkerTheme
import com.pforhan.alphathinker.ui.theme.DarkTheme
import com.pforhan.alphathinker.ui.theme.LightTheme
import com.pforhan.alphathinker.ui.theme.Typography
import java.time.Instant
import java.time.temporal.ChronoUnit

class MainActivity : ComponentActivity() {

    private val app: AlphaThinkerApp
        get() = application as AlphaThinkerApp

    private val viewModel: MainViewModel by viewModels {
        MainViewModelFactory(app.repository)
    }

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AlphaThinkerTheme(darkTheme = false) {
                Surface(
                    modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavGraph(
                        viewModel = viewModel,
                        navController = androidx.navigation.compose.rememberNavController()
                    )
                }
            }
        }
    }
}

@Composable
@Preview
fun MainActivityPreview() {
    AlphaThinkerTheme {
        Surface(
            modifier = androidx.compose.ui.Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            val viewModel = MainViewModel(
                repository = MockRepository(),
                onProjectClick = { },
                onNewProjectClick = { },
            )
            AppNavGraph(
                viewModel = viewModel,
                navController = androidx.navigation.compose.rememberNavController()
            )
        }
    }
}

private class MockRepository : com.pforhan.alphathinker.repository.ProjectRepository {
    data class ProjectState(
        val list: List<MockProject>,
        val selectedProject: MockProject?,
        val isLoading: Boolean,
        val error: String?
    )

    data class MockProject(
        val id: String,
        val synopsis: String,
        val createdAt: String,
        val exchangeRoundCount: Int
    )

    override fun getProjects() = emptyList<MockProject>()
    override fun createProject(synopsis: String) {}
    override fun deleteProject(id: String) {}
    override fun getUnansweredQuestions(projectId: String) = emptyList<String>()
    override fun updateAnswer(projectId: String, questionId: String, text: String) {}
    override fun exportProject(id: String): String = ""
}
