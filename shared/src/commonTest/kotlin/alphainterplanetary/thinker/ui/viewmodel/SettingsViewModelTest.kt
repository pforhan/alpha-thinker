package alphainterplanetary.thinker.ui.viewmodel

import alphainterplanetary.thinker.engine.EngineKind
import alphainterplanetary.thinker.engine.EngineRef
import alphainterplanetary.thinker.engine.RemoteModelCatalog
import alphainterplanetary.thinker.engine.RemoteModelInfo
import alphainterplanetary.thinker.engine.RemoteModelListState
import alphainterplanetary.thinker.engine.RemoteProvider
import alphainterplanetary.thinker.repository.SettingsRepository
import alphainterplanetary.thinker.testutil.FakeStorage
import alphainterplanetary.thinker.ui.theme.PhaseTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest

class SettingsViewModelTest {
  private fun TestScope.viewModel(
    catalog: RemoteModelCatalog = FakeModelCatalog(),
  ): SettingsViewModel {
    val scope = CoroutineScope(coroutineContext)
    val fakeStorage = FakeStorage()
    return SettingsViewModel(
      settingsRepository = SettingsRepository(fakeStorage, scope),
      engineRepository = alphainterplanetary.thinker.repository.EngineRepository(fakeStorage, scope),
      modelCatalog = catalog,
      scope = scope,
    )
  }

  @Test
  fun `phaseTheme starts at the default`() = runTest {
    val vm = viewModel()

    assertEquals(PhaseTheme.Default, vm.phaseTheme.value)
  }

  @Test
  fun `selectPhaseTheme updates the exposed theme`() = runTest {
    val vm = viewModel()

    vm.selectPhaseTheme(PhaseTheme.Ocean)
    testScheduler.advanceUntilIdle()

    assertEquals(PhaseTheme.Ocean, vm.phaseTheme.value)
  }

  @Test
  fun `selectedRef starts at Offline`() = runTest {
    val vm = viewModel()

    assertEquals(EngineRef.Builtin(EngineKind.Default), vm.selectedRef.value)
  }

  @Test
  fun `selectEngine updates the exposed ref`() = runTest {
    val vm = viewModel()
    // Advance to allow asynchronous initial `load()` to complete before
    // interacting with the repository.
    testScheduler.advanceUntilIdle()

    vm.selectEngine(EngineRef.Builtin(EngineKind.OnDevice))
    testScheduler.advanceUntilIdle()

    assertEquals(EngineRef.Builtin(EngineKind.OnDevice), vm.selectedRef.value)
  }

  @Test
  fun `remoteProvider starts at the default`() = runTest {
    val vm = viewModel()

    assertEquals(RemoteProvider.Default, vm.remoteLlmProvider.value)
  }

  @Test
  fun `selectRemoteProvider updates the exposed provider`() = runTest {
    val vm = viewModel()

    vm.selectRemoteProvider(RemoteProvider.Anthropic)
    testScheduler.advanceUntilIdle()

    assertEquals(RemoteProvider.Anthropic, vm.remoteLlmProvider.value)
  }

  @Test
  fun `browsing models surfaces the providers list`() = runTest {
    val vm = viewModel(catalog = FakeModelCatalog())

    vm.refreshRemoteModels()
    testScheduler.advanceUntilIdle()

    val state = vm.remoteModels.value
    assertIs<RemoteModelListState.Loaded>(state)
    assertEquals(listOf("model-a", "model-b"), state.models.map { it.id })
  }

  @Test
  fun `browsing hands the configured api key to the catalogue`() = runTest {
    // A server that gates its `/v1/models` listing needs the credential, so the
    // browser cannot read a list from one without passing it along.
    val catalog = FakeModelCatalog()
    val vm = viewModel(catalog = catalog)

    vm.setRemoteLlmApiKey("sk-secret")
    testScheduler.advanceUntilIdle()
    vm.refreshRemoteModels()
    testScheduler.advanceUntilIdle()

    assertEquals("sk-secret", catalog.lastApiKey)
  }

  @Test
  fun `a failed model probe is captured in the state`() = runTest {
    val vm = viewModel(catalog = FakeModelCatalog(throwOnList = true))

    vm.refreshRemoteModels()
    testScheduler.advanceUntilIdle()

    assertIs<RemoteModelListState.Failed>(vm.remoteModels.value)
  }

  @Test
  fun `selecting a model adopts its window`() = runTest {
    val vm = viewModel()

    vm.selectRemoteModel("model-a", 32_768)
    testScheduler.advanceUntilIdle()

    assertEquals("model-a", vm.remoteLlmModel.value)
    assertEquals(32_768, vm.remoteLlmContextTokens.value)
  }

  @Test
  fun `selecting a model without a window leaves the declared one alone`() = runTest {
    val vm = viewModel()
    val original = vm.remoteLlmContextTokens.value

    vm.selectRemoteModel("model-a", null)
    testScheduler.advanceUntilIdle()

    assertEquals(original, vm.remoteLlmContextTokens.value)
  }

  /** A catalogue that answers without a network, and can be told to fail. */
  private class FakeModelCatalog(
    private val throwOnList: Boolean = false,
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
  }
}
