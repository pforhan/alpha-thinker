package alphainterplanetary.thinker.export

import alphainterplanetary.thinker.database.Storage
import alphainterplanetary.thinker.engine.core.CachedPhaseSummary
import alphainterplanetary.thinker.engine.core.PhaseSummaryIntent
import alphainterplanetary.thinker.engine.core.PhaseSummarization
import alphainterplanetary.thinker.engine.core.PlanningEngine
import alphainterplanetary.thinker.model.Project
import alphainterplanetary.thinker.phases.Phase
import alphainterplanetary.thinker.util.now

/**
 * Produces per-phase summaries for export when requested.
 *
 * Reuses shared programmatic phase summarization so export, prompt compaction,
 * and future background summaries can share candidate selection and the
 * [PlanningEngine.summarizePriorAnswers] interaction. Summaries are intent-aware
 * and cache-backed (preferring cached entries before on-demand generation) and are
 * best-effort: phases that cannot be summarized are omitted so export can still
 * succeed without summaries.
 */
object ProjectExportSummarizer {
  /**
   * Returns summaries keyed by [Phase] for phases with summarizable content.
   *
   * [activityId] groups the summarization interactions in the activity log when
   * the engine is wrapped for logging. Returns an empty map when [engine] cannot
   * summarize.
   *
   * When [storage] and [engineIdentity] are provided, summaries are resolved via
   * the persisted phase-summary cache (intent [PhaseSummaryIntent.Export], keyed by
   * project revision and covered content) before on-demand generation.
   */
  suspend fun summarizePhases(
    project: Project,
    engine: PlanningEngine,
    activityId: String,
    storage: Storage? = null,
    engineIdentity: String? = null,
  ): Map<Phase, String> {
    if (!engine.canSummarize) return emptyMap()

    val projectRevisionEpochMs = project.updatedAt.toEpochMilliseconds()
    val intent = PhaseSummaryIntent.Export
    if (storage != null && engineIdentity != null) {
      storage.deleteStalePhaseSummaryCache(project.id, projectRevisionEpochMs)
    }

    val summaries = linkedMapOf<Phase, String>()
    PhaseSummarization.exportTranscripts(project).forEach { transcript ->
      val coveredContentHash = transcript.coveredContentHash
      val cached = if (storage != null && engineIdentity != null) {
        storage.getCachedPhaseSummary(
          projectId = project.id,
          projectRevisionEpochMs = projectRevisionEpochMs,
          phaseKey = transcript.phase.key,
          intent = intent,
          engineIdentity = engineIdentity,
          coveredContentHash = coveredContentHash,
        )
      } else {
        null
      }
      val summary = cached?.summaryText ?: run {
        val generated = PhaseSummarization.summarizeOrNull(
          engine = engine,
          title = project.title,
          synopsis = project.synopsis,
          transcript = transcript,
          activityId = activityId,
        ) ?: return@forEach
        if (storage != null && engineIdentity != null) {
          storage.saveCachedPhaseSummary(
            CachedPhaseSummary(
              projectId = project.id,
              projectRevisionEpochMs = projectRevisionEpochMs,
              phaseKey = transcript.phase.key,
              intent = intent,
              engineIdentity = engineIdentity,
              coveredContentHash = coveredContentHash,
              coveredQuestionIds = transcript.coveredQuestionIds,
              summaryText = generated,
              createdAtEpochMs = now().toEpochMilliseconds(),
            )
          )
        }
        generated
      }
      summaries[transcript.phase] = summary
    }
    return summaries
  }
}
