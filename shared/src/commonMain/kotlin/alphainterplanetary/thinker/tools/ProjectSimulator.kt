package alphainterplanetary.thinker.tools

import alphainterplanetary.thinker.database.Storage
import alphainterplanetary.thinker.di.AppScope
import alphainterplanetary.thinker.model.Project
import alphainterplanetary.thinker.model.Question
import alphainterplanetary.thinker.phases.BuiltInPhase
import alphainterplanetary.thinker.phases.Phase
import alphainterplanetary.thinker.repository.ContextCompaction
import alphainterplanetary.thinker.repository.ProjectRepository
import alphainterplanetary.thinker.tasks.TaskFailed
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.tatarka.inject.annotations.Inject

/**
 * Drives one whole project — create it, then ask a question round in every phase
 * of the library — against the **currently selected** planning engine, so the
 * pipeline a real user exercises has an end-to-end exercise of its own.
 *
 * [SampleProjectGenerator] is the other half of this and deliberately not the
 * same thing: its projects are static fixtures that never touch an engine, built
 * to put content in front of the UI. This one takes a synopsis the way the
 * new-project dialog does and then goes *through* [ProjectRepository] —
 * `createProject`, the task runner, per-round dedupe, `RoundOutcome` latching,
 * [alphainterplanetary.thinker.engine.PlanningContext] compaction, and the
 * activity log — so each of those seams runs for real, in order, against whatever
 * engine is selected. A tool that called the engine directly would exercise none
 * of them.
 *
 * The run is one question round per phase, in library order. Answers are canned
 * rather than engine-written: `PlanningEngine` has no `suggestAnswer`, because an
 * answer is not a production concern (a phase is resolved for free by ignoring its
 * questions), and a prompt-tuned model-written answer is a knob that can come
 * later if the canned transcript proves too uniform to judge a prompt by. Canned
 * answers plus a deliberate mix of ignored questions still leaves every filter
 * worth looking at populated and the wrap-up reachable — a phase only offers its
 * next phase once nothing in it is unanswered.
 *
 * Repeatable by construction: each run deletes whatever the last one left
 * ([SimulatedStatus] is the marker) and recreates it, so a second run compares
 * like with like instead of stacking another near-identical project in the list.
 */
@AppScope
class ProjectSimulator @Inject constructor(
  private val repository: ProjectRepository,
  private val storage: Storage,
  private val scope: CoroutineScope,
) {

  private val _state = MutableStateFlow<SimulationState>(SimulationState.Idle)
  val state: StateFlow<SimulationState> = _state.asStateFlow()

  private var job: Job? = null

  private var lastProjectId: String? = null

  /**
   * The project the last run created, or null before one has. Survives
   * cancellation and failure on purpose: both leave a real project behind, and a
   * caller that wants to open it (or a test that wants to read it) should not have
   * to distinguish those outcomes to find out its id.
   */
  val projectId: String?
    get() = lastProjectId

  /**
   * Starts a run on the injected app scope, replacing any run already in flight.
   * Fire-and-forget like every other tool action; [state] is how a caller
   * follows it.
   */
  fun run(config: SimulationConfig) {
    job?.cancel()
    job = scope.launch { simulate(config) }
  }

  /**
   * Cancels the run in progress.
   *
   * This cancels the *simulator*, not the generation task it is waiting on:
   * [TaskRunner.await] leaves the awaited task running when its awaiter goes
   * away, and cancelling it mid-flight would strand the round. The in-flight
   * round therefore still lands — its questions persisted, its outcome latched —
   * and the run simply stops advancing after it. What is left behind is a project
   * stopped partway through the phase library, which is a coherent thing to look
   * at and exactly what a cancelled run should leave.
   */
  fun cancel() {
    job?.cancel()
    job = null
  }

  /**
   * The run itself, suspending until it finishes. [run] is the UI's entry point
   * (launched, so a long run survives navigating away); this is the same body for
   * a caller that wants to wait for it — chiefly tests.
   */
  suspend fun simulate(config: SimulationConfig) {
    val phases = BuiltInPhase.entries
    val failures = mutableListOf<String>()
    try {
      deletePreviousRuns()

      _state.value = SimulationState.Running(
        phase = null,
        phaseNumber = 0,
        phaseCount = phases.size,
        detail = "Creating the project…",
      )
      val projectId = createAndAwait(config, failures)
      lastProjectId = projectId
      // Marked only once the first round has landed: until then the generation
      // task is writing the project too, and a second write from here could lose
      // either the round's questions or the marker.
      markSimulated(projectId)

      resolvePhase(projectId, config, allowDrafts = phases.size == 1)

      var stoppedAtQuestionCap = false
      for ((index, phase) in phases.drop(1).withIndex()) {
        if (questionCount(projectId) >= config.questionCap) {
          stoppedAtQuestionCap = true
          break
        }
        _state.value = SimulationState.Running(
          phase = phase,
          phaseNumber = index + 2,
          phaseCount = phases.size,
          detail = "Asking about ${phase.label}…",
        )
        // The compaction choice is passed explicitly rather than left to the
        // repository's default so all three branches are reachable from the tool —
        // including the summarize one, which costs an extra model call per past
        // phase. A project's opening round is the exception: it is the first
        // round of an empty project, so there is nothing to compact.
        advanceAndAwait(projectId, phase, config.compaction, failures)
        resolvePhase(projectId, config, allowDrafts = index == phases.size - 2)
      }

      _state.value = SimulationState.Finished(
        projectId = projectId,
        questions = questionCount(projectId),
        stoppedAtQuestionCap = stoppedAtQuestionCap,
        failures = failures.toList(),
      )
    } catch (e: CancellationException) {
      _state.value = SimulationState.Cancelled
      throw e
    } catch (e: TaskFailed) {
      // The opening round failed, so there are no phases left to walk. The run
      // reports finished-with-failures rather than failed, because the project it
      // left behind is still worth opening — and the failed task names it, which
      // matters because this is the one path where the id never got assigned.
      val id = e.task.projectId.also { lastProjectId = it }
      _state.value = SimulationState.Finished(
        projectId = id,
        questions = questionCount(id),
        stoppedAtQuestionCap = false,
        failures = failures.toList(),
      )
    } catch (e: Exception) {
      _state.value = SimulationState.Failed(e.message ?: e.toString())
    }
  }

  /** Deletes every project a previous run of this tool left behind. */
  private suspend fun deletePreviousRuns() {
    storage.getAllProjects()
      .filter { it.status == SimulatedStatus }
      .forEach { storage.deleteProject(it.id) }
  }

  private suspend fun markSimulated(projectId: String) {
    val project = storage.getProject(projectId) ?: return
    storage.saveProject(project.copy(status = SimulatedStatus))
  }

  private suspend fun questionCount(projectId: String): Int =
    storage.getProject(projectId)?.questions?.size ?: 0

  /**
   * Answers — and deliberately skips — everything the current phase asked, so
   * the next phase is reachable: the wrap-up is only offered from the unanswered
   * empty state, which an unanswered-only run never gets to.
   *
   * Drafts are held back to the run's last phase. A draft is neither answered nor
   * ignored, so it still reads as unanswered and would hold that phase's wrap-up
   * shut — but it is the only thing that gives the Drafts filter content, so the
   * final phase keeps a couple.
   */
  private suspend fun resolvePhase(
    projectId: String,
    config: SimulationConfig,
    allowDrafts: Boolean,
  ) {
    if (!config.resolveQuestions) return
    val project = storage.getProject(projectId) ?: return
    project.currentPhaseQuestions
      .filter { it.isUnanswered }
      .forEachIndexed { index, question ->
        resolve(projectId, project, question, index, allowDrafts)
      }
  }

  private suspend fun resolve(
    projectId: String,
    project: Project,
    question: Question,
    index: Int,
    allowDrafts: Boolean,
  ) {
    val phase = project.phaseForQuestion(question)
    when {
      // Every third question is passed on rather than answered. An ignored
      // question costs the engine nothing to read, so it is the cheapest way to
      // make the transcript — and the compaction that trims it — realistic.
      index % IgnoreEvery == IgnoreEvery - 1 ->
        repository.ignoreQuestion(projectId, question.id)

      allowDrafts && index == DraftIndex ->
        repository.saveAnswer(projectId, question.id, draftFor(question, phase), completed = false)

      else ->
        repository.saveAnswer(projectId, question.id, answerFor(question, phase, index), completed = true)
    }
  }

  /**
   * A committed answer: the question restated, so the transcript reads like an
   * interview rather than a list of strings, plus a body that varies in length.
   * Every third one is deliberately long, which is what gives the context budget
   * something to overrun against a remote engine — without it a six-phase run
   * stays small enough that the compaction paths are never reached.
   */
  private fun answerFor(question: Question, phase: Phase, index: Int): String {
    val lead = "On \"${question.text.trim()}\" — settled during ${phase.label}: "
    return lead + if (index % LongAnswerEvery == 0) longBody else shortBody
  }

  private fun draftFor(question: Question, phase: Phase): String =
    "Rough notes for \"${question.text.trim()}\" while working through ${phase.label}."

/**
   * Creates the project and waits for its opening round.
 *
 * A failed opening round is the one failure the run cannot simply step over: it
 * failed before there was a project to advance, so the run has nothing else to do
 * and ends. The project itself still exists, and its id comes off the failed task
 * rather than off a search for "the newest draft", which would be a guess about
 * storage order in a run that is allowed to be cancelled at any moment.
 */
private suspend fun createAndAwait(config: SimulationConfig, failures: MutableList<String>): String {
    try {
      return repository.createProjectAndWait(config.synopsis, config.title).id
    } catch (e: TaskFailed) {
      failures += e.message ?: e.toString()
      throw e
    }
  }

  /**
   * Advances to [phase] and waits for its round, recording a failure rather than
   * raising it: a failed round is a coherent state the round itself already
   * latched, and a run that stopped at the first failure would be reporting the
   * failure instead of exercising the pipeline.
   */
  private suspend fun advanceAndAwait(
    projectId: String,
    phase: Phase,
    compaction: ContextCompaction,
    failures: MutableList<String>,
  ) {
    try {
      repository.advanceToPhaseAndWait(projectId, phase, compaction)
    } catch (e: TaskFailed) {
      failures += e.message ?: e.toString()
    }
  }

  companion object {
    /**
     * Marks a project as this tool's, so a later run replaces it instead of
     * adding another. [Project.status] is otherwise always [DraftStatus] and read
     * by nothing, which makes it the one field a fixture can claim without
     * touching the schema.
     */
    const val SimulatedStatus: String = "Simulated"

    /** The status every project the app creates for real carries. */
    const val DraftStatus: String = "Draft"

    /**
     * One in every [IgnoreEvery] questions is ignored rather than answered. Three
     * rather than something larger because it has to land inside the smallest
     * batch an engine serves (the built-in pool's phases run ten deep), or the
     * fixture would quietly have nothing ignored in it.
     */
    const val IgnoreEvery: Int = 3

    /** Every third answer is written long enough to matter to the token budget. */
    const val LongAnswerEvery: Int = 3

    /** Which question of the final phase becomes a draft instead of an answer. */
    const val DraftIndex: Int = 1

    private const val shortBody =
      "the constraint is the schedule rather than the technology, so the plan assumes " +
        "one part delivered end to end before anything is split up."

    private val longBody = listOf(
      "the constraint is the schedule rather than the technology, so the plan assumes a " +
        "single part delivered end to end before anything is split up; the first slice " +
        "carries the whole workflow at low fidelity rather than a high-fidelity " +
        "fragment of it, because a working end-to-end path is what tells us whether " +
        "the premise holds at all.",
      "we looked at three comparable efforts and two of them spent their first month " +
        "on instrumentation nobody used, so the sequencing here puts the measurement " +
        "in the same slice as the thing it measures rather than after it.",
      "the open risk is adoption rather than feasibility — nothing here is technically " +
        "uncertain, but the people who have to change their routine are the same people " +
        "whose routine we are asking them to change, so the plan spends its early effort " +
        "on making the first use obviously cheaper than the alternative rather than on " +
        "making the whole system complete.",
      "we are treating the third-party dependency as a risk to retire rather than a " +
        "component to integrate: the surface we need is small enough to reimplement, and " +
        "the failure mode we cannot tolerate is being unable to ship a fix when it breaks.",
    ).joinToString(" ")
  }
}

/**
 * One simulator run's knobs. Mirrors what the new-project dialog collects, plus
 * the two choices a real project makes for you: how the transcript is compacted
 * when a round goes out, and whether questions get answered at all.
 */
data class SimulationConfig(
  /** The project idea, exactly as the new-project dialog would take it. */
  val synopsis: String,
  /** Left null so the engine recommends one, as it does for a real project. */
  val title: String? = null,
  /**
   * What to do with a transcript that no longer fits the model's window. Passed to
   * every phase advance rather than inherited, so the summarize branch is
   * reachable from the tool.
   */
  val compaction: ContextCompaction = ContextCompaction.DropEarlierAnswers,
  /**
   * Whether the run answers what it asked. Off leaves a project with questions
   * across every phase and nothing else — faster, and the shape to look at when
   * only generation is under test.
   */
  val resolveQuestions: Boolean = true,
  /** Ceiling on the questions the run produces before it stops advancing. */
  val questionCap: Int = DefaultQuestionCap,
) {
  companion object {
    /**
     * Six phases at the built-in engine's five questions a round is thirty, so the
     * default is exactly one uncapped run and anything lower is a deliberate cut.
     */
    const val DefaultQuestionCap: Int = 30
  }
}

/** What the simulator is doing, for the Testing sheet to show. */
sealed interface SimulationState {
  data object Idle : SimulationState

  data class Running(
    /** The phase being generated, or null before the project exists. */
    val phase: Phase?,
    /** 1-based position in the library; 0 before the first phase. */
    val phaseNumber: Int,
    val phaseCount: Int,
    val detail: String,
  ) : SimulationState

  data class Finished(
    val projectId: String,
    val questions: Int,
    /** True when the run stopped on [SimulationConfig.questionCap] rather than at the last phase. */
    val stoppedAtQuestionCap: Boolean,
    /** One message per round that failed; empty when every round landed. */
    val failures: List<String>,
  ) : SimulationState

  data object Cancelled : SimulationState

  data class Failed(val message: String) : SimulationState
}
