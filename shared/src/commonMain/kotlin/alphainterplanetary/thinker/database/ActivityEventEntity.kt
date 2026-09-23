package alphainterplanetary.thinker.database

import alphainterplanetary.thinker.activitylog.EngineActivityEvent
import alphainterplanetary.thinker.activitylog.EngineActivityEventType
import alphainterplanetary.thinker.activitylog.LogCategory
import alphainterplanetary.thinker.tasks.TaskKind
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey
import kotlin.time.Instant

/**
 * Append-only storage row for the engine activity event log (see
 * [EngineActivityEvent]). Timestamps persist as epoch millis like every other
 * entity in this app's schema; enums are stored as their names (the app-wide
 * convention, see [ProjectEntity.status]).
 */
@Entity(
  tableName = "activity_events",
  indices = [
    Index("activityId"),
    Index("parentActivityId"),
    Index("projectId"),
    Index(value = ["activityId", "eventId"]),
  ],
)
data class ActivityEventEntity(
  @PrimaryKey(autoGenerate = true) val eventId: Long = 0,
  val activityId: String,
  val parentActivityId: String? = null,
  val projectId: String? = null,
  val roundId: String? = null,
  val kind: String? = null,
  val engine: String? = null,
  val eventType: String,
  val progress: Float? = null,
  val error: String? = null,
  val result: Boolean? = null,
  val promptUsed: String? = null,
  val parameters: String? = null,
  val generationPayload: String? = null,
  val suggestedQuestions: String? = null,
  val durationMs: Long? = null,
  val timestampMillis: Long,
)

fun EngineActivityEvent.toEntity(): ActivityEventEntity = ActivityEventEntity(
  eventId = eventId ?: 0,
  activityId = activityId,
  parentActivityId = parentActivityId,
  projectId = projectId,
  roundId = roundId,
  kind = kind?.name,
  engine = logCategory?.name,
  eventType = eventType.name,
  progress = progress,
  error = error,
  result = result,
  promptUsed = promptUsed,
  parameters = parameters,
  generationPayload = generationPayload,
  suggestedQuestions = suggestedQuestions,
  durationMs = durationMs,
  timestampMillis = timestamp.toEpochMilliseconds(),
)

fun ActivityEventEntity.toEvent(): EngineActivityEvent = EngineActivityEvent(
  eventId = eventId,
  activityId = activityId,
  parentActivityId = parentActivityId,
  projectId = projectId,
  roundId = roundId,
  kind = kind?.let(::parseTaskKind),
  logCategory = engine?.let(::parseEngineKind),
  eventType = parseEventType(eventType),
  progress = progress,
  error = error,
  result = result,
  promptUsed = promptUsed,
  parameters = parameters,
  generationPayload = generationPayload,
  suggestedQuestions = suggestedQuestions,
  durationMs = durationMs,
  timestamp = Instant.fromEpochMilliseconds(timestampMillis),
)

private fun parseTaskKind(name: String): TaskKind? = runCatching { TaskKind.valueOf(name) }.getOrNull()

private fun parseEngineKind(name: String): LogCategory? = runCatching { LogCategory.valueOf(name) }.getOrNull()

private fun parseEventType(name: String): EngineActivityEventType =
  runCatching { EngineActivityEventType.valueOf(name) }
    .getOrElse { EngineActivityEventType.Failed }