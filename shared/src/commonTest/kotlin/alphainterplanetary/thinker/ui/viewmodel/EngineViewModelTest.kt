package alphainterplanetary.thinker.ui.viewmodel

import alphainterplanetary.thinker.engine.EngineInstance
import alphainterplanetary.thinker.engine.EngineKind
import alphainterplanetary.thinker.engine.EngineRef
import alphainterplanetary.thinker.engine.RemoteModelCatalog
import alphainterplanetary.thinker.engine.RemoteModelInfo
import alphainterplanetary.thinker.engine.RemoteModelListState
import alphainterplanetary.thinker.engine.RemoteProvider
import alphainterplanetary.thinker.repository.EngineRepository
import alphainterplanetary.thinker.testutil.FakeStorage
import app.cash.turbine.test
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest

class EngineViewModelTest {

  /**
   * Runs the work the ViewModel and repositories have queued.
   *
   * They run in [TestScope.backgroundScope], whose queued work
   * `advanceUntilIdle` deliberately does not drain — it stops once the
   * *foreground* queue is empty — so `runCurrent` is what settles it here.
   */
  private fun TestScope.settle() = testScheduler.runCurrent()

  /**
   * Builds a ViewModel over a fresh in-memory store, with the
   * [EngineRepository] alongside it for tests that need a second instance.
   *
   * The ViewModel's long-lived `stateIn` collector (and the repositories' own
   * loads) run in [TestScope.backgroundScope] — cancelled with the test but not
   * one of its children, so `runTest` never waits on the collector.
   */
  private fun TestScope.fixture(
    catalog: RemoteModelCatalog = FakeModelCatalog(),
  ): Fixture {
    val fakeStorage = FakeStorage()
    val scope = backgroundScope
    val engines = EngineRepository(fakeStorage, scope)
    val vm = EngineViewModel(
      engineRepository = engines,
      modelCatalog = catalog,
      scope = scope,
    )
    settle()
    return Fixture(vm, engines, fakeStorage)
  }

  private fun TestScope.viewModel(
    catalog: RemoteModelCatalog = FakeModelCatalog(),
  ): EngineViewModel = fixture(catalog).vm

  /** Creates a ViewModel, lets the repository seed, then selects the seeded instance. */
  private fun TestScope.viewModelWithSelectedInstance(
    catalog: RemoteModelCatalog = FakeModelCatalog(),
  ): EngineViewModel {
    val fixture = fixture(catalog)
    fixture.vm.selectEngine(EngineRef.Instance(fixture.engines.instances.value.first().id))
    settle()
    return fixture.vm
  }

  private class Fixture(
    val vm: EngineViewModel,
    val engines: EngineRepository,
    val storage: FakeStorage,
  )

  @Test
  fun `selectedRef starts at the default builtin`() = runTest {
    val vm = viewModel()

    assertEquals(EngineRef.Builtin(EngineKind.Default), vm.selectedRef.value)
  }

  @Test
  fun `selectEngine updates the exposed ref`() = runTest {
    val vm = viewModel()
    settle()

    vm.selectEngine(EngineRef.Builtin(EngineKind.OnDevice))
    settle()

    assertEquals(EngineRef.Builtin(EngineKind.OnDevice), vm.selectedRef.value)
  }

  @Test
  fun `selectEngine persists the selection through the repository`() = runTest {
    val fixture = fixture()
    val ref = EngineRef.Instance(fixture.engines.instances.value.first().id)

    fixture.vm.selectEngine(ref)
    settle()

    // A fresh repository over the same storage sees the selection, so the
    // ViewModel's write reached the engine_state row and not just the flow.
    val reopened = EngineRepository(fixture.storage, backgroundScope)
    settle()
    assertEquals(ref, reopened.selected.value)
  }

  @Test
  fun `remoteConnection is blank when a builtin is selected`() = runTest {
    val vm = viewModel()
    settle()

    vm.remoteConnection.test {
      val connection = awaitItem()
      assertEquals(RemoteProvider.Default, connection.provider)
      assertEquals("", connection.baseUrl)
      assertEquals("", connection.apiKey)
      assertEquals("", connection.model)
      assertEquals(0, connection.contextLength)
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `remoteConnection resolves the selected instance`() = runTest {
    val vm = viewModelWithSelectedInstance()

    vm.remoteConnection.test {
      val connection = awaitItem()
      assertEquals(RemoteProvider.Default, connection.provider)
      assertEquals(RemoteProvider.Default.defaultBaseUrl, connection.baseUrl)
      assertEquals(RemoteProvider.Default.defaultModel, connection.model)
      assertEquals(RemoteProvider.Default.defaultContextTokens, connection.contextLength)
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `selectRemoteProvider updates the connection`() = runTest {
    val vm = viewModelWithSelectedInstance()

    vm.remoteConnection.test {
      assertEquals(RemoteProvider.Default, awaitItem().provider)

      vm.selectRemoteProvider(RemoteProvider.Anthropic)

      assertEquals(RemoteProvider.Anthropic, awaitItem().provider)
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `switching provider re-seeds the previous providers starter endpoint model and window`() = runTest {
    // The seeded instance still holds Ollama's starter values, so nothing is
    // hand-edited and the whole connection moves to Anthropic's.
    val vm = viewModelWithSelectedInstance()

    vm.selectRemoteProvider(RemoteProvider.Anthropic)
    settle()

    vm.remoteConnection.test {
      val connection = awaitItem()
      assertEquals(RemoteProvider.Anthropic, connection.provider)
      assertEquals(RemoteProvider.Anthropic.defaultBaseUrl, connection.baseUrl)
      assertEquals(RemoteProvider.Anthropic.defaultModel, connection.model)
      assertEquals(RemoteProvider.Anthropic.defaultContextTokens, connection.contextLength)
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `switching provider keeps a hand-edited model and window`() = runTest {
    val vm = viewModelWithSelectedInstance()
    vm.setRemoteModel("my-own-model")
    vm.setRemoteContextTokens(4_096)
    settle()

    vm.selectRemoteProvider(RemoteProvider.Anthropic)
    settle()

    vm.remoteConnection.test {
      val connection = awaitItem()
      assertEquals("my-own-model", connection.model)
      assertEquals(4_096, connection.contextLength)
      // The endpoint was not hand-edited, so it still follows the new provider.
      assertEquals(RemoteProvider.Anthropic.defaultBaseUrl, connection.baseUrl)
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `switching provider refines the seeded window from the providers metadata`() = runTest {
    // A provider whose metadata disagrees with the code default wins, because
    // the real window is the number the app should budget against.
    val catalog = FakeModelCatalog(
      windows = mapOf(RemoteProvider.Anthropic.defaultModel to 180_000),
    )
    val vm = viewModelWithSelectedInstance(catalog)

    vm.selectRemoteProvider(RemoteProvider.Anthropic)
    settle()

    vm.remoteConnection.test {
      assertEquals(180_000, awaitItem().contextLength)
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `a switch whose metadata cannot name a window keeps the code default`() = runTest {
    // The fake catalogue answers no window, standing in for an
    // OpenAI-compatible endpoint or an Ollama server that is down.
    val vm = viewModelWithSelectedInstance()

    vm.selectRemoteProvider(RemoteProvider.OpenAiCompatible)
    settle()

    vm.remoteConnection.test {
      val connection = awaitItem()
      assertEquals(RemoteProvider.OpenAiCompatible.defaultBaseUrl, connection.baseUrl)
      assertEquals(RemoteProvider.OpenAiCompatible.defaultContextTokens, connection.contextLength)
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `switching provider discards the previously browsed model list`() = runTest {
    val vm = viewModelWithSelectedInstance()
    vm.refreshRemoteModels()
    settle()
    assertIs<RemoteModelListState.Loaded>(vm.remoteModels.value)

    vm.selectRemoteProvider(RemoteProvider.Anthropic)

    assertEquals(RemoteModelListState.Idle, vm.remoteModels.value)
  }

  @Test
  fun `setRemoteBaseUrl updates the connection`() = runTest {
    val vm = viewModelWithSelectedInstance()

    vm.remoteConnection.test {
      awaitItem()

      vm.setRemoteBaseUrl("http://example.com/v1")

      assertEquals("http://example.com/v1", awaitItem().baseUrl)
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `setRemoteApiKey updates the connection`() = runTest {
    val vm = viewModelWithSelectedInstance()

    vm.remoteConnection.test {
      awaitItem()

      vm.setRemoteApiKey("sk-secret")

      assertEquals("sk-secret", awaitItem().apiKey)
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `browsing models surfaces the providers list`() = runTest {
    val vm = viewModelWithSelectedInstance()

    vm.refreshRemoteModels()
    settle()

    val state = vm.remoteModels.value
    assertIs<RemoteModelListState.Loaded>(state)
    assertEquals(listOf("model-a", "model-b"), state.models.map { it.id })
  }

  @Test
  fun `browsing hands the configured api key to the catalogue`() = runTest {
    // A server that gates its `/v1/models` listing needs the credential, so the
    // browser cannot read a list from one without passing it along.
    val catalog = FakeModelCatalog()
    val vm = viewModelWithSelectedInstance(catalog)

    vm.setRemoteApiKey("sk-secret")
    settle()
    vm.refreshRemoteModels()
    settle()

    assertEquals("sk-secret", catalog.lastApiKey)
  }

  @Test
  fun `a failed model probe is captured in the state`() = runTest {
    val vm = viewModelWithSelectedInstance(FakeModelCatalog(throwOnList = true))

    vm.refreshRemoteModels()
    settle()

    assertIs<RemoteModelListState.Failed>(vm.remoteModels.value)
  }

  @Test
  fun `selecting a model adopts its window`() = runTest {
    val vm = viewModelWithSelectedInstance()

    vm.remoteConnection.test {
      awaitItem()

      vm.selectRemoteModel("model-a", 32_768)

      val connection = awaitItem()
      assertEquals("model-a", connection.model)
      assertEquals(32_768, connection.contextLength)
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `selecting a model without a window leaves the declared one alone`() = runTest {
    val vm = viewModelWithSelectedInstance()

    vm.remoteConnection.test {
      val before = awaitItem()

      vm.selectRemoteModel("model-a", null)

      val after = awaitItem()
      assertEquals("model-a", after.model)
      assertEquals(before.contextLength, after.contextLength)
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `field edits stay with the instance they were made on`() = runTest {
    val fixture = fixture()
    val firstId = fixture.engines.instances.value.first().id
    val secondId = "conn-2"
    fixture.engines.addOrUpdate(
      EngineInstance(
        id = secondId,
        kind = EngineKind.Remote,
        provider = RemoteProvider.Anthropic,
        label = "Claude",
        baseUrl = "https://api.anthropic.com",
        model = "claude-sonnet-4-5",
        contextTokens = 200_000,
      ),
    )

    fixture.vm.selectEngine(EngineRef.Instance(firstId))
    settle()
    fixture.vm.setRemoteBaseUrl("http://first.example/v1")
    settle()

    // The connection follows the selection rather than remembering the first
    // instance's edit — the point of deriving one flow from the repository.
    fixture.vm.selectEngine(EngineRef.Instance(secondId))
    settle()
    fixture.vm.remoteConnection.test {
      val connection = awaitItem()
      assertEquals(RemoteProvider.Anthropic, connection.provider)
      assertEquals("https://api.anthropic.com", connection.baseUrl)
      cancelAndIgnoreRemainingEvents()
    }

    fixture.vm.selectEngine(EngineRef.Instance(firstId))
    settle()
    fixture.vm.remoteConnection.test {
      assertEquals("http://first.example/v1", awaitItem().baseUrl)
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `creating a connection starts it from the provider defaults and selects it`() = runTest {
    val fixture = fixture()

    val ref = fixture.vm.createRemoteInstance()
    settle()

    // Edit implies select (ENGINE-CONFIG-PLAN.md 3a): the row just made is the
    // selection, so the field mutators and the connection flow key off it.
    assertEquals(ref, fixture.vm.selectedRef.value)
    assertEquals(RemoteProvider.Default, fixture.engines.getInstance(ref.id)?.provider)
    assertEquals(RemoteProvider.Default.label, fixture.engines.getInstance(ref.id)?.label)

    fixture.vm.remoteConnection.test {
      val connection = awaitItem()
      assertEquals(RemoteProvider.Default.defaultBaseUrl, connection.baseUrl)
      assertEquals(RemoteProvider.Default.defaultModel, connection.model)
      assertEquals(RemoteProvider.Default.defaultContextTokens, connection.contextLength)
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `creating a connection takes the callers provider and label and persists both`() = runTest {
    val fixture = fixture()

    val ref = fixture.vm.createRemoteInstance(
      provider = RemoteProvider.Anthropic,
      label = "Work Claude",
    )
    settle()

    val created = fixture.engines.getInstance(ref.id)
    assertEquals("Work Claude", created?.label)
    assertEquals(RemoteProvider.Anthropic, created?.provider)
    assertEquals(RemoteProvider.Anthropic.defaultBaseUrl, created?.baseUrl)

    // A fresh repository over the same storage sees the row and the selection.
    val reopened = EngineRepository(fixture.storage, backgroundScope)
    settle()
    assertEquals("Work Claude", reopened.getInstance(ref.id)?.label)
    assertEquals(ref, reopened.selected.value)
  }

  @Test
  fun `renaming a row updates its label and persists it`() = runTest {
    val fixture = fixture()
    val id = fixture.engines.instances.value.first().id

    fixture.vm.renameInstance(id, "Kitchen Ollama")
    settle()

    assertEquals("Kitchen Ollama", fixture.engines.getInstance(id)?.label)

    val reopened = EngineRepository(fixture.storage, backgroundScope)
    settle()
    assertEquals("Kitchen Ollama", reopened.getInstance(id)?.label)
  }

  @Test
  fun `renaming a row that is not selected leaves the selection alone`() = runTest {
    val fixture = fixture()
    val firstId = fixture.engines.instances.value.first().id
    val other = fixture.vm.createRemoteInstance(label = "Second")
    settle()

    fixture.vm.renameInstance(firstId, "Renamed away")
    settle()

    // Renames are row-scoped: pointing at another row must not switch the
    // running engine the way an edit of its connection fields would.
    assertEquals(other, fixture.vm.selectedRef.value)
    assertEquals("Renamed away", fixture.engines.getInstance(firstId)?.label)
  }

  @Test
  fun `renaming a row to a blank label is rejected`() = runTest {
    val fixture = fixture()
    val id = fixture.engines.instances.value.first().id

    assertFailsWith<IllegalArgumentException> {
      fixture.vm.renameInstance(id, "   ")
    }
  }

  @Test
  fun `deleting a row removes it and leaves the other engines alone`() = runTest {
    val fixture = fixture()
    val firstId = fixture.engines.instances.value.first().id
    val other = fixture.vm.createRemoteInstance(label = "Second")
    settle()

    fixture.vm.deleteInstance(firstId)
    settle()

    assertEquals(null, fixture.engines.getInstance(firstId))
    assertEquals(listOf(other.id), fixture.engines.instances.value.map { it.id })
    assertEquals(other, fixture.vm.selectedRef.value)
  }

  @Test
  fun `deleting the selected row falls back to the offline builtin`() = runTest {
    val fixture = fixture()
    val id = fixture.engines.instances.value.first().id
    fixture.vm.selectEngine(EngineRef.Instance(id))
    settle()

    fixture.vm.deleteInstance(id)
    settle()

    assertEquals(EngineRef.Builtin(EngineKind.Default), fixture.vm.selectedRef.value)
    assertEquals(true, fixture.engines.instances.value.none { it.id == id })
  }

  /** A catalogue that answers without a network, and can be told to fail. */
  private class FakeModelCatalog(
    private val throwOnList: Boolean = false,
    private val windows: Map<String, Int> = emptyMap(),
  ) : RemoteModelCatalog {
    var lastApiKey: String? = null

    override suspend fun list(
      provider: RemoteProvider,
      baseUrl: String,
      apiKey: String,
    ): List<RemoteModelInfo> {
      lastApiKey = apiKey
      if (throwOnList) error("probe failed")
      return listOf(
        RemoteModelInfo(id = "model-a", contextLength = 32_768),
        RemoteModelInfo(id = "model-b", contextLength = null),
      )
    }

    override suspend fun contextLength(
      provider: RemoteProvider,
      baseUrl: String,
      model: String,
    ): Int? = windows[model]
  }
}
