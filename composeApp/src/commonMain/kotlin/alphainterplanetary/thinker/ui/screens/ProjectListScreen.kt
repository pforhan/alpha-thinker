package alphainterplanetary.thinker.ui.screens

import alphainterplanetary.thinker.data.ThinkerRepository
import alphainterplanetary.thinker.di.AppComponent
import alphainterplanetary.thinker.model.Project
import alphainterplanetary.thinker.ui.components.CreateProjectDialog
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
  val snackbarHostState = remember { SnackbarHostState() }

  LaunchedEffect(Unit) {
    viewModel.loadProjects()
  }

  val projects by viewModel.projects.collectAsState()
  val isLoading by viewModel.isLoading.collectAsState()
  val createdProject by viewModel.createdProject.collectAsState()
  val error by viewModel.error.collectAsState()

  LaunchedEffect(error) {
    error?.let {
      snackbarHostState.showSnackbar(it)
      viewModel.clearError()
    }
  }

  LaunchedEffect(createdProject) {
    val project = createdProject
    if (project != null) {
      viewModel.consumeCreatedProject()
      onProjectCreated(project)
    }
  }

  Scaffold(
    snackbarHost = { SnackbarHost(snackbarHostState) },
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
      if (isLoading) {
        Box(
          modifier = Modifier.fillMaxSize(),
          contentAlignment = Alignment.Center
        ) {
          CircularProgressIndicator()
        }
      } else if (projects.isEmpty()) {
        Column(
          modifier = Modifier.fillMaxSize(),
          verticalArrangement = Arrangement.Center,
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          Text("No projects yet.")
          Spacer(modifier = Modifier.height(16.dp))
          Button(onClick = { showCreateDialog = true }) {
            Text("Create your first project")
          }
        }
      } else {
        LazyColumn(
          modifier = Modifier.fillMaxSize(),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          items(projects, key = { it.id }) { project ->
            Card(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clickable { onProjectClick(project) }
            ) {
              ListItem(
                headlineContent = { Text(project.editableTitle) },
                supportingContent = {
                  Text(
                    project.synopsis,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                  )
                },
                trailingContent = {
                  Icon(AutoMirrored.Filled.ArrowForward, contentDescription = null)
                }
              )
            }
          }
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
