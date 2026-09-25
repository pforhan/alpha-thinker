package alphainterplanetary.thinker.engine

import alphainterplanetary.thinker.activitylog.LogCategory
import alphainterplanetary.thinker.activitylog.LogSource
import alphainterplanetary.thinker.model.Question
import alphainterplanetary.thinker.phases.BuiltInPhase
import alphainterplanetary.thinker.phases.Phase
import alphainterplanetary.thinker.testutil.FakePlanningEngine
import alphainterplanetary.thinker.testutil.RecordingActivityLogger
import alphainterplanetary.thinker.util.now
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class LoggingPlanningEngineTest {

  @Test
  fun `recommendation records input then response rows under the activity id`() = runTest {
    val log = RecordingActivityLogger()
    val engine = LoggingPlanningEngine(delegate = FakePlanningEngine(), log = log)

    val title = engine.recommendTitle("Build a rocketship", activityId = "task-1")

    assertEquals("Recommended", title)
    assertEquals(2, log.entries.size)
    val input = log.entries[0]
    assertEquals("task-1", input.activityId)
    assertEquals(LogCategory.TitleRecommendation, input.category)
    assertEquals(LogSource.Lite, input.source)
    assertEquals("input: synopsis=Build a rocketship", input.log)
    val terminal = log.entries[1]
    assertEquals("task-1", terminal.activityId)
    assertEquals("response: Recommended", terminal.log)
    assertEquals(LogCategory.TitleRecommendation, terminal.category)
    assertTrue(terminal.timestamp >= input.timestamp)
  }

  @Test
  fun `initial questions record the produced texts in the response row`() = runTest {
    val delegate = FakePlanningEngine()
    delegate.initialQuestions += question("first")
    delegate.initialQuestions += question("second")
    val log = RecordingActivityLogger()
    val engine = LoggingPlanningEngine(delegate = delegate, log = log)

    val questions = engine.generateInitialQuestions(
      editableTitle = "T",
      synopsis = "S",
      roundId = "r1",
      phase = BuiltInPhase.ScopeGoals,
      activityId = "task-2",
    ).questions

    assertEquals(2, questions.size)
    val terminal = log.entries.last()
    assertEquals(LogCategory.QuestionGeneration, terminal.category)
    assertEquals(2, log.entries.size, "input + response, nothing else")
    assertTrue(terminal.log.startsWith("response: 2 questions, done=false"))
    assertTrue(terminal.log.contains("first"))
    assertTrue(terminal.log.contains("second"))
  }

  @Test
  fun `can produce more in phase records the capability answer`() = runTest {
    val delegate = FakePlanningEngine()
    delegate.canProduceMore = true
    val log = RecordingActivityLogger()
    val engine = LoggingPlanningEngine(delegate = delegate, log = log)

    val can = engine.canProduceMoreInPhase(
      synopsis = "S",
      previousQuestions = emptyList(),
      phase = BuiltInPhase.ScopeGoals,
      activityId = "task-3",
    )

    assertTrue(can)
    val terminal = log.entries.last()
    assertEquals(LogCategory.CapabilityCheck, terminal.category)
    assertEquals("response: canProduceMore=true", terminal.log)
  }

  @Test
  fun `follow-up questions record the done signal in the response row`() = runTest {
    val delegate = FakePlanningEngine()
    delegate.followUpDone = true
    val log = RecordingActivityLogger()
    val engine = LoggingPlanningEngine(delegate = delegate, log = log)

    engine.generateFollowUpQuestions(
      synopsis = "S",
      previousQuestions = emptyList(),
      roundId = "r1",
      phase = BuiltInPhase.ScopeGoals,
      activityId = "task-3",
    )

    val terminal = log.entries.last()
    assertEquals(LogCategory.QuestionGeneration, terminal.category)
    assertTrue(terminal.log.startsWith("response: 0 questions, done=true"))
  }

  @Test
  fun `records the delegated engine's source on detail rows`() = runTest {
    val delegate = FakePlanningEngine()
    delegate.source = LogSource.RemoteLLM
    val log = RecordingActivityLogger()
    val engine = LoggingPlanningEngine(delegate = delegate, log = log)

    engine.recommendTitle("Build a rocketship", activityId = "task-5")

    assertEquals(LogSource.RemoteLLM, log.entries.first().source)
  }

  @Test
  fun `records the full prompt when the delegate renders prompts`() = runTest {
    val delegate = FakePlanningEngine().let { engine ->
      object : PlanningEngine by engine, PromptRenderer {
        override fun titlePrompt(synopsis: String): String =
          "SYSTEM\nTitle system\n\nUSER\n$synopsis"

        override fun initialQuestionsPrompt(
          editableTitle: String,
          synopsis: String,
          phase: Phase,
        ): String = "SYSTEM\nQuestions system\n\nUSER\n$editableTitle / $synopsis / ${phase.label}"

        override fun followUpQuestionsPrompt(
          synopsis: String,
          previousQuestions: List<Question>,
          phase: Phase,
        ): String = "SYSTEM\nQuestions system\n\nUSER\n$synopsis"
      }
    }
    val log = RecordingActivityLogger()
    val engine = LoggingPlanningEngine(delegate = delegate, log = log)

    engine.recommendTitle("Build a rocketship", activityId = "task-6")

    assertEquals(
      "prompt: SYSTEM\nTitle system\n\nUSER\nBuild a rocketship",
      log.entries.first().log,
    )
  }

  @Test
  fun `leaves prompt unused null to a non-rendering delegate`() = runTest {
    val log = RecordingActivityLogger()
    val engine = LoggingPlanningEngine(delegate = FakePlanningEngine(), log = log)

    engine.recommendTitle("Build a rocketship", activityId = "task-7")

    assertTrue(log.entries.first().log.startsWith("input: synopsis="))
  }

  @Test
  fun `a throwing engine records a failed row and still propagates`() = runTest {
    val log = RecordingActivityLogger()
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

    val terminal = log.entries.last()
    assertEquals(LogCategory.QuestionGeneration, terminal.category)
    assertEquals("task-4", terminal.activityId)
    assertEquals("failed: model exploded", terminal.log)
  }

  private fun question(text: String): Question =
    Question(id = text, text = text, timestamp = now(), roundId = "r1")

  private class ThrowingEngine : PlanningEngine {
    override val source: LogSource = LogSource.Lite

    override suspend fun recommendTitle(synopsis: String, activityId: String): String =
      throw PlanningEngine.AnalysisFailure("model exploded")

    override suspend fun generateInitialQuestions(
      editableTitle: String,
      synopsis: String,
      roundId: String,
      phase: Phase,
      activityId: String,
    ): QuestionBatch = throw PlanningEngine.AnalysisFailure("model exploded")

    override suspend fun generateFollowUpQuestions(
      synopsis: String,
      previousQuestions: List<Question>,
      roundId: String,
      phase: Phase,
      activityId: String,
    ): QuestionBatch = throw PlanningEngine.AnalysisFailure("model exploded")

    override suspend fun canProduceMoreInPhase(
      synopsis: String,
      previousQuestions: List<Question>,
      phase: Phase,
      activityId: String,
    ): Boolean = throw PlanningEngine.AnalysisFailure("model exploded")
  }
}