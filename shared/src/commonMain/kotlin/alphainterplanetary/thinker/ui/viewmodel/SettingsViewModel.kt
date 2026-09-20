package alphainterplanetary.thinker.ui.viewmodel

import alphainterplanetary.thinker.llm.GeneratorDelayConfig
import alphainterplanetary.thinker.llm.GeneratorInteraction
import alphainterplanetary.thinker.repository.SettingsRepository
import alphainterplanetary.thinker.tools.SampleProjectGenerator
import alphainterplanetary.thinker.ui.theme.PhaseTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface SettingsUiState {
  data object Idle : SettingsUiState
  data object Generating : SettingsUiState
  data class Success(val message: String) : SettingsUiState
  data class Error(val message: String) : SettingsUiState
}

class SettingsViewModel(
  private val settingsRepository: SettingsRepository,
  private val sampleProjectGenerator: SampleProjectGenerator,
  private val scope: CoroutineScope,
) {
  /** The selected phase-color theme; changes apply immediately and persist. */
  val phaseTheme: StateFlow<PhaseTheme> = settingsRepository.phaseTheme

  fun selectPhaseTheme(theme: PhaseTheme) {
    settingsRepository.setPhaseTheme(theme)
  }

  /** Whether QuestionGenerator interactions carry the artificial testing delay. */
  val generatorDelay: StateFlow<GeneratorDelayConfig> = settingsRepository.generatorDelay

  fun setGeneratorDelayEnabled(enabled: Boolean) {
    settingsRepository.setGeneratorDelayEnabled(enabled)
  }

  fun setGeneratorDelay(interaction: GeneratorInteraction, seconds: Int) {
    settingsRepository.setGeneratorDelay(interaction, seconds)
  }

  private val _uiState = MutableStateFlow<SettingsUiState>(SettingsUiState.Idle)
  val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

  fun generateSampleProjects() {
    _uiState.value = SettingsUiState.Generating
    scope.launch {
      try {
        val count = sampleProjectGenerator.generate()
        _uiState.value = SettingsUiState.Success("Populated $count sample projects.")
      } catch (e: Exception) {
        _uiState.value = SettingsUiState.Error(
          "Failed to create sample projects: ${e.message ?: "Unknown error"}"
        )
      }
    }
  }
}