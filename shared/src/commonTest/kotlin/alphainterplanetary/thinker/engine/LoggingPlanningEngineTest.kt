package alphainterplanetary.thinker.engine

import alphainterplanetary.thinker.activitylog.EngineActivityEventType
import alphainterplanetary.thinker.activitylog.EngineKind
import alphainterplanetary.thinker.model.Question
import alphainterplanetary.thinker.phases.BuiltInPhase
import alphainterplanetary.thinker.phases.Phase
import alphainterplanetary.thinker.tasks.TaskKind
import alphainterplanetary.thinker.testutil.FakePlanningEngine
import alphainterplanetary.thinker.testutil.RecordingActivityLog
import alphainterplanetary.thinker.util.now
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

class LoggingPlanningEngineTest {

  @Test
  fun `recommendation records created and succeeded detail rows under the activity id`() = runTest {
    val log = RecordingActivityLog()
    val engine = LoggingPlanningEngine(delegate = FakePlanningEngine(), log = log)

    val title = engine.recommendTitle("Build a rocketship", activityId = "task-1")

    assertEquals("Recommended", title)
    assertEquals(2, log.events.size)
    val created = log.events[0]
    assertEquals("task-1", created.activityId)
    assertEquals(EngineActivityEventType.Created, created.eventType)
    assertEquals(TaskKind.TitleRecommendation, created.kind)
    assertEquals(EngineKind.Hardcoded, created.engine)
    assertTrue(created.parameters.orEmpty().contains("synopsis=Build a rocketship"))
    val terminal = log.events[1]
    assertEquals(EngineActivityEventType.Succeeded, terminal.eventType)
    assertEquals("task-1", terminal.activityId)
    assertEquals("Recommended", terminal.generationPayload)
    assertNotNull(terminal.durationMs)
    assertNull(terminal.error)
  }

  @Test
  fun `initial questions record the produced texts as suggested questions`() = runTest {
    val delegate = FakePlanningEngine()
    delegate.initialQuestions += question("first")
    delegate.initialQuestions += question("second")
    val log = RecordingActivityLog()
    val engine = LoggingPlanningEngine(delegate = delegate, log = log)

    val questions = engine.generateInitialQuestions(
      editableTitle = "T",
      synopsis = "S",
      roundId = "r1",
      phase = BuiltInPhase.ScopeGoals,
      activityId = "task-2",
    )

    assertEquals(2, questions.size)
    val terminal = log.events.last()
    assertEquals(EngineActivityEventType.Succeeded, terminal.eventType)
    assertEquals(TaskKind.InitialQuestions, terminal.kind)
    assertEquals("r1", terminal.roundId)
    assertTrue(terminal.suggestedQuestions.orEmpty().contains("first"))
    assertTrue(terminal.suggestedQuestions.orEmpty().contains("second"))
  }

  @Test
  fun `remaining in phase records the count as generation payload`() = runTest {
    val delegate = FakePlanningEngine()
    delegate.remaining = 4
    val log = RecordingActivityLog()
    val engine = LoggingPlanningEngine(delegate = delegate, log = log)

    val remaining = engine.remainingInPhase(
      synopsis = "S",
      previousQuestions = emptyList(),
      phase = BuiltInPhase.ScopeGoals,
      activityId = "task-3",
    )

    assertEquals(4, remaining)
    val terminal = log.events.last()
    assertEquals(EngineActivityEventType.Succeeded, terminal.eventType)
    assertEquals(TaskKind.RemainingInPhase, terminal.kind)
    assertEquals("4", terminal.generationPayload)
  }

  @Test
  fun `a throwing engine records a failed detail row and still propagates`() = runTest {
    val log = RecordingActivityLog()
    val engine = LoggingPlanningEngine(delegate = ThrowingEngine(), log = log)

    try {
      engine.generateFollowUpQuestions(
        synopsis = "S",
        previousQuestions = emptyList(),
        roundId = "r9",
        phase = BuiltInPhase.Design,
        activityId = "task-4",
      )
      fail("expected the engine failure to propagate")
    } catch (e: PlanningEngine.AnalysisFailure) {
      assertEquals("model exploded", e.message)
    }

    val terminal = log.events.last()
    assertEquals(EngineActivityEventType.Failed, terminal.eventType)
    assertEquals("task-4", terminal.activityId)
    assertEquals("model exploded", terminal.error)
    assertNotNull(terminal.durationMs, "a failed call still records how long it burned before erroring")
  }

  private fun question(text: String): Question =
    Question(id = text, text = text, timestamp = now(), roundId = "r1")

  private class ThrowingEngine : PlanningEngine {
    override suspend fun recommendTitle(synopsis: String, activityId: String): String =
      throw PlanningEngine.AnalysisFailure("model exploded")

    override suspend fun generateInitialQuestions(
      editableTitle: String,
      synopsis: String,
      roundId: String,
      phase: Phase,
      activityId: String,
    ): List<Question> = throw PlanningEngine.AnalysisFailure("model exploded")

    override suspend fun generateFollowUpQuestions(
      synopsis: String,
      previousQuestions: List<Question>,
      roundId: String,
      phase: Phase,
      activityId: String,
    ): List<Question> = throw PlanningEngine.AnalysisFailure("model exploded")

    override suspend fun remainingInPhase(
      synopsis: String,
      previousQuestions: List<Question>,
      phase: Phase,
      activityId: String,
    ): Int = throw PlanningEngine.AnalysisFailure("model exploded")
  }
}