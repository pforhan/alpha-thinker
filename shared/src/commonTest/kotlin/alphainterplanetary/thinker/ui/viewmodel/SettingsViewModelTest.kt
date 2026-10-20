package alphainterplanetary.thinker.ui.viewmodel

import alphainterplanetary.thinker.repository.SettingsRepository
import alphainterplanetary.thinker.testutil.FakeStorage
import alphainterplanetary.thinker.ui.theme.PhaseTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest

/**
 * The settings ViewModel's own concerns — the phase theme and the failure
 * announcement record. Its former engine half now lives in [EngineViewModel]
 * and is tested there (ENGINE-CONFIG-PLAN.md 3a).
 */
class SettingsViewModelTest {

  /** Runs the work the ViewModel and repository have queued (see below). */
  private fun TestScope.settle() = testScheduler.runCurrent()

  /**
   * Builds a ViewModel over a fresh in-memory store. The repository's load
   * runs in [TestScope.backgroundScope] — cancelled with the test but not one
   * of its children, so `runTest` never waits on it — and [settle] drains what
   * the foreground queue has queued in the meantime.
   */
  private fun TestScope.viewModel(): SettingsViewModel {
    val storage = FakeStorage()
    val repository = SettingsRepository(storage, backgroundScope)
    val vm = SettingsViewModel(settingsRepository = repository)
    settle()
    return vm
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
    settle()

    assertEquals(PhaseTheme.Ocean, vm.phaseTheme.value)
  }

  @Test
  fun `announcedFailureActivityId flows out of the settings repository`() = runTest {
    val vm = viewModel()

    assertEquals(null, vm.announcedFailureActivityId.value)

    vm.markFailureAnnounced("activity-7")
    settle()

    assertEquals("activity-7", vm.announcedFailureActivityId.value)
  }
}
