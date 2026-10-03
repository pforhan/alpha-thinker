package alphainterplanetary.thinker.tools

import alphainterplanetary.thinker.engine.PlanningContext
import alphainterplanetary.thinker.engine.PlanningEngine
import alphainterplanetary.thinker.engine.QuestionBatch
import alphainterplanetary.thinker.model.Project
import alphainterplanetary.thinker.model.Question
import alphainterplanetary.thinker.model.RoundOutcome
import alphainterplanetary.thinker.phases.BuiltInPhase
import alphainterplanetary.thinker.phases.Phase
import alphainterplanetary.thinker.repository.ContextCompaction
import alphainterplanetary.thinker.repository.ProjectRepository
import alphainterplanetary.thinker.tasks.TaskRunner
import alphainterplanetary.thinker.testutil.FakePlanningEngine
import alphainterplanetary.thinker.testutil.FakeStorage
import alphainterplanetary.thinker.testutil.RecordingActivityLogger
import alphainterplanetary.thinker.testutil.question
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ProjectSimulatorTest {

  private val synopsis = "A no-fuss method for making cold brew coffee concentrate at home."

  /**
   * The simulator asks for one round per phase, and the repository dedupes a
   * batch against everything already asked — so a fake serving a fixed list
   * would fail its second round as a dead end. This hands out fresh text per
   * call, which is what a real phase-partitioned engine does.
   */
  private fun engine(): FakePlanningEngine =
    FakePlanningEngine().apply { batchFor = ::batch }

  /** A call's batch: [count] fresh questions, which nothing later can collide with. */
  private fun batch(callIndex: Int, roundId: String, count: Int = 3): List<Question> =
    (0 until count).map { index ->
      question("q$callIndex-$index", "Call $callIndex question $index", roundId = roundId)
    }

  /** A simulator over its own storage, plus the storage to read the result from. */
  private fun TestScope.harness(
    storage: FakeStorage = FakeStorage(),
    generator: PlanningEngine = engine(),
  ): Harness {
    val runner = TaskRunner(CoroutineScope(coroutineContext))
    val repository = ProjectRepository(
      storage = storage,
      engineSelector = { generator },
      taskRunner = runner,
      activityLogger = RecordingActivityLogger(),
    )
    val simulator = ProjectSimulator(
      repository = repository,
      storage = storage,
      scope = CoroutineScope(coroutineContext),
    )
    return Harness(simulator, storage, generator as? FakePlanningEngine)
  }

  private class Harness(
    val simulator: ProjectSimulator,
    val storage: FakeStorage,
    val generator: FakePlanningEngine?,
  ) {
    /** The project the run left, whichever way the run ended. */
    val project: Project
      get() = assertNotNull(
        storage.projects.values.singleOrNull(),
        "expected exactly one project, found ${storage.projects.keys}",
      )

    fun finished(): SimulationState.Finished =
      assertNotNull(simulator.state.value as? SimulationState.Finished, "the run did not finish")
  }

  // ---------- the run itself ----------

  @Test
  fun `a run generates one round in every phase in library order`() = runTest {
    val harness = harness()

    harness.simulator.simulate(SimulationConfig(synopsis))
    testScheduler.advanceUntilIdle()

    val project = harness.project
    assertEquals(
      BuiltInPhase.entries.map { it.key },
      project.rounds.map { it.phase.key },
      "one round per phase, in the library's own order",
    )
    assertEquals(BuiltInPhase.entries.size, harness.generator?.calls?.size)
  }

  @Test
  fun `each phase's generation is handed the previous phases' questions`() = runTest {
    val harness = harness()

    harness.simulator.simulate(SimulationConfig(synopsis))
    testScheduler.advanceUntilIdle()

    val calls = assertNotNull(harness.generator).calls
    assertEquals(0, calls.first().previousQuestions.size, "the first round has no history")
    calls.drop(1).forEachIndexed { index, call ->
      assertTrue(
        call.previousQuestions.isNotEmpty(),
        "round ${index + 1} was handed no prior questions, so dedupe had nothing to work against",
      )
    }
  }

  @Test
  fun `every phase's questions are resolved so the wrap-up is reachable`() = runTest {
    val harness = harness()

    harness.simulator.simulate(SimulationConfig(synopsis))
    testScheduler.advanceUntilIdle()

    val project = harness.project
    // The final phase is deliberately left with a draft (see DraftIndex), which
    // reads as unanswered; every phase before it must be fully resolved, because
    // a phase only offers its wrap-up once nothing in it is open.
    project.rounds.map { it.phase }.distinct().dropLast(1).forEach { phase ->
      val inPhase = project.questions.filter { project.phaseForQuestion(it) == phase }
      assertTrue(
        inPhase.all { it.isAnswered || it.isIgnored },
        "$phase still has an unanswered question, which would hold its wrap-up shut",
      )
    }
  }

  @Test
  fun `the run leaves a mix of answers and ignored questions and drafts in the last phase`() =
    runTest {
      val harness = harness()

      harness.simulator.simulate(SimulationConfig(synopsis))
      testScheduler.advanceUntilIdle()

      val project = harness.project
      assertTrue(project.questions.any { it.isIgnored }, "nothing was ignored")
      assertTrue(project.questions.any { it.isAnswered }, "nothing was answered")
      assertTrue(project.questions.any { it.isDraft }, "the Drafts filter has nothing in it")
      assertTrue(
        project.questions.filter { project.phaseForQuestion(it) == BuiltInPhase.DefinitionOfDone }
          .any { it.isDraft },
        "drafts are held back to the final phase, which is where they belong",
      )
    }

  @Test
  fun `leaving resolveQuestions off still generates every phase but answers nothing`() = runTest {
    val harness = harness()

    harness.simulator.simulate(SimulationConfig(synopsis, resolveQuestions = false))
    testScheduler.advanceUntilIdle()

    val project = harness.project
    assertEquals(BuiltInPhase.entries.size, project.rounds.map { it.phase }.distinct().size)
    assertTrue(project.questions.all { it.isUnanswered }, "resolveQuestions = false answered something")
  }

  // ---------- the knobs ----------

  @Test
  fun `choosing summarize compaction reaches the summarize path`() = runTest {
    val generator = engine().apply {
      canSummarize = true
      // A window small enough that the transcript overruns it, so the choice has
      // something to act on rather than being a no-op.
      contextWindowTokens = 400
    }
    val harness = harness(generator = generator)

    harness.simulator.simulate(
      SimulationConfig(synopsis, compaction = ContextCompaction.SummarizeEarlierPhases)
    )
    testScheduler.advanceUntilIdle()

    assertTrue(
      generator.summarizeCalls.isNotEmpty(),
      "SummarizeEarlierPhases never asked the engine for a summary, so the branch was not reached",
    )
  }

  @Test
  fun `choosing keep-everything never asks for a summary`() = runTest {
    val generator = engine().apply {
      canSummarize = true
      contextWindowTokens = 400
    }
    val harness = harness(generator = generator)

    harness.simulator.simulate(
      SimulationConfig(synopsis, compaction = ContextCompaction.KeepEverything)
    )
    testScheduler.advanceUntilIdle()

    assertTrue(
      generator.summarizeCalls.isEmpty(),
      "KeepEverything summarized anyway",
    )
    assertEquals(BuiltInPhase.entries.size, generator.calls.size, "the run still covers every phase")
  }

  @Test
  fun `the question cap stops the run before the last phase`() = runTest {
    val harness = harness()

    harness.simulator.simulate(SimulationConfig(synopsis, questionCap = 3))
    testScheduler.advanceUntilIdle()

    val state = harness.finished()
    assertTrue(state.stoppedAtQuestionCap, "the run did not report the cap")
    assertEquals(BuiltInPhase.entries.first(), harness.project.rounds.single().phase)
    assertEquals(1, harness.generator?.calls?.size)
  }

  // ---------- repeatability and failure ----------

  @Test
  fun `a second run replaces the first rather than stacking projects`() = runTest {
    val harness = harness()

    harness.simulator.simulate(SimulationConfig(synopsis))
    testScheduler.advanceUntilIdle()
    val first = harness.project
    assertEquals(1, harness.storage.projects.size, "the run created its project")

    harness.simulator.simulate(SimulationConfig(synopsis))
    testScheduler.advanceUntilIdle()

    assertEquals(1, harness.storage.projects.size, "the previous run's project was replaced")
  }

  @Test
  fun `a failed opening round ends the run but still leaves its project`() = runTest {
    val generator = engine().apply {
      generationFailure = IllegalStateException("engine is down")
    }
    val harness = harness(generator = generator)

    harness.simulator.simulate(SimulationConfig(synopsis))
    testScheduler.advanceUntilIdle()

    val state = harness.finished()
    assertTrue(state.failures.isNotEmpty(), "a phase that never generated anything was not reported")
    // The run asked the opening phase twice before giving up on it — and both
    // rounds latched their failure on the project, which is then a project with
    // no first phase and so no library left to walk.
    val rounds = harness.project.rounds
    assertEquals(2, rounds.size, "the opening phase was not asked a second time")
    assertTrue(rounds.all { it.outcome == RoundOutcome.Failed }, "a failed round did not latch its failure")
  }

  @Test
  fun `a failure in a later round is reported and the run carries on`() = runTest {
    val generator = engine()
    val harness = harness(
      generator = ScriptedPlanningEngine(generator, failFromCall = 2)
    )

    harness.simulator.simulate(SimulationConfig(synopsis))
    testScheduler.advanceUntilIdle()

    val state = harness.finished()
    assertEquals(
      BuiltInPhase.entries.size - 1,
      state.failures.size,
      "every phase after the first came up empty, and each is reported once",
    )
    assertEquals(
      BuiltInPhase.entries.map { it.key },
      harness.project.rounds.map { it.phase.key }.distinct(),
      "an empty phase is still a phase the run moved past",
    )
  }

  // ---------- a phase that comes back empty ----------

  @Test
  fun `a phase that generates nothing is asked again`() = runTest {
    val generator = engine().apply {
      // Phase 2's opening round (call 1) comes back empty; the run's second ask,
      // and everything after it, serves questions.
      batchFor = { callIndex, roundId -> if (callIndex == 1) emptyList() else batch(callIndex, roundId) }
    }
    val harness = harness(generator = generator)

    harness.simulator.simulate(SimulationConfig(synopsis))
    testScheduler.advanceUntilIdle()

    val project = harness.project
    BuiltInPhase.entries.forEach { phase ->
      assertTrue(
        project.questions.any { project.phaseForQuestion(it) == phase },
        "$phase ended up empty, so the run never asked for it twice",
      )
    }
    assertTrue(
      harness.finished().failures.isEmpty(),
      "a round that failed and then landed questions is nothing to report",
    )
  }

  @Test
  fun `a phase that stays empty is reported once and the run carries on by default`() = runTest {
    val harness = harness(generator = engineEmptyFrom(BuiltInPhase.Research))

    harness.simulator.simulate(SimulationConfig(synopsis))
    testScheduler.advanceUntilIdle()

    val project = harness.project
    assertTrue(
      project.questions.none { project.phaseForQuestion(it) == BuiltInPhase.Research },
      "the empty phase was supposed to stay empty",
    )
    assertEquals(
      BuiltInPhase.entries.map { it.key },
      project.rounds.map { it.phase.key }.distinct(),
      "the phases after the empty one were still generated",
    )
    val state = harness.finished()
    assertEquals(1, state.failures.size, "one thing went wrong in that phase, so it is reported once")
    assertTrue(!state.stoppedAtEmptyPhase, "stopping is off unless the run asks for it")
    assertEquals(
      BuiltInPhase.entries.size - 1,
      state.phasesCovered,
      "the report counts the phases that landed questions, so the hole shows",
    )
  }

  @Test
  fun `stopping on an empty phase ends the run there`() = runTest {
    val harness = harness(generator = engineEmptyFrom(BuiltInPhase.Design))

    harness.simulator.simulate(SimulationConfig(synopsis, stopOnEmptyPhase = true))
    testScheduler.advanceUntilIdle()

    val state = harness.finished()
    assertTrue(state.stoppedAtEmptyPhase, "the run did not report stopping at the empty phase")
    assertEquals(
      listOf(BuiltInPhase.ScopeGoals, BuiltInPhase.Research, BuiltInPhase.Design),
      harness.project.rounds.map { it.phase }.distinct(),
      "the run kept walking past the phase it was told to stop at",
    )
  }

  @Test
  fun `a failed opening round is asked again before the run gives up on it`() = runTest {
    val harness = harness(generator = FailFirstPlanningEngine(engine(), failures = 1))

    harness.simulator.simulate(SimulationConfig(synopsis))
    testScheduler.advanceUntilIdle()

    val project = harness.project
    assertTrue(project.questions.isNotEmpty(), "the recovered opening round left the project empty")
    assertEquals(
      BuiltInPhase.entries.map { it.key },
      project.rounds.map { it.phase.key }.distinct(),
      "a project that recovered its first round walks the whole library",
    )
  }

  @Test
  fun `an engine that reports a phase done is not asked again`() = runTest {
    // Every phase answers `done` with nothing, which is the one empty phase that
    // is the engine's answer rather than a miss — so nothing is retried, and
    // nothing is a failure.
    val generator = engine().apply {
      done = true
      batchFor = { _, _ -> emptyList() }
    }
    val harness = harness(generator = generator)

    harness.simulator.simulate(SimulationConfig(synopsis))
    testScheduler.advanceUntilIdle()

    val state = harness.finished()
    assertEquals(
      BuiltInPhase.entries.size,
      generator.calls.size,
      "a phase the engine reported done was asked a second time",
    )
    assertEquals(0, state.questions)
    assertTrue(state.failures.isEmpty(), "an engine that answered done did not fail")
    assertTrue(!state.stoppedAtEmptyPhase, "a phase that is done is not an empty phase to stop at")
  }

  /**
   * An engine that serves nothing at all for one phase — and keeps serving
   * nothing for it, which is what "asked again and got the same answer" has to
   * look like from the run's side of the seam.
   */
  private fun engineEmptyFrom(phase: Phase): FakePlanningEngine =
    engine().apply {
      batchFor = { callIndex, roundId ->
        if (calls[callIndex].phase == phase) emptyList() else batch(callIndex, roundId)
      }
    }

  @Test
  fun `cancelling mid-round leaves the phases that already landed`() = runTest {
    val delegate = engine()
    // The third round parks here, so the run is suspended inside a live task when
    // the cancel lands — the only moment worth testing.
    val gate = CompletableDeferred<Unit>()
    val harness = harness(generator = ScriptedPlanningEngine(delegate, parkOnCall = 3, gate = gate))

    val run = launch { harness.simulator.simulate(SimulationConfig(synopsis)) }
    testScheduler.runCurrent()
    run.cancelAndJoin()
    // The parked task was never cancelled (await does not touch what it waits on),
    // so it has to be let go before the scheduler can drain.
    gate.complete(Unit)
    testScheduler.advanceUntilIdle()

    assertEquals(SimulationState.Cancelled, harness.simulator.state.value)
    val project = harness.project
    // The third round was opened before the cancel and its questions still landed
    // (the task was never cancelled) — they are simply unresolved, which is what a
    // stop partway through the library looks like.
    assertEquals(3, project.rounds.size, "the rounds opened before the cancel are intact")
    val resolved = project.rounds.dropLast(1).flatMap { round ->
      project.questions.filter { it.roundId == round.id }
    }
    assertTrue(resolved.isNotEmpty())
    assertTrue(
      resolved.all { it.isAnswered || it.isIgnored },
      "the phases the run finished are still resolved",
    )
    val interrupted = project.questions.filter { it.roundId == project.rounds.last().id }
    assertTrue(
      interrupted.isNotEmpty() && interrupted.all { it.isUnanswered },
      "the phase the cancel landed in was resolved anyway",
    )
  }
}

/**
 * A [PlanningEngine] that scripts one run's generation: park inside a numbered
 * call until released, fail from a numbered call on, or neither. Both are needed
 * because the only moments a run's failure and cancellation handling can be
 * observed at are the ones a fake that finishes instantly never reaches.
 */
private class ScriptedPlanningEngine(
  private val delegate: PlanningEngine,
  private val parkOnCall: Int? = null,
  private val gate: CompletableDeferred<Unit>? = null,
  private val failFromCall: Int? = null,
) : PlanningEngine {
  private var calls = 0

  override val source get() = delegate.source
  override val contextWindowTokens get() = delegate.contextWindowTokens
  override val canSummarize get() = delegate.canSummarize

  override suspend fun recommendTitle(synopsis: String, activityId: String): String =
    delegate.recommendTitle(synopsis, activityId)

  override suspend fun summarizePriorAnswers(
    title: String,
    synopsis: String,
    phase: Phase,
    transcript: String,
    activityId: String,
  ): String = delegate.summarizePriorAnswers(title, synopsis, phase, transcript, activityId)

  override suspend fun generateQuestions(
    title: String,
    synopsis: String,
    previousQuestions: List<Question>,
    roundId: String,
    phase: Phase,
    activityId: String,
    priorSummaries: List<PlanningContext.PhaseSummary>,
  ): QuestionBatch {
    calls += 1
    val batch = delegate.generateQuestions(
      title,
      synopsis,
      previousQuestions,
      roundId,
      phase,
      activityId,
      priorSummaries,
    )
    if (failFromCall != null && calls >= failFromCall) {
      throw IllegalStateException("engine call $calls failed")
    }
    if (parkOnCall != null && calls == parkOnCall) gate?.await()
    return batch
  }
}

/**
 * A [PlanningEngine] that fails its first [failures] generations and delegates
 * the rest, so a run's second ask at a phase can be the one that lands.
 */
private class FailFirstPlanningEngine(
  private val delegate: PlanningEngine,
  private val failures: Int,
) : PlanningEngine {
  private var calls = 0

  override val source get() = delegate.source
  override val contextWindowTokens get() = delegate.contextWindowTokens
  override val canSummarize get() = delegate.canSummarize

  override suspend fun recommendTitle(synopsis: String, activityId: String): String =
    delegate.recommendTitle(synopsis, activityId)

  override suspend fun summarizePriorAnswers(
    title: String,
    synopsis: String,
    phase: Phase,
    transcript: String,
    activityId: String,
  ): String = delegate.summarizePriorAnswers(title, synopsis, phase, transcript, activityId)

  override suspend fun generateQuestions(
    title: String,
    synopsis: String,
    previousQuestions: List<Question>,
    roundId: String,
    phase: Phase,
    activityId: String,
    priorSummaries: List<PlanningContext.PhaseSummary>,
  ): QuestionBatch {
    calls += 1
    if (calls <= failures) throw IllegalStateException("generation call $calls failed")
    return delegate.generateQuestions(
      title,
      synopsis,
      previousQuestions,
      roundId,
      phase,
      activityId,
      priorSummaries,
    )
  }
}
