package alphainterplanetary.thinker.ui.screens

import alphainterplanetary.thinker.ProjectUpdateMode
import alphainterplanetary.thinker.data.ThinkerRepository
import alphainterplanetary.thinker.di.AppComponent
import alphainterplanetary.thinker.ui.components.EditProjectDialog
import alphainterplanetary.thinker.ui.components.QuestionFilter
import alphainterplanetary.thinker.ui.components.QuestionFilterBar
import alphainterplanetary.thinker.ui.components.QuestionItem
import alphainterplanetary.thinker.ui.viewmodel.ProjectDetailViewModel
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectDetailScreen(
  appComponent: AppComponent,
  projectId: String,
  onBack: () -> Unit,
) {
  val repository = remember {
    ThinkerRepository(appComponent.projectRepository)
  }
  val viewModel = remember { ProjectDetailViewModel(repository) }
  val snackbarHostState = remember { SnackbarHostState() }

  LaunchedEffect(projectId) {
    viewModel.loadProject(projectId)
  }

  val project by viewModel.project.collectAsState()
  val isLoading by viewModel.isLoading.collectAsState()
  val error by viewModel.error.collectAsState()

  LaunchedEffect(error) {
    error?.let {
      snackbarHostState.showSnackbar(it)
      viewModel.clearError()
    }
  }

  var selectedFilter by remember { mutableStateOf(QuestionFilter.Unanswered) }
  var showEditDialog by remember { mutableStateOf(false) }

  val filteredQuestions = remember(project, selectedFilter) {
    val loadedProject = project ?: return@remember emptyList()
    val all = selectedFilter.apply(loadedProject.questions)
    when (selectedFilter) {
      QuestionFilter.Unanswered -> all
        .take(3)

      QuestionFilter.Answered -> all
        .sortedByDescending { it.currentAnswer?.modifiedAt ?: it.currentAnswer?.answeredAt }

      QuestionFilter.Ignored -> all
        .sortedByDescending { it.ignoredAt }
    }
  }

  val showShuffle = remember(project, selectedFilter, filteredQuestions) {
    selectedFilter == QuestionFilter.Unanswered &&
      (project?.unansweredQuestions?.size ?: 0) > 3
  }

  Scaffold(
    snackbarHost = { SnackbarHost(snackbarHostState) },
    topBar = {
      TopAppBar(
        title = { Text(project?.editableTitle ?: "Loading...") },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
        actions = {
          IconButton(onClick = { showEditDialog = true }) {
            Icon(Icons.Default.Edit, contentDescription = "Edit Project")
          }
        }
      )
    }
  ) { paddingValues ->
    if (isLoading || project == null) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .padding(paddingValues),
        contentAlignment = Alignment.Center
      ) {
        CircularProgressIndicator()
      }
    } else {
      val currentProject = project!!
      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(paddingValues)
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
        ) {
          Text("Synopsis:", style = MaterialTheme.typography.titleSmall)
          Text(currentProject.synopsis)
        }

        HorizontalDivider()

        QuestionFilterBar(
          selectedFilter = selectedFilter,
          onFilterSelected = { selectedFilter = it },
          shuffleEnabled = showShuffle,
          onShuffle = { viewModel.shuffle() }
        )

        Box(modifier = Modifier.fillMaxSize()) {
          LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            items(filteredQuestions) { question ->
              QuestionItem(
                question = question,
                filter = selectedFilter,
                onAnswerClick = { /* TODO */ },
                onAskLater = { viewModel.askLater(question.id) },
                onIgnore = { viewModel.ignoreQuestion(projectId, question.id) },
                onUnignore = { viewModel.unignoreQuestion(projectId, question.id) }
              )
            }
          }
        }
      }
    }
  }

  if (showEditDialog) {
    EditProjectDialog(
      project = project!!,
      onDismiss = { showEditDialog = false },
      onSave = { title, synopsis, mode ->
        viewModel.updateProject(projectId, title, synopsis, mode)
        showEditDialog = false
        viewModel.loadProject(projectId)
        if (mode == ProjectUpdateMode.CLEAR) {
          selectedFilter = QuestionFilter.Unanswered
        }
      }
    )
  }
}
