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
  fun `questions record the produced texts in the response row`() = runTest {
    val delegate = FakePlanningEngine()
    delegate.questions += question("first")
    delegate.questions += question("second")
    val log = RecordingActivityLogger()
    val engine = LoggingPlanningEngine(delegate = delegate, log = log)

    val questions = engine.generateQuestions(
      title = "T",
      synopsis = "S",
      previousQuestions = emptyList(),
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
  fun `questions record the done signal in the response row`() = runTest {
    val delegate = FakePlanningEngine()
    delegate.done = true
    val log = RecordingActivityLogger()
    val engine = LoggingPlanningEngine(delegate = delegate, log = log)

    engine.generateQuestions(
      title = "T",
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

  /**
   * The compact `input:` summary a non-rendering delegate gets has to name the
   * whole context now that there is one call: the title is in it, and so is the
   * size of the transcript.
   */
  @Test
  fun `a non-rendering delegate gets one input summary covering the whole context`() = runTest {
    val log = RecordingActivityLogger()
    val engine = LoggingPlanningEngine(delegate = FakePlanningEngine(), log = log)

    engine.generateQuestions(
      title = "T",
      synopsis = "S",
      previousQuestions = listOf(question("Already asked")),
      roundId = "r1",
      phase = BuiltInPhase.ScopeGoals,
      activityId = "task-8",
    )

    assertEquals(
      "input: phase=ScopeGoals, synopsis=S, previous questions=1",
      log.entries.first().log,
    )
  }

  @Test
  fun `a rendering delegate has its questions prompt filed instead`() = runTest {
    val log = RecordingActivityLogger()
    val engine = LoggingPlanningEngine(delegate = PromptRenderingEngine(), log = log)

    engine.generateQuestions(
      title = "Menu Planner",
      synopsis = "S",
      previousQuestions = listOf(question("Already asked")),
      roundId = "r1",
      phase = BuiltInPhase.ScopeGoals,
      activityId = "task-9",
    )

    assertEquals(
      "prompt: SYSTEM\nQuestions system\n\nUSER\nMenu Planner / S / Scope & Goals / 1",
      log.entries.first().log,
    )
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
    val log = RecordingActivityLogger()
    val engine = LoggingPlanningEngine(delegate = PromptRenderingEngine(), log = log)

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
      engine.generateQuestions(
        title = "T",
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

  private class PromptRenderingEngine : PlanningEngine by FakePlanningEngine(), PromptRenderer {
    override fun titlePrompt(synopsis: String): String =
      "SYSTEM\nTitle system\n\nUSER\n$synopsis"

    override fun questionsPrompt(
      title: String,
      synopsis: String,
      previousQuestions: List<Question>,
      phase: Phase,
    ): String =
      "SYSTEM\nQuestions system\n\nUSER\n$title / $synopsis / ${phase.label} / ${previousQuestions.size}"
  }

  private class ThrowingEngine : PlanningEngine {
    override val source: LogSource = LogSource.Lite

    override suspend fun recommendTitle(synopsis: String, activityId: String): String =
      throw PlanningEngine.AnalysisFailure("model exploded")

    override suspend fun generateQuestions(
      title: String,
      synopsis: String,
      previousQuestions: List<Question>,
      roundId: String,
      phase: Phase,
      activityId: String,
    ): QuestionBatch = throw PlanningEngine.AnalysisFailure("model exploded")
  }
}