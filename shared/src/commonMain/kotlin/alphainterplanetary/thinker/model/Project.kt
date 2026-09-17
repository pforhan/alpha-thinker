package alphainterplanetary.thinker.model

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
  /** The round currently in progress — the newest round that hasn't been wrapped up. */
  val currentRound: Round?
    get() = rounds.filterNot { it.isCompleted }.maxByOrNull { it.roundNumber }

  /** The planning phase of the round currently in progress; the library's first phase if no round exists. */
  val currentPhase: Phase
    get() = currentRound?.phase ?: Phase.first

  /** The phase a question was asked in, resolved through its round; falls back to the current phase. */
  fun phaseForQuestion(question: Question): Phase =
    rounds.find { it.id == question.roundId }?.phase ?: currentPhase

  /** Rounds belonging to the current planning phase (a phase can span several rounds). */
  val currentPhaseRounds: List<Round>
    get() = rounds.filter { it.phase == currentPhase }

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
}
