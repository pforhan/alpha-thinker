package alphainterplanetary.thinker.engine

import alphainterplanetary.thinker.activitylog.LogSource
import alphainterplanetary.thinker.repository.EngineRepository
import alphainterplanetary.thinker.testutil.FakeStorage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest

class EngineRegistryTest {

  private fun TestScope.repository(storage: FakeStorage): EngineRepository =
    EngineRepository(storage, CoroutineScope(coroutineContext))

  /** A window no provider default carries, so only the instance can supply it. */
  private val instance = EngineInstance(
    id = "conn-1",
    kind = EngineKind.Remote,
    provider = RemoteProvider.Ollama,
    label = "Ollama",
    baseUrl = "http://localhost:11434",
    model = "llama3.2:latest",
    contextTokens = 42_000,
  )

  private suspend fun TestScope.loadedRepository(instance: EngineInstance): EngineRepository {
    val storage = FakeStorage()
    storage.upsertEngineInstance(instance)
    return repository(storage).also { testScheduler.advanceUntilIdle() }
  }

  @Test
  fun `the Offline builtin resolves to the OfflinePlanningEngine`() = runTest {
    val registry = EngineRegistry(loadedRepository(instance))

    val engine = registry.getEngine(EngineRef.Builtin(EngineKind.Offline))

    assertEquals(OfflinePlanningEngine::class, engine::class)
    assertEquals(LogSource.Lite, engine.source)
  }

  @Test
  fun `a Remote instance resolves to a KoogPlanningEngine built from its fields`() = runTest {
    val registry = EngineRegistry(loadedRepository(instance))

    val engine = registry.getEngine(EngineRef.Instance("conn-1"))

    assertIs<KoogPlanningEngine>(engine)
    assertEquals(LogSource.RemoteLLM, engine.source)
    // The window is read off the model the backend built from the connection,
    // so 42_000 reaching the engine proves the instance's fields (not a provider
    // default, and not any flat setting) are what the backend was configured with.
    assertEquals(42_000, engine.contextWindowTokens)
  }

  @Test
  fun `repeat requests for the same ref return the identical engine`() = runTest {
    val registry = EngineRegistry(loadedRepository(instance))

    assertSame(
      registry.getEngine(EngineRef.Instance("conn-1")),
      registry.getEngine(EngineRef.Instance("conn-1")),
    )
    assertSame(
      registry.getEngine(EngineRef.Builtin(EngineKind.Offline)),
      registry.getEngine(EngineRef.Builtin(EngineKind.Offline)),
    )
  }

  @Test
  fun `different refs resolve to different engines`() = runTest {
    val storage = FakeStorage()
    storage.upsertEngineInstance(instance)
    storage.upsertEngineInstance(instance.copy(id = "conn-2", contextTokens = 8_192))
    val registry = EngineRegistry(repository(storage).also { testScheduler.advanceUntilIdle() })

    assertNotSame(
      registry.getEngine(EngineRef.Instance("conn-1")),
      registry.getEngine(EngineRef.Instance("conn-2")),
    )
    assertNotSame(
      registry.getEngine(EngineRef.Instance("conn-1")),
      registry.getEngine(EngineRef.Builtin(EngineKind.Offline)),
    )
  }

  @Test
  fun `invalidate forces a rebuild that picks up changed instance fields`() = runTest {
    val repository = loadedRepository(instance)
    val registry = EngineRegistry(repository)
    val ref = EngineRef.Instance("conn-1")

    val before = registry.getEngine(ref)
    assertEquals(42_000, before.contextWindowTokens)

    // The cached engine is a snapshot: editing the instance alone does not
    // reach it — only an invalidate swaps it.
    repository.addOrUpdate(instance.copy(contextTokens = 8_192))
    assertSame(before, registry.getEngine(ref))
    assertEquals(42_000, registry.getEngine(ref).contextWindowTokens)

    registry.invalidate(ref)

    val after = registry.getEngine(ref)
    assertNotSame(before, after)
    assertEquals(8_192, after.contextWindowTokens)
  }

  @Test
  fun `invalidate leaves other refs cached`() = runTest {
    val registry = EngineRegistry(loadedRepository(instance))
    val offline = registry.getEngine(EngineRef.Builtin(EngineKind.Offline))

    registry.invalidate(EngineRef.Instance("conn-1"))

    assertSame(offline, registry.getEngine(EngineRef.Builtin(EngineKind.Offline)))
  }

  @Test
  fun `a builtin other than Offline fails loudly until implemented`() = runTest {
    val registry = EngineRegistry(loadedRepository(instance))

    assertFailsWith<IllegalStateException> {
      registry.getEngine(EngineRef.Builtin(EngineKind.OnDevice))
    }
  }

  @Test
  fun `an unknown instance ref fails loudly`() = runTest {
    val registry = EngineRegistry(loadedRepository(instance))

    assertFailsWith<IllegalStateException> {
      registry.getEngine(EngineRef.Instance("missing"))
    }
  }

  @Test
  fun `a non-Remote instance kind fails loudly until implemented`() = runTest {
    val localFile = instance.copy(
      id = "file-1",
      kind = EngineKind.LocalFile,
      provider = null,
      localPath = "/models/foo.litertlm",
    )
    val registry = EngineRegistry(loadedRepository(localFile))

    assertFailsWith<IllegalStateException> {
      registry.getEngine(EngineRef.Instance("file-1"))
    }
  }
}
