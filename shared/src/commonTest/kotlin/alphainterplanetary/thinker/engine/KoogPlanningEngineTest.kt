package alphainterplanetary.thinker.engine

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.Prompt
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import kotlin.time.Instant
import alphainterplanetary.thinker.model.Question
import alphainterplanetary.thinker.phases.BuiltInPhase
import alphainterplanetary.thinker.util.now
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

class KoogPlanningEngineTest {

  private val provider = LLMProvider("fake", "Fake")
  private val model = LLModel(provider, "fake-model")

  private fun engine(client: LLMClient): KoogPlanningEngine =
    KoogPlanningEngine(executor = MultiLLMPromptExecutor(client), model = model)

  @Test
  fun `recommendTitle returns the model's plain text reply trimmed`() = runTest {
    val client = FakeClient(provider, "  Mobile Menu Planner  ")
    val engine = engine(client)

    val title = engine.recommendTitle("A menu planner for phone screenshots", activityId = "t1")

    assertEquals("Mobile Menu Planner", title)
  }

  @Test
  fun `recommendTitle fails when the model returns an empty title`() = runTest {
    val engine = engine(FakeClient(provider, "   "))

    try {
      engine.recommendTitle("something", activityId = "t1")
      fail("expected the empty title to fail")
    } catch (e: PlanningEngine.AnalysisFailure) {
      assertTrue(e.message.orEmpty().contains("empty title"))
    }
  }

  @Test
  fun `initial questions parse a JSON reply into a batch with the round id`() = runTest {
    val client = FakeClient(provider, 
      """{"questions":[{"text":"Who is this building for?"},{"text":"What is the MVP?"}]}""",
    )
    val engine = engine(client)

    val batch = engine.generateInitialQuestions(
      editableTitle = "Menu Planner",
      synopsis = "Plan meals for the week",
      roundId = "r1",
      phase = BuiltInPhase.ScopeGoals,
      activityId = "t1",
    )

    assertEquals(listOf("Who is this building for?", "What is the MVP?"), batch.questions.map { it.text })
    assertTrue(batch.questions.all { it.roundId == "r1" })
    assertTrue(batch.questions.all { it.id.isNotBlank() })
    assertFalse(batch.done)
  }

  @Test
  fun `questions inside a markdown fence still parse`() = runTest {
    val client = FakeClient(provider, 
      "```json\n{\"questions\":[{\"text\":\"What data flows through the system?\"}]}\n```",
    )
    val engine = engine(client)

    val batch = engine.generateInitialQuestions(
      editableTitle = "T",
      synopsis = "S",
      roundId = "r1",
      phase = BuiltInPhase.Design,
      activityId = "t1",
    )

    assertEquals(listOf("What data flows through the system?"), batch.questions.map { it.text })
    assertFalse(batch.done)
  }

  @Test
  fun `an empty JSON reply produces no questions and signals done`() = runTest {
    val client = FakeClient(provider, """{"questions":[]}""")
    val engine = engine(client)

    val batch = engine.generateFollowUpQuestions(
      synopsis = "S",
      previousQuestions = emptyList(),
      roundId = "r2",
      phase = BuiltInPhase.Research,
      activityId = "t1",
    )

    assertTrue(batch.questions.isEmpty())
    assertTrue(batch.done)
  }

  @Test
  fun `a non-JSON reply degrades to bullet-point extraction`() = runTest {
    val client = FakeClient(provider, 
      "Here are the questions:\n- What is the timeline?\n• Who signs off?\n* Where is the budget?",
    )
    val engine = engine(client)

    val batch = engine.generateFollowUpQuestions(
      synopsis = "S",
      previousQuestions = emptyList(),
      roundId = "r2",
      phase = BuiltInPhase.ExecutionPlan,
      activityId = "t1",
    )

    assertEquals(
      listOf("What is the timeline?", "Who signs off?", "Where is the budget?"),
      batch.questions.map { it.text },
    )
    assertFalse(batch.done)
  }

  @Test
  fun `follow-up prompt includes the phase and already asked questions`() = runTest {
    val client = FakeClient(provider, """{"questions":[{"text":"A fresh question"}]}""")
    val engine = engine(client)
    val previous = listOf(question("Already asked"))

    val batch = engine.generateFollowUpQuestions(
      synopsis = "S",
      previousQuestions = previous,
      roundId = "r2",
      phase = BuiltInPhase.ScopeGoals,
      activityId = "t1",
    )

    assertEquals(listOf("A fresh question"), batch.questions.map { it.text })
    assertFalse(batch.done)

    val request = client.lastPrompt.messages.joinToString("\n") { it.textContent() }
    assertTrue(request.contains("Scope & Goals"))
    assertTrue(request.contains("Already asked"))
  }

  @Test
  fun `canProduceMoreInPhase always permits fresh questions`() = runTest {
    val engine = engine(FakeClient(provider, ""))

    val can = engine.canProduceMoreInPhase(
      synopsis = "S",
      previousQuestions = emptyList(),
      phase = BuiltInPhase.Design,
      activityId = "t1",
    )

    assertTrue(can)
  }

  @Test
  fun `an executor failure surfaces as an analysis failure`() = runTest {
    val engine = engine(ThrowingClient(provider))

    try {
      engine.generateInitialQuestions(
        editableTitle = "T",
        synopsis = "S",
        roundId = "r1",
        phase = BuiltInPhase.Design,
        activityId = "t1",
      )
      fail("expected the executor failure to propagate")
    } catch (e: PlanningEngine.AnalysisFailure) {
      assertEquals("model exploded", e.message)
    }
  }

  private fun question(text: String): Question =
    Question(id = text, text = text, timestamp = now(), roundId = "r0")

  /** A Koog [LLMClient] answering from a canned queue, capturing each built [Prompt]. */
private class FakeClient(
    private val provider: LLMProvider,
    vararg responses: String,
  ) : LLMClient() {
    private val queue = ArrayDeque(responses.toList())
    var lastPrompt: Prompt = ai.koog.prompt.dsl.prompt("unset") { }
      private set

    override fun llmProvider(): LLMProvider = provider

    override suspend fun execute(
      prompt: Prompt,
      model: LLModel,
      tools: List<ToolDescriptor>,
    ): Message.Assistant {
      lastPrompt = prompt
      val text = queue.removeFirstOrNull() ?: ""
      return Message.Assistant(text, ResponseMetaInfo(Instant.fromEpochMilliseconds(0)))
    }

    override suspend fun moderate(prompt: Prompt, model: LLModel): ModerationResult =
      ModerationResult(false, emptyMap())

    override fun close() = Unit
  }

  private class ThrowingClient(
    private val provider: LLMProvider,
  ) : LLMClient() {
    override fun llmProvider(): LLMProvider = provider

    override suspend fun execute(
      prompt: Prompt,
      model: LLModel,
      tools: List<ToolDescriptor>,
    ): Message.Assistant = throw IllegalStateException("model exploded")

    override suspend fun moderate(prompt: Prompt, model: LLModel): ModerationResult =
      ModerationResult(false, emptyMap())

    override fun close() = Unit
  }
}