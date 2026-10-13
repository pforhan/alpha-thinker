package alphainterplanetary.thinker.ui.viewmodel

import alphainterplanetary.thinker.engine.EngineMode
import alphainterplanetary.thinker.repository.SettingsRepository
import alphainterplanetary.thinker.testutil.FakeStorage
import alphainterplanetary.thinker.ui.theme.PhaseTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SettingsViewModelTest {
  val storage = FakeStorage()

  private fun TestScope.viewModel(): SettingsViewModel {
    val scope = CoroutineScope(coroutineContext)
    return SettingsViewModel(
      settingsRepository = SettingsRepository(storage, scope),
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
}
