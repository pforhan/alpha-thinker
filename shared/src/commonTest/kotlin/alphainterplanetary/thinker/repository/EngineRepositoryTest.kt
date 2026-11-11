package alphainterplanetary.thinker.repository

import alphainterplanetary.thinker.engine.config.EngineInstance
import alphainterplanetary.thinker.engine.config.EngineKind
import alphainterplanetary.thinker.engine.config.EngineRef
import alphainterplanetary.thinker.engine.config.EngineSelectionState
import alphainterplanetary.thinker.engine.config.RemoteProvider
import alphainterplanetary.thinker.testutil.FakeStorage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest

class EngineRepositoryTest {

  private fun TestScope.repository(storage: FakeStorage = FakeStorage()): EngineRepository =
    EngineRepository(storage, CoroutineScope(coroutineContext))

  private val preset =
    EngineInstance(
      id = "conn-1",
      kind = EngineKind.Remote,
      provider = RemoteProvider.Anthropic,
      label = "Claude",
      baseUrl = "https://api.anthropic.com",
      apiKey = "sk-ant",
      model = "claude-sonnet-4-5",
      contextTokens = 200_000,
    )

  @Test
  fun `a fresh install has no custom instances`() = runTest {
    val repo = repository()
    testScheduler.advanceUntilIdle()

    assertEquals(emptyList(), repo.instances.value)
  }

  @Test
  fun `the seeded default is not selected`() = runTest {
    val repo = repository()
    testScheduler.advanceUntilIdle()

    assertEquals(EngineRef.Builtin(EngineKind.Offline), repo.selected.value)
  }

  @Test
  fun `first read does not reseed an already-stored instance list`() = runTest {
    val storage = FakeStorage()
    storage.upsertEngineInstance(preset)

    val repo = repository(storage)
    testScheduler.advanceUntilIdle()

    assertEquals(listOf(preset), repo.instances.value)
  }

  @Test
  fun `selecting an instance persists across a fresh repository`() = runTest {
    val storage = FakeStorage()
    storage.upsertEngineInstance(preset)
    val repo = repository(storage)
    testScheduler.advanceUntilIdle()

    repo.select(EngineRef.Instance("conn-1"))
    testScheduler.advanceUntilIdle()

    val reopened = repository(storage)
    testScheduler.advanceUntilIdle()
    assertEquals(EngineRef.Instance("conn-1"), reopened.selected.value)
  }

  @Test
  fun `selecting a built-in kind persists too`() = runTest {
    val storage = FakeStorage()
    val repo = repository(storage)
    testScheduler.advanceUntilIdle()
    repo.select(EngineRef.Builtin(EngineKind.Offline))
    testScheduler.advanceUntilIdle()
    repo.select(EngineRef.Builtin(EngineKind.Remote))
    testScheduler.advanceUntilIdle()

    val reopened = repository(storage)
    testScheduler.advanceUntilIdle()
    assertEquals(EngineRef.Builtin(EngineKind.Remote), reopened.selected.value)
  }

  @Test
  fun `markUsed records and persists the timestamp`() = runTest {
    val storage = FakeStorage()
    storage.upsertEngineInstance(preset)
    val repo = repository(storage)
    testScheduler.advanceUntilIdle()

    repo.markUsed(EngineRef.Instance("conn-1"), at = 1_000L)
    testScheduler.advanceUntilIdle()

    assertEquals(1_000L, repo.lastUsedAt.value[EngineRef.Instance("conn-1")])

    val reopened = repository(storage)
    testScheduler.advanceUntilIdle()
    assertEquals(1_000L, reopened.lastUsedAt.value[EngineRef.Instance("conn-1")])
  }

  @Test
  fun `markUsed on a builtin records recency without adding an instance row`() = runTest {
    val repo = repository()
    testScheduler.advanceUntilIdle()

    repo.markUsed(EngineRef.Builtin(EngineKind.Offline), at = 3_000L)
    testScheduler.advanceUntilIdle()

    assertEquals(3_000L, repo.lastUsedAt.value[EngineRef.Builtin(EngineKind.Offline)])
    assertEquals(emptyList(), repo.instances.value)
  }

  @Test
  fun `markUsed keeps the engine selected`() = runTest {
    val storage = FakeStorage()
    storage.upsertEngineInstance(preset)
    val repo = repository(storage)
    testScheduler.advanceUntilIdle()
    repo.select(EngineRef.Instance("conn-1"))
    testScheduler.advanceUntilIdle()

    repo.markUsed(EngineRef.Instance("conn-1"), at = 5L)
    testScheduler.advanceUntilIdle()

    assertEquals(EngineRef.Instance("conn-1"), repo.selected.value)
  }

  @Test
  fun `addOrUpdate replaces an existing instance in place`() = runTest {
    val storage = FakeStorage()
    storage.upsertEngineInstance(preset)
    val repo = repository(storage)
    testScheduler.advanceUntilIdle()

    val edited = preset.copy(model = "claude-opus-4-1")
    repo.addOrUpdate(edited)
    testScheduler.advanceUntilIdle()

    assertEquals(listOf(edited), repo.instances.value)
    assertEquals(edited, storage.engineInstances["conn-1"])
  }

  @Test
  fun `addOrUpdate keeps an edited instance at its position`() = runTest {
    val storage = FakeStorage()
    storage.upsertEngineInstance(preset)
    val second = preset.copy(id = "conn-2", label = "Second")
    storage.upsertEngineInstance(second)
    val repo = repository(storage)
    testScheduler.advanceUntilIdle()

    repo.addOrUpdate(preset.copy(label = "Renamed"))
    testScheduler.advanceUntilIdle()

    assertEquals(listOf("conn-1", "conn-2"), repo.instances.value.map { it.id })
  }

  @Test
  fun `addOrUpdate appends a new instance`() = runTest {
    val storage = FakeStorage()
    storage.upsertEngineInstance(preset)
    val repo = repository(storage)
    testScheduler.advanceUntilIdle()

    val added = preset.copy(id = "conn-2", label = "Second")
    repo.addOrUpdate(added)
    testScheduler.advanceUntilIdle()

    assertEquals(listOf("conn-1", "conn-2"), repo.instances.value.map { it.id })
  }

  @Test
  fun `deleting the selected instance falls back to the built-in Offline`() = runTest {
    val storage = FakeStorage()
    storage.upsertEngineInstance(preset)
    val repo = repository(storage)
    testScheduler.advanceUntilIdle()
    repo.select(EngineRef.Instance("conn-1"))
    testScheduler.advanceUntilIdle()

    repo.delete("conn-1")
    testScheduler.advanceUntilIdle()

    assertTrue(repo.instances.value.none { it.id == "conn-1" })
    assertEquals(EngineRef.Builtin(EngineKind.Offline), repo.selected.value)
  }

  @Test
  fun `selectedInstance reflects the selected instance`() = runTest {
    val storage = FakeStorage()
    storage.upsertEngineInstance(preset)
    val repo = repository(storage)
    testScheduler.advanceUntilIdle()

    // Initially it's the default (Builtin(Default)), so selectedInstance should be null
    assertEquals(null, repo.selectedInstance.first())

    repo.select(EngineRef.Instance("conn-1"))
    testScheduler.advanceUntilIdle()

    assertEquals(preset, repo.selectedInstance.first())

    repo.select(EngineRef.Builtin(EngineKind.Offline))
    testScheduler.advanceUntilIdle()

    assertEquals(null, repo.selectedInstance.first())
  }

  @Test
  fun `selectedInstance updates when instance is updated`() = runTest {
    val storage = FakeStorage()
    storage.upsertEngineInstance(preset)
    val repo = repository(storage)
    testScheduler.advanceUntilIdle()

    repo.select(EngineRef.Instance("conn-1"))
    testScheduler.advanceUntilIdle()
    assertEquals(preset, repo.selectedInstance.first())

    val edited = preset.copy(label = "New Label")
    repo.addOrUpdate(edited)
    testScheduler.advanceUntilIdle()

    assertEquals(edited, repo.selectedInstance.first())
  }

  @Test
  fun `selectedInstance becomes null when instance is deleted`() = runTest {
    val storage = FakeStorage()
    storage.upsertEngineInstance(preset)
    val repo = repository(storage)
    testScheduler.advanceUntilIdle()

    repo.select(EngineRef.Instance("conn-1"))
    testScheduler.advanceUntilIdle()
    assertEquals(preset, repo.selectedInstance.first())

    repo.delete("conn-1")
    testScheduler.advanceUntilIdle()

    assertEquals(null, repo.selectedInstance.first())
  }

  @Test
  fun `updateSelectedInstance persists field changes`() = runTest {
    val storage = FakeStorage()
    storage.upsertEngineInstance(preset)
    val repo = repository(storage)
    testScheduler.advanceUntilIdle()

    repo.select(EngineRef.Instance("conn-1"))
    testScheduler.advanceUntilIdle()

    repo.updateSelectedInstance {
      it.copy(
        baseUrl = "https://new.example.com",
        model = "new-model"
      )
    }
    testScheduler.advanceUntilIdle()

    val updated = repo.instances.value.single { it.id == "conn-1" }
    assertEquals("https://new.example.com", updated.baseUrl)
    assertEquals("new-model", updated.model)
  }

  @Test
  fun `updateSelectedInstance is a no-op when builtin selected`() = runTest {
    val storage = FakeStorage()
    storage.upsertEngineInstance(preset)
    val repo = repository(storage)
    testScheduler.advanceUntilIdle()

    assertEquals(EngineRef.Builtin(EngineKind.Default), repo.selected.value)

    repo.updateSelectedInstance { it.copy(label = "Should not persist") }
    testScheduler.advanceUntilIdle()

    assertEquals(preset, repo.instances.value.single { it.id == "conn-1" })
  }

  @Test
  fun `updateSelectedInstance is a no-op when selected instance missing`() = runTest {
    val storage = FakeStorage()
    storage.upsertEngineInstance(preset)
    val repo = repository(storage)
    testScheduler.advanceUntilIdle()

    repo.select(EngineRef.Instance("conn-1"))
    testScheduler.advanceUntilIdle()
    repo.delete("conn-1")
    testScheduler.advanceUntilIdle()

    repo.updateSelectedInstance { it.copy(label = "Should not persist") }
    testScheduler.advanceUntilIdle()

    assertEquals(emptyList(), repo.instances.value)
  }

  @Test
  fun `a store with no engine state is a first run`() = runTest {
    val repo = repository()
    testScheduler.advanceUntilIdle()

    assertTrue(repo.firstRunPending.value)
    assertTrue(shouldOfferFirstRun(emptyList()))
  }

  @Test
  fun `a stored selection is not a first run`() = runTest {
    val storage = FakeStorage()
    storage.setSelectedEngine(EngineRef.Builtin(EngineKind.Offline))

    val repo = repository(storage)
    testScheduler.advanceUntilIdle()

    assertEquals(false, repo.firstRunPending.value)
  }

  @Test
  fun `markFirstRunSeen persists the default selection so it never reopens`() = runTest {
    val storage = FakeStorage()
    val repo = repository(storage)
    testScheduler.advanceUntilIdle()
    assertTrue(repo.firstRunPending.value)

    repo.markFirstRunSeen()
    testScheduler.advanceUntilIdle()

    assertEquals(false, repo.firstRunPending.value)
    // The write is what makes it final: a relaunch reads a state row.
    val reopened = repository(storage)
    testScheduler.advanceUntilIdle()
    assertEquals(false, reopened.firstRunPending.value)
    assertEquals(EngineRef.Builtin(EngineKind.Offline), reopened.selected.value)
  }

  @Test
  fun `a used engine makes the store no longer a first run`() = runTest {
    val storage = FakeStorage()
    val repo = repository(storage)
    testScheduler.advanceUntilIdle()

    repo.markUsed(EngineRef.Builtin(EngineKind.Offline), at = 1_000L)
    testScheduler.advanceUntilIdle()

    val reopened = repository(storage)
    testScheduler.advanceUntilIdle()
    assertEquals(false, reopened.firstRunPending.value)
  }

  @Test
  fun `a stale on-device selection is pruned and falls back to Offline`() = runTest {
    val storage = FakeStorage()
    storage.upsertEngineSelectionState(
      EngineSelectionState(
        ref = EngineRef.Instance("on-device"),
        selected = true,
        lastUsedAt = 7_000L,
      ),
    )

    val repo = repository(storage)
    testScheduler.advanceUntilIdle()

    assertEquals(EngineRef.Builtin(EngineKind.Offline), repo.selected.value)
    assertEquals(emptyMap(), repo.lastUsedAt.value)
    assertTrue(repo.firstRunPending.value)
    assertTrue("on-device" !in storage.engineSelectionStates)
  }

  @Test
  fun `stale downloaded state rows are deleted without disturbing live state`() = runTest {
    val storage = FakeStorage()
    storage.upsertEngineInstance(preset)
    storage.setSelectedEngine(EngineRef.Builtin(EngineKind.Offline))
    storage.upsertEngineSelectionState(
      EngineSelectionState(
        ref = EngineRef.Instance("conn-1"),
        selected = false,
        lastUsedAt = 9_000L,
      ),
    )
    storage.upsertEngineSelectionState(
      EngineSelectionState(
        ref = EngineRef.Instance("downloaded"),
        selected = false,
        lastUsedAt = 1_000L,
      ),
    )

    val repo = repository(storage)
    testScheduler.advanceUntilIdle()

    assertEquals(EngineRef.Builtin(EngineKind.Offline), repo.selected.value)
    assertEquals(
      mapOf<EngineRef, Long>(EngineRef.Instance("conn-1") to 9_000L),
      repo.lastUsedAt.value
    )
    assertEquals(false, repo.firstRunPending.value)
    assertEquals(setOf("offline", "conn-1"), storage.engineSelectionStates.keys)
  }
}
