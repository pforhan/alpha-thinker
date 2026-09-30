package alphainterplanetary.thinker.engine

import alphainterplanetary.thinker.activitylog.ActivityRecord
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
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The decorator's remaining job is narrow: install the interaction's [LogScope],
 * and file the interaction's *own* outcome row only when the delegate reported
 * no request of its own (the Lite path, and any engine that fails before it
 * sends). Everything else is the engine's, one row pair per request.
 */
class LoggingPlanningEngineTest {

  @Test
  fun `a recommendation records its result under the activity id`() = runTest {
    val log = RecordingActivityLogger()
    val engine = LoggingPlanningEngine(delegate = FakePlanningEngine(), log = log)

    val title = engine.recommendTitle("Build a rocketship", activityId = "task-1")

    assertEquals("Recommended", title)
    assertEquals(1, log.entries.size, "nothing was sent to a model, so there is no prompt row")
    val terminal = log.entries.single()
    assertEquals("task-1", terminal.activityId)
    assertEquals(LogCategory.TitleRecommendation, terminal.category)
    assertEquals(LogSource.Lite, terminal.source)
    assertEquals("response: Recommended", terminal.log)
    assertNull(terminal.raw, "an engine that never spoke to a model has no reply to keep")
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
    assertEquals(1, log.entries.size, "the result row, nothing else")
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

  /**
   * The no-double-file rule: an engine that reported a request has already
   * written the activity's outcome, so the decorator's own row would be a
   * second, contradicting headline.
   */
  @Test
  fun `an engine that reported its own request files no second outcome row`() = runTest {
    val log = RecordingActivityLogger()
    val engine = LoggingPlanningEngine(delegate = ReplyingEngine(), log = log)

    engine.recommendTitle("A menu planner", activityId = "task-6")

    assertEquals(
      listOf("prompt: SYSTEM\nTitle system\n\nUSER\nA menu planner", "response: Mobile Menu Planner"),
      log.entries.map { it.log },
    )
  }

  /**
   * An interaction that fans out files one pair per request, all under the one
   * activity id, and the read model takes the activity's headline and batch
   * count from the *last* pair — so the request the activity is really about has
   * to be filed last.
   */
  @Test
  fun `two requests in one interaction file two pairs and the last drives the headline`() = runTest {
    val log = RecordingActivityLogger()
    val engine = LoggingPlanningEngine(delegate = SummarizingThenGenerating(), log = log)

    engine.generateQuestions(
      title = "T",
      synopsis = "S",
      previousQuestions = emptyList(),
      roundId = "r1",
      phase = BuiltInPhase.ScopeGoals,
      activityId = "task-7",
    )

    assertEquals(
      listOf(
        "prompt: SYSTEM\nSummarize",
        "response: a summary of earlier answers",
        "prompt: SYSTEM\nQuestions",
        "response: 3 questions, done=false\n• one\n• two\n• three",
      ),
      log.entries.map { it.log },
    )
    assertTrue(log.entries.all { it.activityId == "task-7" })
    assertEquals("Generated 3 questions", activity(log).summary)
  }

  /**
   * A sub-request's failure is the activity's failure — it is filed on the
   * sub-request's own row, and the parent neither swallows it nor re-files it.
   */
  @Test
  fun `a failed sub-request surfaces as the activity's failure headline`() = runTest {
    val log = RecordingActivityLogger()
    val engine = LoggingPlanningEngine(
      delegate = SummarizingThenGenerating(failSummary = true),
      log = log,
    )

    try {
      engine.generateQuestions(
        title = "T",
        synopsis = "S",
        previousQuestions = emptyList(),
        roundId = "r1",
        phase = BuiltInPhase.ScopeGoals,
        activityId = "task-8",
      )
      fail("expected the sub-request failure to propagate")
    } catch (e: PlanningEngine.AnalysisFailure) {
      assertEquals("couldn't be read", e.message)
    }

    assertEquals(
      listOf("prompt: SYSTEM\nSummarize", "failed: couldn't be read"),
      log.entries.map { it.log },
    )
    assertEquals("I am not able to help.", log.entries.last().raw, "the reply that caused it")
    val activity = activity(log)
    assertTrue(activity.hasError)
    assertEquals("failed: couldn't be read", activity.summary)
  }

  private fun activity(log: RecordingActivityLogger): ActivityRecord =
    ActivityRecord.groupByActivity(log.entries).single()

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

  /**
   * Stands in for an LLM-backed engine: files one request the way
   * [KoogPlanningEngine] does, reporting the reply it read.
   */
  private class ReplyingEngine : PlanningEngine {
    override val source: LogSource = LogSource.RemoteLLM

    override suspend fun recommendTitle(synopsis: String, activityId: String): String {
      val request = logRequest("SYSTEM\nTitle system\n\nUSER\n$synopsis")
      val reply = "Mobile Menu Planner"
      request?.responded(reply, reply)
      return reply
    }

    override suspend fun generateQuestions(
      title: String,
      synopsis: String,
      previousQuestions: List<Question>,
      roundId: String,
      phase: Phase,
      activityId: String,
    ): QuestionBatch = throw PlanningEngine.AnalysisFailure("not used")
  }

  /**
   * The fan-out shape: a sub-request (today, a summarizing request) followed by
   * the request the interaction is actually about — the one whose row the
   * activity's headline reads.
   */
  private class SummarizingThenGenerating(
    private val failSummary: Boolean = false,
  ) : PlanningEngine {
    override val source: LogSource = LogSource.RemoteLLM

    override suspend fun recommendTitle(synopsis: String, activityId: String): String =
      throw PlanningEngine.AnalysisFailure("not used")

    override suspend fun generateQuestions(
      title: String,
      synopsis: String,
      previousQuestions: List<Question>,
      roundId: String,
      phase: Phase,
      activityId: String,
    ): QuestionBatch {
      val summary = logRequest("SYSTEM\nSummarize")
      if (failSummary) {
        summary?.failed("couldn't be read", "I am not able to help.")
        throw PlanningEngine.AnalysisFailure("couldn't be read")
      }
      summary?.responded("a summary of earlier answers", "the summarized text")

      val request = logRequest("SYSTEM\nQuestions")
      val batch = QuestionBatch(
        listOf(question("one", roundId), question("two", roundId), question("three", roundId)),
        false,
      )
      request?.responded(batch.summary(), """["one","two","three"]""")
      return batch
    }
  }
}

private fun question(text: String, roundId: String = "r1"): Question =
  Question(id = text, text = text, timestamp = now(), roundId = roundId)
