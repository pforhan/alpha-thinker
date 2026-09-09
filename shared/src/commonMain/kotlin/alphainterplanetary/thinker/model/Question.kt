package alphainterplanetary.thinker.model

import kotlin.time.Instant

/**
 * A question and its answer state.
 *
 * The committed/draft state lives on the question (not on the immutable
 * [Answer] history rows): [answerId] points at the current committed version,
 * and [draftText] holds in-progress text. A question is either committed or a
 * draft — never both (see the [init] guards).
 */
data class Question(
  val id: String,
  val text: String,
  val timestamp: Instant,
  val contextId: String,
  val ignoredAt: Instant? = null,
  val answerId: String? = null,
  val draftText: String? = null,
  val draftUpdatedAt: Instant? = null,
  val answers: List<Answer> = emptyList(),
) {
  init {
    require(answerId == null || draftText.isNullOrBlank()) {
      "Question $id has both a committed answer ($answerId) and draft text; " +
        "a question cannot be committed and unresolved at the same time"
    }
    require(draftText.isNullOrBlank() || draftUpdatedAt != null) {
      "Question $id has draft text but no draftUpdatedAt"
    }
    require(answerId == null || answers.any { it.id == answerId }) {
      "Question $id points at answerId=$answerId but no such answer is stored"
    }
  }

  val currentAnswer: Answer?
    get() = answerId?.let { id -> answers.find { it.id == id } }

  val isAnswered: Boolean
    get() = currentAnswer != null

  val isUnanswered: Boolean
    get() = !isAnswered && !isIgnored

  val isIgnored: Boolean
    get() = ignoredAt != null

  val isDraft: Boolean
    get() = !draftText.isNullOrBlank()
}