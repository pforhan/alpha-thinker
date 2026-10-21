package alphainterplanetary.thinker.ui.chrome

import alphainterplanetary.thinker.engine.EngineCapability
import alphainterplanetary.thinker.engine.EngineInstance
import alphainterplanetary.thinker.engine.EngineKind
import alphainterplanetary.thinker.engine.EngineRef
import alphainterplanetary.thinker.engine.RemoteProvider
import alphainterplanetary.thinker.repository.EngineRepository
import alphainterplanetary.thinker.testutil.FakeStorage
import app.cash.turbine.test
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest

/**
 * The chrome's one status derivation ([engineStatusFlow]) re-derives on every
 * change of the selection or the instance list, so the header, flyout, and
 * Status sheet can never show a status from before the change (a stale cache of
 * a previous backend is exactly what the Stage 2 requirement forbids).
 */
class AppChromeStateTest {

  private fun TestScope.engineRepository(storage: FakeStorage = FakeStorage()): EngineRepository =
    EngineRepository(storage, CoroutineScope(coroutineContext)).also {
      testScheduler.advanceUntilIdle()
    }

  /** A complete Remote connection: provider, endpoint, key-free, model, window. */
  private fun remoteInstance(
    id: String = "conn-1",
    model: String = "llama3.2:latest",
  ) = EngineInstance(
    id = id,
    kind = EngineKind.Remote,
    provider = RemoteProvider.Ollama,
    label = "Ollama",
    baseUrl = "http://localhost:11434",
    model = model,
    contextTokens = 8_192,
  )

  @Test
  fun `the initial status describes the selected builtin`() = runTest {
    val repository = engineRepository()

    engineStatusFlow(repository).test {
      val status = awaitItem()
      assertEquals(EngineKind.Offline, status.kind)
      assertEquals(
        listOf(CapabilityState.Unused, CapabilityState.Unused, CapabilityState.Unused),
        status.slots.map { it.state },
      )
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `selecting an instance emits a status from its fields`() = runTest {
    val storage = FakeStorage()
    storage.upsertEngineInstance(remoteInstance())
    val repository = engineRepository(storage)

    engineStatusFlow(repository).test {
      assertEquals(EngineKind.Offline, awaitItem().kind)

      repository.select(EngineRef.Instance("conn-1"))

      val status = awaitItem()
      assertEquals(EngineKind.Remote, status.kind)
      assertEquals(CapabilityState.Active, status.slot(EngineCapability.Network).state)
      assertEquals("http://localhost:11434", status.slot(EngineCapability.Network).detail)
      assertEquals("llama3.2:latest", status.slot(EngineCapability.Llm).detail)
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `editing the selected instance emits a refreshed status`() = runTest {
    val storage = FakeStorage()
    storage.upsertEngineInstance(remoteInstance())
    val repository = engineRepository(storage)
    repository.select(EngineRef.Instance("conn-1"))

    engineStatusFlow(repository).test {
      val before = awaitItem()
      assertEquals(CapabilityState.Active, before.slot(EngineCapability.Llm).state)

      // Blank the model: the LLM slot must go Incomplete on the very next
      // emission rather than reusing the status built before the edit.
      repository.addOrUpdate(remoteInstance(model = ""))

      val after = awaitItem()
      assertEquals(CapabilityState.Incomplete, after.slot(EngineCapability.Llm).state)
      assertEquals(true, after.needsAttention)
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `renaming the selected instance re-derives the status label`() = runTest {
    val storage = FakeStorage()
    storage.upsertEngineInstance(remoteInstance())
    val repository = engineRepository(storage)
    repository.select(EngineRef.Instance("conn-1"))

    engineStatusFlow(repository).test {
      assertEquals("Ollama", awaitItem().label)

      // The chrome words the engine by the instance's own name, so a rename
      // has to reach the status rather than leave the old name on screen.
      repository.addOrUpdate(remoteInstance().copy(label = "Work Ollama"))

      assertEquals("Work Ollama", awaitItem().label)
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `deleting a non-selected instance leaves the selected status alone`() = runTest {
    val storage = FakeStorage()
    storage.upsertEngineInstance(remoteInstance())
    storage.upsertEngineInstance(remoteInstance(id = "conn-2"))
    val repository = engineRepository(storage)
    repository.select(EngineRef.Instance("conn-1"))

    engineStatusFlow(repository).test {
      assertEquals(EngineKind.Remote, awaitItem().kind)

      repository.delete("conn-2")

      // The combine is keyed on the instance list, so it re-fires — but the
      // derivation for the still-selected conn-1 must be equivalent, not stale
      // and not confused by the removal.
      val status = awaitItem()
      assertEquals(EngineKind.Remote, status.kind)
      assertEquals("llama3.2:latest", status.slot(EngineCapability.Llm).detail)
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `an unavailable engine derives its supported slots as incomplete`() = runTest {
    // Builtin OnDevice is catalogued Unavailable, so its LLM slot reads
    // Incomplete rather than Active even though the kind supports it.
    val repository = engineRepository()

    engineStatusFlow(repository).test {
      assertEquals(EngineKind.Offline, awaitItem().kind)

      repository.select(EngineRef.Builtin(EngineKind.OnDevice))

      val status = awaitItem()
      assertEquals(EngineKind.OnDevice, status.kind)
      assertEquals(CapabilityState.Incomplete, status.slot(EngineCapability.Llm).state)
      assertEquals(CapabilityState.Unused, status.slot(EngineCapability.Network).state)
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `a selected instance missing from the list derives as unknown`() = runTest {
    val storage = FakeStorage()
    storage.upsertEngineInstance(remoteInstance())
    val repository = engineRepository(storage)

    engineStatusFlow(repository).test {
      assertEquals(EngineKind.Offline, awaitItem().kind)

      // The ref is selected before its row exists — the catalog lookup misses,
      // so availability degrades to Unavailable instead of crashing.
      repository.select(EngineRef.Instance("ghost"))

      val status = awaitItem()
      assertEquals(EngineKind.Remote, status.kind)
      assertEquals(CapabilityState.Incomplete, status.slot(EngineCapability.Network).state)
      assertEquals(CapabilityState.Incomplete, status.slot(EngineCapability.Llm).state)
      cancelAndIgnoreRemainingEvents()
    }
  }
}
