package alphainterplanetary.thinker.ui.components

import alphainterplanetary.thinker.model.Question
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

enum class QuestionViewMode(
  val displayName: String,
  val emptyMessage: String,
  val startAction: SwipeAction?,
  val endAction: SwipeAction?,
) {
  Unanswered(
    "Unanswered",
    "No unanswered questions.",
    startAction = SwipeAction("Ask later", Icons.AutoMirrored.Filled.RotateLeft, SwipeActionStyle.AskLater),
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
        .filter { it.isDraft }
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

  companion object {
    val answerDateComparator: Comparator<Question> =
      compareByDescending { it.currentAnswer?.createdAt ?: it.draftUpdatedAt ?: it.timestamp }
    val ignoredDateComparator: Comparator<Question> =
      compareByDescending { it.ignoredAt }
  }
}