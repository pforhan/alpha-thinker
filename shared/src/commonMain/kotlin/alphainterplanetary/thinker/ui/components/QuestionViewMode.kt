package alphainterplanetary.thinker.ui.components

import alphainterplanetary.thinker.model.Question
import alphainterplanetary.thinker.phases.Phase
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.RotateLeft
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.ui.graphics.vector.ImageVector

enum class SwipeActionStyle {
  AskLater,
  Ignore,
  Delete,
  Unignore,
}

data class SwipeAction(
  val label: String,
  val icon: ImageVector,
  val style: SwipeActionStyle,
)

/** A slice of the question list grouped by the phase its questions were asked in. */
data class PhaseSection(
  val phase: Phase,
  val questions: List<Question>,
)

enum class QuestionViewMode(
  val displayName: String,
  val emptyMessage: String,
  val startAction: SwipeAction?,
  val endAction: SwipeAction?,
) {
  Unanswered(
    "Unanswered",
    "No unanswered questions.",
    startAction = SwipeAction(
      "Ask later",
      Icons.AutoMirrored.Filled.RotateLeft,
      SwipeActionStyle.AskLater
    ),
    endAction = SwipeAction("Ignore", Icons.Default.VisibilityOff, SwipeActionStyle.Ignore),
  ),
  Answered(
    "Answered",
    "No answered questions.",
    startAction = SwipeAction("Ignore", Icons.Default.VisibilityOff, SwipeActionStyle.Ignore),
    endAction = SwipeAction("Delete answer", Icons.Default.Delete, SwipeActionStyle.Delete),
  ),
  Draft(
    "Drafts",
    "No drafts.",
    startAction = SwipeAction("Ignore", Icons.Default.VisibilityOff, SwipeActionStyle.Ignore),
    endAction = SwipeAction("Delete answer", Icons.Default.Delete, SwipeActionStyle.Delete),
  ),
  Ignored(
    "Ignored",
    "No ignored questions.",
    startAction = SwipeAction("Unignore", Icons.Default.Visibility, SwipeActionStyle.Unignore),
    endAction = SwipeAction("Unignore", Icons.Default.Visibility, SwipeActionStyle.Unignore),
  );

  fun apply(questions: List<Question>): List<Question> {
    return when (this) {
      Unanswered -> questions.filter { it.isUnanswered }
      Answered -> questions
        .filter { it.isAnswered && !it.isIgnored }
        .sortedWith(answerDateComparator)

      Draft -> questions
        .filter { it.isDraft && !it.isIgnored }
        .sortedWith(answerDateComparator)

      Ignored -> questions
        .filter { it.isIgnored }
        .sortedWith(ignoredDateComparator)
    }
  }

  fun recommendedViews(questions: List<Question>): List<QuestionViewMode> {
    return entries
      .filter { it != this && it.apply(questions).isNotEmpty() }
      .sortedBy { if (it == Unanswered) 0 else 1 }
  }

  /**
   * The word a per-phase section header uses for its resolved count
   * ("Phase 2: Research — 5 answered"). Empty for views without headers.
   */
  val resolvedCountLabel: String
    get() = when (this) {
      Answered -> "answered"
      Ignored -> "ignored"
      else -> ""
    }

  /**
   * Groups the view's questions by the phase they were asked in, for the
   * phase-section headers shown in the Answered and Ignored lists.
   *
   * Sections come out most-recently-active first — the questions are already
   * sorted by date ([apply]) before grouping, so a phase's first occurrence
   * marks its most recent activity — while questions within each section keep
   * that date ordering. Returns an empty list for views without headers
   * ([Unanswered], [Draft]).
   */
  fun sections(
    questions: List<Question>,
    phaseFor: (Question) -> Phase,
  ): List<PhaseSection> {
    if (this != Answered && this != Ignored) return emptyList()
    val byPhase = LinkedHashMap<Phase, MutableList<Question>>()
    for (question in apply(questions)) {
      byPhase.getOrPut(phaseFor(question)) { mutableListOf() }.add(question)
    }
    return byPhase.map { (phase, phaseQuestions) -> PhaseSection(phase, phaseQuestions) }
  }

  companion object {
    val answerDateComparator: Comparator<Question> =
      compareByDescending { it.currentAnswer?.createdAt ?: it.draftUpdatedAt ?: it.timestamp }
    val ignoredDateComparator: Comparator<Question> =
      compareByDescending { it.ignoredAt }
  }
}