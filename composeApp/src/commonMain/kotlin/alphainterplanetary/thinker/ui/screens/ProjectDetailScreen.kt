package alphainterplanetary.thinker.ui.screens

import alphainterplanetary.thinker.ProjectUpdateMode
import alphainterplanetary.thinker.data.ThinkerRepository
import alphainterplanetary.thinker.di.AppComponent
import alphainterplanetary.thinker.model.Project
import alphainterplanetary.thinker.model.Question
import alphainterplanetary.thinker.ui.components.AnswerDialog
import alphainterplanetary.thinker.ui.components.AnswerDialogResult
import alphainterplanetary.thinker.ui.components.EditProjectDialog
import alphainterplanetary.thinker.ui.components.QuestionFilter
import alphainterplanetary.thinker.ui.components.QuestionFilterBar
import alphainterplanetary.thinker.ui.components.QuestionItem
import alphainterplanetary.thinker.ui.viewmodel.ProjectDetailUiState
import alphainterplanetary.thinker.ui.viewmodel.ProjectDetailViewModel
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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

  LaunchedEffect(projectId) {
    viewModel.loadProject(projectId)
  }

  val uiState by viewModel.uiState.collectAsState()

  var selectedFilter by remember { mutableStateOf(QuestionFilter.Unanswered) }
  var showEditDialog by remember { mutableStateOf(false) }
  var selectedQuestion by remember { mutableStateOf<Question?>(null) }

  Scaffold(
    topBar = {
      val title = when (val ui = uiState) {
        ProjectDetailUiState.Loading -> "Loading..."
        is ProjectDetailUiState.Success -> ui.project.editableTitle
        is ProjectDetailUiState.Error -> "Error"
      }
      TopAppBar(
        title = { Text(title) },
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
    when (val ui = uiState) {
      ProjectDetailUiState.Loading -> {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues),
          contentAlignment = Alignment.Center
        ) {
          CircularProgressIndicator()
        }
      }

      is ProjectDetailUiState.Success -> {
        ProjectDetailContent(
          project = ui.project,
          selectedFilter = selectedFilter,
          onFilterSelected = { selectedFilter = it },
          onShuffle = { viewModel.shuffle() },
          onAskLater = { viewModel.askLater(it) },
          onIgnore = { viewModel.ignoreQuestion(projectId, it) },
          onUnignore = { viewModel.unignoreQuestion(projectId, it) },
          onAnswerClick = { selectedQuestion = it },
          modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
        )
      }

      is ProjectDetailUiState.Error -> {
        ProjectDetailError(
          message = ui.message,
          onRetry = { viewModel.loadProject(projectId) },
          modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
        )
      }
    }
  }

  if (showEditDialog) {
    val project = (uiState as? ProjectDetailUiState.Success)?.project
    if (project != null) {
      EditProjectDialog(
        project = project,
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

  val questionToShow = selectedQuestion
  if (questionToShow != null) {
    AnswerDialog(
      question = questionToShow,
      onDismiss = { selectedQuestion = null },
      onResult = { result, text ->
        when (result) {
          AnswerDialogResult.Submitted -> {
            viewModel.updateAnswer(projectId, questionToShow.id, text.trim(), isDraft = false)
          }
          AnswerDialogResult.AskLater -> {
            viewModel.updateAnswer(projectId, questionToShow.id, text.trim(), isDraft = true)
            viewModel.askLater(questionToShow.id)
          }
          AnswerDialogResult.DeletedAnswer -> {
            val answerId = questionToShow.currentAnswer?.id
            if (answerId != null) {
              viewModel.deleteAnswer(projectId, questionToShow.id, answerId)
            }
          }
        }
        selectedQuestion = null
      }
    )
  }
}

@Composable
private fun ProjectDetailContent(
  project: Project,
  selectedFilter: QuestionFilter,
  onFilterSelected: (QuestionFilter) -> Unit,
  onShuffle: () -> Unit,
  onAskLater: (String) -> Unit,
  onIgnore: (String) -> Unit,
  onUnignore: (String) -> Unit,
  onAnswerClick: (Question) -> Unit,
  modifier: Modifier = Modifier,
) {
  val filteredQuestions = remember(project, selectedFilter) {
    val all = selectedFilter.apply(project.questions)
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
      project.unansweredQuestions.size > 3
  }

  Column(modifier = modifier) {
    ProjectSynopsis(synopsis = project.synopsis)

    HorizontalDivider()

    QuestionFilterBar(
      selectedFilter = selectedFilter,
      onFilterSelected = onFilterSelected,
      shuffleEnabled = showShuffle,
      onShuffle = onShuffle
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
            onAnswerClick = { onAnswerClick(question) },
            onAskLater = { onAskLater(question.id) },
            onIgnore = { onIgnore(question.id) },
            onUnignore = { onUnignore(question.id) }
          )
        }
      }
    }
  }
}

@Composable
private fun ProjectDetailError(
  message: String,
  onRetry: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier,
    verticalArrangement = Arrangement.Center,
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Text(message)
    Spacer(modifier = Modifier.height(16.dp))
    Button(onClick = onRetry) {
      Text("Retry")
    }
  }
}

@Composable
private fun ProjectSynopsis(synopsis: String) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(16.dp),
  ) {
    Text("Synopsis:", style = MaterialTheme.typography.titleSmall)
    Text(synopsis)
  }
}
