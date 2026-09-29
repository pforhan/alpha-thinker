package alphainterplanetary.thinker.tasks

import kotlin.time.Instant

/**
 * The kind of long-running planning-engine work a task performs.
 */
enum class TaskKind {
  /**
   * A batch of questions for one round — a project's opening round, a
   * "Get more questions" round, or the opening round of a later phase. The
   * engine takes the same call for all three (see
   * [alphainterplanetary.thinker.engine.PlanningEngine.generateQuestions]); what
   * distinguishes them is the round's [alphainterplanetary.thinker.model.RoundOrigin].
   */
  QuestionGeneration,

  /** Recommending an editable title from the synopsis when the user didn't type one. */
  TitleRecommendation,

  /** Rewriting the project synopsis / title from the accumulated answers. */
  SynopsisRewrite,

  /** Deciding whether older questions should be auto-archived/deselected. */
  AutoArchive;

  /**
   * The resource group this kind lands in by default ([TaskRunner] schedules
   * against [ConcurrencyGroup]). Every current kind drives the shared local planning
   * engine and stays serial; a future remote-engine kind (e.g. `Lookup`)
   * opts into [ConcurrencyGroup.Remote] here so it runs in parallel.
   */
  val group: ConcurrencyGroup
    get() = when (this) {
      QuestionGeneration,
      TitleRecommendation,
      SynopsisRewrite,
      AutoArchive,
      -> ConcurrencyGroup.Engine
    }
}


enum class TaskStatus {
  Queued,
  Running,
  Succeeded,
  Failed,
}

/**
 * An observable unit of long-running generation work (see ENG-DESIGN.md
 * "Generation Task Framework"). The live set stays in memory for the Task
 * Manager; every lifecycle transition is also appended to the durable app-wide
 * activity log ([LogEntry] via [TaskRunner]) so the System/Debug workspace can
 * replay what ran.
 */
data class GenerationTask(
  val id: String,
  val projectId: String,
  val kind: TaskKind,
  /** The resource group the task runs in; drives [TaskRunner] scheduling. */
  val group: ConcurrencyGroup = ConcurrencyGroup.Engine,
  val status: TaskStatus,
  /** Streaming progress 0..1; `null` means indeterminate (e.g. discrete question rounds). */
  val progress: Float? = null,
  /** Set when [status] is [TaskStatus.Failed]. */
  val error: String? = null,
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