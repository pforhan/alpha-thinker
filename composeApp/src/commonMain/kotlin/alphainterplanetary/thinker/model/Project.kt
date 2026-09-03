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

  val questionOrderIds: List<String>
    get() = questionsOrdered.map { it.id }

  fun withUniqueSortOrder(): Project {
    return copy(questions = questions.withUniqueSortOrder())
  }

  fun moveToEnd(questionId: String): Project {
    val ordered = questionsOrdered
    val target = ordered.find { it.id == questionId } ?: return this
    if (ordered.last().id == questionId) return this
    val moved = ordered.filterNot { it.id == questionId } + target
    return copy(questions = moved.withUniqueSortOrder())
  }

  fun rotateToEnd(questionIds: List<String>): Project {
    val ordered = questionsOrdered
    val ids = questionIds.toSet()
    val toMove = ordered.filter { it.id in ids }
    if (toMove.isEmpty()) return this
    val remaining = ordered.filter { it.id !in ids }
    return copy(questions = (remaining + toMove).withUniqueSortOrder())
  }

  private val questionsOrdered: List<Question>
    get() = questions.withUniqueSortOrder()
}

fun List<Question>.withUniqueSortOrder(): List<Question> {
  return sortedWith(compareBy({ it.sortOrder }, { it.timestamp }))
    .mapIndexed { idx, q -> q.copy(sortOrder = idx) }
}
