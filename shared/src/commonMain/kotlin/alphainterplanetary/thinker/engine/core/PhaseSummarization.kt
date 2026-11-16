package alphainterplanetary.thinker.engine.core

import alphainterplanetary.thinker.model.Project
import alphainterplanetary.thinker.model.Question
import alphainterplanetary.thinker.phases.Phase
import kotlin.coroutines.cancellation.CancellationException

/**
 * Shared, programmatic phase-summarization primitives.
 *
 * Centralizes candidate selection, transcript rendering, and the
 * [PlanningEngine.summarizePriorAnswers] interaction so budget-driven prompt
 * compaction and best-effort export/background summarization can share core
 * logic while applying different strategies (stop-when-fits vs summarize all
 * eligible phases, past-only vs including the current phase).
 */
object PhaseSummarization {
  /**
   * One phase's summarizable content: the questions that would be covered, the
   * rendered transcript to hand the model, and its estimated token cost.
   *
   * Serves as the shared input unit for prompt compaction and export/background
   * summarization; pair with [toPhaseSummary] to produce a prompt/context-ready
   * [PlanningContext.SummarizedPhase].
   */
  data class Candidate(
    val phase: Phase,
    val questions: List<Question>,
    val transcript: String,
    val estimatedTokens: Int,
  ) {
    /** The questions a summary of this phase would cover. */
    val coveredQuestionIds: Set<String>
      get() = questions.mapTo(mutableSetOf()) { it.id }

    /** Builds a [PlanningContext.SummarizedPhase] for [summary] covering this candidate. */
    fun toPhaseSummary(summary: String): PlanningContext.SummarizedPhase =
      PlanningContext.SummarizedPhase(
        phase = phase,
        summary = summary,
        coversQuestionIds = coveredQuestionIds,
      )
  }

  /**
   * Candidates for prompt compaction: past phases with answered/draft,
   * non-ignored content, oldest first, excluding the current phase. Matches
   * [PlanningContext.summarizablePhases].
   */
  fun compactionCandidates(project: Project): List<Candidate> =
    PlanningContext.summarizablePhases(project).map { it.toCandidate() }

  /**
   * Candidates for export/background summarization: phases with answered/draft,
   * non-ignored content (including the current phase), in phase-library order.
   */
  fun exportCandidates(project: Project): List<Candidate> =
    project.allPhaseContent()
      .sortedBy { it.phase.order }
      .mapNotNull { content ->
        candidateFor(content.phase, content.summarizableQuestions.sortedBy { it.timestamp })
      }

  /**
   * Builds a [Candidate] for [phase] from [questions], or null when there is no
   * answered/draft, non-ignored content worth summarizing.
   */
  fun candidateFor(phase: Phase, questions: List<Question>): Candidate? {
    val content = questions.filter { (it.isAnswered || it.isDraft) && !it.isIgnored }
    if (content.isEmpty()) return null
    val transcript = PlanningContext.render(content).ifBlank { return null }
    return Candidate(
      phase = phase,
      questions = content,
      transcript = transcript,
      estimatedTokens = PlanningContext.estimateTokens(transcript),
    )
  }

  /**
   * Summarizes [candidate] and returns the trimmed summary text.
   *
   * Propagates failures from [PlanningEngine.summarizePriorAnswers] (except
   * callers that want best-effort behavior should use [summarizeOrNull]).
   */
  suspend fun summarize(
    engine: PlanningEngine,
    title: String,
    synopsis: String,
    candidate: Candidate,
    activityId: String,
  ): String =
    engine.summarizePriorAnswers(
      title = title,
      synopsis = synopsis,
      phase = candidate.phase,
      transcript = candidate.transcript,
      activityId = activityId,
    )

  /**
   * Summarizes [candidate] and returns the trimmed summary text, or null when
   * [engine] cannot summarize or summarization fails. Cancellation is rethrown.
   */
  suspend fun summarizeOrNull(
    engine: PlanningEngine,
    title: String,
    synopsis: String,
    candidate: Candidate,
    activityId: String,
  ): String? {
    if (!engine.canSummarize) return null
    val summary = try {
      summarize(engine, title, synopsis, candidate, activityId)
    } catch (e: CancellationException) {
      throw e
    } catch (_: Exception) {
      null
    }
    return summary?.trim()?.ifBlank { null }
  }

  private fun PlanningContext.PhaseTranscript.toCandidate(): Candidate = Candidate(
    phase = phase,
    questions = questions,
    transcript = transcript,
    estimatedTokens = estimatedTokens,
  )
}
