package alphainterplanetary.thinker.ui.viewmodel

import alphainterplanetary.thinker.testutil.FakeStorage
import alphainterplanetary.thinker.tools.SampleProjectGenerator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SettingsViewModelTest {
  val storage = FakeStorage()
  val sampleProjectGenerator = SampleProjectGenerator(storage)

  private fun TestScope.viewModel(): SettingsViewModel {
    return SettingsViewModel(
      sampleProjectGenerator = sampleProjectGenerator,
      scope = CoroutineScope(coroutineContext),
    )
  }

  @Test
  fun `generateSampleProjects reports success and persists the sample projects`() = runTest {
    val vm = viewModel()

    vm.generateSampleProjects()
    testScheduler.advanceUntilIdle()

    assertEquals(SettingsUiState.Success("Populated ${sampleProjectGenerator.count()} sample projects."), vm.uiState.value)
    assertTrue(
      storage.projects.keys.containsAll(
        setOf("sample-scope", "sample-done", "sample-stress"),
      ),
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