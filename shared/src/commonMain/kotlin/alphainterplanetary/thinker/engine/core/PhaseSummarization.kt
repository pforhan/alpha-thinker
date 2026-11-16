package alphainterplanetary.thinker.engine.core

import alphainterplanetary.thinker.model.Project
import alphainterplanetary.thinker.phases.Phase
import kotlin.coroutines.cancellation.CancellationException

/**
 * Shared, programmatic phase-summarization primitives.
 *
 * Centralizes phase transcript selection and the [PlanningEngine.summarizePriorAnswers]
 * interaction so budget-driven prompt compaction and best-effort export/background
 * summarization can share core logic while applying different strategies.
 */
object PhaseSummarization {
  /**
   * Transcripts for prompt compaction: past phases with summarizable content,
   * oldest first, excluding the current phase.
   */
  fun compactionTranscripts(project: Project): List<PhaseTranscript> =
    project.phaseTranscripts(includeCurrentPhase = false)

  /**
   * Transcripts for export/background summarization: phases with summarizable
   * content (including the current phase), in phase-library order.
   */
  fun exportTranscripts(project: Project): List<PhaseTranscript> =
    project.phaseTranscripts(includeCurrentPhase = true)

  /**
   * Summarizes [transcript] and returns the summary text.
   *
   * Propagates failures from [PlanningEngine.summarizePriorAnswers]; callers
   * that want best-effort behavior should use [summarizeOrNull].
   */
  suspend fun summarize(
    engine: PlanningEngine,
    title: String,
    synopsis: String,
    transcript: PhaseTranscript,
    activityId: String,
  ): String =
    engine.summarizePriorAnswers(
      title = title,
      synopsis = synopsis,
      phase = transcript.phase,
      transcript = transcript.transcript,
      activityId = activityId,
    )

  /**
   * Summarizes [transcript] and returns the trimmed summary, or null when
   * [engine] cannot summarize or summarization fails. Cancellation is rethrown.
   */
  suspend fun summarizeOrNull(
    engine: PlanningEngine,
    title: String,
    synopsis: String,
    transcript: PhaseTranscript,
    activityId: String,
  ): String? {
    if (!engine.canSummarize) return null
    val summary = try {
      summarize(engine, title, synopsis, transcript, activityId)
    } catch (e: CancellationException) {
      throw e
    } catch (_: Exception) {
      null
    }
    return summary?.trim()?.ifBlank { null }
  }
}
