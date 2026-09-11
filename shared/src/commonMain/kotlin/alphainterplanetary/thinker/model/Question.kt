package alphainterplanetary.thinker.model

import kotlin.time.Instant

/**
 * A question and its answer state.
 *
 * The committed/draft state lives on the question (not on the immutable
 * [Answer] history rows): [answerId] points at the current committed version,
 * and [draftText] holds in-progress text. A question is either committed or a
 * draft — never both (see the [init] guards).
 *
 * State transitions go through the [withAnswer], [withDraft], [withoutAnswer],
 * [withIgnored], and [withoutIgnored] mutators so the committed/draft
 * invariants can't be broken by ad-hoc [copy] calls.
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

  fun withAnswer(answer: Answer): Question = copy(
    answerId = answer.id,
    draftText = null,
    draftUpdatedAt = null,
    answers = answers + answer,
  )

  fun withDraft(text: String, updatedAt: Instant): Question = copy(
    answerId = null,
    draftText = text,
    draftUpdatedAt = updatedAt,
  )

  fun withoutAnswer(): Question = copy(
    answerId = null,
    draftText = null,
    draftUpdatedAt = null,
  )

  fun withIgnored(at: Instant): Question = copy(ignoredAt = at)

  fun withoutIgnored(): Question = copy(ignoredAt = null)

  fun resetState(): Question = copy(
    answerId = null,
    draftText = null,
    draftUpdatedAt = null,
    ignoredAt = null,
  )
}