package alphainterplanetary.thinker.ui.viewmodel

import alphainterplanetary.thinker.downloads.DownloadHandle
import alphainterplanetary.thinker.downloads.DownloadKind
import alphainterplanetary.thinker.downloads.DownloadState
import alphainterplanetary.thinker.downloads.DownloadStatus
import alphainterplanetary.thinker.downloads.ModelDownloadSpec
import alphainterplanetary.thinker.downloads.ModelDownloader
import alphainterplanetary.thinker.engine.EngineInstance
import alphainterplanetary.thinker.engine.EngineKind
import alphainterplanetary.thinker.engine.EngineRef
import alphainterplanetary.thinker.engine.ModelSource
import alphainterplanetary.thinker.engine.RemoteModelCatalog
import alphainterplanetary.thinker.engine.RemoteModelInfo
import alphainterplanetary.thinker.engine.RemoteModelListState
import alphainterplanetary.thinker.engine.RemoteProvider
import alphainterplanetary.thinker.repository.EngineRepository
import alphainterplanetary.thinker.testutil.FakeStorage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
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
    downloader: ModelDownloader? = null,
  ): Fixture {
    val fakeStorage = FakeStorage()
    val scope = backgroundScope
    val engines = EngineRepository(fakeStorage, scope)
    val vm = EngineViewModel(
      engineRepository = engines,
      modelCatalog = catalog,
      scope = scope,
      modelDownloader = downloader,
    )
    settle()
    return Fixture(vm, engines, fakeStorage)
  }

  private fun TestScope.viewModel(
    catalog: RemoteModelCatalog = FakeModelCatalog(),
  ): EngineViewModel = fixture(catalog).vm

  private class Fixture(
    val vm: EngineViewModel,
    val engines: EngineRepository,
    val storage: FakeStorage,
  )

  /** Adds a second connection through the dialog flow and returns its ref. */
  private fun addConnection(vm: EngineViewModel, label: String): EngineRef.Instance {
    vm.startAddConnection()
    vm.setDraftLabel(label)
    val ref = EngineRef.Instance(checkNotNull(vm.draft.value).id)
    vm.saveDraft()
    return ref
  }

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
  fun `draft is closed until a connection is being added or edited`() = runTest {
    val vm = viewModel()

    assertNull(vm.draft.value)

    vm.startAddConnection()
    assertIs<EngineInstance>(vm.draft.value)

    vm.cancelDraft()
    assertNull(vm.draft.value)
  }

  @Test
  fun `the add draft starts from the provider defaults without a stored row`() = runTest {
    val fixture = fixture()

    fixture.vm.startAddConnection()
    val draft = checkNotNull(fixture.vm.draft.value)

    assertEquals(RemoteProvider.Default, draft.provider)
    assertEquals(RemoteProvider.Default.suggestedConnectionName, draft.label)
    assertEquals(RemoteProvider.Default.defaultBaseUrl, draft.baseUrl)
    assertEquals(RemoteProvider.Default.defaultModel, draft.model)
    assertEquals(RemoteProvider.Default.defaultContextTokens, draft.contextTokens)
    // The dialog edits a draft, not a row: nothing is stored until Save.
    assertNull(fixture.engines.getInstance(draft.id))
  }

  @Test
  fun `the edit draft starts from the stored row`() = runTest {
    val fixture = fixture()
    val id = fixture.engines.instances.value.first().id

    fixture.vm.startEditConnection(id)

    assertEquals(fixture.engines.getInstance(id), fixture.vm.draft.value)
  }

  @Test
  fun `setDraftProvider updates the draft`() = runTest {
    val fixture = fixture()
    fixture.vm.startAddConnection()

    fixture.vm.setDraftProvider(RemoteProvider.Anthropic)

    assertEquals(RemoteProvider.Anthropic, fixture.vm.draft.value?.provider)
  }

  @Test
  fun `switching provider re-seeds the previous providers starter endpoint model and window`() =
    runTest {
      // The draft still holds Ollama's starter values, so nothing is
      // hand-edited and the whole connection moves to Anthropic's.
      val fixture = fixture()
      fixture.vm.startAddConnection()

      fixture.vm.setDraftProvider(RemoteProvider.Anthropic)
      settle()

      val draft = checkNotNull(fixture.vm.draft.value)
      assertEquals(RemoteProvider.Anthropic, draft.provider)
      assertEquals(RemoteProvider.Anthropic.defaultBaseUrl, draft.baseUrl)
      assertEquals(RemoteProvider.Anthropic.defaultModel, draft.model)
      assertEquals(RemoteProvider.Anthropic.defaultContextTokens, draft.contextTokens)
    }

  @Test
  fun `switching provider follows the suggested name until it is hand-written`() = runTest {
    val fixture = fixture()
    fixture.vm.startAddConnection()
    assertEquals(
      RemoteProvider.Ollama.suggestedConnectionName,
      fixture.vm.draft.value?.label,
    )

    fixture.vm.setDraftProvider(RemoteProvider.Anthropic)

    assertEquals(
      RemoteProvider.Anthropic.suggestedConnectionName,
      fixture.vm.draft.value?.label,
    )

    // A name the user typed is theirs: pill switches leave it alone.
    fixture.vm.setDraftLabel("My Claude")
    fixture.vm.setDraftProvider(RemoteProvider.OpenAiCompatible)

    assertEquals("My Claude", fixture.vm.draft.value?.label)
  }

  @Test
  fun `switching provider keeps a hand-edited model and window`() = runTest {
    val fixture = fixture()
    fixture.vm.startAddConnection()
    fixture.vm.setDraftModel("my-own-model")
    fixture.vm.setDraftContextTokens(4_096)
    settle()

    fixture.vm.setDraftProvider(RemoteProvider.Anthropic)
    settle()

    val draft = checkNotNull(fixture.vm.draft.value)
    assertEquals("my-own-model", draft.model)
    assertEquals(4_096, draft.contextTokens)
    // The endpoint was not hand-edited, so it still follows the new provider.
    assertEquals(RemoteProvider.Anthropic.defaultBaseUrl, draft.baseUrl)
  }

  @Test
  fun `switching provider refines the seeded window from the providers metadata`() = runTest {
    // A provider whose metadata disagrees with the code default wins, because
    // the real window is the number the app should budget against.
    val catalog = FakeModelCatalog(
      windows = mapOf(RemoteProvider.Anthropic.defaultModel to 180_000),
    )
    val fixture = fixture(catalog)
    fixture.vm.startAddConnection()

    fixture.vm.setDraftProvider(RemoteProvider.Anthropic)
    settle()

    assertEquals(180_000, fixture.vm.draft.value?.contextTokens)
  }

  @Test
  fun `a switch whose metadata cannot name a window keeps the code default`() = runTest {
    // The fake catalogue answers no window, standing in for an
    // OpenAI-compatible endpoint or an Ollama server that is down.
    val fixture = fixture()
    fixture.vm.startAddConnection()

    fixture.vm.setDraftProvider(RemoteProvider.OpenAiCompatible)
    settle()

    val draft = checkNotNull(fixture.vm.draft.value)
    assertEquals(RemoteProvider.OpenAiCompatible.defaultBaseUrl, draft.baseUrl)
    assertEquals(RemoteProvider.OpenAiCompatible.defaultContextTokens, draft.contextTokens)
  }

  @Test
  fun `switching provider discards the previously browsed model list`() = runTest {
    val fixture = fixture()
    fixture.vm.startAddConnection()
    fixture.vm.refreshDraftModels()
    settle()
    assertIs<RemoteModelListState.Loaded>(fixture.vm.draftModels.value)

    fixture.vm.setDraftProvider(RemoteProvider.Anthropic)

    assertEquals(RemoteModelListState.Idle, fixture.vm.draftModels.value)
  }

  @Test
  fun `setDraftBaseUrl updates the draft`() = runTest {
    val fixture = fixture()
    fixture.vm.startAddConnection()

    fixture.vm.setDraftBaseUrl("http://example.com/v1")

    assertEquals("http://example.com/v1", fixture.vm.draft.value?.baseUrl)
  }

  @Test
  fun `setDraftApiKey updates the draft`() = runTest {
    val fixture = fixture()
    fixture.vm.startAddConnection()

    fixture.vm.setDraftApiKey("sk-secret")

    assertEquals("sk-secret", fixture.vm.draft.value?.apiKey)
  }

  @Test
  fun `browsing models surfaces the providers list`() = runTest {
    val fixture = fixture()
    fixture.vm.startAddConnection()

    fixture.vm.refreshDraftModels()
    settle()

    val state = fixture.vm.draftModels.value
    assertIs<RemoteModelListState.Loaded>(state)
    assertEquals(listOf("model-a", "model-b"), state.models.map { it.id })
  }

  @Test
  fun `browsing hands the configured api key to the catalogue`() = runTest {
    // A server that gates its `/v1/models` listing needs the credential, so the
    // browser cannot read a list from one without passing it along.
    val catalog = FakeModelCatalog()
    val fixture = fixture(catalog)
    fixture.vm.startAddConnection()

    fixture.vm.setDraftApiKey("sk-secret")
    settle()
    fixture.vm.refreshDraftModels()
    settle()

    assertEquals("sk-secret", catalog.lastApiKey)
  }

  @Test
  fun `a failed model probe is captured in the state`() = runTest {
    val fixture = fixture(FakeModelCatalog(throwOnList = true))
    fixture.vm.startAddConnection()

    fixture.vm.refreshDraftModels()
    settle()

    assertIs<RemoteModelListState.Failed>(fixture.vm.draftModels.value)
  }

  @Test
  fun `a completed pull refreshes the draft model list`() = runTest {
    // The list is a snapshot taken when the user browsed. Once a pull finishes,
    // it no longer matches what the server serves, so the chip that started the
    // download would keep offering it. Completing the pull re-reads the list.
    val catalog = FakeModelCatalog()
    val downloader = FakeDownloader()
    val fixture = fixture(catalog, downloader)
    fixture.vm.startAddConnection()
    fixture.vm.refreshDraftModels()
    settle()
    assertEquals(1, catalog.listCalls)

    fixture.vm.pullOllamaModel("model-c")
    settle()
    // Still running: no reason to probe the server yet.
    assertEquals(1, catalog.listCalls)

    downloader.succeed()
    settle()
    assertEquals(2, catalog.listCalls)
  }

  @Test
  fun `tapping a pull chip again while it downloads starts no second pull`() = runTest {
    // The chip stays a pull chip until the model finishes downloading, so a
    // second tap is possible. It must not enqueue a duplicate pull: the new
    // one would have no byte counts yet and replace the determinate ring with
    // an indeterminate spinner.
    val downloader = FakeDownloader()
    val fixture = fixture(FakeModelCatalog(), downloader)
    fixture.vm.startAddConnection()

    fixture.vm.pullOllamaModel("model-c")
    settle()
    fixture.vm.pullOllamaModel("model-c")
    settle()
    assertEquals(1, downloader.pullCount)

    // Once the pull has finished, tapping again is a fresh download.
    downloader.succeed()
    settle()
    fixture.vm.pullOllamaModel("model-c")
    settle()
    assertEquals(2, downloader.pullCount)
  }

  @Test
  fun `picking a model adopts its window`() = runTest {
    val fixture = fixture()
    fixture.vm.startAddConnection()

    fixture.vm.pickDraftModel("model-a", 32_768)

    val draft = checkNotNull(fixture.vm.draft.value)
    assertEquals("model-a", draft.model)
    assertEquals(32_768, draft.contextTokens)
  }

  @Test
  fun `picking a model without a window leaves the declared one alone`() = runTest {
    val fixture = fixture()
    fixture.vm.startAddConnection()
    val before = checkNotNull(fixture.vm.draft.value).contextTokens

    fixture.vm.pickDraftModel("model-a", null)

    val draft = checkNotNull(fixture.vm.draft.value)
    assertEquals("model-a", draft.model)
    assertEquals(before, draft.contextTokens)
  }

  @Test
  fun `editing a draft leaves the stored rows alone until saved`() = runTest {
    val fixture = fixture()
    val id = fixture.engines.instances.value.first().id
    val before = fixture.engines.getInstance(id)

    fixture.vm.startEditConnection(id)
    fixture.vm.setDraftModel("my-own-model")
    settle()

    // The dialog shapes a draft: the running engine keeps its stored values
    // until Save commits them.
    assertEquals(before, fixture.engines.getInstance(id))

    fixture.vm.saveDraft()
    settle()

    assertEquals("my-own-model", fixture.engines.getInstance(id)?.model)
    assertNull(fixture.vm.draft.value)
  }

  @Test
  fun `saving a new draft creates the row and selects it and closes the dialog`() = runTest {
    val fixture = fixture()

    fixture.vm.startAddConnection(provider = RemoteProvider.Anthropic)
    fixture.vm.setDraftLabel("Work Claude")
    val ref = EngineRef.Instance(checkNotNull(fixture.vm.draft.value).id)
    fixture.vm.saveDraft()
    settle()

    assertEquals(ref, fixture.vm.selectedRef.value)
    val saved = fixture.engines.getInstance(ref.id)
    assertEquals("Work Claude", saved?.label)
    assertEquals(RemoteProvider.Anthropic, saved?.provider)
    assertEquals(RemoteProvider.Anthropic.defaultBaseUrl, saved?.baseUrl)
    assertNull(fixture.vm.draft.value)

    // A fresh repository over the same storage sees the row and the selection.
    val reopened = EngineRepository(fixture.storage, backgroundScope)
    settle()
    assertEquals("Work Claude", reopened.getInstance(ref.id)?.label)
    assertEquals(ref, reopened.selected.value)
  }

  @Test
  fun `saving an edit updates its row without switching the running engine`() = runTest {
    val fixture = fixture()
    val firstId = fixture.engines.instances.value.first().id
    val other = addConnection(fixture.vm, "Second")
    settle()

    fixture.vm.startEditConnection(firstId)
    fixture.vm.setDraftLabel("Renamed away")
    fixture.vm.setDraftModel("my-own-model")
    fixture.vm.saveDraft()
    settle()

    // Edits are row-scoped: pointing the dialog at another row must not switch
    // the running engine the way selecting its row would.
    assertEquals(other, fixture.vm.selectedRef.value)
    val edited = fixture.engines.getInstance(firstId)
    assertEquals("Renamed away", edited?.label)
    assertEquals("my-own-model", edited?.model)
    assertEquals("Second", fixture.engines.getInstance(other.id)?.label)
  }

  @Test
  fun `saving a draft with a blank label is rejected`() = runTest {
    val fixture = fixture()
    fixture.vm.startAddConnection()
    fixture.vm.setDraftLabel("   ")

    assertFailsWith<IllegalArgumentException> {
      fixture.vm.saveDraft()
    }
  }

  @Test
  fun `deleting a row removes it and leaves the other engines alone`() = runTest {
    val fixture = fixture()
    val firstId = fixture.engines.instances.value.first().id
    val other = addConnection(fixture.vm, "Second")
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

  @Test
  fun `instances lists the rows the connections list renders`() = runTest {
    val fixture = fixture()
    val seededLabel = fixture.engines.instances.value.first().label

    assertEquals(listOf(seededLabel), fixture.vm.instances.value.map { it.label })

    // The list the connections UI renders follows the CRUD: a saved draft
    // appends its row beside the add button, an edit updates in place, a
    // delete drops it.
    val created = addConnection(fixture.vm, "Work")
    settle()
    assertEquals(listOf(seededLabel, "Work"), fixture.vm.instances.value.map { it.label })

    fixture.vm.startEditConnection(created.id)
    fixture.vm.setDraftLabel("Day job")
    fixture.vm.saveDraft()
    settle()
    assertEquals(listOf(seededLabel, "Day job"), fixture.vm.instances.value.map { it.label })

    fixture.vm.deleteInstance(created.id)
    settle()
    assertEquals(listOf(seededLabel), fixture.vm.instances.value.map { it.label })
  }

  @Test
  fun `the first-run trigger is exposed and cleared through the view model`() = runTest {
    val fixture = fixture()
    settle()

    // A fresh store has no engine_state row, so the chrome's first-run effect
    // has something to act on (ENGINE-CONFIG-PLAN.md 3d).
    assertEquals(true, fixture.vm.firstRunPending.value)

    fixture.vm.markFirstRunSeen()
    settle()

    assertEquals(false, fixture.vm.firstRunPending.value)
  }

  /** A catalogue that answers without a network, and can be told to fail. */
  private class FakeModelCatalog(
    private val throwOnList: Boolean = false,
    private val windows: Map<String, Int> = emptyMap(),
  ) : RemoteModelCatalog {
    var lastApiKey: String? = null
    var listCalls = 0

    override suspend fun list(
      provider: RemoteProvider,
      baseUrl: String,
      apiKey: String,
    ): List<RemoteModelInfo> {
      listCalls++
      lastApiKey = apiKey
      if (throwOnList) error("probe failed")
      return listOf(
        RemoteModelInfo(
          id = "model-a",
          source = ModelSource.OpenAiCompatible,
          contextLength = 32_768
        ),
        RemoteModelInfo(
          id = "model-b",
          source = ModelSource.OpenAiCompatible,
          contextLength = null
        ),
      )
    }

    override suspend fun contextLength(
      provider: RemoteProvider,
      baseUrl: String,
      model: String,
    ): Int? = windows[model]
  }

  /** A downloader whose pull the test completes by hand. */
  private class FakeDownloader : ModelDownloader {
    private val states = MutableStateFlow<List<DownloadState>>(emptyList())
    private var nextId = 0
    private var lastHandle: DownloadHandle? = null
    var pullCount = 0
      private set

    override fun downloads(): StateFlow<List<DownloadState>> = states

    override fun pullOllama(modelId: String, baseUrl: String): DownloadHandle {
      pullCount++
      val handle = DownloadHandle(id = "pull-${nextId++}", taskId = "task-$nextId")
      lastHandle = handle
      states.update {
        it + DownloadState(
          handle = handle,
          kind = DownloadKind.OllamaPull,
          status = DownloadStatus.Running,
          modelId = modelId,
          source = "ollama",
        )
      }
      return handle
    }

    fun succeed() {
      val id = checkNotNull(lastHandle).id
      states.update { list ->
        list.map { if (it.handle.id == id) it.copy(status = DownloadStatus.Succeeded) else it }
      }
    }

    override fun downloadModel(spec: ModelDownloadSpec): DownloadHandle =
      error("not used by these tests")

    override fun cancel(handleId: String) = Unit
  }
}
