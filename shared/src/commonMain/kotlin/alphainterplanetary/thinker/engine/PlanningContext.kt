package alphainterplanetary.thinker.engine

import alphainterplanetary.thinker.model.Project
import alphainterplanetary.thinker.model.Question
import alphainterplanetary.thinker.phases.Phase

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
 * Dropping is lossy, so a project that has grown enough to need it can do
 * better: [summarizablePhases] cuts the project into whole past phases, and a
 * [PhaseSummary] written by the model for one of them rides ahead of the
 * transcript in its place. The covered questions keep their `Q:` line and read
 * [SummarizedNote] for the answer, so the reader still sees what was asked and
 * what it was told — through the model, rather than verbatim.
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

  /** Chars per token for the estimate, the usual ~4:1 rule of thumb. */
  const val CharsPerToken: Int = 4

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
   * [CharsPerToken] — an estimate standing in for a thing we can't measure.
   */
  const val TranscriptSharePercent: Int = 90

  /**
   * The token budget for a [contextWindowTokens]-sized window: what the
   * interview is allowed to occupy, and the point at which the user is asked
   * what to do about the overflow.
   *
   * There is one number because there is one thing to respect. An earlier shape
   * offered the ceiling as a share of the window and a second, nested band
   * below it for when to ask; with the ceiling pinned at
   * [TranscriptSharePercent] the band was the ceiling, so the setting the user
   * was asked to tune was a dial on a number that does not change.
   */
  fun budgetTokens(contextWindowTokens: Int): Int =
    (contextWindowTokens * TranscriptSharePercent).coerceAtLeast(0) / 100

  /** [budgetTokens] as a display string, e.g. `2048` → `2k`. */
  fun formatTokens(tokens: Int): String =
    if (tokens >= 1000) "${(tokens / 100) / 10.0}k".removeSuffix(".0k") else "$tokens"

  /** Marks a question the user chose to skip rather than answer. */
  const val SkippedNote: String = "skipped"

  /** Stands in for an answer this transcript had to leave out to fit the budget */
  const val OmittedNote: String = "omitted"

  /** Stands in for an answer a [PhaseSummary] now carries on the phase's behalf. */
  const val SummarizedNote: String = "summarized below"

  /** Marks a question the user genuinely hasn't reached yet. */
  const val NotAnsweredNote: String = "not yet answered"

  /** Heads the summaries block, so a model can tell it from the transcript. */
  const val SummariesHeading: String = "Summaries of earlier phases:"

  /**
   * One phase's answers replaced by a model-written [summary], for a transcript
   * that no longer fits them verbatim. [coversQuestionIds] names the questions
   * whose answers the summary stands in for — the transcript keeps their `Q:`
   * lines and reads [SummarizedNote] for the answer, so nothing looks
   * unanswered. The ids are carried rather than the phase because the engine
   * that renders a transcript is handed a flat list of questions with no round
   * or phase to resolve, and an id is the one thing that survives that.
   */
  data class PhaseSummary(
    val phase: Phase,
    val summary: String,
    val coversQuestionIds: Set<String> = emptySet(),
  )

  /**
   * One past phase's answers, as the unit a [PhaseSummary] is written from:
   * the phase, its answered/draft questions, and the rendered transcript to
   * hand the model. Whole phases rather than individual answers, because a
   * summary of three unrelated answers answers no question — and a phase is
   * the smallest thing the planner actually worked through.
   */
  data class PhaseTranscript(
    val phase: Phase,
    val questions: List<Question>,
    val transcript: String,
    val estimatedTokens: Int,
  ) {
    /** The questions a summary of this phase would cover. */
    val coveredQuestionIds: Set<String>
      get() = questions.mapTo(mutableSetOf()) { it.id }
  }

  /**
   * The transcript for [questions], one line each, preceded by any [summaries]
   * standing in for answers that no longer fit; empty when there is nothing to
   * say. A covered question keeps its text and reads [SummarizedNote] in the
   * answer slot — dropping the line would hide what was already asked, which
   * is the one thing the transcript exists to convey.
   */
  fun render(questions: List<Question>, summaries: List<PhaseSummary> = emptyList()): String {
    val spoken = summaries.filter { it.summary.isNotBlank() }
    val covered = spoken.flatMapTo(mutableSetOf()) { it.coversQuestionIds }
    val lines = questions
      .joinToString(separator = "\n") { question -> line(question, summarized = question.id in covered) }
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
   * and the transcript says so in one word. [summarized] is what a
   * [PhaseSummary] covering this question renders instead of an answer.
   */
  fun line(question: Question, summarized: Boolean = false): String {
    val answer = question.currentAnswer
    val state = when {
      question.isIgnored -> SkippedNote
      summarized -> "A: $SummarizedNote"
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
  fun estimateTokens(questions: List<Question>, summaries: List<PhaseSummary> = emptyList()): Int =
    estimateTokens(render(questions, summaries))

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
   * The project's past phases, oldest first, as whole units a model could
   * summarize in place of their answers.
   *
   * The current phase is excluded: it is the phase being asked about, and its
   * answers are the ones the next round has to build on. A phase with nothing
   * but ignored and unanswered questions is excluded too — there is nothing to
   * summarize, and a summary of an empty phase would read as an absence rather
   * than a finding. Ordering is the phase library's own ([Phase.order]), which
   * is the order the project worked through them in, so the oldest is the one
   * the model can most afford to lose first.
   */
  fun summarizablePhases(project: Project): List<PhaseTranscript> {
    val current = project.currentPhase
    return project.questions
      .filter { (it.isAnswered || it.isDraft) && !it.isIgnored }
      .groupBy { project.phaseForQuestion(it) }
      .filterKeys { it != current }
      .toList()
      .sortedBy { (phase, _) -> phase.order }
      .map { (phase, questions) ->
        val transcript = render(questions)
        PhaseTranscript(
          phase = phase,
          questions = questions,
          transcript = transcript,
          estimatedTokens = estimateTokens(transcript),
        )
      }
  }

  /**
   * Result of [trim]: the questions to send as context, the [PhaseSummary]s
   * riding with them, and how many answers it had to compact away to fit the
   * budget.
   */
  data class TrimResult(
    val questions: List<Question>,
    val droppedAnswers: Int = 0,
    val summaries: List<PhaseSummary> = emptyList(),
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
   * are questions a [PhaseSummary] already covers: their answers are gone from
   * the prompt in favor of the summary, so "dropping" one again would compact a
   * question the model can already read.
   */
  fun trim(
    project: Project,
    budgetTokens: Int,
    summaries: List<PhaseSummary> = emptyList(),
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
    for (question in compactable) {
      if (estimateTokens(trimmed, summaries) <= budgetTokens) break
      val next = trimmed.map { if (it.id == question.id) it.asCompacted() else it }
      if (next != trimmed) {
        dropped += 1
      }
      trimmed = next
    }
    return TrimResult(trimmed, droppedAnswers = dropped, summaries = summaries)
  }
}
