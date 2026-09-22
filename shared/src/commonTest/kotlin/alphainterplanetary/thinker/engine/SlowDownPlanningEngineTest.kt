package alphainterplanetary.thinker.engine

import alphainterplanetary.thinker.model.Question
import alphainterplanetary.thinker.phases.BuiltInPhase
import alphainterplanetary.thinker.phases.Phase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class SlowDownPlanningEngineTest {

  private val defaultSecondsByInteraction: Map<EngineInteraction, Int> =
    EngineInteraction.entries.associateWith {
      EngineDelayConfig.DelayOptionsSeconds.first()
    }

  @Test
  fun `does not delay when the slow-down flag is off`() = runTest {
    val delegate = TrackingPlanningEngine()
    val generator = SlowDownPlanningEngine(
      delegate = delegate,
      config = MutableStateFlow(EngineDelayConfig(enabled = false)),
    )

    generator.recommendTitle("synopsis", activityId = "test-activity")

    assertEquals(1, delegate.titleCalls)
  }

  @Test
  fun `delays before delegating when enabled`() = runTest {
    val delegate = TrackingPlanningEngine()
    val generator = SlowDownPlanningEngine(
      delegate = delegate,
      config = MutableStateFlow(
        EngineDelayConfig(enabled = true, secondsByInteraction = defaultSecondsByInteraction),
      ),
    )

    val job = launch { generator.recommendTitle("synopsis", activityId = "test-activity") }
    testScheduler.runCurrent()
    assertEquals(0, delegate.titleCalls)

    testScheduler.advanceTimeBy(2_000)
    testScheduler.runCurrent()
    assertEquals(1, delegate.titleCalls)

    job.join()
  }

  @Test
  fun `every interaction is slowed with its own delay`() = runTest {
    val delegate = TrackingPlanningEngine()
    val generator = SlowDownPlanningEngine(
      delegate = delegate,
      config = MutableStateFlow(
        EngineDelayConfig(
          enabled = true,
          secondsByInteraction = mapOf(
            EngineInteraction.InitialQuestions to 2,
            EngineInteraction.FollowUpQuestions to 5,
            EngineInteraction.RemainingInPhase to 30,
          ),
        ),
      ),
    )

    val job = launch {
      generator.generateInitialQuestions("title", "synopsis", "r1", BuiltInPhase.ScopeGoals, activityId = "test-activity")
      generator.generateFollowUpQuestions("synopsis", emptyList(), "r2", BuiltInPhase.ScopeGoals, activityId = "test-activity")
      generator.canProduceMoreInPhase("synopsis", emptyList(), BuiltInPhase.ScopeGoals, activityId = "test-activity")
    }
    testScheduler.runCurrent()
    assertEquals(0, delegate.totalCalls())

    testScheduler.advanceTimeBy(2_000)
    testScheduler.runCurrent()
    assertEquals(1, delegate.initialCalls)
    assertEquals(0, delegate.followUpCalls)
    assertEquals(0, delegate.remainingCalls)

    testScheduler.advanceTimeBy(5_000)
    testScheduler.runCurrent()
    assertEquals(1, delegate.followUpCalls)
    assertEquals(0, delegate.remainingCalls)

    testScheduler.advanceTimeBy(30_000)
    testScheduler.runCurrent()
    assertEquals(1, delegate.remainingCalls)

    job.join()
  }

  @Test
  fun `a zero delay for an interaction means no delay`() = runTest {
    val delegate = TrackingPlanningEngine()
    val generator = SlowDownPlanningEngine(
      delegate = delegate,
      config = MutableStateFlow(
        EngineDelayConfig(
          enabled = true,
          secondsByInteraction = mapOf(
            EngineInteraction.RecommendTitle to 0,
            EngineInteraction.InitialQuestions to 2,
          ),
        ),
      ),
    )

    val job = launch {
      generator.recommendTitle("synopsis", activityId = "test-activity")
      generator.generateInitialQuestions("title", "synopsis", "r1", BuiltInPhase.ScopeGoals, activityId = "test-activity")
    }
    testScheduler.runCurrent()
    assertEquals(1, delegate.titleCalls)
    assertEquals(0, delegate.initialCalls)

    testScheduler.advanceTimeBy(2_000)
    testScheduler.runCurrent()
    assertEquals(1, delegate.initialCalls)

    job.join()
  }

  @Test
  fun `turning the flag off removes the delay for later calls`() = runTest {
    val config = MutableStateFlow(
      EngineDelayConfig(enabled = true, secondsByInteraction = defaultSecondsByInteraction),
    )
    val delegate = TrackingPlanningEngine()
    val generator = SlowDownPlanningEngine(delegate = delegate, config = config)

    val first = launch { generator.recommendTitle("a", activityId = "test-activity") }
    testScheduler.runCurrent()
    assertEquals(0, delegate.titleCalls)
    testScheduler.advanceTimeBy(2_000)
    testScheduler.runCurrent()
    assertEquals(1, delegate.titleCalls)
    first.join()

    config.value = config.value.copy(enabled = false)
    val second = launch { generator.recommendTitle("b", activityId = "test-activity") }
    testScheduler.runCurrent()
    assertEquals(2, delegate.titleCalls)
    second.join()
  }
}

/** Counts calls into each [PlanningEngine] interaction. */
private class TrackingPlanningEngine : PlanningEngine {
  var titleCalls: Int = 0
  var initialCalls: Int = 0
  var followUpCalls: Int = 0
  var remainingCalls: Int = 0

  fun totalCalls(): Int = titleCalls + initialCalls + followUpCalls + remainingCalls

  override suspend fun recommendTitle(synopsis: String, activityId: String): String {
    titleCalls++
    return "title"
  }

  override suspend fun generateInitialQuestions(
    editableTitle: String,
    synopsis: String,
    roundId: String,
    phase: Phase,
    activityId: String,
  ): QuestionBatch {
    initialCalls++
    return QuestionBatch(emptyList(), done = true)
  }

  override suspend fun generateFollowUpQuestions(
    synopsis: String,
    previousQuestions: List<Question>,
    roundId: String,
    phase: Phase,
    activityId: String,
  ): QuestionBatch {
    followUpCalls++
    return QuestionBatch(emptyList(), done = true)
  }

  override suspend fun canProduceMoreInPhase(
    synopsis: String,
    previousQuestions: List<Question>,
    phase: Phase,
    activityId: String,
  ): Boolean {
    remainingCalls++
    return false
  }
}