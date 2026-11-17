package alphainterplanetary.thinker.engine.core

import alphainterplanetary.thinker.phases.Phase

/**
 * A persisted, intent-aware phase summary produced for a specific project revision
 * and covered content.
 *
 * [projectRevisionEpochMs] is the project revision ([Project.updatedAt]) the
 * summary was derived from. [coveredContentHash] fingerprints the covered phase
 * transcript/content (see [PhaseTranscript.coveredContentHash]) so a cache hit is
 * valid only when the summarized content is unchanged (defense-in-depth alongside
 * revision invalidation). [coveredQuestionIds] records which questions the summary
 * stands in for (aligned with [SummarizedPhase]).
 */
data class CachedPhaseSummary(
  val projectId: String,
  val projectRevisionEpochMs: Long,
  val phaseKey: String,
  val intent: PhaseSummaryIntent,
  val engineIdentity: String,
  val coveredContentHash: String,
  val coveredQuestionIds: Set<String>,
  val summaryText: String,
  val createdAtEpochMs: Long,
) {
  fun toSummarizedPhase(phase: Phase): SummarizedPhase =
    SummarizedPhase(
      phase = phase,
      summary = summaryText,
      coversQuestionIds = coveredQuestionIds,
    )
}
