package alphainterplanetary.thinker.eval

import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import alphainterplanetary.thinker.di.DeviceInfo
import alphainterplanetary.thinker.engine.EngineInstance
import alphainterplanetary.thinker.engine.EngineKind
import alphainterplanetary.thinker.engine.LocalBackendFactory
import alphainterplanetary.thinker.engine.LocalKoogPlanningBackend
import alphainterplanetary.thinker.engine.LocalModel
import alphainterplanetary.thinker.engine.LocalModelCatalog
import alphainterplanetary.thinker.engine.LocalStreamingBackend
import alphainterplanetary.thinker.engine.LocalStreamingBackendFactory
import alphainterplanetary.thinker.engine.LocalStreamingReply
import alphainterplanetary.thinker.engine.ModelPresentationTier
import alphainterplanetary.thinker.testutil.FakeLlmClient
import alphainterplanetary.thinker.testutil.FakePlatformContext
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class ModelEvaluatorTest {
  private val model: LocalModel = LocalModelCatalog.models.first { it.label == "Llama 3.2 3B" }

  private val device = DeviceInfo(
    platform = "android",
    os = "Android",
    osVersion = "15",
    device = "Pixel 9",
    cpu = "Tensor G4",
    ramGb = 12.0,
    appVersion = "1.0",
  )

  private val provider = LLMProvider("test", "Test")
  private val llmModel = LLModel(provider, "test-model", contextLength = 4_096)

  private class FakeEvalModelSource(
    private val models: List<LocalModel>,
    private val installed: Set<String> = models.map { it.id }.toSet(),
  ) : EvalModelSource {
    override val catalog: List<LocalModel> = models

    override fun isInstalled(model: LocalModel): Boolean = model.id in installed

    override fun instance(model: LocalModel): EngineInstance = EngineInstance(
      id = "local-${model.id}",
      kind = EngineKind.LocalFile,
      provider = null,
      label = model.label,
      model = model.fileName,
      contextTokens = model.contextTokens,
      localPath = "/models/${model.fileName}",
    )
  }

  private fun replyFor(interaction: EvalFixtures.Interaction): String = when (interaction) {
    EvalFixtures.Interaction.Title -> "Water Log"
    EvalFixtures.Interaction.InitialQuestions ->
      """["Who is it for?","What is the MVP?","How will you measure success?"]"""

    EvalFixtures.Interaction.Summarize ->
      "The user settled the scope, the goals, and the deadline for this phase."
  }

  /** A client that answers the warmup and then every fixture in suite order. */
  private fun cleanClient(): FakeLlmClient {
    val replies = listOf("ready") + EvalFixtures.groups.map { replyFor(it.first) }
    return FakeLlmClient(provider, *replies.toTypedArray())
  }

  private fun TestScope.evaluator(
    client: FakeLlmClient,
    platform: FakePlatformContext = FakePlatformContext(device = device),
    source: EvalModelSource = FakeEvalModelSource(listOf(model)),
    streaming: LocalStreamingBackendFactory = LocalStreamingBackendFactory { null },
  ): ModelEvaluator = ModelEvaluator(
    models = source,
    backendFactory = LocalBackendFactory {
      LocalKoogPlanningBackend(
        executor = MultiLLMPromptExecutor(client),
        model = llmModel,
      )
    },
    platform = platform,
    scope = CoroutineScope(coroutineContext),
    streamingFactory = streaming,
  )

  @Test
  fun `a clean sweep measures every fixture and leaves a finished row`() = runTest {
    val platform = FakePlatformContext(device = device)
    val client = cleanClient()
    val evaluator = evaluator(client, platform)

    assertEquals(listOf(model), evaluator.installedModels())
    evaluator.start(listOf(model))
    advanceUntilIdle()

    val finished = assertIs<ModelEvalState.Finished>(evaluator.state.value)
    assertEquals(1, finished.models)
    assertEquals(EvalFixtures.groups.size, finished.cells)
    assertEquals(1 + EvalFixtures.groups.size, client.calls)

    val row = evaluator.rows.value.single()
    assertFalse(row.inProgress)
    assertEquals(true, row.warmup?.ok)
    assertEquals(EvalFixtures.groups.size, row.cells.size)
    assertEquals(device.key(), row.deviceKey)
    assertTrue(row.cells.all { it.error == null && it.raw != null })
    assertEquals(EvalFixtures.Schema, row.schema)
    assertEquals(EvalFixtures.Version, row.fixturesVersion)

    // The row also survives a fresh read of the platform's eval directory.
    val reloaded = EvalRowStore(platform).loadAll().single()
    assertEquals(row, reloaded)
  }

  @Test
  fun `a second start while a sweep is running is ignored`() = runTest {
    val client = cleanClient()
    val evaluator = evaluator(client)

    // The first start sets Running synchronously, so the second tap lands on a
    // sweep that has already begun and must not queue a duplicate.
    evaluator.start(listOf(model))
    evaluator.start(listOf(model))
    advanceUntilIdle()

    assertEquals(1 + EvalFixtures.groups.size, client.calls)
    assertEquals(1, evaluator.rows.value.size)
  }

  @Test
  fun `a finished row reports a perfect score on this device`() = runTest {
    val evaluator = evaluator(cleanClient())

    evaluator.start(listOf(model))
    advanceUntilIdle()

    val rollup = EvalReport.analyze(evaluator.rows.value).single()

    assertEquals(100.0, rollup.quality)
    assertEquals(ModelPresentationTier.Recommended, rollup.tier)
    assertEquals(device.key(), rollup.deviceKey)
  }

  @Test
  fun `an interrupted row resumes at the cell it stopped on`() = runTest {
    val platform = FakePlatformContext(device = device)
    val store = EvalRowStore(platform)
    val alreadyMeasured = EvalFixtures.groups.dropLast(1).map { (interaction, fixture) ->
      EvalCell(
        fixture = fixture.name,
        interaction = interaction.wire,
        run = 1,
        latencyS = 1.0,
        wallS = 1.0,
        raw = replyFor(interaction),
      )
    }
    store.save(
      EvalRow(
        modelId = model.id,
        model = EvalModelMeta(displayName = model.label),
        run = EvalRunMeta(
          id = "interrupted-run",
          runs = 1,
          hardware = EvalHardware(device = device.device, key = device.key()),
        ),
        warmup = EvalWarmup(ok = true, wallS = 1.0),
        cells = alreadyMeasured,
        startedAt = "2026-01-01T00:00:00Z",
        inProgress = true,
        deviceKey = device.key(),
      ),
    )
    val client = cleanClient()

    val evaluator = evaluator(client, platform)
    assertTrue(evaluator.hasInterrupted(model))

    evaluator.start(listOf(model))
    advanceUntilIdle()

    // The warmup was already paid and eight of the nine cells were saved, so
    // resuming costs exactly one request.
    assertEquals(1, client.calls)

    val row = evaluator.rows.value.single()
    assertEquals("interrupted-run", row.run.id)
    assertEquals(EvalFixtures.groups.size, row.cells.size)
    assertFalse(row.inProgress)
    assertFalse(evaluator.hasInterrupted(model))
  }

  @Test
  fun `force starts a fresh row instead of resuming`() = runTest {
    val platform = FakePlatformContext(device = device)
    val store = EvalRowStore(platform)
    store.save(
      EvalRow(
        modelId = model.id,
        run = EvalRunMeta(
          id = "interrupted-run",
          runs = 1,
          hardware = EvalHardware(device = device.device, key = device.key()),
        ),
        warmup = EvalWarmup(ok = true),
        startedAt = "2026-01-01T00:00:00Z",
        inProgress = true,
        deviceKey = device.key(),
      ),
    )
    val client = cleanClient()

    val evaluator = evaluator(client, platform)
    evaluator.start(listOf(model), force = true)
    advanceUntilIdle()

    val rows = evaluator.rows.value
    assertEquals(2, rows.size)
    assertTrue(rows.any { it.run.id == "interrupted-run" })
    assertTrue(rows.any { it.run.id != "interrupted-run" && !it.inProgress })
    assertEquals(1 + EvalFixtures.groups.size, client.calls)
  }

  @Test
  fun `a slow cell is recorded as a timeout and the sweep still finishes`() = runTest {
    val client = FakeLlmClient(provider, *Array(20) { "" }).apply { delayMillis = 10_000 }
    val evaluator = evaluator(client)

    evaluator.start(listOf(model), cellTimeout = 1.seconds, loadTimeout = 1.seconds)
    advanceUntilIdle()

    assertIs<ModelEvalState.Finished>(evaluator.state.value)
    val row = evaluator.rows.value.single()
    assertEquals(true, row.warmup?.timeout)
    assertEquals(EvalFixtures.groups.size, row.cells.size)
    assertTrue(row.cells.all { it.timeout })
    assertTrue(row.cells.all { it.error.orEmpty().contains("timeout") })
    // The warmup times out too, so the rollup counts one more than the cells.
    assertEquals(EvalFixtures.groups.size + 1, EvalReport.analyzeGroup(listOf(row)).timeouts)
  }

  @Test
  fun `an out of memory error persists the cell it was measuring and stops`() = runTest {
    val platform = FakePlatformContext(device = device)
    val client = FakeLlmClient(provider, *Array(20) { "" }).apply {
      failureAfterCalls = 1
      failure = TestOutOfMemoryError("simulated out of memory")
    }
    val evaluator = evaluator(client, platform)

    evaluator.start(listOf(model))
    advanceUntilIdle()

    val failed = assertIs<ModelEvalState.Failed>(evaluator.state.value)
    assertTrue(failed.outOfMemory)
    assertContains(failed.message, "OutOfMemory")

    // Two requests: the warmup paid the cold load, the first measured cell hit
    // the wall. That partial row is on disk with in_progress still true.
    assertEquals(2, client.calls)
    val row = EvalRowStore(platform).loadAll().single()
    assertTrue(row.inProgress)
    assertEquals(true, row.warmup?.ok)
    assertEquals(1, row.cells.size)
    assertContains(row.cells.single().error.orEmpty(), "OutOfMemory")
    assertTrue(evaluator.hasInterrupted(model))
  }

  @Test
  fun `a streaming runtime records time to first token and its own timing counters`() = runTest {
    val backend = FakeStreamingBackend { call ->
      if (call == 0) {
        "ready"
      } else {
        val interaction = EvalFixtures.groups[call - 1].first
        replyFor(interaction)
      }
    }
    val evaluator = evaluator(
      cleanClient(),
      streaming = LocalStreamingBackendFactory { backend },
    )

    evaluator.start(listOf(model))
    advanceUntilIdle()

    assertIs<ModelEvalState.Finished>(evaluator.state.value)
    // One warmup plus one request per cell went to the streaming runtime, not
    // to the whole-reply KOog fallback.
    assertEquals(1 + EvalFixtures.groups.size, backend.calls)
    assertTrue(backend.closed)

    val row = evaluator.rows.value.single()
    assertEquals(EvalFixtures.groups.size, row.cells.size)
    assertTrue(row.cells.all { it.error == null })
    assertTrue(row.cells.all { it.timings?.firstTokenS == 0.05 })
    assertTrue(row.cells.all { it.timings?.genTps == 12.0 })
  }

  @Test
  fun `a streaming reply that loops is aborted instead of waiting for the timeout`() = runTest {
    val looping = "The project needs a clearer answer. ".repeat(200)
    val backend = FakeStreamingBackend { looping }
    val evaluator = evaluator(
      cleanClient(),
      streaming = LocalStreamingBackendFactory { backend },
    )

    evaluator.start(listOf(model), cellTimeout = 30.seconds, loadTimeout = 30.seconds)
    advanceUntilIdle()

    assertIs<ModelEvalState.Finished>(evaluator.state.value)
    val row = evaluator.rows.value.single()
    assertEquals(EvalFixtures.groups.size, row.cells.size)
    assertTrue(row.cells.all { it.aborted == "repetition" })
    assertTrue(row.cells.all { it.error.orEmpty().contains("repetition loop") })
    assertTrue(row.cells.all { it.timings?.firstTokenS != null })

    val rollup = EvalReport.analyzeGroup(listOf(row))
    assertEquals(EvalFixtures.groups.size, rollup.aborted)
    assertEquals(0.0, rollup.okRate)
  }

  @Test
  fun `a platform with no installed models fails without measuring`() = runTest {
    val client = cleanClient()
    val evaluator = evaluator(client, source = FakeEvalModelSource(listOf(model), installed = emptySet()))

    evaluator.start(listOf(model))
    advanceUntilIdle()

    assertIs<ModelEvalState.Failed>(evaluator.state.value)
    assertEquals(0, client.calls)
    assertTrue(evaluator.rows.value.isEmpty())
  }
}

/** An [Error], as a JVM OOM is, whose name is what the evaluator recognizes. */
private class TestOutOfMemoryError(message: String) : Error(message)

/** A token-streaming runtime whose whole reply is decided by the call number. */
private class FakeStreamingBackend(
  private val replyForCall: (Int) -> String,
) : LocalStreamingBackend {
  var calls = 0
  var closed = false

  override suspend fun execute(
    text: String,
    onToken: (String) -> Unit,
  ): LocalStreamingReply {
    val reply = replyForCall(calls)
    calls++
    reply.chunked(8).forEach(onToken)
    return LocalStreamingReply(
      text = reply,
      inputTokens = 12,
      outputTokens = reply.length / 4,
      firstTokenS = 0.05,
      genTps = 12.0,
    )
  }

  override fun close() {
    closed = true
  }
}
