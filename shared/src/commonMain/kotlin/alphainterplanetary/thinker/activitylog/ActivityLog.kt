package alphainterplanetary.thinker.activitylog

import alphainterplanetary.thinker.database.ActivityDatabase
import alphainterplanetary.thinker.database.LogDao
import alphainterplanetary.thinker.database.SettingsKey
import alphainterplanetary.thinker.database.Storage
import alphainterplanetary.thinker.database.toEntity
import alphainterplanetary.thinker.database.toEntry
import alphainterplanetary.thinker.util.now
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/**
 * The app-scoped handle on the app-wide activity log (ENG-DESIGN.md schema
 * item 4). Writers append [LogEntry]s; nothing here edits a row, so the log is
 * immutable history. Read models (per-activity grouping, project filters) are
 * derived ([LogActivity]) for each consumer.
 */
interface ActivityLog {
  /** Appends one immutable log row (no-op on write failure — the log never throws). */
  suspend fun append(entry: LogEntry)

  /** All rows in append order (drives the Activity Log viewer). */
  fun entries(): Flow<List<LogEntry>>

  /** All rows for one project, in append order (a project-scoped view). */
  suspend fun entriesForProject(projectId: String): List<LogEntry>

  /**
   * Row-age retention sweep ([SettingsKey.ActivityLogRetentionDays] default
   * 7): deletes rows older than the window regardless of activity state.
   * Returns the number of rows deleted.
   */
  suspend fun prune(retentionDays: Int, now: Instant): Int

  /** Manual "Clear log" — wipes the activity log wholesale, nothing else. */
  suspend fun clear()
}

/**
 * Room-backed [ActivityLog] over the standalone [ActivityDatabase]. On
 * construction it runs one startup sweep on [scope]: a row-age TTL prune at
 * the persisted [SettingsKey.ActivityLogRetentionDays] (default 7 days).
 */
class RoomActivityLog(
  private val database: ActivityDatabase,
  private val storage: Storage,
  private val scope: CoroutineScope,
  private val runStartupSweep: Boolean = true,
) : ActivityLog {
  private val dao: LogDao = database.logDao()

  init {
    if (runStartupSweep) {
      scope.launch {
        runCatching { runStartupSweep() }
      }
    }
  }

  /** Startup sweep: the row-age TTL prune (no activity-state recovery needed). */
  suspend fun runStartupSweep() {
    prune(defaultRetentionDays(), now())
  }

  override suspend fun append(entry: LogEntry) {
    runCatching { dao.append(entry.toEntity()) }
  }

  override fun entries(): Flow<List<LogEntry>> =
    dao.observeAll().map { entities -> entities.map { it.toEntry() } }

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
    val stored = storage.getSetting(SettingsKey.ActivityLogRetentionDays, "")
    return stored.toIntOrNull()?.takeIf { it >= 1 } ?: DefaultRetentionDays
  }

  companion object {
    const val DefaultRetentionDays: Int = 7
  }
}