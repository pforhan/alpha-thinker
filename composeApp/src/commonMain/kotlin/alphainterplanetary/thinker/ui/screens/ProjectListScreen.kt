package alphainterplanetary.thinker.ui.screens

import alphainterplanetary.thinker.data.ThinkerRepository
import alphainterplanetary.thinker.di.AppComponent
import alphainterplanetary.thinker.model.Project
import alphainterplanetary.thinker.ui.components.CreateProjectDialog
import alphainterplanetary.thinker.ui.viewmodel.ProjectListUiState
import alphainterplanetary.thinker.ui.viewmodel.ProjectListViewModel
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.Icons.AutoMirrored
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectListScreen(
  appComponent: AppComponent,
  onProjectClick: (Project) -> Unit,
  onProjectCreated: (Project) -> Unit,
) {
  var showCreateDialog by remember { mutableStateOf(false) }

  val repository = remember {
    ThinkerRepository(appComponent.projectRepository)
  }
  val viewModel = remember { ProjectListViewModel(repository) }

  LaunchedEffect(Unit) {
    viewModel.loadProjects()
  }

  val uiState by viewModel.uiState.collectAsState()
  val createdProject by viewModel.createdProject.collectAsState()

  LaunchedEffect(createdProject) {
    val project = createdProject
    if (project != null) {
      viewModel.consumeCreatedProject()
      onProjectCreated(project)
    }
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Alpha Thinker") },
        actions = {
          IconButton(onClick = { viewModel.loadProjects() }) {
            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
          }
        }
      )
    },
    floatingActionButton = {
      FloatingActionButton(onClick = { showCreateDialog = true }) {
        Icon(Icons.Default.Add, contentDescription = "Add Project")
      }
    }
  ) { paddingValues ->
    Box(modifier = Modifier.padding(paddingValues)) {
      when (val ui = uiState) {
        ProjectListUiState.Loading -> {
          ProjectListLoading()
        }

        is ProjectListUiState.Success -> {
          ProjectListSuccess(
            projects = ui.projects,
            onProjectClick = onProjectClick,
            onCreateClick = { showCreateDialog = true },
          )
        }

        is ProjectListUiState.Error -> {
          ProjectListError(
            message = ui.message,
            onRetry = { viewModel.loadProjects() },
          )
        }
      }
    }
  }

  if (showCreateDialog) {
    CreateProjectDialog(
      onDismiss = { showCreateDialog = false },
      onCreate = { title, synopsis ->
        if (synopsis.isNotBlank()) {
          viewModel.createProject(synopsis, title.ifBlank { null })
        }
        showCreateDialog = false
      }
    )
  }
}

@Composable
private fun ProjectListLoading() {
  Box(
    modifier = Modifier.fillMaxSize(),
    contentAlignment = Alignment.Center,
  ) {
    CircularProgressIndicator()
  }
}

@Composable
private fun ProjectListEmpty(onCreateClick: () -> Unit) {
  Column(
    modifier = Modifier.fillMaxSize(),
    verticalArrangement = Arrangement.Center,
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text("No projects yet.")
    Spacer(modifier = Modifier.height(16.dp))
    Button(onClick = onCreateClick) {
      Text("Create your first project")
    }
  }
}

@Composable
private fun ProjectListItem(
  project: Project,
  onClick: () -> Unit,
) {
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 16.dp)
      .clickable(onClick = onClick),
  ) {
    ListItem(
      headlineContent = { Text(project.editableTitle) },
      supportingContent = {
        Text(
          project.synopsis,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
      },
      trailingContent = {
        Icon(AutoMirrored.Filled.ArrowForward, contentDescription = null)
      },
    )
  }
}

@Composable
private fun ProjectListSuccess(
  projects: List<Project>,
  onProjectClick: (Project) -> Unit,
  onCreateClick: () -> Unit,
) {
  if (projects.isEmpty()) {
    ProjectListEmpty(onCreateClick = onCreateClick)
  } else {
    LazyColumn(
      modifier = Modifier.fillMaxSize(),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      items(projects, key = { it.id }) { project ->
        ProjectListItem(
          project = project,
          onClick = { onProjectClick(project) },
        )
      }
    }
  }
}

@Composable
private fun ProjectListError(
  message: String,
  onRetry: () -> Unit,
) {
  Column(
    modifier = Modifier.fillMaxSize(),
    verticalArrangement = Arrangement.Center,
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text(message)
    Spacer(modifier = Modifier.height(16.dp))
    Button(onClick = onRetry) {
      Text("Retry")
    }
  }
}
