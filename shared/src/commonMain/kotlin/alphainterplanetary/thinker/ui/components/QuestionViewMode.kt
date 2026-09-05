package alphainterplanetary.thinker.ui.components

import alphainterplanetary.thinker.model.Question

enum class QuestionViewMode(val displayName: String) {
  Unanswered("Unanswered"),
  Answered("Answered"),
  Draft("Drafts"),
  Ignored("Ignored");

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

  companion object {
    val answerDateComparator: Comparator<Question> =
      compareByDescending { it.currentAnswer?.modifiedAt ?: it.currentAnswer?.answeredAt }
    val ignoredDateComparator: Comparator<Question> =
      compareByDescending { it.ignoredAt }
  }
}