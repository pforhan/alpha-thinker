package alphainterplanetary.thinker.activitylog

import alphainterplanetary.thinker.database.ActivityDatabase
import alphainterplanetary.thinker.database.LogDao
import alphainterplanetary.thinker.database.SettingsKey
import alphainterplanetary.thinker.database.Storage
import alphainterplanetary.thinker.database.toEntity
import alphainterplanetary.thinker.database.toEntry
import alphainterplanetary.thinker.util.now
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/**
 * The app-scoped writer handle on the app-wide activity log (ENG-DESIGN.md
 * schema item 4). Writers append [LogEntry]s; nothing here edits a row, so the
 * log is immutable history. Read models (per-activity grouping, project
 * filters) are derived ([ActivityRecord]) for each consumer.
 */
interface ActivityLogger {
  /** Appends one immutable log row (no-op on write failure — the log never throws). */
  suspend fun append(entry: LogEntry)

  /**
   * A scoped writer for one activity's rows (see [LogContext]): carries the
   * fixed [activityId]/[category]/[source]/[projectId] and files each row under
   * the [LogMarkers] prefix the read model parses, so a writer appending a
   * sequence ("started" → interaction rows → one terminal row) never repeats
   * the sibling fields or hand-builds a `log` string.
   */
  fun context(
    activityId: String,
    category: LogCategory,
    source: LogSource?,
    projectId: String? = null,
  ): LogContext = LogContext(this, activityId, category, source, projectId)

  /** All rows in append order (drives the Activity Log viewer). */
  fun entries(): Flow<List<LogEntry>>

  /**
   * The most recent activity, or null on an empty log — the cheap status read
   * (an app-wide header showing [ActivityRecord.summary] / [ActivityRecord.hasError])
   * that must not pay for [entries]' full-table read on every emission.
   *
   * Implementations read a bounded tail of the log ([RecentActivityRowLimit]
   * rows) and fold it, so an in-progress activity whose rows straddle the
   * boundary may summarize from its newest rows only.
   */
  fun latestActivity(): Flow<ActivityRecord?>

  /** All rows for one project, in append order (a project-scoped view). */
  suspend fun entriesForProject(projectId: String): List<LogEntry>

  /**
   * Row-age retention sweep ([SettingsKey.ActivityLoggerRetentionDays] default
   * 7): deletes rows older than the window regardless of activity state.
   * Returns the number of rows deleted.
   */
  suspend fun prune(retentionDays: Int, now: Instant): Int

  /** Manual "Clear log" — wipes the activity log wholesale, nothing else. */
  suspend fun clear()

  companion object {
    /**
     * How many trailing rows [latestActivity] folds: generous enough that an
     * activity's interaction rows arrive together (a generation activity files
     * a prompt and a response), and small enough that a status surface never
     * pays for the whole retained log.
     */
    const val RecentActivityRowLimit: Int = 50
  }
}

/**
 * Room-backed [ActivityLogger] over the standalone [ActivityDatabase]. The
 * composition root launches the one startup sweep — a row-age TTL prune at the
 * persisted [SettingsKey.ActivityLoggerRetentionDays] (default 7 days) — so a
 * constructed logger is inert until someone asks for the sweep.
 */
class RoomActivityLogger(
  private val database: ActivityDatabase,
  private val storage: Storage,
) : ActivityLogger {
  private val dao: LogDao = database.logDao()

  /** Startup sweep: the row-age TTL prune (no activity-state recovery needed). */
  internal suspend fun runStartupSweep() {
    prune(defaultRetentionDays(), now())
  }

  override suspend fun append(entry: LogEntry) {
    runCatching { dao.append(entry.toEntity()) }
  }

  override fun entries(): Flow<List<LogEntry>> =
    dao.observeAll().map { entities -> entities.map { it.toEntry() } }

  override fun latestActivity(): Flow<ActivityRecord?> =
    dao.observeRecent(ActivityLogger.RecentActivityRowLimit)
      .map { entities -> ActivityRecord.groupByActivity(entities.map { it.toEntry() }).firstOrNull() }

  override suspend fun entriesForProject(projectId: String): List<LogEntry> =
    dao.allForProject(projectId).map { it.toEntry() }

  override suspend fun prune(retentionDays: Int, now: Instant): Int {
    val cutoff = now.minus(retentionDays.days)
    return dao.pruneOlderThan(cutoff.toEpochMilliseconds())
  }

  override suspend fun clear() {
    dao.clearAll()
  }

  private suspend fun defaultRetentionDays(): Int {
    val stored = storage.getSetting(SettingsKey.ActivityLoggerRetentionDays, "")
    return stored.toIntOrNull()?.takeIf { it >= 1 } ?: DefaultRetentionDays
  }

  companion object {
    const val DefaultRetentionDays: Int = 7
  }
}