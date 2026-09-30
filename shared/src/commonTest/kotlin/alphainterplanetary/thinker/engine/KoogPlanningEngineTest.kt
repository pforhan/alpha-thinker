package alphainterplanetary.thinker.engine

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.Prompt
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import alphainterplanetary.thinker.activitylog.LogSource
import alphainterplanetary.thinker.model.Answer
import alphainterplanetary.thinker.model.Question
import alphainterplanetary.thinker.phases.BuiltInPhase
import alphainterplanetary.thinker.util.now
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

class KoogPlanningEngineTest {

  private val provider = LLMProvider("fake", "Fake")
  private val model = LLModel(provider, "fake-model")

  private fun engine(client: LLMClient): KoogPlanningEngine {
    val backend = object : KoogPlanningBackend {
      override val executor: PromptExecutor = MultiLLMPromptExecutor(client)
      override val model: LLModel = this@KoogPlanningEngineTest.model
      override val source: LogSource = LogSource.RemoteLLM
    }
    return KoogPlanningEngine(backend)
  }

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
  fun `questions parse a JSON array reply into a batch with the round id`() = runTest {
    val client = FakeClient(provider,
      """["Who is this building for?","What is the MVP?"]""",
    )
    val engine = engine(client)

    val batch = engine.generateQuestions(
      title = "Menu Planner",
      synopsis = "Plan meals for the week",
      previousQuestions = emptyList(),
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
      "```json\n[\"What data flows through the system?\"]\n```",
    )
    val engine = engine(client)

    val batch = engine.generateQuestions(
      title = "T",
      synopsis = "S",
      previousQuestions = emptyList(),
      roundId = "r1",
      phase = BuiltInPhase.Design,
      activityId = "t1",
    )

    assertEquals(listOf("What data flows through the system?"), batch.questions.map { it.text })
    assertFalse(batch.done)
  }

  @Test
  fun `an empty JSON array produces no questions and signals done`() = runTest {
    val client = FakeClient(provider, "[]")
    val engine = engine(client)

    val batch = engine.generateQuestions(
      title = "T",
      synopsis = "S",
      previousQuestions = emptyList(),
      roundId = "r2",
      phase = BuiltInPhase.Research,
      activityId = "t1",
    )

    assertTrue(batch.questions.isEmpty())
    assertTrue(batch.done)
  }

  /**
   * A JSON array of quoted strings is a valid reply wherever it appears — bare,
   * fenced, or wrapped in an object, since only the array is read. These are
   * worth pinning because the extractor ignores everything around the array.
   */
  @Test
  fun `an array of strings is read from whatever surrounds it`() = runTest {
    val replies = mapOf(
      """["What is the MVP?"]""" to "What is the MVP?",
      "```json\n[\"What is the MVP?\"]\n```" to "What is the MVP?",
      """{"questions":["What is the MVP?"]}""" to "What is the MVP?",
      """Here you go: ["What is the MVP?"]. Let me know!""" to "What is the MVP?",
    )

    for ((reply, expected) in replies) {
      val engine = engine(FakeClient(provider, reply))
      val batch = engine.generateQuestions(
        title = "T",
        synopsis = "S",
        previousQuestions = emptyList(),
        roundId = "r2",
        phase = BuiltInPhase.ScopeGoals,
        activityId = "t1",
      )

      assertEquals(listOf(expected), batch.questions.map { it.text }, "for reply: $reply")
      assertFalse(batch.done)
    }
  }

  /**
   * Everything else is refused, so the generation fails instead of latching the
   * round exhausted (see `KoogPlanningEngine.batch`) — a partly-recovered batch
   * would read downstream as a phase with more to come.
   */
  @Test
  fun `a reply with no array of strings fails the generation`() = runTest {
    val replies = listOf(
      """[{"text":"What is the MVP?"}]""",
      """[P1] What is the MVP?
[P2] Who is this for?""",
      "Here are the questions:\n- What is the MVP?\n- Who is this for?",
      "No structure here. First, what is the MVP? Then, who is this for?",
      "I am not able to help with that request.",
    )

    for (reply in replies) {
      val engine = engine(FakeClient(provider, reply))
      try {
        engine.generateQuestions(
          title = "T",
          synopsis = "S",
          previousQuestions = emptyList(),
          roundId = "r2",
          phase = BuiltInPhase.ScopeGoals,
          activityId = "t1",
        )
        fail("expected ${reply.take(40)} to fail")
      } catch (e: PlanningEngine.AnalysisFailure) {
        assertTrue(
          e.message.orEmpty().contains("JSON array"),
          "unexpected message for ${reply.take(40)}: ${e.message}",
        )
      }
    }
  }

  @Test
  fun `an unreadable reply is a failure rather than a signal that the phase is done`() = runTest {
    val engine = engine(FakeClient(provider, "I am not able to help with that request."))

    val failure = try {
      engine.generateQuestions(
        title = "T",
        synopsis = "S",
        previousQuestions = emptyList(),
        roundId = "r1",
        phase = BuiltInPhase.ScopeGoals,
        activityId = "t1",
      )
      fail("expected the unreadable reply to fail")
    } catch (e: PlanningEngine.AnalysisFailure) {
      e
    }

    assertTrue(failure.message.orEmpty().contains("JSON array"))
  }

  /**
   * The raw reply is published before anything tries to read it, so the
   * activity log keeps the text even on the generations that go on to fail —
   * which is exactly the case where the message alone is undiagnosable.
   */
  @Test
  fun `a reply is published verbatim whether or not it parses`() = runTest {
    val unreadable = "I am not able to help with that request."
    val captured = mutableListOf<String>()

    val ok = capturingRawResponses({ captured += it }) {
      engine(FakeClient(provider, """["What is the MVP?"]""")).generateQuestions(
        title = "T",
        synopsis = "S",
        previousQuestions = emptyList(),
        roundId = "r1",
        phase = BuiltInPhase.ScopeGoals,
        activityId = "t1",
      )
    }
    assertEquals(listOf("What is the MVP?"), ok.questions.map { it.text })

    try {
      capturingRawResponses({ captured += it }) {
        engine(FakeClient(provider, unreadable)).generateQuestions(
          title = "T",
          synopsis = "S",
          previousQuestions = emptyList(),
          roundId = "r1",
          phase = BuiltInPhase.ScopeGoals,
          activityId = "t1",
        )
      }
      fail("expected the unreadable reply to fail")
    } catch (e: PlanningEngine.AnalysisFailure) {
      assertTrue(e.message.orEmpty().contains("JSON array"))
    }

    assertEquals(listOf("""["What is the MVP?"]""", unreadable), captured)
  }

  @Test
  fun `a blank reply publishes no raw payload`() = runTest {
    val captured = mutableListOf<String>()

    try {
      capturingRawResponses({ captured += it }) {
        engine(FakeClient(provider, "   ")).recommendTitle("something", activityId = "t1")
      }
      fail("expected the empty title to fail")
    } catch (e: PlanningEngine.AnalysisFailure) {
      assertTrue(e.message.orEmpty().contains("empty title"))
    }

    assertTrue(captured.isEmpty(), "there is nothing to show for an empty reply")
  }

  @Test
  fun `the questions prompt carries the title and the phase and the already asked questions`() = runTest {
    val client = FakeClient(provider, """["A fresh question"]""")
    val engine = engine(client)
    val previous = listOf(question("Already asked"))

    val batch = engine.generateQuestions(
      title = "Menu Planner",
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
    assertTrue(
      request.contains("Menu Planner"),
      "the title reaches every round, not just a project's opening one",
    )
  }

  /**
   * The whole point of sending prior questions is what the user did with them,
   * so a question the model would otherwise re-ask (or mistake for unanswered)
   * has to arrive carrying its answer, its draft, or the fact that it was
   * skipped or compacted out of the context.
   */
  @Test
  fun `the questions prompt renders the interview so far with each answer state`() = runTest {
    val client = FakeClient(provider, """["A fresh question"]""")
    val engine = engine(client)
    val previous = listOf(
      question("What is the MVP?").withAnswer(
        Answer(questionId = "What is the MVP?", text = "A menu planner", createdAt = now()),
      ),
      question("Who is this for?").withDraft("home cooks", now()),
      question("How much will it cost?").withIgnored(now()),
      question("What is the timeline?"),
      question("Who owns this?").asCompacted(),
    )

    engine.generateQuestions(
      title = "Menu Planner",
      synopsis = "S",
      previousQuestions = previous,
      roundId = "r2",
      phase = BuiltInPhase.ScopeGoals,
      activityId = "t1",
    )

    val request = client.lastPrompt.messages.joinToString("\n") { it.textContent() }
    assertTrue(request.contains("Q: What is the MVP? / A: A menu planner"), "committed")
    assertTrue(request.contains("Q: Who is this for? / Draft: home cooks"), "draft")
    assertTrue(request.contains("Q: How much will it cost? / ${PlanningContext.SkippedNote}"), "ignored")
    assertTrue(request.contains("Q: What is the timeline? / ${PlanningContext.NotAnsweredNote}"), "unanswered")
    assertTrue(
      request.contains("Q: Who owns this? / A: ${PlanningContext.OmittedNote}"),
      "an answer left out of the context still reads as answered",
    )
  }

  /**
   * "skipped" and "not yet answered" read themselves, so the one marker the
   * prompt has to define is "A: omitted" — a bare "omitted" is as apt to read as
   * an open question as a settled one, and there is no other way to tell.
   */
  @Test
  fun `the questions system prompt defines the one placeholder that needs it`() = runTest {
    val client = FakeClient(provider, """["A fresh question"]""")
    val engine = engine(client)

    engine.generateQuestions(
      title = "Menu Planner",
      synopsis = "S",
      previousQuestions = emptyList(),
      roundId = "r1",
      phase = BuiltInPhase.ScopeGoals,
      activityId = "t1",
    )

    val system = client.lastPrompt.messages.filterIsInstance<Message.System>().single().textContent()
    assertTrue(
      system.contains("A: ${PlanningContext.OmittedNote}"),
      "the unusual marker is spelled out verbatim",
    )
    assertTrue(
      system.contains("treat it as answered, not open"),
      "the explanation makes omitted read as settled, so the model does not re-ask",
    )
    assertTrue(
      system.contains("\"${PlanningContext.SkippedNote}\"") &&
        system.contains("\"${PlanningContext.NotAnsweredNote}\""),
      "the self-explanatory markers are listed, not defined",
    )
  }

  @Test
  fun `the questions prompt renders a none marker when nothing has been asked yet`() = runTest {
    val client = FakeClient(provider, """["A first question"]""")
    val engine = engine(client)

    engine.generateQuestions(
      title = "Menu Planner",
      synopsis = "S",
      previousQuestions = emptyList(),
      roundId = "r1",
      phase = BuiltInPhase.ScopeGoals,
      activityId = "t1",
    )

    val request = client.lastPrompt.messages.joinToString("\n") { it.textContent() }
    assertTrue(
      request.contains("(none)"),
      "the already-asked block stays present on a project's opening round",
    )
  }

  /**
   * The prompt id is the one place the opening/continuing distinction still
   * lives — it keeps the two prompt shapes separately traceable at no cost.
   */
  @Test
  fun `the prompt id separates a project's opening round from a later one`() = runTest {
    val client = FakeClient(provider, """["A question"]""", """["Another question"]""")
    val engine = engine(client)

    engine.generateQuestions(
      title = "T",
      synopsis = "S",
      previousQuestions = emptyList(),
      roundId = "r1",
      phase = BuiltInPhase.ScopeGoals,
      activityId = "t1",
    )
    val opening = client.lastPrompt.id

    engine.generateQuestions(
      title = "T",
      synopsis = "S",
      previousQuestions = listOf(question("Already asked")),
      roundId = "r2",
      phase = BuiltInPhase.ScopeGoals,
      activityId = "t1",
    )

    assertEquals(KoogPlanningEngine.PromptInitialQuestions, opening)
    assertEquals(KoogPlanningEngine.PromptFollowUpQuestions, client.lastPrompt.id)
  }

  @Test
  fun `the questions prompt renders the same user message the generation sends`() = runTest {
    val client = FakeClient(provider, """["A question"]""")
    val engine = engine(client)
    val previous = listOf(question("Already asked"))

    engine.generateQuestions(
      title = "Menu Planner",
      synopsis = "S",
      previousQuestions = previous,
      roundId = "r2",
      phase = BuiltInPhase.ScopeGoals,
      activityId = "t1",
    )

    val messages = client.lastPrompt.messages
    assertEquals(
      engine.questionsPrompt("Menu Planner", "S", previous, BuiltInPhase.ScopeGoals),
      "SYSTEM\n${KoogPlanningEngine.QuestionsSystemPrompt}\n\nUSER\n" +
        messages.last().textContent(),
      "the rendered prompt and the one generation sends are the same text",
    )
  }

  @Test
  fun `an executor failure surfaces as an analysis failure`() = runTest {
    val engine = engine(ThrowingClient(provider))

    try {
      engine.generateQuestions(
        title = "T",
        synopsis = "S",
        previousQuestions = emptyList(),
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