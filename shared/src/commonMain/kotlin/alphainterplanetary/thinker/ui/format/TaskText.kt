package alphainterplanetary.thinker.ui.format

import alphainterplanetary.thinker.tasks.GenerationTask
import alphainterplanetary.thinker.tasks.TaskKind
import alphainterplanetary.thinker.tasks.TaskStatus
import alphainterplanetary.thinker.util.formatTaskDuration
import kotlin.time.Duration
import kotlin.time.Instant

/** Human title for a task kind, e.g. for the Task Manager rows. */
val TaskKind.title: String
  get() = when (this) {
    TaskKind.InitialQuestions -> "Initial questions"
    TaskKind.FollowUpQuestions -> "Follow-up questions"
    TaskKind.SynopsisRewrite -> "Synopsis rewrite"
    TaskKind.AutoArchive -> "Auto-archive"
  }

/** Short label describing what a running task is doing, e.g. a list chip. */
val TaskKind.progressLabel: String
  get() = when (this) {
    TaskKind.InitialQuestions -> "Creating questions"
    TaskKind.FollowUpQuestions -> "Generating questions"
    TaskKind.SynopsisRewrite -> "Rewriting synopsis"
    TaskKind.AutoArchive -> "Reviewing answers"
  }

val TaskStatus.title: String
  get() = when (this) {
    TaskStatus.Queued -> "Queued"
    TaskStatus.Running -> "Running"
    TaskStatus.Succeeded -> "Succeeded"
    TaskStatus.Failed -> "Failed"
  }

/**
 * Elapsed time to show for a task: wall-clock while [TaskStatus.Running], the
 * total execution time once finished (from [GenerationTask.startedAt]). Tasks
 * that never started have no duration yet.
 */
fun GenerationTask.durationText(at: Instant): String? {
  if (status == TaskStatus.Queued || startedAt == null) return null
  val end = finishedAt ?: at
  return formatTaskDuration((end - startedAt).coerceAtLeast(Duration.ZERO))
}