package alphainterplanetary.thinker.ui.screens

import alphainterplanetary.thinker.di.AppComponent
import alphainterplanetary.thinker.model.Project
import alphainterplanetary.thinker.tasks.GenerationTask
import alphainterplanetary.thinker.tasks.TaskKind
import alphainterplanetary.thinker.ui.chrome.AppChromeState
import alphainterplanetary.thinker.ui.chrome.AppScaffold
import alphainterplanetary.thinker.ui.chrome.ChromeSheet
import alphainterplanetary.thinker.ui.components.CreateProjectDialog
import alphainterplanetary.thinker.ui.components.GenerationProblemKind
import alphainterplanetary.thinker.ui.components.PhaseBadge
import alphainterplanetary.thinker.ui.components.SpinnerLabel
import alphainterplanetary.thinker.ui.components.SwipeAction
import alphainterplanetary.thinker.ui.components.SwipeActionStyle
import alphainterplanetary.thinker.ui.components.SwipeableCard
import alphainterplanetary.thinker.ui.components.UntitledProjectLabel
import alphainterplanetary.thinker.ui.components.activeTaskSummary
import alphainterplanetary.thinker.ui.components.generationProblemKind
import alphainterplanetary.thinker.ui.theme.Dimens
import alphainterplanetary.thinker.ui.theme.PhaseStyles
import alphainterplanetary.thinker.ui.viewmodel.ProjectListUiState
import alphainterplanetary.thinker.ui.viewmodel.ProjectListViewModel
import alphainterplanetary.thinker.util.normalizeWhitespace
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.style.TextOverflow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectListScreen(
  appComponent: AppComponent,
  chrome: AppChromeState,
  onProjectClick: (Project) -> Unit,
  onProjectCreated: (Project) -> Unit,
) {
  var showCreateDialog by remember { mutableStateOf(false) }
  var projectToDelete by remember { mutableStateOf<Project?>(null) }
  var deletingId by remember { mutableStateOf<String?>(null) }

  val viewModel = remember {
    ProjectListViewModel(
      appComponent.projectRepository,
      appComponent.taskRunner,
      appComponent.appScope,
    )
  }

  DisposableEffect(Unit) {
    onDispose { viewModel.close() }
  }

  LaunchedEffect(Unit) {
    viewModel.loadProjects()
  }

  val uiState by viewModel.uiState.collectAsState()
  val createdProject by viewModel.createdProject.collectAsState()
  val tasks by viewModel.tasks.collectAsState()
  val activeTasksByProject = remember(tasks) {
    tasks.filter { it.isActive }.groupBy { it.projectId }
  }
  // A failure is worth a marker on the card: the list is where the user is when
  // generation comes up empty, and a project with no questions and no
  // explanation just looks idle. The marker is the route to the engine status
  // sheet, which is where the engine's own explanation of the failure lives —
  // the retry stays on the project itself, so nothing about the engine reaches
  // into a project.
  val problemsByProject = remember(tasks, uiState) {
    (uiState as? ProjectListUiState.Success)
      ?.projects
      ?.associate { project -> project.id to generationProblemKind(project, tasks) }
      .orEmpty()
  }

  LaunchedEffect(createdProject) {
    val project = createdProject
    if (project != null) {
      viewModel.consumeCreatedProject()
      onProjectCreated(project)
    }
  }

  AppScaffold(
    title = { Text("Alpha Thinker") },
    chrome = chrome,
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
            activeTasksByProject = activeTasksByProject,
            problemsByProject = problemsByProject,
            onOpenEngineStatus = { chrome.openSheet(ChromeSheet.Status) },
            pendingDeletionId = projectToDelete?.id,
            deletingId = deletingId,
            onProjectClick = onProjectClick,
            onCreateClick = { showCreateDialog = true },
            onDeleteProject = { projectToDelete = it },
            onDeleteConfirmed = { project ->
              deletingId = null
              viewModel.deleteProject(project.id)
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
      onDismiss = { projectToDelete = null },
      // Confirm: hand the id to its list item, which then decides whether to
      // animate the swipe-out (trash-icon path) or delete immediately (swipe path).
      onConfirm = {
        projectToDelete = null
        deletingId = project.id
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
    Spacer(modifier = Modifier.height(Dimens.MessageActionGap))
    Button(onClick = onCreateClick) {
      Text("Create your first project")
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProjectListItem(
  project: Project,
  activeTasks: List<GenerationTask>,
  /** The generation failure to mark, or null when there is nothing to act on. */
  problem: GenerationProblemKind?,
  onOpenEngineStatus: () -> Unit,
  pendingDeletionId: String?,
  deletingId: String?,
  onClick: () -> Unit,
  onDelete: () -> Unit,
  onDeleteConfirmed: (Project) -> Unit,
) {
  val dismissState = rememberSwipeToDismissBoxState()
  val scope = rememberCoroutineScope()

  // Gesture-initiated deletes leave the card swiped out while the dialog is open.
  // When the dialog closes without confirming, slide the card back into place.
  LaunchedEffect(pendingDeletionId, deletingId) {
    if (deletingId != project.id &&
      pendingDeletionId == null &&
      dismissState.settledValue != SwipeToDismissBoxValue.Settled
    ) {
      dismissState.reset()
    }
  }

  // Confirm-initiated: delete the card now that the dialog was accepted. If the
  // card is still settled (delete was triggered by the trash icon, so nothing has
  // swiped yet) animate it out first; if a swipe already dismissed it, the
  // animation already happened, so delete immediately from the swiped-out position.
  LaunchedEffect(deletingId) {
    if (deletingId == project.id) {
      if (dismissState.settledValue == SwipeToDismissBoxValue.Settled) {
        dismissState.dismiss(SwipeToDismissBoxValue.EndToStart)
      }
      onDeleteConfirmed(project)
    }
  }

  // Gesture path: a full swipe counts as the delete gesture itself, so just show
  // the dialog and keep the card held out. Guarded so the programmatic dismiss
  // used by the trash-icon path doesn't re-open the dialog on its way out.
  val handleSwipeDismiss = {
    if (deletingId != project.id) {
      onDelete()
    }
  }

  SwipeableCard(
    state = dismissState,
    startAction = SwipeAction("Delete", Icons.Default.Delete, SwipeActionStyle.Delete),
    endAction = SwipeAction("Delete", Icons.Default.Delete, SwipeActionStyle.Delete),
    onSwipeStart = handleSwipeDismiss,
    onSwipeEnd = handleSwipeDismiss,
    settleAfterDismiss = false,
    resetScope = scope,
  ) {
    Card(
      onClick = onClick,
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = Dimens.ScreenPadding),
    ) {
      val style = PhaseStyles.forPhase(project.currentPhase)
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .clip(CardDefaults.shape)
          .background(PhaseStyles.rowTint(project.currentPhase))
          .drawBehind {
            drawRect(
              color = style.container,
              topLeft = Offset.Zero,
              size = Size(Dimens.PhaseRowBarWidth.toPx(), size.height),
            )
          }
          .padding(Dimens.CardPadding),
      ) {
        Column {
          Row(verticalAlignment = Alignment.CenterVertically) {
            val titleGenerating = project.editableTitle.isBlank() &&
              activeTasks.any { it.kind == TaskKind.TitleRecommendation }
            Text(
              // A title that never landed would otherwise render the card blank;
              // the marker below carries the reason and the detail screen the fix.
              text = if (titleGenerating) {
                "Generating title…"
              } else {
                project.editableTitle.normalizeWhitespace().ifBlank { UntitledProjectLabel }
              },
              modifier = Modifier.weight(1f),
              style = MaterialTheme.typography.titleMedium,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.width(Dimens.LabelChipGap))
            PhaseBadge(phase = project.currentPhase)
            Spacer(modifier = Modifier.width(Dimens.LabelChipGap))
            Text(
              text = "${project.currentPhaseCompletionPercent}%",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          Spacer(modifier = Modifier.height(Dimens.ContentGap))
          if (activeTasks.isNotEmpty()) {
            ActiveTaskChip(tasks = activeTasks)
            Spacer(modifier = Modifier.height(Dimens.ContentGap))
          } else if (problem != null) {
            GenerationProblemChip(kind = problem, onClick = onOpenEngineStatus)
            Spacer(modifier = Modifier.height(Dimens.ContentGap))
          }
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
              text = project.synopsis.normalizeWhitespace(),
              modifier = Modifier.weight(1f),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 2,
              overflow = TextOverflow.Ellipsis,
            )
            // Tap path: no swipe here — just open the confirmation; the card is
            // animated out after the dialog is accepted (see the deletingId effect).
            IconButton(onClick = onDelete) {
              Icon(Icons.Default.Delete, contentDescription = "Delete project")
            }
          }
        }
      }
    }
  }
}

@Composable
private fun ProjectListSuccess(
  projects: List<Project>,
  activeTasksByProject: Map<String, List<GenerationTask>>,
  /** Per-project generation failures, absent where the project is fine. */
  problemsByProject: Map<String, GenerationProblemKind?>,
  pendingDeletionId: String?,
  deletingId: String?,
  onProjectClick: (Project) -> Unit,
  onOpenEngineStatus: () -> Unit,
  onCreateClick: () -> Unit,
  onDeleteProject: (Project) -> Unit,
  onDeleteConfirmed: (Project) -> Unit,
) {
  if (projects.isEmpty()) {
    ProjectListEmpty(onCreateClick = onCreateClick)
  } else {
    LazyColumn(
      modifier = Modifier.fillMaxSize(),
      verticalArrangement = Arrangement.spacedBy(Dimens.ListGap),
    ) {
      items(projects, key = { it.id }) { project ->
        ProjectListItem(
          project = project,
          activeTasks = activeTasksByProject[project.id].orEmpty(),
          problem = problemsByProject[project.id],
          onOpenEngineStatus = onOpenEngineStatus,
          pendingDeletionId = pendingDeletionId,
          deletingId = deletingId,
          onClick = { onProjectClick(project) },
          onDelete = { onDeleteProject(project) },
          onDeleteConfirmed = onDeleteConfirmed,
        )
      }
    }
  }
}

@Composable
private fun ActiveTaskChip(tasks: List<GenerationTask>) {
  // Same wording as the floating task bar, so a card and the bar never describe
  // the same work two different ways. Non-null: the caller only shows this chip
  // when the project has active tasks.
  SpinnerLabel(
    text = activeTaskSummary(tasks).orEmpty(),
    textStyle = MaterialTheme.typography.labelSmall,
    textColor = MaterialTheme.colorScheme.primary,
  )
}

@Composable
private fun GenerationProblemChip(kind: GenerationProblemKind, onClick: () -> Unit) {
  // Tappable into the engine status sheet, which is where the engine's account of
  // the failure is — and one row from the picker. The card's own tap target still
  // opens the project, where the retry lives, so the marker and the card lead to
  // two different places by design: this one explains, that one fixes.
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier
      .clip(MaterialTheme.shapes.small)
      .clickable(onClick = onClick),
  ) {
    Icon(
      imageVector = Icons.Default.Warning,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.error,
      modifier = Modifier.size(Dimens.ProgressIndicatorSize),
    )
    Spacer(modifier = Modifier.width(Dimens.IconLabelGap))
    Text(
      text = kind.headline,
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.error,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
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
      Column {
        Text(
          text = project.editableTitle.normalizeWhitespace(),
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(Dimens.ContentGap))
        Text("This action cannot be undone.")
      }
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
    Spacer(modifier = Modifier.height(Dimens.MessageActionGap))
    Button(onClick = onRetry) {
      Text("Retry")
    }
  }
}
