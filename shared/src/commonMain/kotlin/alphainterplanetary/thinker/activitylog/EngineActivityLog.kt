package alphainterplanetary.thinker.activitylog

import alphainterplanetary.thinker.database.ActivityDao
import alphainterplanetary.thinker.database.ActivityDatabase
import alphainterplanetary.thinker.database.SettingsKey
import alphainterplanetary.thinker.database.Storage
import alphainterplanetary.thinker.database.toEntity
import alphainterplanetary.thinker.database.toEvent
import alphainterplanetary.thinker.util.now
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/**
 * The app-scoped handle on the engine activity event log (ENG-DESIGN.md schema
 * item 4). Writers append [EngineActivityEvent]s; nothing here edits a row, so
 * the log is immutable history. Read models (latest event per activity, per-
 * activity history) are derived for each consumer.
 */
interface EngineActivityLog {
  /** Appends one immutable event row (no-op on write failure — the log never throws). */
  suspend fun append(event: EngineActivityEvent)

  /** All events in append order (drives the future LLM Interaction Log viewer). */
  fun events(): Flow<List<EngineActivityEvent>>

  /** The ordered history of one activity (the log viewer's tree reads this + parents). */
  suspend fun historyForActivity(activityId: String): List<EngineActivityEvent>

  /** The latest event per activity, newest activity first — the derived read model. */
  suspend fun latestPerActivity(): List<EngineActivityEvent>

  /**
   * Startup recovery: any activity whose latest event is non-terminal is closed
   * with a `Failed("interrupted")` row so in-flight work resolves cleanly after
   * a process death instead of hanging the read models.
   */
  suspend fun recoverInterrupted(now: Instant)

  /**
   * Retention sweep ([SettingsKey.ActivityLogRetentionDays] default 7): deletes
   * only whole activities whose terminal event is older than the window. Live
   * activities are never pruned. Returns the number of rows deleted.
   */
  suspend fun prune(retentionDays: Int, now: Instant): Int

  /** Manual "Clear log" — wipes the activity log wholesale, nothing else. */
  suspend fun clear()
}

/**
 * Room-backed [EngineActivityLog] over the standalone [ActivityDatabase]. On
 * construction it runs one startup sweep on [scope]: interrupt-recovery first,
 * then a TTL prune at the persisted [SettingsKey.ActivityLogRetentionDays]
 * (default 7 days).
 */
class RoomEngineActivityLog(
  private val database: ActivityDatabase,
  private val storage: Storage,
  private val scope: CoroutineScope,
  private val runStartupSweep: Boolean = true,
) : EngineActivityLog {
  private val dao: ActivityDao = database.activityDao()

  init {
    if (runStartupSweep) {
      scope.launch {
        runCatching { runStartupSweep() }
      }
    }
  }

  /** Startup sweep: interrupt recovery first, then a TTL prune. */
  suspend fun runStartupSweep() {
    recoverInterrupted(now())
    prune(defaultRetentionDays(), now())
  }

  override suspend fun append(event: EngineActivityEvent) {
    runCatching { dao.append(event.toEntity()) }
  }

  override fun events(): Flow<List<EngineActivityEvent>> =
    dao.observeAll().map { entities -> entities.map { it.toEvent() } }

  override suspend fun historyForActivity(activityId: String): List<EngineActivityEvent> =
    dao.historyForActivity(activityId).map { it.toEvent() }

  override suspend fun latestPerActivity(): List<EngineActivityEvent> =
    dao.latestPerActivity().map { it.toEvent() }

  override suspend fun recoverInterrupted(now: Instant) {
    latestPerActivity()
      // Already-closed rows (including a previous launch's own "interrupted"
      // marker) must not be reopened, so recovery is idempotent per activity.
      .filterNot { it.isTerminal }
      .forEach { latest ->
        dao.append(
          EngineActivityEvent(
            activityId = latest.activityId,
            parentActivityId = latest.parentActivityId,
            projectId = latest.projectId,
            roundId = latest.roundId,
            kind = latest.kind,
            engine = latest.engine,
            eventType = EngineActivityEventType.Failed,
            error = "interrupted",
            timestamp = now,
          ).toEntity(),
        )
      }
  }

  override suspend fun prune(retentionDays: Int, now: Instant): Int {
    val cutoff = now.minus(retentionDays.days)
    return dao.pruneTerminalActivities(cutoff.toEpochMilliseconds())
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