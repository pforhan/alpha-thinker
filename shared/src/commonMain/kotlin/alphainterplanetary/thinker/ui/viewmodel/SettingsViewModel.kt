package alphainterplanetary.thinker.ui.viewmodel

import alphainterplanetary.thinker.repository.SettingsRepository
import alphainterplanetary.thinker.ui.theme.PhaseTheme
import kotlinx.coroutines.flow.StateFlow

/**
 * The app settings that are not tools or engines: the phase-color theme and the
 * failure-announcement record. The engine concern lives beside it in
 * [EngineViewModel], and the testing controls in `ToolsViewModel`, so this
 * ViewModel stays a description of what settings *are*.
 */
class SettingsViewModel(
  private val settingsRepository: SettingsRepository,
) {
  /** The selected phase-color theme; changes apply immediately and persist. */
  val phaseTheme: StateFlow<PhaseTheme> = settingsRepository.phaseTheme

  fun selectPhaseTheme(theme: PhaseTheme) {
    settingsRepository.setPhaseTheme(theme)
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
