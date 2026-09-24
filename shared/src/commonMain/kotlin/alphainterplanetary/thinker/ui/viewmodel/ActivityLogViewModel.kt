package alphainterplanetary.thinker.ui.viewmodel

import alphainterplanetary.thinker.activitylog.ActivityLog
import alphainterplanetary.thinker.activitylog.LogActivity
import alphainterplanetary.thinker.repository.ProjectRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * One activity with its owning project's title resolved for display. [projectLabel]
 * is null for rows not tied to a project (app-wide tool/app entries).
 */
data class ActivityLogItem(
  val activity: LogActivity,
  val projectLabel: String?,
)

/**
 * Read-only view over the app-wide activity log (ENG-DESIGN.md schema item 4).
 * The log is append-only and independent of projects/questions — it records
 * every generation task transition and every PlanningEngine interaction-detail
 * row.
 *
 * Exposes each activity as an [ActivityLogItem] (the clumped rows for one activity
 * id, with a synthesized summary and derived duration) ordered newest-first, so
 * the viewer can audit why a backend LLM stopped offering questions (see
 * IMPLEMENTATION-PLAN.md line 228): a `RemainingInPhase` task that answered
 * `false`, a `FollowUpQuestions` batch that produced nothing yet marked `done`,
 * or a failed/cancelled generation task with its error. The owning project's
 * title is looked up from the [ProjectRepository] at collection time (never
 * pinned in the log), so renames stay current.
 *
 * [clear] wipes the whole log; the diagnostic viewer surfaces it without a
 * confirmation dialog.
 */
class ActivityLogViewModel(
  private val log: ActivityLog,
  private val repository: ProjectRepository,
  scope: CoroutineScope,
) {
  private val vmScope = CoroutineScope(scope.coroutineContext + SupervisorJob())

  /** Resolved titles are stable for a session; renames show here next launch. */
  private val projectTitles = mutableMapOf<String, String>()

  /** Per-activity rows grouped by [LogEntry.activityId], newest activity first. */
  val items: StateFlow<List<ActivityLogItem>> =
    log.entries()
      .map { entries ->
        LogActivity.groupByActivity(entries).map { activity ->
          ActivityLogItem(
            activity = activity,
            projectLabel = activity.projectId?.let { titleFor(it) },
          )
        }
      }
      .stateIn(scope, SharingStarted.Eagerly, emptyList())

  private suspend fun titleFor(projectId: String): String {
    projectTitles[projectId]?.let { return it }
    val project = repository.getProject(projectId)
    val title = project?.editableTitle?.trim()?.takeIf { it.isNotEmpty() } ?: projectId
    projectTitles[projectId] = title
    return title
  }

  /** Wipes the entire log (append-only store; nothing else is touched). */
  fun clear() {
    vmScope.launch {
      log.clear()
    }
  }
}