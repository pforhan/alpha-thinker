package alphainterplanetary.thinker.ui.components

import alphainterplanetary.thinker.model.Question

enum class QuestionViewMode(val displayName: String, val emptyMessage: String) {
  Unanswered("Unanswered", "No unanswered questions."),
  Answered("Answered", "No answered questions."),
  Draft("Drafts", "No drafts."),
  Ignored("Ignored", "No ignored questions.");

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