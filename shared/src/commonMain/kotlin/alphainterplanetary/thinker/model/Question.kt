package alphainterplanetary.thinker.model

import kotlinx.datetime.Instant

data class Question(
  val id: String,
  val text: String,
  val timestamp: Instant,
  val contextId: String,
  val ignoredAt: Instant? = null,
  val answers: List<Answer> = emptyList(),
) {
  val isIgnored: Boolean
    get() = ignoredAt != null

  val currentAnswer: Answer?
    get() = answers.lastOrNull()?.takeIf { it.deletedAt == null }
}

