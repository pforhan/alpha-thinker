package alphainterplanetary.thinker.export

import alphainterplanetary.thinker.engine.core.PhaseSummarization
import alphainterplanetary.thinker.engine.core.PlanningEngine
import alphainterplanetary.thinker.model.Project
import alphainterplanetary.thinker.phases.Phase

/**
 * Produces per-phase summaries for export when requested.
 *
 * Reuses shared programmatic phase summarization so export, prompt compaction,
 * and future background summaries can share candidate selection and the
 * [PlanningEngine.summarizePriorAnswers] interaction. Summaries are generated on
 * demand and are best-effort: phases that cannot be summarized are omitted so
 * export can still succeed without summaries.
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

    val summaries = linkedMapOf<Phase, String>()
    PhaseSummarization.exportCandidates(project).forEach { candidate ->
      val summary = PhaseSummarization.summarizeOrNull(
        engine = engine,
        title = project.title,
        synopsis = project.synopsis,
        candidate = candidate,
        activityId = activityId,
      ) ?: return@forEach
      summaries[candidate.phase] = summary
    }
    return summaries
  }
}
