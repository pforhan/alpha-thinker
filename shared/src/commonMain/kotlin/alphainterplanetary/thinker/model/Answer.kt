package alphainterplanetary.thinker.model

import kotlin.time.Instant

/**
 * A committed answer version. Rows are immutable: each edit/save creates a new
 * version rather than mutating a previous one. [createdAt] is the commit time.
 */
data class Answer(
  val id: String = "",
  val questionId: String,
  val text: String,
  val createdAt: Instant,
)