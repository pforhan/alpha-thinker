package alphainterplanetary.thinker.model

import alphainterplanetary.thinker.phases.BuiltInPhase
import alphainterplanetary.thinker.phases.Phase
import kotlin.math.roundToInt
import kotlin.time.Instant

data class Project(
  val id: String,
  val synopsis: String,
  val editableTitle: String,
  val status: String,
  val questions: List<Question>,
  val rounds: List<Round> = emptyList(),
  val createdAt: Instant,
  val updatedAt: Instant,
) {
  companion object {
    /** How many next-phase options the empty state surfaces at most. */
    const val NextPhaseSuggestionLimit = 3
  }

  /** The round currently in progress — the newest round that hasn't been wrapped up. */
  val currentRound: Round?
    get() = rounds.filterNot { it.isCompleted }.maxByOrNull { it.roundNumber }

  /** The planning phase of the round currently in progress; the library's first phase if no round exists. */
  val currentPhase: Phase
    get() = currentRound?.phase ?: Phase.first

  /** The phase a question was asked in, resolved through its round; falls back to the current phase. */
  fun phaseForQuestion(question: Question): Phase =
    rounds.find { it.id == question.roundId }?.phase ?: currentPhase

  /**
   * Candidate "what's next?" phases to nudge the project toward once its
   * current round is wrapped up. The first suggestion is always the immediate
   * successor in the library ordering ([currentPhase] + 1); the following
   * slots fill with the phases the project hasn't visited yet, wherever they
   * fall in the ordering — so a phase that was skipped stays reachable. If
   * the current phase is the last one there is no successor, and the
   * suggestions are just the unvisited phases. This is the placeholder rule
   * while generator-proposed (text-scored) recommendations are pending — it
   * follows the library's ordering rather than the project's content.
   */
  val nextPhaseSuggestions: List<Phase>
    get() {
      val visited = rounds.map { it.phase }.toSet()
      val successor = BuiltInPhase.entries.firstOrNull { it.order == currentPhase.order + 1 }
      val remainingLimit = NextPhaseSuggestionLimit - (if (successor == null) 0 else 1)
      val remaining = BuiltInPhase.entries
        .filter { it != currentPhase && it !in visited && it != successor }
        .take(remainingLimit)
      return if (successor == null) remaining else listOf(successor) + remaining
    }

  /** Rounds belonging to the current planning phase (a phase can span several rounds). */
  val currentPhaseRounds: List<Round>
    get() = rounds.filter { it.phase == currentPhase }

  /** The newest round in the current phase — the one carrying its latest generation answer. */
  val currentPhaseLatestRound: Round?
    get() = currentPhaseRounds.maxByOrNull { it.roundNumber }

  /**
   * Whether the current phase has nothing more to give: its newest round
   * reported [RoundOutcome.Exhausted]. Derived from storage rather than cached
   * per session, so it survives restarts, and a phase opened afresh (advancing
   * on, or revisiting an exhausted one) resets it by starting a new round. A
   * [RoundOutcome.Failed] attempt is a dead end, not exhaustion — the phase
   * stays open so the user can retry.
   */
  val currentPhaseExhausted: Boolean
    get() = currentPhaseLatestRound?.outcome == RoundOutcome.Exhausted

  /** Why the current phase's newest attempt failed, when it did. */
  val currentPhaseFailure: String?
    get() = currentPhaseLatestRound
      ?.takeIf { it.outcome == RoundOutcome.Failed }
      ?.outcomeDetail

  /** Questions asked while the current planning phase was in progress. */
  val currentPhaseQuestions: List<Question>
    get() {
      val roundIds = currentPhaseRounds.map { it.id }.toSet()
      return questions.filter { it.roundId in roundIds }
    }

  /** Questions resolved (answered or ignored) in the current planning phase. */
  val currentPhaseResolvedCount: Int
    get() = currentPhaseQuestions.count { it.isAnswered || it.isIgnored }

  /** Total questions asked in the current planning phase. */
  val currentPhaseQuestionCount: Int
    get() = currentPhaseQuestions.size

  /** The current phase's completion as a whole percent, rounded to the nearest percent (0 if no questions). */
  val currentPhaseCompletionPercent: Int
    get() = if (currentPhaseQuestionCount == 0) 0
    else (currentPhaseResolvedCount * 100.0 / currentPhaseQuestionCount).roundToInt()

  val unansweredQuestions: List<Question>
    get() = questions.filter { it.isUnanswered }

  val activeQuestions: List<Question>
    get() = questions.filterNot { it.isIgnored }

  val allActiveQuestionsAnswered: Boolean
    get() = activeQuestions.isNotEmpty() &&
      activeQuestions.all { it.isAnswered }

  val questionOrderIds: List<String>
    get() = questions.map { it.id }

  fun moveToEnd(questionId: String): Project {
    val target = questions.find { it.id == questionId } ?: return this
    if (questions.lastOrNull()?.id == questionId) return this
    return copy(questions = questions.filterNot { it.id == questionId } + target)
  }

  fun rotateToEnd(questionIds: List<String>): Project {
    val ids = questionIds.toSet()
    val toMove = questions.filter { it.id in ids }
    if (toMove.isEmpty()) return this
    return copy(questions = questions.filterNot { it.id in ids } + toMove)
  }

  /**
   * A copy with [roundId] replaced by whatever [transform] makes of it — the
   * seam for latching a generation outcome onto a round without every caller
   * re-spelling the "map the list, match on id, leave the rest alone" loop.
   * `updatedAt` is the caller's to stamp, since only the caller knows whether
   * the write is part of a larger change to the aggregate.
   */
  fun withRoundOutcome(roundId: String, transform: (Round) -> Round): Project =
    copy(rounds = rounds.map { if (it.id == roundId) transform(it) else it })
}
