package alphainterplanetary.thinker.ui.screens

import alphainterplanetary.thinker.ProjectUpdateMode
import alphainterplanetary.thinker.di.AppComponent
import alphainterplanetary.thinker.model.PhaseStats
import alphainterplanetary.thinker.model.Project
import alphainterplanetary.thinker.model.Question
import alphainterplanetary.thinker.model.phaseStats
import alphainterplanetary.thinker.phases.Phase
import alphainterplanetary.thinker.tasks.TaskKind
import alphainterplanetary.thinker.ui.chrome.AppChromeState
import alphainterplanetary.thinker.ui.chrome.AppScaffold
import alphainterplanetary.thinker.ui.components.AnswerDialog
import alphainterplanetary.thinker.ui.components.AnswerDialogResult
import alphainterplanetary.thinker.ui.components.ContextCompactionDialog
import alphainterplanetary.thinker.ui.components.EditProjectDialog
import alphainterplanetary.thinker.ui.components.EmptyState
import alphainterplanetary.thinker.ui.components.PhaseAdvanceDialog
import alphainterplanetary.thinker.ui.components.PhaseBadgeCell
import alphainterplanetary.thinker.ui.components.PhasePill
import alphainterplanetary.thinker.ui.components.PhaseSectionHeader
import alphainterplanetary.thinker.ui.components.QuestionItem
import alphainterplanetary.thinker.ui.components.QuestionViewMode
import alphainterplanetary.thinker.ui.components.QuestionViewModeBar
import alphainterplanetary.thinker.ui.components.ScrollableOverflowText
import alphainterplanetary.thinker.ui.components.SpinnerLabel
import alphainterplanetary.thinker.ui.components.SwipeableCard
import alphainterplanetary.thinker.ui.components.UntitledProjectLabel
import alphainterplanetary.thinker.ui.components.activeTaskSummary
import alphainterplanetary.thinker.ui.theme.Dimens
import alphainterplanetary.thinker.ui.viewmodel.ProjectDetailUiState
import alphainterplanetary.thinker.ui.viewmodel.ProjectDetailViewModel
import alphainterplanetary.thinker.util.formatDuration
import alphainterplanetary.thinker.util.normalizeWhitespace
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.CoroutineScope
import kotlin.time.Clock

private const val SubtleCheckIntensity = 10

private const val SubtleCheckDurationMs = 1000

/** Scale the completed-phase badge pops in from, before its confetti burst. */
private const val CelebratedHeaderStartScale = 0.82f

private const val UnansweredBatchSize = 3

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectDetailScreen(
  appComponent: AppComponent,
  chrome: AppChromeState,
  projectId: String,
  onBack: () -> Unit,
) {
  val viewModel = remember {
    ProjectDetailViewModel(
      appComponent.projectRepository,
      appComponent.taskRunner,
      appComponent.appScope,
    )
  }

  LaunchedEffect(projectId) {
    viewModel.loadProject(projectId)
  }

  DisposableEffect(Unit) {
    onDispose { viewModel.close() }
  }

  val uiState by viewModel.uiState.collectAsState()
  val pendingUndo by viewModel.pendingUndo.collectAsState()
  val phaseSuggestions by viewModel.nextPhaseSuggestions.collectAsState()
  val contextPrompt by viewModel.contextPrompt.collectAsState()
  val tasks by viewModel.tasks.collectAsState()
  // Reconnects to tasks that are already in flight (or finished) when the
  // screen (re)enters composition — the VM's collector replays the current
  // list, so an extant task shows here even if it outlived a previous visit.
  val activeTasksLabel = activeTaskSummary(tasks)
  val titleGenerating = tasks.any { it.isActive && it.kind == TaskKind.TitleRecommendation }

  // The title has a retry affordance of its own because nothing else on this
  // screen could ask for one: the question list's "Get more questions" covers a
  // missing batch, but an untitled project has no list to retry from. Read off
  // the state already here — blank title, nothing in flight — rather than from a
  // derived failure, so it also covers a title that came back empty.
  val untitled = (uiState as? ProjectDetailUiState.Success)
    ?.project
    ?.editableTitle
    ?.isBlank() == true

  var selectedView by remember { mutableStateOf(QuestionViewMode.Unanswered) }
  var showEditDialog by remember { mutableStateOf(false) }
  var showPhaseAdvanceDialog by remember { mutableStateOf(false) }
  var selectedQuestion by remember { mutableStateOf<Question?>(null) }

  LaunchedEffect(pendingUndo) {
    val undo = pendingUndo ?: return@LaunchedEffect
    val result = chrome.showMessage(
      text = undo.message,
      actionLabel = "Undo",
      duration = SnackbarDuration.Long,
    )
    when (result) {
      SnackbarResult.ActionPerformed -> viewModel.undo(undo)
      SnackbarResult.Dismissed -> Unit
    }
  }

  AppScaffold(
    title = {
      val title = when (val ui = uiState) {
        ProjectDetailUiState.Loading -> "Loading..."
        is ProjectDetailUiState.Success -> {
          if (titleGenerating && ui.project.editableTitle.isBlank()) {
            "Generating title…"
          } else {
            ui.project.editableTitle.ifBlank { UntitledProjectLabel }
          }
        }
        is ProjectDetailUiState.Error -> "Error"
      }
      Text(
        text = title.normalizeWhitespace(),
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
    },
    chrome = chrome,
    onBack = onBack,
    actions = {
      // Only while the project has no title and nothing is producing one: once a
      // title lands (or a retry is running) the action has nothing to do.
      if (untitled && !titleGenerating) {
        IconButton(onClick = { viewModel.retryTitle(projectId) }) {
          Icon(Icons.Default.Refresh, contentDescription = "Suggest a title")
        }
      }
      IconButton(onClick = { showEditDialog = true }) {
        Icon(Icons.Default.Edit, contentDescription = "Edit Project")
      }
    },
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
        val now = remember { Clock.System.now() }
        val phaseStatsAll = remember(ui.project, now) { ui.project.phaseStats(now) }
        val phaseStatsCurrent = remember(phaseStatsAll, ui.project.currentPhase) {
          phaseStatsAll.firstOrNull { it.phase == ui.project.currentPhase }
        }
        ProjectDetailContent(
          project = ui.project,
          phaseStatsAll = phaseStatsAll,
          phaseStatsCurrent = phaseStatsCurrent,
          selectedView = selectedView,
          canGenerateMore = ui.canGenerateMoreInPhase,
          activeTaskLabel = activeTasksLabel,
          nextPhases = phaseSuggestions.orEmpty(),
          onViewSelected = { selectedView = it },
          onShuffle = { viewModel.shuffle() },
          onAskLater = { viewModel.askLater(it) },
          onIgnore = { viewModel.ignoreQuestion(projectId, it) },
          onUnignore = { viewModel.unignoreQuestion(projectId, it) },
          onAnswerClick = { selectedQuestion = it },
          onDeleteAnswer = { viewModel.saveAnswer(projectId, it.id, "", completed = false) },
          onGenerateMore = { viewModel.requestMoreQuestions(projectId) },
          onAdvancePhase = { viewModel.requestPhaseAdvance(projectId, it) },
          onBeginWrapUp = { showPhaseAdvanceDialog = true },
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

  if (showPhaseAdvanceDialog) {
    val state = uiState
    if (state is ProjectDetailUiState.Success) {
      val nowDialog = remember { Clock.System.now() }
      val dialogStats = remember(state.project, nowDialog) { state.project.phaseStats(nowDialog) }
      PhaseAdvanceDialog(
        phaseStats = dialogStats,
        completedPhase = state.project.currentPhase,
        suggestions = phaseSuggestions.orEmpty(),
        suggestionsLoading = phaseSuggestions == null,
        onAdvance = { viewModel.requestPhaseAdvance(projectId, it) },
        onDismiss = { showPhaseAdvanceDialog = false },
      )
    }
  }

  // The near-limit choice. Its state lives in the view model rather than here
  // because the request behind it is parked there too: answering the question
  // has to know whether it was "get more questions" or a wrap-up into a
  // particular phase, and that is not something a composable can recover.
  contextPrompt?.let { prompt ->
    ContextCompactionDialog(
      check = prompt.check,
      onChoose = { viewModel.resolveContextPrompt(it) },
      onDismiss = { viewModel.dismissContextPrompt() },
    )
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

@Composable
private fun ProjectDetailContent(
  project: Project,
  phaseStatsAll: List<PhaseStats>,
  phaseStatsCurrent: PhaseStats?,
  selectedView: QuestionViewMode,
  canGenerateMore: Boolean,
  /** Short label of the active generation task(s), e.g. "Generating title…"; null when idle. */
  activeTaskLabel: String?,
  nextPhases: List<Phase>,
  onViewSelected: (QuestionViewMode) -> Unit,
  onShuffle: () -> Unit,
  onAskLater: (String) -> Unit,
  onIgnore: (String) -> Unit,
  onUnignore: (String) -> Unit,
  onAnswerClick: (Question) -> Unit,
  onDeleteAnswer: (Question) -> Unit,
  onGenerateMore: () -> Unit,
  onAdvancePhase: (Phase) -> Unit,
  onBeginWrapUp: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var showPhaseOverview by remember { mutableStateOf(false) }
  val phaseSummaries = remember(project, phaseStatsAll) { project.priorPhaseStats(phaseStatsAll) }
  // Round → phase resolved once for the whole screen: the sections below and
  // every row's card tint read the same lookup rather than each scanning rounds.
  val phaseForQuestion = remember(project) { project.phaseLookup() }
  val unansweredCount = remember(project) { project.unansweredCount }

  Column(modifier = modifier) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = Dimens.SectionGap)
        .clickable(enabled = phaseSummaries.isNotEmpty()) { showPhaseOverview = true },
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(
          start = Dimens.ScreenPadding,
          end = Dimens.ScreenPadding,
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
      // One filter+sort pass per (project, view). Everything below reads off
      // these: the visible rows, the phase sections, and the shuffle row's
      // remaining count. `applied` stays whole because the sections group it;
      // only the unanswered view trims to the visible batch.
      val applied = remember(project, view) { view.apply(project.questions) }
      val visibleQuestions = remember(applied, view) {
        if (view == QuestionViewMode.Unanswered) applied.take(UnansweredBatchSize) else applied
      }
      val sections = remember(applied, view) {
        view.sections(applied, phaseForQuestion)
      }
      val recommendedViews = remember(project, view) {
        view.recommendedViews(project.questions)
      }

      val showShuffle = remember(view, unansweredCount, canGenerateMore) {
        view == QuestionViewMode.Unanswered && (unansweredCount > UnansweredBatchSize || canGenerateMore)
        // With a full batch (more than 3 unanswered) shuffle rotates the
        // visible cards; when the batch is exhausted (<= 3 unanswered) the
        // same affordance becomes "synthesize a fresh batch" via the
        // follow-up generation task when the pool still has questions.
      }

      Box(modifier = Modifier.fillMaxSize()) {
        if (visibleQuestions.isEmpty()) {
          QuestionEmptyState(
            title = view.emptyMessage,
            recommendedViews = recommendedViews,
            onViewSelected = onViewSelected,
            onGenerateMore = if (view == QuestionViewMode.Unanswered) onGenerateMore else null,
            canGenerateMore = view == QuestionViewMode.Unanswered && canGenerateMore,
            generatingLabel = activeTaskLabel.takeIf { view == QuestionViewMode.Unanswered },
            nextPhases = if (view == QuestionViewMode.Unanswered) nextPhases else emptyList(),
            onBeginWrapUp = if (view == QuestionViewMode.Unanswered) onBeginWrapUp else null,
            completedStats = phaseStatsCurrent,
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
                    phaseForQuestion = phaseForQuestion,
                  )
                }
              }
            } else {
              items(visibleQuestions, key = { it.id }) { question ->
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
                  phaseForQuestion = phaseForQuestion,
                )
              }
            }
            if (showShuffle) {
              val shuffleGenerates = unansweredCount <= UnansweredBatchSize
              item {
                ShuffleRow(
                  remainingCount = if (shuffleGenerates) null else unansweredCount - visibleQuestions.size,
                  generatingLabel = activeTaskLabel,
                  generateFresh = shuffleGenerates,
                  onClick = if (shuffleGenerates) onGenerateMore else onShuffle,
                )
              }
            }
          }
        }
      }
    }
  }
}

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
  phaseForQuestion: (Question) -> Phase,
) {
  val dismissState = rememberSwipeToDismissBoxState()
  val phase = phaseForQuestion(question)
  SwipeableCard(
    state = dismissState,
    startAction = view.startAction,
    endAction = view.endAction,
    resetScope = dismissScope,
    onSwipeStart = {
      when (view) {
        QuestionViewMode.Unanswered -> onAskLater(question.id)
        QuestionViewMode.Answered,
        QuestionViewMode.Draft,
          -> onIgnore(question.id)

        QuestionViewMode.Ignored -> onUnignore(question.id)
      }
    },
    onSwipeEnd = {
      when (view) {
        QuestionViewMode.Unanswered -> onIgnore(question.id)
        QuestionViewMode.Answered,
        QuestionViewMode.Draft,
          -> onDeleteAnswer(question)

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
  canGenerateMore: Boolean,
  /** Non-null while a generation task is in flight; replaces the button. */
  generatingLabel: String?,
  nextPhases: List<Phase>,
  onBeginWrapUp: (() -> Unit)?,
  completedStats: PhaseStats?,
  modifier: Modifier = Modifier,
) {
  Box(
    modifier = modifier,
    contentAlignment = Alignment.Center,
  ) {
    Column(
      modifier = Modifier
        .verticalScroll(rememberScrollState())
        .padding(Dimens.EmptyStatePadding),
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
        if (generatingLabel != null) {
          SpinnerLabel(text = generatingLabel)
        } else if (canGenerateMore) {
          Button(onClick = onGenerateMore) {
            Text("Get more questions")
          }
        }
      }
      if (onBeginWrapUp != null && nextPhases.isNotEmpty()) {
        Spacer(modifier = Modifier.height(Dimens.SectionGap))
        Surface(
          shape = MaterialTheme.shapes.large,
          color = MaterialTheme.colorScheme.secondaryContainer,
        ) {
          Column(
            modifier = Modifier.padding(
              horizontal = Dimens.ScreenPadding,
              vertical = Dimens.ScreenPadding,
            ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Dimens.ContentGap, Alignment.CenterVertically),
          ) {
            if (completedStats != null) {
              CelebratedPhaseHeader(stats = completedStats)
              Text(
                text = "Answered ${completedStats.answered} of ${completedStats.total} over ${formatDuration(completedStats.spent)}",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            Button(onClick = onBeginWrapUp) {
              Text("Choose the next phase (${nextPhases.size} options)")
            }
          }
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
}

@Composable
private fun CelebratedPhaseHeader(stats: PhaseStats) {
  val scale = remember { Animatable(CelebratedHeaderStartScale) }
  var showBurst by remember { mutableStateOf(false) }

  LaunchedEffect(Unit) {
    scale.animateTo(
      targetValue = 1f,
      animationSpec = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMedium / 2f,
      ),
    )
    showBurst = true
  }

  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    PhaseBadgeCell(
      phase = stats.phase,
      burstVisible = showBurst,
      intensity = SubtleCheckIntensity,
      durationMs = SubtleCheckDurationMs,
      cellWidth = Dimens.ConfettiBurstWidth,
      modifier = Modifier.graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
        alpha = scale.value.coerceAtMost(1f)
      },
    )
    Text(
      text = "${stats.phase.label} complete",
      textAlign = TextAlign.Center,
      style = MaterialTheme.typography.titleMedium,
      color = MaterialTheme.colorScheme.onSurface,
    )
  }
}

@Composable
private fun ProjectDetailError(
  message: String,
  onRetry: () -> Unit,
  modifier: Modifier = Modifier,
) {
  EmptyState(
    message = message,
    actionLabel = "Retry",
    onAction = onRetry,
    modifier = modifier,
  )
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
  remainingCount: Int?,
  /** Non-null while a generation task is in flight: the row becomes the spinner-and-label. */
  generatingLabel: String?,
  generateFresh: Boolean,
  onClick: () -> Unit,
) {
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = Dimens.ScreenPadding)
      .clip(MaterialTheme.shapes.medium)
      .clickable(enabled = generatingLabel == null, onClick = onClick),
    contentAlignment = Alignment.Center,
  ) {
    Row(
      modifier = Modifier.padding(vertical = Dimens.ActionRowVerticalPadding),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.Center,
    ) {
      if (generatingLabel != null) {
        SpinnerLabel(text = generatingLabel)
      } else {
        Icon(
          Icons.Default.Shuffle,
          contentDescription = null,
          modifier = Modifier.size(Dimens.IconSizeMedium),
          tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.width(Dimens.IconLabelGap))
        Text(
          text = if (generateFresh) "Get fresh questions" else "Shuffle questions",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.primary,
        )
        if (!generateFresh && remainingCount != null && remainingCount > 0) {
          Text(
            text = " · $remainingCount more available",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }
  }
}

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
 * Stats for the phases the project has already entered, newest first, for the
 * current phase's overview popup. Only visited phases (those with a round) and
 * only phases before the current one are included — future phases never
 * appear. Counts mirror the current-phase summary line (resolved = answered or
 * ignored).
 */
private fun Project.priorPhaseStats(allStats: List<PhaseStats>): List<PhaseStats> {
  val currentOrder = currentPhase.order
  return allStats
    .filter { it.phase.order < currentOrder }
    .sortedByDescending { it.phase.order }
}
