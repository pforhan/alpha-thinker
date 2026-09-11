package alphainterplanetary.thinker.ui.viewmodel

import alphainterplanetary.thinker.database.InMemoryStorage
import alphainterplanetary.thinker.tools.SampleProjectGenerator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SettingsViewModelTest {

  private fun TestScope.viewModel(
    storage: InMemoryStorage = InMemoryStorage(),
  ): SettingsViewModel {
    return SettingsViewModel(
      sampleProjectGenerator = SampleProjectGenerator(storage),
      scope = CoroutineScope(coroutineContext),
    )
  }

  @Test
  fun `generateSampleProjects reports success and persists the sample projects`() = runTest {
    val storage = InMemoryStorage()
    val vm = viewModel(storage)

    vm.generateSampleProjects()
    testScheduler.advanceUntilIdle()

    assertEquals(SettingsUiState.Success("Sample projects created."), vm.uiState.value)
    assertTrue(
      storage.projects.keys.containsAll(setOf("sample-sparse", "sample-complete", "sample-stress"))
    )
  }

  @Test
  fun `state is Generating while generation is running`() = runTest {
    val vm = viewModel()

    vm.generateSampleProjects()

    assertEquals(SettingsUiState.Generating, vm.uiState.value)
    testScheduler.advanceUntilIdle()
  }
}