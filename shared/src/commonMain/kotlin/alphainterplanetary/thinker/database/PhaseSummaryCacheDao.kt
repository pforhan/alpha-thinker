package alphainterplanetary.thinker.database

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert

@Dao
interface PhaseSummaryCacheDao {
  @Upsert
  suspend fun upsert(entry: PhaseSummaryCacheEntity)

  @Query(
    """
    SELECT * FROM phase_summary_cache
    WHERE projectId = :projectId
      AND projectRevisionEpochMs = :projectRevisionEpochMs
      AND intent = :intent
      AND engineIdentity = :engineIdentity
    """
  )
  suspend fun getForRevision(
    projectId: String,
    projectRevisionEpochMs: Long,
    intent: String,
    engineIdentity: String,
  ): List<PhaseSummaryCacheEntity>

  @Query(
    """
    SELECT * FROM phase_summary_cache
    WHERE projectId = :projectId
      AND projectRevisionEpochMs = :projectRevisionEpochMs
      AND phaseKey = :phaseKey
      AND intent = :intent
      AND engineIdentity = :engineIdentity
      AND coveredContentHash = :coveredContentHash
    LIMIT 1
    """
  )
  suspend fun getMatching(
    projectId: String,
    projectRevisionEpochMs: Long,
    phaseKey: String,
    intent: String,
    engineIdentity: String,
    coveredContentHash: String,
  ): PhaseSummaryCacheEntity?

  @Query(
    """
    DELETE FROM phase_summary_cache
    WHERE projectId = :projectId
      AND projectRevisionEpochMs != :currentRevisionEpochMs
    """
  )
  suspend fun deleteStaleRevisions(projectId: String, currentRevisionEpochMs: Long)

  @Query("DELETE FROM phase_summary_cache WHERE projectId = :projectId")
  suspend fun deleteForProject(projectId: String)
}
