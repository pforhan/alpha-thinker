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
}
