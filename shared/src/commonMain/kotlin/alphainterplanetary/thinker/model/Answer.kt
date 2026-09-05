package alphainterplanetary.thinker.model

import kotlinx.datetime.Instant

data class Answer(
  val id: Long = 0,
  val questionId: String,
  val text: String,
  val answeredAt: Instant?,
  val modifiedAt: Instant? = null,
  val deletedAt: Instant? = null,
) {
  val isComplete: Boolean
    get() = text.isNotBlank() && !isDraft

  val isDraft: Boolean
    get() = answeredAt == null
}