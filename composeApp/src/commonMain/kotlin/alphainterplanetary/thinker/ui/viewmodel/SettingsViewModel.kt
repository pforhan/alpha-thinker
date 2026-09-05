package alphainterplanetary.thinker.ui.viewmodel

import alphainterplanetary.thinker.data.ThinkerRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface SettingsUiState {
  data object Idle : SettingsUiState
  data object Generating : SettingsUiState
  data class Success(val message: String) : SettingsUiState
  data class Error(val message: String) : SettingsUiState
}

class SettingsViewModel(private val repository: ThinkerRepository) {
  private val _uiState = MutableStateFlow<SettingsUiState>(SettingsUiState.Idle)
  val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

  fun generateSampleProjects() {
    _uiState.value = SettingsUiState.Generating
    repository.generateSampleProjects { result ->
      result.onSuccess {
        _uiState.value = SettingsUiState.Success("Sample projects created.")
      }.onFailure { e ->
        _uiState.value = SettingsUiState.Error(
          "Failed to create sample projects: ${e.message ?: "Unknown error"}"
        )
      }
    }
  }
}