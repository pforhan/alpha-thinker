package alphainterplanetary.thinker.engine

import alphainterplanetary.thinker.model.Project
import alphainterplanetary.thinker.model.Question

/**
 * The planning transcript: a project's questions rendered with their answer
 * state, plus the token budget that transcript has to fit inside.
 *
 * A planning engine can only produce a round that builds on what the user has
 * already written if the prompt carries it, so a [Question] is rendered whole —
 * `Q: … / A: …` for a committed answer, a `Draft:` line for typed-but-uncommitted
 * text, and a single word when there is nothing to show: [SkippedNote] for a
 * question the user ignored, `not yet answered` for a genuinely open one, and
 * `A: omitted` for one whose answer is settled but did not fit. Rendering the
 * state is what lets a model tell "asked and answered" from "asked and ignored",
 * which is the difference between a question to go deeper on and one to leave
 * alone — so a state never has to borrow the shape of another: an ignored
 * question is [SkippedNote] and nothing else, and an omitted answer keeps the
 * answer slot rather than reading as one the user never gave.
 *
 * This is the single transcript the app renders: the question prompt sends it
 * ([KoogPlanningEngine]), and the phase-grouped export is expected to build on
 * the same [line]s rather than write a second format.
 *
 * Edge models have small context windows and answers run long, so a transcript
 * is measured against a budget ([estimateTokens], ~1 token ≈ [CharsPerToken]
 * chars) and [trim]med down to fit before it is ever sent. Trimming drops
 * answers while keeping question text — the questions still have to be
   * recognized as asked — and does it phase by phase from the earliest, since the
   * project has already moved past those, never touching the current phase. What
   * it drops it marks [Question.compacted], so the transcript reads
   * [OmittedNote] rather than implying the user never gave an answer.
 *
 * Pure and stateless: callers hold the decision, and the engine stays
 * budget-ignorant — it renders whatever list it is handed.
 */
object PlanningContext {

  /** Chars per token for the estimate, the usual ~4:1 rule of thumb. */
  const val CharsPerToken: Int = 4

  /** The budgets offered in settings, in tokens. */
  val BudgetOptionsTokens: List<Int> = listOf(500, 1000, 2000, 4000, 8000)

  /**
   * A fresh install's budget: room for a long interview's worth of answers
   * while still leaving a small edge model's window room for the prompt around
   * them.
   */
  const val DefaultBudgetTokens: Int = 2000

  /** Marks a question the user chose to skip rather than answer. */
  const val SkippedNote: String = "skipped"

  /** Stands in for an answer this transcript had to leave out to fit the budget */
  const val OmittedNote: String = "omitted"

  /** Marks a question the user genuinely hasn't reached yet. */
  const val NotAnsweredNote: String = "not yet answered"

  /** The transcript for [questions], one line each; empty when there are none. */
  fun render(questions: List<Question>): String =
    questions.joinToString(separator = "\n", transform = ::line)

  /**
   * One question and whatever the user has done with it. The state always
   * follows the question, so a reader (or a model) can tell at a glance which
   * of the already-asked questions still need work.
   *
   * Ignored wins over everything, answer included: the user passed on the
   * question, so what they wrote for it is not part of the plan going forward,
   * and the transcript says so in one word.
   */
  fun line(question: Question): String {
    val answer = question.currentAnswer
    val state = when {
      question.isIgnored -> SkippedNote
      question.compacted -> "A: $OmittedNote"
      answer != null -> "A: ${answer.text}"
      question.isDraft -> "Draft: ${question.draftText}"
      else -> NotAnsweredNote
    }
    return "Q: ${question.text} / $state"
  }

  /**
   * The estimated token cost of [text]. An estimate, not a count: the heuristic
   * is length, which is what the budget is protecting against and is close
   * enough to keep a prompt inside a model's window.
   */
  fun estimateTokens(text: String): Int =
    (text.length + CharsPerToken - 1) / CharsPerToken

  /** The estimated token cost of a project's transcript, rendered fresh. */
  fun estimateTokens(questions: List<Question>): Int = estimateTokens(render(questions))

  /**
   * Result of [trim]: the questions to send as context, and how many answers it
   * had to compact away to fit the budget.
   */
  data class TrimResult(
    val questions: List<Question>,
    val droppedAnswers: Int = 0,
  )

  /**
   * The project's questions as a transcript that fits [budgetTokens]: the
   * project's own list when it already fits, otherwise the same questions with
   * some answers marked [Question.compacted] (see [line] for what survives).
   * Question text is never dropped — the engine has to see what was already
   * asked — so a project whose questions alone exceed the budget comes back
   * whole.
   *
   * Answers go in phase order, earliest first, and oldest question first within
   * a phase: a project works its way forward, so what it has moved past is what
   * a model can most afford to lose. The current phase is left alone for the
   * same reason — it is the phase being asked about, and its answers are the
   * ones a new round should build on. If that still overruns the budget the
   * transcript is returned as-is rather than gutting the live phase.
   *
   * Ignored questions are not candidates: [line] renders one as [SkippedNote]
   * whatever it holds, so their answers cost nothing here to begin with and
   * compacting them would report a loss that never reached the model.
   */
  fun trim(project: Project, budgetTokens: Int): TrimResult {
    val questions = project.questions
    if (estimateTokens(questions) <= budgetTokens) return TrimResult(questions, droppedAnswers = 0)

    val current = project.currentPhase
    val compactable = questions
      .filter { (it.isAnswered || it.isDraft) && !it.isIgnored && project.phaseForQuestion(it) != current }
      .sortedWith(compareBy({ project.phaseForQuestion(it).order }, { it.timestamp }))

    var trimmed = questions
    var dropped = 0
    for (question in compactable) {
      if (estimateTokens(trimmed) <= budgetTokens) break
      val next = trimmed.map { if (it.id == question.id) it.asCompacted() else it }
      if (next != trimmed) {
        dropped += 1
      }
      trimmed = next
    }
    return TrimResult(trimmed, droppedAnswers = dropped)
  }
}
