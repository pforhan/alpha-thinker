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
 * [asCompacted], [withIgnored], and [withoutIgnored] mutators so the
 * committed/draft invariants can't be broken by ad-hoc [copy] calls.
 */
data class Question(
  val id: String,
  val text: String,
  val timestamp: Instant,
  val roundId: String,
  val ignoredAt: Instant? = null,
  val answerId: String? = null,
  val draftText: String? = null,
  val draftUpdatedAt: Instant? = null,
  val answers: List<Answer> = emptyList(),
  /**
   * Set only on the throwaway copies a transcript builder makes when an answer
   * is too long to send (`PlanningContext.trim`): the answer existed, and was
   * deliberately left out of this one transcript, which renders it as
   * `A: omitted` rather than pretending the question is open.
   *
   * It is never persisted — no stored question is compacted, and the mapping
   * from storage leaves it false — so it costs no schema column, and a compacted
   * copy stays honestly un-answered ([isAnswered] false) with its [answers]
   * history intact.
   */
  val compacted: Boolean = false,
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

  /**
   * Marks this question's answer as compacted out of the transcript it is about
   * to be rendered into — so the reader can tell "the user never answered this"
   * from "there was an answer here and it did not fit". For use in rendering copy
   * only (see [compacted]); nothing persists the result.
   */
  fun asCompacted(): Question = copy(
    answerId = null,
    draftText = null,
    draftUpdatedAt = null,
    compacted = true,
  )

  fun withIgnored(at: Instant): Question = copy(ignoredAt = at)

  fun withoutIgnored(): Question = copy(ignoredAt = null)

  fun resetState(): Question = copy(
    answerId = null,
    draftText = null,
    draftUpdatedAt = null,
    ignoredAt = null,
    compacted = false,
  )
}