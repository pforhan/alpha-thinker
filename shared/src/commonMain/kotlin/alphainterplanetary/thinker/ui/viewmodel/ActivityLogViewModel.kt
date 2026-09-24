package alphainterplanetary.thinker.ui.viewmodel

import alphainterplanetary.thinker.activitylog.ActivityLog
import alphainterplanetary.thinker.activitylog.LogActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Read-only view over the app-wide activity log (ENG-DESIGN.md schema item 4).
 * The log is append-only and independent of projects/questions — it records
 * every generation task transition and every PlanningEngine interaction-detail
 * row.
 *
 * Exposes each activity as a [LogActivity] (the clumped rows for one activity
 * id, with a synthesized summary and derived duration) ordered newest-first, so
 * the viewer can audit why a backend LLM stopped offering questions (see
 * IMPLEMENTATION-PLAN.md line 228): a `RemainingInPhase` task that answered
 * `false`, a `FollowUpQuestions` batch that produced nothing yet marked `done`,
 * or a failed/cancelled generation task with its error.
 *
 * [clear] wipes the whole log; the diagnostic viewer surfaces it without a
 * confirmation dialog.
 */
class ActivityLogViewModel(
  private val log: ActivityLog,
  scope: CoroutineScope,
) {
  private val vmScope = CoroutineScope(scope.coroutineContext + SupervisorJob())

  /** Per-activity rows grouped by [LogEntry.activityId], newest activity first. */
  val items: StateFlow<List<LogActivity>> =
    log.entries()
      .map { entries -> LogActivity.groupByActivity(entries) }
      .stateIn(scope, SharingStarted.Eagerly, emptyList())

  /** Wipes the entire log (append-only store; nothing else is touched). */
  fun clear() {
    vmScope.launch {
      log.clear()
    }
  }
}