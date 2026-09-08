package alphainterplanetary.thinker.model

import kotlin.time.Instant

data class Question(
  val id: String,
  val text: String,
  val timestamp: Instant,
  val contextId: String,
  val ignoredAt: Instant? = null,
  val answers: List<Answer> = emptyList(),
) {
  val isAnswered: Boolean
    get() = currentAnswer?.isComplete == true

  val isUnanswered: Boolean
    get() = !isAnswered && !isIgnored

  val isIgnored: Boolean
    get() = ignoredAt != null

  val currentAnswer: Answer?
    get() = answers
      .filterNot { it.deletedAt != null }
      .sortedWith(compareBy<Answer> { it.createdAt }.thenBy { it.id })
      .lastOrNull()
}

