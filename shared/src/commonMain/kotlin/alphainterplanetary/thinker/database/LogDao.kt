package alphainterplanetary.thinker.database

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import kotlinx.coroutines.flow.Flow

/**
 * The append-only store behind the app-wide activity log. Nothing here updates
 * or overwrites an existing row — the log is immutable history; pruning and
 * clearing are the only destructive operations.
 */
@Dao
interface LogDao {
  /** Appends one immutable log row, returning its autoincrement [LogEntryEntity.id]. */
  @Insert
  suspend fun append(entry: LogEntryEntity): Long

  @Query("SELECT * FROM log_events ORDER BY id ASC")
  suspend fun all(): List<LogEntryEntity>

  @Query("SELECT * FROM log_events ORDER BY id ASC")
  fun observeAll(): Flow<List<LogEntryEntity>>

  @Query("SELECT * FROM log_events WHERE projectId = :projectId ORDER BY id ASC")
  suspend fun allForProject(projectId: String): List<LogEntryEntity>

  /**
   * Row-age retention sweep: deletes every row older than [cutoffMillis]
   * regardless of activity state (the log is a flat journal). Returns the
   * number of deleted rows.
   */
  @Query("DELETE FROM log_events WHERE timestampMillis < :cutoffMillis")
  suspend fun pruneOlderThan(cutoffMillis: Long): Int

  /** Manual "Clear log": wipes the whole table (and only this table). */
  @Query("DELETE FROM log_events")
  suspend fun clearAll()
}