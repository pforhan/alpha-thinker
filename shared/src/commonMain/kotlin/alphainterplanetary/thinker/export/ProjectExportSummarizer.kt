package alphainterplanetary.thinker.export

import alphainterplanetary.thinker.engine.core.PlanningContext
import alphainterplanetary.thinker.engine.core.PlanningEngine
import alphainterplanetary.thinker.model.Project
import alphainterplanetary.thinker.model.Question
import alphainterplanetary.thinker.phases.Phase
import kotlin.coroutines.cancellation.CancellationException

/**
 * Produces per-phase summaries for export when requested.
 *
 * Reuses the engine's [PlanningEngine.summarizePriorAnswers] interaction and
 * [PlanningContext] transcript rendering. Summaries are generated on demand and
 * are best-effort: if the engine cannot summarize or a phase summary fails, that
 * summary is omitted so export can still succeed without summaries.
 */
object ProjectExportSummarizer {
  /**
   * Returns summaries keyed by [Phase] for phases with summarizable content.
   *
   * [activityId] groups the summarization interactions in the activity log when
   * the engine is wrapped for logging. Returns an empty map when [engine] cannot
   * summarize.
   */
  suspend fun summarizePhases(
    project: Project,
    engine: PlanningEngine,
    activityId: String,
  ): Map<Phase, String> {
    if (!engine.canSummarize) return emptyMap()

    val phaseLookup = project.phaseLookup()
    val questionsByPhase = project.questions
      .sortedBy { it.timestamp }
      .groupBy { phaseLookup(it) }

    val summaries = linkedMapOf<Phase, String>()
    questionsByPhase.toList()
      .sortedBy { (phase, _) -> phase.order }
      .forEach { (phase, questions) ->
        val transcript = transcriptFor(questions) ?: return@forEach
        val summary = try {
          engine.summarizePriorAnswers(
            title = project.title,
            synopsis = project.synopsis,
            phase = phase,
            transcript = transcript,
            activityId = activityId,
          )
        } catch (e: CancellationException) {
          throw e
        } catch (_: Exception) {
          null
        }
        if (!summary.isNullOrBlank()) {
          summaries[phase] = summary.trim()
        }
      }
    return summaries
  }

  /**
   * Renders a phase transcript for summarization, or null when the phase has no
   * answered/draft content worth summarizing.
   */
  private fun transcriptFor(questions: List<Question>): String? {
    val content = questions.filter { (it.isAnswered || it.isDraft) && !it.isIgnored }
    if (content.isEmpty()) return null
    return PlanningContext.render(content).ifBlank { null }
  }
}
