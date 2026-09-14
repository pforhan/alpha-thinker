package alphainterplanetary.thinker.model

import kotlin.time.Instant

data class Project(
  val id: String,
  val synopsis: String,
  val editableTitle: String,
  val status: String,
  val questions: List<Question>,
  val rounds: List<Round> = emptyList(),
  val createdAt: Instant,
  val updatedAt: Instant,
) {
  /** The round currently in progress — the newest round that hasn't been wrapped up. */
  val currentRound: Round?
    get() = rounds.filterNot { it.isCompleted }.maxByOrNull { it.roundNumber }
  val unansweredQuestions: List<Question>
    get() = questions.filter { it.isUnanswered }

  val activeQuestions: List<Question>
    get() = questions.filterNot { it.isIgnored }

  val allActiveQuestionsAnswered: Boolean
    get() = activeQuestions.isNotEmpty() &&
      activeQuestions.all { it.isAnswered }

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
