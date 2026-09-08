package alphainterplanetary.thinker.ui.components

import alphainterplanetary.thinker.model.Question
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.RotateLeft
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

data class SwipeAction(
  val label: String,
  val icon: ImageVector,
  val background: Color,
)

private val AskLaterColor = Color(0xFF2962FF)
private val IgnoreColor = Color(0xFF757575)
private val DeleteAnswerColor = Color(0xFFD32F2F)
private val UnignoreColor = Color(0xFF2E7D32)

enum class QuestionViewMode(
  val displayName: String,
  val emptyMessage: String,
  val startAction: SwipeAction?,
  val endAction: SwipeAction?,
) {
  Unanswered(
    "Unanswered",
    "No unanswered questions.",
    startAction = SwipeAction("Ask later", Icons.AutoMirrored.Filled.RotateLeft, AskLaterColor),
    endAction = SwipeAction("Ignore", Icons.Default.VisibilityOff, IgnoreColor),
  ),
  Answered(
    "Answered",
    "No answered questions.",
    startAction = SwipeAction("Ignore", Icons.Default.VisibilityOff, IgnoreColor),
    endAction = SwipeAction("Delete answer", Icons.Default.Delete, DeleteAnswerColor),
  ),
  Draft(
    "Drafts",
    "No drafts.",
    startAction = SwipeAction("Ignore", Icons.Default.VisibilityOff, IgnoreColor),
    endAction = SwipeAction("Delete answer", Icons.Default.Delete, DeleteAnswerColor),
  ),
  Ignored(
    "Ignored",
    "No ignored questions.",
    startAction = SwipeAction("Unignore", Icons.Default.Visibility, UnignoreColor),
    endAction = SwipeAction("Unignore", Icons.Default.Visibility, UnignoreColor),
  );

  fun apply(questions: List<Question>): List<Question> {
    return when (this) {
      Unanswered -> questions.filter { it.isUnanswered }
      Answered -> questions
        .filter { it.isAnswered && !it.isIgnored }
        .sortedWith(answerDateComparator)

      Draft -> questions
        .filter { it.currentAnswer?.isDraft == true }
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
      compareByDescending { it.currentAnswer?.modifiedAt ?: it.currentAnswer?.answeredAt }
    val ignoredDateComparator: Comparator<Question> =
      compareByDescending { it.ignoredAt }
  }
}