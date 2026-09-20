package alphainterplanetary.thinker.ui.viewmodel

import alphainterplanetary.thinker.llm.GeneratorDelayConfig
import alphainterplanetary.thinker.llm.GeneratorInteraction
import alphainterplanetary.thinker.repository.SettingsRepository
import alphainterplanetary.thinker.testutil.FakeStorage
import alphainterplanetary.thinker.tools.SampleProjectGenerator
import alphainterplanetary.thinker.ui.theme.PhaseTheme
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
      settingsRepository = SettingsRepository(storage, CoroutineScope(coroutineContext)),
      sampleProjectGenerator = sampleProjectGenerator,
      scope = CoroutineScope(coroutineContext),
    )
  }

  @Test
  fun `generateSampleProjects reports success and persists the sample projects`() = runTest {
    val vm = viewModel()

    vm.generateSampleProjects()
    testScheduler.advanceUntilIdle()

    assertEquals(
      SettingsUiState.Success("Populated ${sampleProjectGenerator.count()} sample projects."),
      vm.uiState.value
    )
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
  fun `generatorDelay starts disabled with default delays`() = runTest {
    val vm = viewModel()

    assertEquals(GeneratorDelayConfig.Default, vm.generatorDelay.value)
  }

  @Test
  fun `generatorDelay setters update the exposed config`() = runTest {
    val vm = viewModel()

    vm.setGeneratorDelayEnabled(true)
    vm.setGeneratorDelay(GeneratorInteraction.FollowUpQuestions, 30)
    testScheduler.advanceUntilIdle()

    val config = vm.generatorDelay.value
    assertEquals(true, config.enabled)
    assertEquals(30, config.secondsByInteraction[GeneratorInteraction.FollowUpQuestions])
  }
}