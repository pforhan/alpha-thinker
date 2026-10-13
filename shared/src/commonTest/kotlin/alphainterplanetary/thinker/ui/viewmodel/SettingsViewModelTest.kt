package alphainterplanetary.thinker.ui.viewmodel

import alphainterplanetary.thinker.engine.EngineMode
import alphainterplanetary.thinker.engine.RemoteModelCatalog
import alphainterplanetary.thinker.engine.RemoteModelInfo
import alphainterplanetary.thinker.engine.RemoteModelListState
import alphainterplanetary.thinker.engine.RemoteProvider
import alphainterplanetary.thinker.repository.SettingsRepository
import alphainterplanetary.thinker.testutil.FakeStorage
import alphainterplanetary.thinker.ui.theme.PhaseTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SettingsViewModelTest {
  val storage = FakeStorage()

  private fun TestScope.viewModel(
    catalog: RemoteModelCatalog = FakeModelCatalog(),
  ): SettingsViewModel {
    val scope = CoroutineScope(coroutineContext)
    return SettingsViewModel(
      settingsRepository = SettingsRepository(storage, scope),
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
  fun `engineMode starts at Lite`() = runTest {
    val vm = viewModel()

    assertEquals(EngineMode.Default, vm.engineMode.value)
  }

  @Test
  fun `selectEngineMode updates the exposed mode`() = runTest {
    val vm = viewModel()

    vm.selectEngineMode(EngineMode.OnDevice)
    testScheduler.advanceUntilIdle()

    assertEquals(EngineMode.OnDevice, vm.engineMode.value)
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
    override suspend fun list(
      provider: RemoteProvider,
      baseUrl: String,
    ): List<RemoteModelInfo> {
      if (throwOnList) error("probe failed")
      return listOf(
        RemoteModelInfo(id = "model-a", contextLength = 32_768),
        RemoteModelInfo(id = "model-b", contextLength = null),
      )
    }
  }
}
