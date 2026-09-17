package alphainterplanetary.thinker.ui.screens

import alphainterplanetary.thinker.ProjectUpdateMode
import alphainterplanetary.thinker.di.AppComponent
import alphainterplanetary.thinker.model.Project
import alphainterplanetary.thinker.model.Question
import alphainterplanetary.thinker.phases.Phase
import alphainterplanetary.thinker.ui.components.AnswerDialog
import alphainterplanetary.thinker.ui.components.AnswerDialogResult
import alphainterplanetary.thinker.ui.components.EditProjectDialog
import alphainterplanetary.thinker.ui.components.PhaseSectionHeader
import alphainterplanetary.thinker.ui.components.QuestionItem
import alphainterplanetary.thinker.ui.components.QuestionViewMode
import alphainterplanetary.thinker.ui.components.QuestionViewModeBar
import alphainterplanetary.thinker.ui.components.PhasePill
import alphainterplanetary.thinker.ui.components.ScrollableOverflowText
import alphainterplanetary.thinker.ui.components.SwipeableCard
import alphainterplanetary.thinker.ui.theme.Dimens
import alphainterplanetary.thinker.ui.viewmodel.ProjectDetailUiState
import alphainterplanetary.thinker.ui.viewmodel.ProjectDetailViewModel
import alphainterplanetary.thinker.util.normalizeWhitespace
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.CoroutineScope

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectDetailScreen(
  appComponent: AppComponent,
  projectId: String,
  onBack: () -> Unit,
) {
  val viewModel = remember {
    ProjectDetailViewModel(appComponent.projectRepository, appComponent.appScope)
  }

  LaunchedEffect(projectId) {
    viewModel.loadProject(projectId)
  }

  val uiState by viewModel.uiState.collectAsState()
  val pendingUndo by viewModel.pendingUndo.collectAsState()

  var selectedView by remember { mutableStateOf(QuestionViewMode.Unanswered) }
  var showEditDialog by remember { mutableStateOf(false) }
  var selectedQuestion by remember { mutableStateOf<Question?>(null) }

  val snackbarHostState = remember { SnackbarHostState() }

  LaunchedEffect(pendingUndo) {
    val undo = pendingUndo ?: return@LaunchedEffect
    val result = snackbarHostState.showSnackbar(
      message = undo.message,
      actionLabel = "Undo",
      duration = SnackbarDuration.Long,
    )
    when (result) {
      SnackbarResult.ActionPerformed -> viewModel.undo(undo)
      SnackbarResult.Dismissed -> Unit
    }
  }

  Scaffold(
    snackbarHost = { SnackbarHost(snackbarHostState) },
    topBar = {
      val title = when (val ui = uiState) {
        ProjectDetailUiState.Loading -> "Loading..."
        is ProjectDetailUiState.Success -> ui.project.editableTitle
        is ProjectDetailUiState.Error -> "Error"
      }
      TopAppBar(
        title = {
          Text(
            text = title.normalizeWhitespace(),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
        },
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
          selectedView = selectedView,
          onViewSelected = { selectedView = it },
          onShuffle = { viewModel.shuffle() },
          onAskLater = { viewModel.askLater(it) },
          onIgnore = { viewModel.ignoreQuestion(projectId, it) },
          onUnignore = { viewModel.unignoreQuestion(projectId, it) },
onAnswerClick = { selectedQuestion = it },
      onDeleteAnswer = { viewModel.saveAnswer(projectId, it.id, "", completed = false) },
          onGenerateMore = { viewModel.generateMoreQuestions(projectId) },
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
            selectedView = QuestionViewMode.Unanswered
          }
        }
      )
    }
  }

  val questionToShow = selectedQuestion
  if (questionToShow != null) {
    val project = (uiState as? ProjectDetailUiState.Success)?.project
    if (project != null) {
      AnswerDialog(
        question = questionToShow,
        phase = project.phaseForQuestion(questionToShow),
        onDismiss = { selectedQuestion = null },
        onResult = { result, text ->
          when (result) {
            AnswerDialogResult.Submitted -> {
              viewModel.saveAnswer(projectId, questionToShow.id, text, completed = true)
            }

            AnswerDialogResult.SavedDraft -> {
              viewModel.saveAnswer(projectId, questionToShow.id, text, completed = false)
            }

            AnswerDialogResult.DeletedAnswer -> {
              viewModel.saveAnswer(projectId, questionToShow.id, "", completed = false)
            }

            AnswerDialogResult.Unignored -> {
              viewModel.unignoreQuestion(projectId, questionToShow.id)
            }
          }
          selectedQuestion = null
        }
      )
    }
  }
}

@OptIn(ExperimentalAnimationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun ProjectDetailContent(
  project: Project,
  selectedView: QuestionViewMode,
  onViewSelected: (QuestionViewMode) -> Unit,
  onShuffle: () -> Unit,
  onAskLater: (String) -> Unit,
  onIgnore: (String) -> Unit,
  onUnignore: (String) -> Unit,
  onAnswerClick: (Question) -> Unit,
  onDeleteAnswer: (Question) -> Unit,
  onGenerateMore: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var showPhaseOverview by remember { mutableStateOf(false) }
  val phaseSummaries = remember(project) { project.priorPhaseSummaries() }

  Column(modifier = modifier) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .clickable(enabled = phaseSummaries.isNotEmpty()) { showPhaseOverview = true },
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(
          start = Dimens.ScreenPadding,
          end = Dimens.ScreenPadding,
          top = Dimens.SectionGap,
        ),
      ) {
        PhaseSummaryRow(
          phase = project.currentPhase,
          resolved = project.currentPhaseResolvedCount,
          total = project.currentPhaseQuestionCount,
        )
        if (phaseSummaries.isNotEmpty()) {
          Spacer(modifier = Modifier.width(Dimens.TightGap))
          Icon(
            Icons.Default.ArrowDropDown,
            contentDescription = null,
            modifier = Modifier.size(Dimens.IconSizeSmall),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
      if (phaseSummaries.isNotEmpty()) {
        DropdownMenu(
          expanded = showPhaseOverview,
          onDismissRequest = { showPhaseOverview = false },
        ) {
          phaseSummaries.forEach { summary ->
            DropdownMenuItem(
              text = {
                PhaseSummaryRow(
                  phase = summary.phase,
                  resolved = summary.resolved,
                  total = summary.total,
                  modifier = Modifier.fillMaxWidth(),
                  endAligned = true,
                )
              },
              onClick = { showPhaseOverview = false },
            )
          }
        }
      }
    }

    ProjectSynopsis(synopsis = project.synopsis)

    HorizontalDivider()

    QuestionViewModeBar(
      selectedView = selectedView,
      onViewSelected = onViewSelected,
    )

    val dismissScope = rememberCoroutineScope()

    AnimatedContent(
      targetState = selectedView,
      transitionSpec = {
        fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 4 } togetherWith
          fadeOut(tween(200))
      },
      contentKey = { it },
      label = "FilterContent",
    ) { view ->
      val filteredQuestions = remember(project, view) {
        val all = view.apply(project.questions)
        if (view == QuestionViewMode.Unanswered) all.take(3) else all
      }

      val sections = remember(project, view) {
        view.sections(project.questions, project::phaseForQuestion)
      }

      val showShuffle = remember(project, view, filteredQuestions) {
        view == QuestionViewMode.Unanswered &&
          project.unansweredQuestions.size > 3
      }

      Box(modifier = Modifier.fillMaxSize()) {
        if (filteredQuestions.isEmpty()) {
          QuestionEmptyState(
            title = view.emptyMessage,
            recommendedViews = view.recommendedViews(project.questions),
            onViewSelected = onViewSelected,
            onGenerateMore = if (view == QuestionViewMode.Unanswered) onGenerateMore else null,
            modifier = Modifier.fillMaxSize(),
          )
        } else {
          LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(Dimens.ListGap)
          ) {
            if (sections.isNotEmpty()) {
              sections.forEach { section ->
                item(key = "phase-${section.phase.key}") {
                  PhaseSectionHeader(
                    phase = section.phase,
                    count = section.questions.size,
                    countLabel = view.resolvedCountLabel,
                  )
                }
                items(section.questions, key = { it.id }) { question ->
                  QuestionListRow(
                    project = project,
                    question = question,
                    view = view,
                    dismissScope = dismissScope,
                    onAskLater = onAskLater,
                    onIgnore = onIgnore,
                    onUnignore = onUnignore,
                    onDeleteAnswer = onDeleteAnswer,
                    onAnswerClick = onAnswerClick,
                  )
                }
              }
            } else {
              items(filteredQuestions, key = { it.id }) { question ->
                QuestionListRow(
                  project = project,
                  question = question,
                  view = view,
                  dismissScope = dismissScope,
                  onAskLater = onAskLater,
                  onIgnore = onIgnore,
                  onUnignore = onUnignore,
                  onDeleteAnswer = onDeleteAnswer,
                  onAnswerClick = onAnswerClick,
                )
              }
            }
            if (showShuffle) {
              item {
                ShuffleRow(
                  remainingCount = project.unansweredQuestions.size - filteredQuestions.size,
                  onClick = onShuffle,
                )
              }
            }
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuestionListRow(
  project: Project,
  question: Question,
  view: QuestionViewMode,
  dismissScope: CoroutineScope,
  onAskLater: (String) -> Unit,
  onIgnore: (String) -> Unit,
  onUnignore: (String) -> Unit,
  onDeleteAnswer: (Question) -> Unit,
  onAnswerClick: (Question) -> Unit,
) {
  val dismissState = rememberSwipeToDismissBoxState()
  val phase = project.phaseForQuestion(question)
  SwipeableCard(
    state = dismissState,
    startAction = view.startAction,
    endAction = view.endAction,
    resetScope = dismissScope,
    onSwipeStart = {
      when (view) {
        QuestionViewMode.Unanswered -> onAskLater(question.id)
        QuestionViewMode.Answered,
        QuestionViewMode.Draft -> onIgnore(question.id)
        QuestionViewMode.Ignored -> onUnignore(question.id)
      }
    },
    onSwipeEnd = {
      when (view) {
        QuestionViewMode.Unanswered -> onIgnore(question.id)
        QuestionViewMode.Answered,
        QuestionViewMode.Draft -> onDeleteAnswer(question)
        QuestionViewMode.Ignored -> onUnignore(question.id)
      }
    },
  ) {
    QuestionItem(
      question = question,
      view = view,
      dismissState = dismissState,
      phase = phase,
      showPhasePill = view == QuestionViewMode.Unanswered && project.currentPhase != phase,
      onAnswerClick = { onAnswerClick(question) },
    )
  }
}

@Composable
private fun QuestionEmptyState(
  title: String,
  recommendedViews: List<QuestionViewMode>,
  onViewSelected: (QuestionViewMode) -> Unit,
  onGenerateMore: (() -> Unit)?,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier.padding(Dimens.EmptyStatePadding),
    verticalArrangement = Arrangement.Center,
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text(
      text = title,
      textAlign = TextAlign.Center,
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (onGenerateMore != null) {
      Spacer(modifier = Modifier.height(Dimens.EmptyStateActionGap))
      Button(onClick = onGenerateMore) {
        Text("Get more questions")
      }
    }
    if (recommendedViews.isNotEmpty()) {
      Spacer(modifier = Modifier.height(Dimens.SectionGap))
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
      ) {
        Text(
          text = "You have questions in:",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        recommendedViews.forEachIndexed { index, view ->
          if (index > 0) {
            Text(
              text = "•",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          TextButton(
            onClick = { onViewSelected(view) },
            contentPadding = PaddingValues(horizontal = Dimens.ButtonHorizontalPadding),
          ) {
            Text(
              text = view.displayName,
              style = MaterialTheme.typography.bodySmall,
            )
          }
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
    Spacer(modifier = Modifier.height(Dimens.MessageActionGap))
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
      .padding(Dimens.SectionPadding),
  ) {
    Text("Synopsis:", style = MaterialTheme.typography.titleSmall)
    ScrollableOverflowText(
      text = synopsis.normalizeWhitespace(),
      collapsedMaxLines = 5,
      style = MaterialTheme.typography.bodyMedium,
    )
  }
}

@Composable
private fun ShuffleRow(
  remainingCount: Int,
  onClick: () -> Unit,
) {
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = Dimens.ScreenPadding)
      .clip(MaterialTheme.shapes.medium)
      .clickable(onClick = onClick),
    contentAlignment = Alignment.Center,
  ) {
    Row(
      modifier = Modifier.padding(vertical = Dimens.ActionRowVerticalPadding),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.Center,
    ) {
      Icon(
        Icons.Default.Shuffle,
        contentDescription = null,
        modifier = Modifier.size(Dimens.IconSizeMedium),
        tint = MaterialTheme.colorScheme.primary,
      )
      Spacer(modifier = Modifier.width(Dimens.IconLabelGap))
      Text(
        "Shuffle questions",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.primary,
      )
      if (remainingCount > 0) {
        Text(
          " · $remainingCount more available",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

private data class PhaseSummary(
  val phase: Phase,
  val resolved: Int,
  val total: Int,
)

/**
 * The "phase pill + N of M completed" summary line, shared by the project
 * detail header (current phase) and the prior-phase overview popup so both
 * render exactly the same widget.
 */
@Composable
private fun PhaseSummaryRow(
  phase: Phase,
  resolved: Int,
  total: Int,
  modifier: Modifier = Modifier,
  endAligned: Boolean = false,
) {
  Row(
    modifier = modifier,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    PhasePill(phase = phase)
    if (endAligned) {
      Spacer(modifier = Modifier.weight(1f))
    } else {
      Spacer(modifier = Modifier.width(Dimens.LabelChipGap))
    }
    Text(
      text = "$resolved of $total completed",
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

/**
 * Summaries for the phases the project has already entered, newest first, for
 * the current phase's overview popup. Only visited phases (those with a round)
 * and only phases before the current one are included — future phases never
 * appear. Counts mirror the current-phase summary line (resolved = answered or
 * ignored).
 */
private fun Project.priorPhaseSummaries(): List<PhaseSummary> {
  val currentOrder = currentPhase.order
  return rounds
    .map { it.phase }
    .distinct()
    .filter { it.order < currentOrder }
    .sortedByDescending { it.order }
    .map { phase ->
      val phaseQuestions = questions.filter { phaseForQuestion(it) == phase }
      PhaseSummary(
        phase = phase,
        resolved = phaseQuestions.count { it.isAnswered || it.isIgnored },
        total = phaseQuestions.size,
      )
    }
}
