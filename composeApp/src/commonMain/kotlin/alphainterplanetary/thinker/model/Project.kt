package alphainterplanetary.thinker.model

import kotlinx.datetime.Instant

data class Project(
  val id: String,
  val synopsis: String,
  val editableTitle: String,
  val status: String,
  val questions: List<Question>,
  val createdAt: Instant,
  val updatedAt: Instant,
) {
  val unansweredQuestions: List<Question>
    get() = questions.filter { it.isUnanswered }

  val activeQuestions: List<Question>
    get() = questions.filterNot { it.isIgnored }

  val allActiveQuestionsAnswered: Boolean
    get() = activeQuestions.isNotEmpty() &&
      activeQuestions.all { it.currentAnswer?.isComplete == true }

  val questionOrderIds: List<String>
    get() = questions.map { it.id }

  fun moveToEnd(questionId: String): Project {
    val target = questions.find { it.id == questionId } ?: return this
    if (questions.lastOrNull()?.id == questionId) return this
    return copy(questions = questions.filterNot { it.id == questionId } + target)
  }

  fun rotateToEnd(questionIds: List<String>): Project {
    val ids = questionIds.toSet()
    val toMove = questions.filter { it.id in ids }
    if (toMove.isEmpty()) return this
    return copy(questions = questions.filterNot { it.id in ids } + toMove)
  }
}
