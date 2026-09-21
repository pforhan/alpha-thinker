package alphainterplanetary.thinker.tasks

import kotlin.time.Instant

/**
 * The kind of long-running LLM work a task performs.
 */
enum class TaskKind {
  /** The opening question batch for a brand-new project ([createProject]). */
  InitialQuestions,

  /** Follow-up questions in a later round ("Get more questions" / wrap-up). */
  FollowUpQuestions,

  /** Recommending an editable title from the synopsis when the user didn't type one. */
  TitleRecommendation,

  /** Asking the generator whether the current phase's pool can still produce questions. */
  RemainingInPhase,

  /** Rewriting the project synopsis / title from the accumulated answers. */
  SynopsisRewrite,

  /** Deciding whether older questions should be auto-archived/deselected. */
  AutoArchive,
}

enum class TaskStatus {
  Queued,
  Running,
  Succeeded,
  Failed,
}

/**
 * An observable unit of long-running generation work (see ENG-DESIGN.md
 * "Generation Task Framework"). In-memory for now; persistence and the
 * [LLMInteraction] audit log are deferred until the System/Debug workspace.
 */
data class GenerationTask(
  val id: String,
  val projectId: String,
  val kind: TaskKind,
  val status: TaskStatus,
  /** Streaming progress 0..1; `null` means indeterminate (e.g. discrete question rounds). */
  val progress: Float? = null,
  /** Set when [status] is [TaskStatus.Failed]. */
  val error: String? = null,
  /** Result of a boolean-answering task, e.g. [TaskKind.RemainingInPhase] availability. */
  val result: Boolean? = null,
  val createdAt: Instant,
  val startedAt: Instant? = null,
  val finishedAt: Instant? = null,
) {
  val isFinished: Boolean
    get() = status == TaskStatus.Succeeded || status == TaskStatus.Failed

  val isActive: Boolean
    get() = status == TaskStatus.Queued || status == TaskStatus.Running

  fun asStarted(at: Instant): GenerationTask = copy(
    status = TaskStatus.Running,
    startedAt = at,
  )

  fun asSucceeded(at: Instant): GenerationTask = copy(
    status = TaskStatus.Succeeded,
    error = null,
    finishedAt = at,
  )

  fun asFailed(at: Instant, message: String): GenerationTask = copy(
    status = TaskStatus.Failed,
    error = message,
    finishedAt = at,
  )
}