package alphainterplanetary.thinker.database

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import kotlinx.coroutines.flow.Flow

/**
 * The append-only store behind the engine activity event log. Nothing here
 * updates or overwrites an existing row — the event log is immutable history;
 * pruning and clearing are the only destructive operations.
 */
@Dao
interface ActivityDao {
  /** Appends one immutable event row, returning its autoincrement [ActivityEventEntity.eventId]. */
  @Insert
  suspend fun append(event: ActivityEventEntity): Long

  @Query("SELECT * FROM activity_events ORDER BY eventId ASC")
  suspend fun all(): List<ActivityEventEntity>

  @Query("SELECT * FROM activity_events ORDER BY eventId ASC")
  fun observeAll(): Flow<List<ActivityEventEntity>>

  @Query("SELECT * FROM activity_events WHERE activityId = :activityId ORDER BY eventId ASC")
  suspend fun historyForActivity(activityId: String): List<ActivityEventEntity>

  @Query("SELECT * FROM activity_events WHERE projectId = :projectId ORDER BY eventId ASC")
  suspend fun allForProject(projectId: String): List<ActivityEventEntity>

  /**
   * The latest event per activity (highest [ActivityEventEntity.eventId]), newest
   * activity first. This is the derived read model both the Task Manager and the
   * startup recovery pass fold over.
   */
  @Query(
    """
    SELECT eventId, activityId, parentActivityId, projectId, roundId, kind, engine, eventType,
           progress, error, result, promptUsed, parameters, generationPayload, suggestedQuestions,
           durationMs, timestampMillis
    FROM (
      SELECT *, ROW_NUMBER() OVER (PARTITION BY activityId ORDER BY eventId DESC) AS rn
      FROM activity_events
    )
    WHERE rn = 1
    ORDER BY eventId DESC
    """
  )
  suspend fun latestPerActivity(): List<ActivityEventEntity>

  /**
   * Deletes every row of any activity whose latest event is terminal
   * ([Succeeded]/[Failed]/[Cancelled]) and older than [cutoffMillis]. Live
   * activities (latest still [Created] or [Progress]) are never pruned.
   * Returns the number of deleted rows.
   */
  @Query(
    """
    DELETE FROM activity_events WHERE activityId IN (
      SELECT activityId FROM (
        SELECT activityId, eventType, timestampMillis,
               ROW_NUMBER() OVER (PARTITION BY activityId ORDER BY eventId DESC) AS rn
        FROM activity_events
      )
      WHERE rn = 1
        AND eventType IN ('Succeeded', 'Failed', 'Cancelled')
        AND timestampMillis < :cutoffMillis
    )
    """
  )
  suspend fun pruneTerminalActivities(cutoffMillis: Long): Int

  /** Manual "Clear log": wipes the whole table (and only this table). */
  @Query("DELETE FROM activity_events")
  suspend fun clearAll()
}