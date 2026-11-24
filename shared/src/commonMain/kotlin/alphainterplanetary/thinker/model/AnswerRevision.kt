package alphainterplanetary.thinker.model

/** One answer snapshot in a question's revision history, newest-first in UI lists. */
data class AnswerRevision(
  val answer: Answer,
  /** True when this snapshot is the question's active snapshot in its current state. */
  val isCurrent: Boolean,
)
