package alphainterplanetary.thinker.database

import alphainterplanetary.thinker.activitylog.LogCategory
import alphainterplanetary.thinker.activitylog.LogEntry
import alphainterplanetary.thinker.activitylog.LogSource
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey
import kotlin.time.Instant

/**
 * Append-only storage row for the app-wide activity log (see [LogEntry]).
 * Timestamps persist as epoch millis like every other entity in this app's
 * schema; enums are stored as their names (the app-wide convention, see
 * [ProjectEntity.status]).
 */
@Entity(
  tableName = "log_events",
  indices = [
    Index("activityId"),
    Index("projectId"),
    Index(value = ["activityId", "id"]),
  ],
)
data class LogEntryEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val projectId: String? = null,
  val activityId: String? = null,
  val category: String,
  val source: String? = null,
  val log: String,
  val timestampMillis: Long,
)

fun LogEntry.toEntity(): LogEntryEntity = LogEntryEntity(
  id = id ?: 0,
  projectId = projectId,
  activityId = activityId,
  category = category.name,
  source = source?.name,
  log = log,
  timestampMillis = timestamp.toEpochMilliseconds(),
)

fun LogEntryEntity.toEntry(): LogEntry = LogEntry(
  id = id,
  projectId = projectId,
  activityId = activityId,
  category = parseCategory(category),
  source = source?.let(::parseSource),
  log = log,
  timestamp = Instant.fromEpochMilliseconds(timestampMillis),
)

/** Unknown stored categories degrade to [LogCategory.Info] rather than crash. */
private fun parseCategory(name: String): LogCategory =
  runCatching { LogCategory.valueOf(name) }.getOrElse { LogCategory.Info }

private fun parseSource(name: String): LogSource? =
  runCatching { LogSource.valueOf(name) }.getOrNull()