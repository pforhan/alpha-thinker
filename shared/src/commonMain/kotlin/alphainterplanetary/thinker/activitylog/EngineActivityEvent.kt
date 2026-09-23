package alphainterplanetary.thinker.activitylog

import alphainterplanetary.thinker.tasks.TaskKind
import kotlin.time.Instant

/**
 * The kind of engine that produced an [EngineActivityEvent] row. `Lookup` marks
 * a child tool-call row performed on behalf of an inference (see
 * [EngineActivityEvent.parentActivityId]); the inference kinds distinguish a
 * local edge model from a remote/cloud one.
 */
enum class LogCategory {
  LocalInference,
  RemoteInference,
  Hardcoded,
  Lookup,
}

/**
 * The transition an [EngineActivityEvent] records. Terminal types
 * ([Succeeded], [Failed], [Cancelled]) end an activity; [Created] and
 * [Progress] leave it live for the startup recovery pass
 * ([alphainterplanetary.thinker.activitylog.EngineActivityLog.recoverInterrupted]).
 */
enum class EngineActivityEventType {
  Created,
  Progress,
  Succeeded,
  Failed,
  Cancelled,
}

/**
 * One immutable, append-only row in the engine activity event log (ENG-DESIGN.md
 * schema item 4). The log lives in its own `ActivityDatabase` so it grows and
 * prunes independently of projects/questions/settings.
 *
 * A logical activity is the group of rows sharing [activityId] — a generation
 * task (written by the [alphainterplanetary.thinker.tasks.TaskRunner] lifecycle)
 * joined with its interaction-detail rows (written by the
 * `LoggingPlanningEngine` decorator — same [activityId], live/detail fields).
 * Global order is the autoincrement [eventId] primary key once persisted;
 * before that (in-memory) it is null. Tool calls are child rows whose
 * [parentActivityId] points at the requesting inference's activity.
 */
data class EngineActivityEvent(
  /** Autoincrement primary key; null until persisted. */
  val eventId: Long? = null,
  val activityId: String,
  val parentActivityId: String? = null,
  val projectId: String? = null,
  val roundId: String? = null,
  /** The generation kind this activity belongs to; null for tool-call (`Lookup`) rows. */
  val kind: TaskKind? = null,
  val logCategory: LogCategory? = null,
  val eventType: EngineActivityEventType,
  val progress: Float? = null,
  val error: String? = null,
  val result: Boolean? = null,
  /** Payload populated on detail `Created` rows. */
  val promptUsed: String? = null,
  val parameters: String? = null,
  /** Payload populated on terminal rows (e.g. recommended title / remaining count). */
  val generationPayload: String? = null,
  /** JSON list of produced question texts on terminal question rows. */
  val suggestedQuestions: String? = null,
  val durationMs: Long? = null,
  val timestamp: Instant,
) {
  /** Whether [eventType] ends the activity (nothing more can follow in practice). */
  val isTerminal: Boolean
    get() = when (eventType) {
      EngineActivityEventType.Succeeded,
      EngineActivityEventType.Failed,
      EngineActivityEventType.Cancelled,
      -> true

      EngineActivityEventType.Created,
      EngineActivityEventType.Progress,
      -> false
    }
}