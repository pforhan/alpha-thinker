package alphainterplanetary.thinker.testutil

import alphainterplanetary.thinker.activitylog.ActivityLog
import alphainterplanetary.thinker.activitylog.LogEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlin.time.Instant

/**
 * In-memory [ActivityLog] for tests: every [append] lands in [entries] in
 * order, and the derived read models fold over that list the way the Room
 * implementation folds the table.
 */
class RecordingActivityLog : ActivityLog {
  val entries: MutableList<LogEntry> = mutableListOf()

  override suspend fun append(entry: LogEntry) {
    entries += entry
  }

  override fun entries(): Flow<List<LogEntry>> = flowOf(entries.toList())

  override suspend fun entriesForProject(projectId: String): List<LogEntry> =
    entries.filter { it.projectId == projectId }

  override suspend fun prune(retentionDays: Int, now: Instant): Int = 0

  override suspend fun clear() {
    entries.clear()
  }
}