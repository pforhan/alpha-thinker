package alphainterplanetary.thinker.engine

import alphainterplanetary.thinker.activitylog.LogSource
import alphainterplanetary.thinker.phases.BuiltInPhase
import alphainterplanetary.thinker.testutil.FakePlanningEngine
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
    val delegate = FakePlanningEngine()
    val generator = SlowDownPlanningEngine(
      delegate = delegate,
      config = MutableStateFlow(EngineDelayConfig(enabled = false)),
    )

    generator.recommendTitle("synopsis", activityId = "test-activity")

    assertEquals(1, delegate.titleCalls)
  }

  @Test
  fun `reports the delegated engine's source`() {
    val generator = SlowDownPlanningEngine(
      delegate = FakePlanningEngine(source = LogSource.RemoteLLM),
      config = MutableStateFlow(EngineDelayConfig(enabled = false)),
    )

    assertEquals(LogSource.RemoteLLM, generator.source)
  }

  @Test
  fun `delays before delegating when enabled`() = runTest {
    val delegate = FakePlanningEngine()
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
    val delegate = FakePlanningEngine()
    val generator = SlowDownPlanningEngine(
      delegate = delegate,
      config = MutableStateFlow(
        EngineDelayConfig(
          enabled = true,
          secondsByInteraction = mapOf(
            EngineInteraction.RecommendTitle to 2,
            EngineInteraction.QuestionGeneration to 5,
          ),
        ),
      ),
    )

    val job = launch {
      generator.recommendTitle("synopsis", activityId = "test-activity")
      generator.generateQuestions("title", "synopsis", emptyList(), "r1", BuiltInPhase.ScopeGoals, activityId = "test-activity")
    }
    testScheduler.runCurrent()
    assertEquals(0, delegate.calls.size + delegate.titleCalls)

    testScheduler.advanceTimeBy(2_000)
    testScheduler.runCurrent()
    assertEquals(1, delegate.titleCalls)
    assertEquals(0, delegate.calls.size)
    testScheduler.advanceTimeBy(5_000)
    testScheduler.runCurrent()
    assertEquals(1, delegate.calls.size)

    job.join()
  }

  @Test
  fun `a zero delay for an interaction means no delay`() = runTest {
    val delegate = FakePlanningEngine()
    val generator = SlowDownPlanningEngine(
      delegate = delegate,
      config = MutableStateFlow(
        EngineDelayConfig(
          enabled = true,
          secondsByInteraction = mapOf(
            EngineInteraction.RecommendTitle to 0,
            EngineInteraction.QuestionGeneration to 2,
          ),
        ),
      ),
    )

    val job = launch {
      generator.recommendTitle("synopsis", activityId = "test-activity")
      generator.generateQuestions("title", "synopsis", emptyList(), "r1", BuiltInPhase.ScopeGoals, activityId = "test-activity")
    }
    testScheduler.runCurrent()
    assertEquals(1, delegate.titleCalls)
    assertEquals(0, delegate.calls.size)

    testScheduler.advanceTimeBy(2_000)
    testScheduler.runCurrent()
    assertEquals(1, delegate.calls.size)

    job.join()
  }

  @Test
  fun `turning the flag off removes the delay for later calls`() = runTest {
    val config = MutableStateFlow(
      EngineDelayConfig(enabled = true, secondsByInteraction = defaultSecondsByInteraction),
    )
    val delegate = FakePlanningEngine()
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
