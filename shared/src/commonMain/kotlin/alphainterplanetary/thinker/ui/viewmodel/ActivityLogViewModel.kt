package alphainterplanetary.thinker.ui.viewmodel

import alphainterplanetary.thinker.activitylog.EngineActivityEvent
import alphainterplanetary.thinker.activitylog.EngineActivityLog
import alphainterplanetary.thinker.activitylog.LogCategory
import alphainterplanetary.thinker.tasks.TaskKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/**
 * One logical activity from the engine activity log: the ordered history of its
 * own events plus any child tool-call (`Lookup`) rows that ran on its behalf.
 */
data class ActivityLogItem(
  val activityId: String,
  val history: List<EngineActivityEvent>,
  val children: List<EngineActivityEvent>,
) {
  /** The newest event in the activity — what the row's headline is built from. */
  val latest: EngineActivityEvent = history.maxByOrNull { it.eventId ?: 0L }!!

  /** The last terminal event (Succeeded/Failed/Cancelled), if the activity closed. */
  val terminal: EngineActivityEvent? = history.lastOrNull { it.isTerminal }

  /**
   * The first populated kind across the activity. Task lifecycle rows carry
   * [EngineActivityEvent.kind] but no [EngineActivityEvent.logCategory], while
   * the interaction-detail rows carry both — so the headline's kind and the
   * engine family are best read from whichever row recorded them.
   */
  val kind: TaskKind? get() = history.firstNotNullOfOrNull { it.kind }

  val logCategory: LogCategory? get() = history.firstNotNullOfOrNull { it.logCategory }

  /** All question-payload strings across every interaction-detail row in the activity. */
  val questionPayloads: List<String>
    get() = history.mapNotNull { it.suggestedQuestions }

  /** Total questions produced across every interaction-detail row in the activity. */
  val totalQuestions: Int
    get() = questionPayloads.sumOf { payload -> payload.decodeQuestionCount() }

  /**
   * The latest [EngineActivityEvent.generationPayload] recorded on an
   * interaction-detail row (the task lifecycle rows don't carry one) — the
   * `done=` flag for question batches or the "can produce more" answer.
   */
  val detailPayload: String?
    get() = history.mapNotNull { it.generationPayload }.lastOrNull()
}

/** Decodes a JSON list-of-strings payload into its question count. */
internal fun String.decodeQuestionCount(): Int = runCatching {
  Json.decodeFromString<List<String>>(this).size
}.getOrElse { 0 }

/**
 * Read-only view over the engine activity log (ENG-DESIGN.md schema item 4). The
 * log is append-only and independent of projects/questions — it records every
 * generation task transition and every PlanningEngine interaction-detail row.
 *
 * Exposes each activity as an [ActivityLogItem] (history + child `Lookup` rows)
 * ordered newest-first, so the viewer can audit why a backend LLM stopped
 * offering questions (see IMPLEMENTATION-PLAN.md line 228): a `RemainingInPhase`
 * task that answered `false`, a `FollowUpQuestions` batch that produced nothing
 * yet marked `done`, or a failed/cancelled generation task with its error.
 *
 * [clear] wipes the whole log; the diagnostic viewer surfaces it without a
 * confirmation dialog.
 */
class ActivityLogViewModel(
  private val log: EngineActivityLog,
  scope: CoroutineScope,
) {
  private val vmScope = CoroutineScope(scope.coroutineContext + SupervisorJob())

  /** Activities grouped by [EngineActivityEvent.activityId], newest activity first. */
  val items: StateFlow<List<ActivityLogItem>> =
    log.events()
      .map { events -> buildItems(events) }
      .stateIn(scope, SharingStarted.Eagerly, emptyList())

  /** Wipes the entire log (append-only store; nothing else is touched). */
  fun clear() {
    vmScope.launch {
      log.clear()
    }
  }

  private fun buildItems(events: List<EngineActivityEvent>): List<ActivityLogItem> {
    val grouped = events.groupBy { it.activityId }
    val childrenByParent = events
      .filter { it.parentActivityId != null }
      .groupBy { it.parentActivityId!! }

    return grouped
      .map { (activityId, history) ->
        ActivityLogItem(
          activityId = activityId,
          history = history.sortedBy { it.eventId ?: 0L },
          children = (childrenByParent[activityId] ?: emptyList())
            .sortedBy { it.eventId ?: 0L },
        )
      }
      .sortedByDescending { it.latest.timestamp }
  }
}