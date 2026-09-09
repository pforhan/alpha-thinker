package alphainterplanetary.thinker.ui.screens

import alphainterplanetary.thinker.data.ThinkerRepository
import alphainterplanetary.thinker.di.AppComponent
import alphainterplanetary.thinker.model.Project
import alphainterplanetary.thinker.ui.components.CreateProjectDialog
import alphainterplanetary.thinker.ui.components.SwipeAction
import alphainterplanetary.thinker.ui.components.SwipeableCard
import alphainterplanetary.thinker.ui.viewmodel.ProjectListUiState
import alphainterplanetary.thinker.ui.viewmodel.ProjectListViewModel
import alphainterplanetary.thinker.util.normalizeWhitespace
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private val DeleteBackgroundColor = Color(0xFFD32F2F)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectListScreen(
  appComponent: AppComponent,
  onProjectClick: (Project) -> Unit,
  onProjectCreated: (Project) -> Unit,
  onSettingsClick: () -> Unit,
) {
  var showCreateDialog by remember { mutableStateOf(false) }
  var projectToDelete by remember { mutableStateOf<Project?>(null) }
  var pendingDeletionId by remember { mutableStateOf<String?>(null) }

  val repository = remember {
    ThinkerRepository(appComponent.projectRepository, appComponent.sampleProjectGenerator)
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
          IconButton(onClick = onSettingsClick) {
            Icon(Icons.Filled.Settings, contentDescription = "Settings")
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
            pendingDeletionId = pendingDeletionId,
            onProjectClick = onProjectClick,
            onCreateClick = { showCreateDialog = true },
            onDeleteProject = {
              projectToDelete = it
              pendingDeletionId = it.id
            },
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

  projectToDelete?.let { project ->
    ConfirmDeleteProjectDialog(
      project = project,
      onDismiss = {
        projectToDelete = null
        pendingDeletionId = null
      },
      onConfirm = {
        projectToDelete = null
        viewModel.deleteProject(project.id)
      },
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProjectListItem(
  project: Project,
  pendingDeletionId: String?,
  onClick: () -> Unit,
  onDelete: () -> Unit,
) {
  val dismissState = rememberSwipeToDismissBoxState()
  val scope = rememberCoroutineScope()

  LaunchedEffect(pendingDeletionId) {
    if (pendingDeletionId == null && dismissState.settledValue != SwipeToDismissBoxValue.Settled) {
      dismissState.reset()
    }
  }

  SwipeableCard(
    state = dismissState,
    startAction = SwipeAction("Delete", Icons.Default.Delete, DeleteBackgroundColor),
    endAction = SwipeAction("Delete", Icons.Default.Delete, DeleteBackgroundColor),
    onSwipeStart = onDelete,
    onSwipeEnd = onDelete,
    settleAfterDismiss = false,
    resetScope = scope,
  ) {
    Card(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp)
        .clickable(onClick = onClick),
    ) {
      ListItem(
        headlineContent = {
          Text(
            project.editableTitle.normalizeWhitespace(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        },
        supportingContent = {
          Text(
            project.synopsis.normalizeWhitespace(),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
        },
        trailingContent = {
          Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
              onClick = {
                scope.launch { dismissState.dismiss(SwipeToDismissBoxValue.EndToStart) }
              }
            ) {
              Icon(
                Icons.Default.Delete,
                contentDescription = "Delete project",
              )
            }
            Icon(AutoMirrored.Filled.ArrowForward, contentDescription = null)
          }
        },
      )
    }
  }
}

@Composable
private fun ProjectListSuccess(
  projects: List<Project>,
  pendingDeletionId: String?,
  onProjectClick: (Project) -> Unit,
  onCreateClick: () -> Unit,
  onDeleteProject: (Project) -> Unit,
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
          pendingDeletionId = pendingDeletionId,
          onClick = { onProjectClick(project) },
          onDelete = { onDeleteProject(project) },
        )
      }
    }
  }
}

@Composable
private fun ConfirmDeleteProjectDialog(
  project: Project,
  onDismiss: () -> Unit,
  onConfirm: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Delete project?") },
    text = {
      Text(
        "${project.editableTitle.normalizeWhitespace()}\n\nThis action cannot be undone."
      )
    },
    confirmButton = {
      TextButton(onClick = onConfirm) {
        Text("Delete")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("Cancel")
      }
    },
  )
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
