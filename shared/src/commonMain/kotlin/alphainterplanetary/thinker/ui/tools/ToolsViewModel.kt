package alphainterplanetary.thinker.ui.tools

import alphainterplanetary.thinker.engine.EngineDelayConfig
import alphainterplanetary.thinker.engine.EngineInteraction
import alphainterplanetary.thinker.repository.SettingsRepository
import alphainterplanetary.thinker.tools.ProjectSimulator
import alphainterplanetary.thinker.tools.SampleProjectGenerator
import alphainterplanetary.thinker.tools.SimulationConfig
import alphainterplanetary.thinker.tools.SimulationState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ToolsUiState {
  data object Idle : ToolsUiState
  data object Generating : ToolsUiState
  data class Success(val message: String) : ToolsUiState
  data class Error(val message: String) : ToolsUiState
}

/**
 * The dev tools' ViewModel: the sample-project generator, the project simulator,
 * and the artificial engine delay. It exists so `SettingsViewModel` describes only
 * what settings *are*, and it lives in its own package rather than with the
 * settings ViewModel because the dev tools are a separate concern from the
 * settings they happen to be reached through.
 *
 * Hoisted on `AppChromeState` like the settings, and for the same reason: a
 * simulator run is minutes of real latency and a generation can be started from
 * the header that is composed over every screen, so their progress has to outlive
 * the sheet that started it.
 */
class ToolsViewModel(
  private val settingsRepository: SettingsRepository,
  private val sampleProjectGenerator: SampleProjectGenerator,
  private val projectSimulator: ProjectSimulator,
  private val scope: CoroutineScope,
) {
  /** Whether PlanningEngine interactions carry the artificial testing delay. */
  val engineDelay: StateFlow<EngineDelayConfig> = settingsRepository.engineDelay

  fun setEngineDelayEnabled(enabled: Boolean) {
    settingsRepository.setEngineDelayEnabled(enabled)
  }

  fun setEngineDelay(interaction: EngineInteraction, seconds: Int) {
    settingsRepository.setEngineDelay(interaction, seconds)
  }

  private val _uiState = MutableStateFlow<ToolsUiState>(ToolsUiState.Idle)
  val uiState: StateFlow<ToolsUiState> = _uiState.asStateFlow()

  /**
   * Clears a finished tool result once it has been reported. Without this the
   * same message would never be emitted twice: generating the same number of
   * sample projects twice produces the same [ToolsUiState.Success], and a
   * collector keyed on the state would not see the second one.
   */
  fun consumeUiState() {
    _uiState.value = ToolsUiState.Idle
  }

  fun generateSampleProjects() {
    _uiState.value = ToolsUiState.Generating
    scope.launch {
      try {
        val count = sampleProjectGenerator.generate()
        _uiState.value = ToolsUiState.Success("Populated $count sample projects.")
      } catch (e: Exception) {
        _uiState.value = ToolsUiState.Error(
          "Failed to create sample projects: ${e.message ?: "Unknown error"}"
        )
      }
    }
  }

  /**
   * Where a project-simulator run has got to. Read straight off the simulator
   * rather than mirrored here, so the Testing sheet's progress cannot drift from
   * the run it is describing; a run outlives the sheet that started it, which is
   * the whole reason this ViewModel is hoisted.
   */
  val simulation: StateFlow<SimulationState> = projectSimulator.state

  suspend fun hasPreviousSimulation(): Boolean = projectSimulator.hasPreviousSimulatedProject()

  suspend fun previousSimulationTitle(): String? = projectSimulator.previousSimulatedProjectTitle()

  /** Starts a simulator run against the selected engine, replacing any in flight. */
  fun simulateProject(config: SimulationConfig) {
    projectSimulator.run(config)
  }

  /**
   * Stops a run in progress. The round it was waiting on still lands; the run
   * simply stops advancing (see `ProjectSimulator.cancel`).
   */
  fun cancelSimulation() {
    projectSimulator.cancel()
  }
}
