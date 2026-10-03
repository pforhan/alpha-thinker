package alphainterplanetary.thinker.ui.viewmodel

import alphainterplanetary.thinker.engine.EngineDelayConfig
import alphainterplanetary.thinker.engine.EngineInteraction
import alphainterplanetary.thinker.engine.EngineMode
import alphainterplanetary.thinker.repository.ProjectRepository
import alphainterplanetary.thinker.repository.SettingsRepository
import alphainterplanetary.thinker.tasks.TaskRunner
import alphainterplanetary.thinker.testutil.FakePlanningEngine
import alphainterplanetary.thinker.testutil.FakeStorage
import alphainterplanetary.thinker.testutil.RecordingActivityLogger
import alphainterplanetary.thinker.tools.ProjectSimulator
import alphainterplanetary.thinker.tools.SampleProjectGenerator
import alphainterplanetary.thinker.tools.SimulationState
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
    val scope = CoroutineScope(coroutineContext)
    val runner = TaskRunner(scope)
    val repository = ProjectRepository(
      storage = storage,
      engineSelector = { FakePlanningEngine() },
      taskRunner = runner,
      activityLogger = RecordingActivityLogger(),
    )
    return SettingsViewModel(
      settingsRepository = SettingsRepository(storage, scope),
      sampleProjectGenerator = sampleProjectGenerator,
      projectSimulator = ProjectSimulator(repository, storage, scope),
      scope = scope,
    )
  }

  @Test
  fun `simulation state is the simulator's own so the sheet cannot drift from the run`() = runTest {
    val vm = viewModel()

    assertEquals(SimulationState.Idle, vm.simulation.value)
    // Nothing is running, so a cancel is a no-op rather than an error.
    vm.cancelSimulation()
    assertEquals(SimulationState.Idle, vm.simulation.value)
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
  fun `engineDelay starts disabled with default delays`() = runTest {
    val vm = viewModel()

    assertEquals(EngineDelayConfig.Default, vm.engineDelay.value)
  }

  @Test
  fun `engineDelay setters update the exposed config`() = runTest {
    val vm = viewModel()

    vm.setEngineDelayEnabled(true)
    vm.setEngineDelay(EngineInteraction.QuestionGeneration, 30)
    testScheduler.advanceUntilIdle()

    val config = vm.engineDelay.value
    assertEquals(true, config.enabled)
    assertEquals(30, config.secondsByInteraction[EngineInteraction.QuestionGeneration])
  }
}