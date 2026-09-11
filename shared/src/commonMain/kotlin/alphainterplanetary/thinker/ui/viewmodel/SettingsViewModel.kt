package alphainterplanetary.thinker.ui.viewmodel

import alphainterplanetary.thinker.tools.SampleProjectGenerator
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
  private val sampleProjectGenerator: SampleProjectGenerator,
  private val scope: CoroutineScope,
) {
  private val _uiState = MutableStateFlow<SettingsUiState>(SettingsUiState.Idle)
  val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

  fun generateSampleProjects() {
    _uiState.value = SettingsUiState.Generating
    scope.launch {
      try {
        sampleProjectGenerator.generate()
        _uiState.value = SettingsUiState.Success("Sample projects created.")
      } catch (e: Exception) {
        _uiState.value = SettingsUiState.Error(
          "Failed to create sample projects: ${e.message ?: "Unknown error"}"
        )
      }
    }
  }
}