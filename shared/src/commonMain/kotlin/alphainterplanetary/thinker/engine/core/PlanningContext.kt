package alphainterplanetary.thinker.engine.core

import alphainterplanetary.thinker.model.Project
import alphainterplanetary.thinker.model.Question
import alphainterplanetary.thinker.util.TokenEstimator

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
 * The question prompt sends this transcript; the
 * human-facing export (rebuilt via `ProjectExportRenderer` /
 * `ProjectRepository.exportProject`) shares its state semantics and phase
 * grouping rather than re-deriving them, though the export renders a detailed
 * document rather than these terse [line]s.
 *
 * Edge models have small context windows and answers run long, so a transcript
 * is measured against a budget ([estimateTokens], ~1 token ≈ [TokenEstimator.CHARS_PER_TOKEN]
 * chars) and [trim]med down to fit before it is ever sent. Trimming drops
 * answers while keeping question text — the questions still have to be
 * recognized as asked — and does it phase by phase from the earliest, since the
 * project has already moved past those, never touching the current phase. What
 * it drops it marks [Question.compacted], so the transcript reads
 * [OmittedNote] rather than implying the user never gave an answer.
 *
 * Dropping is lossy, so a project that has grown enough to need it can do
 * better: [PhaseSummarization.compactionTranscripts] cuts the project into whole
 * past phases, and a [SummarizedPhase] written by the model for one of them
 * rides ahead of the transcript in its place. The covered questions keep their
 * `Q:` line and read [SummarizedNote] for the answer, so the reader still sees
 * what was asked and what it was told — through the model, rather than verbatim.
 *
 * How much of this a user sees is a decision, not a default: [nearLimit] says
 * when a project's transcript has outgrown the budget (the user may keep
 * everything, have earlier answers dropped, or have a phase summarized —
 * `ContextCompaction` in the repository layer holds that choice and hands it
 * here). The budget itself is [budgetTokens] — [TranscriptSharePercent] of the
 * selected model's own context window, read from the model rather than
 * configured here, and nothing at all for an engine that composes no prompt.
 *
 * Pure and stateless: callers hold the decision, and the engine stays
 * budget-ignorant — it renders whatever list it is handed.
 */
object PlanningContext {
  /**
   * The share of a model's context window the transcript may fill; the rest is
   * the prompt built around it — system prompt, title, synopsis, phase — plus
   * the room the model needs to answer at all. A fraction rather than a fixed
   * token reserve because the two costs both scale with the window, and a
   * reserve sized for a 4k edge model would be a rounding error on a hosted
   * 200k one.
   *
   * Not a setting, for the same reason: the window is the model's, and a user
   * picking a percentage of someone else's context window is a control that
   * looks meaningful and mostly isn't. This is the same kind of constant as
   * [TokenEstimator.CHARS_PER_TOKEN] — an estimate standing in for a thing we
   * can't measure.
   */
  const val TranscriptSharePercent: Int = 90

  /**
   * The token budget for a [contextWindowTokens]-sized window: what the
   * interview is allowed to occupy, and the point at which the user is asked
   * what to do about the overflow.
   */
  fun budgetTokens(contextWindowTokens: Int): Int =
    (contextWindowTokens * TranscriptSharePercent).coerceAtLeast(0) / 100

  /** Marks a question the user chose to skip rather than answer. */
  const val SkippedNote: String = "skipped"

  /** Stands in for an answer this transcript had to leave out to fit the budget */
  const val OmittedNote: String = "omitted"

  /** Stands in for an answer a [SummarizedPhase] now carries on the phase's behalf. */
  const val SummarizedNote: String = "summarized below"

  /** Marks a question the user genuinely hasn't reached yet. */
  const val NotAnsweredNote: String = "not yet answered"

  /** Heads the summaries block, so a model can tell it from the transcript. */
  const val SummariesHeading: String = "Summaries of earlier phases:"

  /**
   * The transcript for [questions], one line each, preceded by any [summaries]
   * standing in for answers that no longer fit; empty when there is nothing to
   * say. A covered question keeps its text and reads [SummarizedNote] in the
   * answer slot — dropping the line would hide what was already asked, which
   * is the one thing the transcript exists to convey.
   */
  fun render(questions: List<Question>, summaries: List<SummarizedPhase> = emptyList()): String {
    val spoken = summaries.filter { it.summary.isNotBlank() }
    val covered = spoken.flatMapTo(mutableSetOf()) { it.coversQuestionIds }
    val lines = questions
      .joinToString(separator = "\n") { question ->
        line(
          question,
          coveredBySummary = question.id in covered
        )
      }
    val summarized = spoken
      .joinToString(separator = "\n") { "${it.phase.label}: ${it.summary}" }
      .ifEmpty { null }
    val blocks = listOfNotNull(
      summarized?.let { "$SummariesHeading\n$it" },
      lines.ifEmpty { null },
    )
    return blocks.joinToString(separator = "\n\n")
  }

  /**
   * One question and whatever the user has done with it. The state always
   * follows the question, so a reader (or a model) can tell at a glance which
   * of the already-asked questions still need work.
   *
   * Ignored wins over everything, answer included: the user passed on the
   * question, so what they wrote for it is not part of the plan going forward,
   * and the transcript says so in one word. [coveredBySummary] is what a
   * [SummarizedPhase] covering this question renders instead of an answer.
   */
  fun line(question: Question, coveredBySummary: Boolean = false): String {
    val answer = question.currentAnswer
    val state = when {
      question.isIgnored -> SkippedNote
      coveredBySummary -> "A: $SummarizedNote"
      question.compacted -> "A: $OmittedNote"
      answer != null -> "A: ${answer.text}"
      question.isDraft -> "Draft: ${question.draftText}"
      else -> NotAnsweredNote
    }
    return "Q: ${question.text} / $state"
  }

  /** The estimated token cost of a project's transcript, rendered fresh. */
  fun estimateTokens(
    questions: List<Question>,
    summaries: List<SummarizedPhase> = emptyList(),
  ): Int = TokenEstimator.estimateTokens(render(questions, summaries))

  /**
   * Whether a transcript this big is worth putting to the user before the next
   * generation, rather than letting [trim] decide on its own.
   *
   * One threshold now: the budget. There is no separately-tunable band below it
   * to be early about anything, because a transcript comfortably inside the
   * budget has nothing to decide — it costs what it costs, and every question
   * about it has the same answer. At the budget something has to give, and which
   * is the user's call.
   */
  fun nearLimit(estimatedTokens: Int, budgetTokens: Int): Boolean =
    estimatedTokens > budgetTokens

  /**
   * Result of [trim]: the questions to send as context, the [SummarizedPhase]s
   * riding with them, and how many answers it had to compact away to fit the
   * budget.
   */
  data class TrimResult(
    val questions: List<Question>,
    val droppedAnswers: Int = 0,
    val summaries: List<SummarizedPhase> = emptyList(),
  )

  /**
   * The project's questions as a transcript that fits [budgetTokens]: the
   * project's own list when it already fits, otherwise the same questions with
   * some answers marked [Question.compacted] (see [line] for what survives).
   * Question text is never dropped — the engine has to see what was already
   * asked — so a project whose questions alone exceed the budget comes back
   * whole. Any [summaries] written ahead of the call are carried through
   * untouched and counted in the estimate, so a phase the model already
   * summarized is not spent twice.
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
   * compacting them would report a loss that never reached the model. Neither
   * are questions a [SummarizedPhase] already covers: their answers are gone from
   * the prompt in favor of the summary, so "dropping" one again would compact a
   * question the model can already read.
   */
  fun trim(
    project: Project,
    budgetTokens: Int,
    summaries: List<SummarizedPhase> = emptyList(),
  ): TrimResult {
    val questions = project.questions
    if (estimateTokens(questions, summaries) <= budgetTokens) {
      return TrimResult(questions, droppedAnswers = 0, summaries = summaries)
    }

    val current = project.currentPhase
    val summarized = summaries.flatMapTo(mutableSetOf()) { it.coversQuestionIds }
    val compactable = questions
      .filter {
        (it.isAnswered || it.isDraft) &&
          !it.isIgnored &&
          it.id !in summarized &&
          project.phaseForQuestion(it) != current
      }
      .sortedWith(compareBy({ project.phaseForQuestion(it).order }, { it.timestamp }))

    var trimmed = questions
    var dropped = 0
    for ((id) in compactable) {
      if (estimateTokens(trimmed, summaries) <= budgetTokens) break
      val next = trimmed.map { if (it.id == id) it.asCompacted() else it }
      if (next != trimmed) {
        dropped += 1
      }
      trimmed = next
    }
    return TrimResult(trimmed, droppedAnswers = dropped, summaries = summaries)
  }
}
