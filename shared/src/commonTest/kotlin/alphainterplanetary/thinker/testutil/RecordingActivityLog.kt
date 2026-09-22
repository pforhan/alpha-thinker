package alphainterplanetary.thinker.testutil

import alphainterplanetary.thinker.activitylog.EngineActivityEvent
import alphainterplanetary.thinker.activitylog.EngineActivityEventType
import alphainterplanetary.thinker.activitylog.EngineActivityLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlin.time.Instant

/**
 * In-memory [EngineActivityLog] for tests: every [append] lands in [events] in
 * order, and the derived read models fold over that list the way the Room
 * implementation folds the table.
 */
class RecordingActivityLog : EngineActivityLog {
  val events: MutableList<EngineActivityEvent> = mutableListOf()

  override suspend fun append(event: EngineActivityEvent) {
    events += event
  }

  override fun events(): Flow<List<EngineActivityEvent>> = flowOf(events.toList())

  override suspend fun historyForActivity(activityId: String): List<EngineActivityEvent> =
    events.filter { it.activityId == activityId }

  override suspend fun latestPerActivity(): List<EngineActivityEvent> =
    events.groupBy { it.activityId }
      .values
      .map { it.last() }
      .asReversed()

  override suspend fun recoverInterrupted(now: Instant) {
    latestPerActivity()
      .filterNot { it.isTerminal }
      .forEach { latest ->
        events += latest.copy(
          eventId = null,
          eventType = EngineActivityEventType.Failed,
          error = "interrupted",
          timestamp = now,
        )
      }
  }

  override suspend fun prune(retentionDays: Int, now: Instant): Int = 0

  override suspend fun clear() {
    events.clear()
  }
}