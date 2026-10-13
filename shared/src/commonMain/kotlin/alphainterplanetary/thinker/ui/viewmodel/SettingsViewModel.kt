package alphainterplanetary.thinker.ui.viewmodel

import alphainterplanetary.thinker.engine.EngineMode
import alphainterplanetary.thinker.repository.SettingsRepository
import alphainterplanetary.thinker.ui.theme.PhaseTheme
import kotlinx.coroutines.flow.StateFlow

class SettingsViewModel(
  private val settingsRepository: SettingsRepository,
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
}
