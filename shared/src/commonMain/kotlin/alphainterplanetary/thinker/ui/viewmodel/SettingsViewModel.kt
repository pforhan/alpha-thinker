package alphainterplanetary.thinker.ui.viewmodel

import alphainterplanetary.thinker.engine.EngineDelayConfig
import alphainterplanetary.thinker.engine.EngineInteraction
import alphainterplanetary.thinker.engine.EngineMode
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

  /** The selected planning backend; only available modes can be chosen. */
  val engineMode: StateFlow<EngineMode> = settingsRepository.engineMode

  fun selectEngineMode(mode: EngineMode) {
    settingsRepository.setEngineMode(mode)
  }

  /** The OpenAI-compatible endpoint the Remote backend connects to. */
  val remoteLlmBaseUrl: StateFlow<String> = settingsRepository.remoteLlmBaseUrl

  fun setRemoteLlmBaseUrl(url: String) {
    settingsRepository.setRemoteLlmBaseUrl(url)
  }

  /** The API key for the remote endpoint (empty for local Ollama). */
  val remoteLlmApiKey: StateFlow<String> = settingsRepository.remoteLlmApiKey

  fun setRemoteLlmApiKey(key: String) {
    settingsRepository.setRemoteLlmApiKey(key)
  }

  /** The model name the remote endpoint serves. */
  val remoteLlmModel: StateFlow<String> = settingsRepository.remoteLlmModel

  fun setRemoteLlmModel(model: String) {
    settingsRepository.setRemoteLlmModel(model)
  }

  /**
   * The context window declared for [remoteLlmModel] — the number a model named
   * in a settings field cannot supply for itself, and that the planning-context
   * budget is a share of.
   */
  val remoteLlmContextTokens: StateFlow<Int> = settingsRepository.remoteLlmContextTokens

  fun setRemoteLlmContextTokens(tokens: Int) {
    settingsRepository.setRemoteLlmContextTokens(tokens)
  }

  /** Whether PlanningEngine interactions carry the artificial testing delay. */
  val engineDelay: StateFlow<EngineDelayConfig> = settingsRepository.engineDelay

  fun setEngineDelayEnabled(enabled: Boolean) {
    settingsRepository.setEngineDelayEnabled(enabled)
  }

  fun setEngineDelay(interaction: EngineInteraction, seconds: Int) {
    settingsRepository.setEngineDelay(interaction, seconds)
  }

  /**
   * The newest failure activity already raised for the user, and the write that
   * records one. Hoisted here with the rest of the settings because the chrome
   * reaches persistence only through this ViewModel; nothing in the settings UI
   * reads it, and it is listed with the others rather than promoted to a
   * top-level screen concern precisely because it is not one.
   */
  val announcedFailureActivityId: StateFlow<String?> = settingsRepository.announcedFailureActivityId

  fun markFailureAnnounced(activityId: String) {
    settingsRepository.markFailureAnnounced(activityId)
  }

  private val _uiState = MutableStateFlow<SettingsUiState>(SettingsUiState.Idle)
  val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

  /**
   * Clears a finished tool result once it has been reported. Without this the
   * same message would never be emitted twice: generating the same number of
   * sample projects twice produces the same [SettingsUiState.Success], and a
   * collector keyed on the state would not see the second one.
   */
  fun consumeUiState() {
    _uiState.value = SettingsUiState.Idle
  }

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