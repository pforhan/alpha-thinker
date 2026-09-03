package alphainterplanetary.thinker.ui.components

import alphainterplanetary.thinker.model.Question

enum class QuestionFilter(val displayName: String) {
  Unanswered("Unanswered"),
  Answered("Answered"),
  Ignored("Ignored");

  fun apply(questions: List<Question>): List<Question> {
    return when (this) {
      Unanswered -> questions.filter { it.isUnanswered }
      Answered -> questions.filter { it.isAnswered && !it.isIgnored }
      Ignored -> questions.filter { it.isIgnored }
    }
  }
}
