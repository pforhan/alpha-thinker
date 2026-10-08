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

  /** Whether [question] belongs in this view, before any ordering. */
  fun includes(question: Question): Boolean = when (this) {
    Unanswered -> question.isUnanswered
    Answered -> question.isAnswered && !question.isIgnored
    Draft -> question.isDraft && !question.isIgnored
    Ignored -> question.isIgnored
  }

  /**
   * The order this view's questions render in, or null to keep the project's
   * own order — [Unanswered] does, since its list is the persisted shuffled
   * order the 3-card rotation reads from.
   */
  private val ordering: Comparator<Question>?
    get() = when (this) {
      Unanswered -> null
      Answered, Draft -> answerDateComparator
      Ignored -> ignoredDateComparator
    }

  fun apply(questions: List<Question>): List<Question> {
    val included = questions.filter { includes(it) }
    return ordering?.let { included.sortedWith(it) } ?: included
  }

  /**
   * The other views that have questions to show, [Unanswered] first — what an
   * empty list recommends switching to. Answers "does this view have
   * anything?" via [includes] rather than by building and sorting each view's
   * full list, so the ordering in [apply] is irrelevant here.
   */
  fun recommendedViews(questions: List<Question>): List<QuestionViewMode> {
    return entries
      .filter { it != this && questions.any(it::includes) }
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
   * Groups a view's questions by the phase they were asked in, for the
   * phase-section headers shown in the Answered and Ignored lists.
   *
   * [questions] must be this view's own [apply] output — already filtered and
   * in render order — so callers that have it don't pay for a second pass;
   * [sections] neither re-filters nor re-sorts. That ordering is load-bearing:
   * sections come out most-recently-active first, because a phase's first
   * occurrence in a date-ordered list marks its most recent activity, while
   * questions within a section keep that order.
   *
   * Returns an empty list for views without headers ([Unanswered], [Draft]).
   */
  fun sections(
    questions: List<Question>,
    phaseFor: (Question) -> Phase,
  ): List<PhaseSection> {
    if (this != Answered && this != Ignored) return emptyList()
    val byPhase = LinkedHashMap<Phase, MutableList<Question>>()
    for (question in questions) {
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