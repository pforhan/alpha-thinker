package alphainterplanetary.thinker.model

import kotlin.time.Instant

/**
 * A question and its answer state.
 *
 * All answer snapshots live in [answers] as immutable full-text rows (no
 * revision kind is stored on [Answer]). [answerState] is the single source of
 * truth for whether the question is unanswered, draft, or answered. When state
 * is [AnswerState.Draft] or [AnswerState.Answered], the active snapshot is the
 * latest row in [answers] by [Answer.createdAt]; [AnswerState.Unanswered] means
 * no active snapshot even if history exists.
 *
 * State transitions go through the [withAnswer], [withDraft], [withoutAnswer],
 * [asCompacted], [withIgnored], and [withoutIgnored] mutators so invariants
 * can't be broken by ad-hoc [copy] calls.
 */
data class Question(
  val id: String,
  val text: String,
  val timestamp: Instant,
  val roundId: String,
  val ignoredAt: Instant? = null,
  val answerState: AnswerState = AnswerState.Unanswered,
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
    require(answerState != AnswerState.Draft || latestAnswer != null) {
      "Question $id is Draft but has no answer snapshots"
    }
    require(answerState != AnswerState.Answered || latestAnswer != null) {
      "Question $id is Answered but has no answer snapshots"
    }
  }

  private val latestAnswer: Answer?
    get() = answers.maxWithOrNull(compareBy({ it.createdAt }, { it.id }))

  /** Committed answer id, or null when draft/unanswered. */
  val answerId: String?
    get() = if (answerState == AnswerState.Answered) latestAnswer?.id else null

  /** Current draft snapshot id, or null when not in draft state. */
  val draftAnswerId: String?
    get() = if (answerState == AnswerState.Draft) latestAnswer?.id else null

  val currentAnswer: Answer?
    get() = if (answerState == AnswerState.Answered) latestAnswer else null

  val currentDraft: Answer?
    get() = if (answerState == AnswerState.Draft) latestAnswer else null

  val draftText: String?
    get() = currentDraft?.text

  val draftUpdatedAt: Instant?
    get() = currentDraft?.createdAt

  val isDraft: Boolean
    get() = answerState == AnswerState.Draft

  val isAnswered: Boolean
    get() = answerState == AnswerState.Answered

  val isUnanswered: Boolean
    get() = !isAnswered && !isIgnored

  val isIgnored: Boolean
    get() = ignoredAt != null

  /** All answer snapshots newest-first, with a marker for the active snapshot. */
  fun revisionHistory(): List<AnswerRevision> {
    val currentId = when (answerState) {
      AnswerState.Answered, AnswerState.Draft -> latestAnswer?.id
      AnswerState.Unanswered -> null
    }
    return answers
      .sortedWith(compareByDescending<Answer> { it.createdAt }.thenByDescending { it.id })
      .map { answer -> AnswerRevision(answer = answer, isCurrent = answer.id == currentId) }
  }

  fun withAnswer(answer: Answer): Question = copy(
    answerState = AnswerState.Answered,
    answers = answers + answer,
  )

  fun withDraft(answer: Answer): Question = copy(
    answerState = AnswerState.Draft,
    answers = answers + answer,
  )

  fun withoutAnswer(): Question = copy(
    answerState = AnswerState.Unanswered,
  )

  /**
   * Marks this question's answer as compacted out of the transcript it is about
   * to be rendered into — so the reader can tell "the user never answered this"
   * from "there was an answer here and it did not fit". For use in rendering copy
   * only (see [compacted]); nothing persists the result.
   */
  fun asCompacted(): Question = copy(
    answerState = AnswerState.Unanswered,
    compacted = true,
  )

  fun withIgnored(at: Instant): Question = copy(ignoredAt = at)

  fun withoutIgnored(): Question = copy(ignoredAt = null)

  fun resetState(): Question = copy(
    answerState = AnswerState.Unanswered,
    ignoredAt = null,
    compacted = false,
  )
}
