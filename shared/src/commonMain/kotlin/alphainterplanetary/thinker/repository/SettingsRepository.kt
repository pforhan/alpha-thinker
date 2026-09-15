package alphainterplanetary.thinker.repository

import alphainterplanetary.thinker.database.SettingsKey
import alphainterplanetary.thinker.database.Storage
import alphainterplanetary.thinker.di.AppScope
import alphainterplanetary.thinker.ui.theme.PhaseTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.tatarka.inject.annotations.Inject

/**
 * Owns the app-wide settings. The selected [PhaseTheme] is loaded once at
 * startup and applied from the app root, so a change here re-colors phase
 * badges and pills immediately and persists across launches.
 */
@AppScope
class SettingsRepository @Inject constructor(
  private val storage: Storage,
  private val scope: CoroutineScope,
) {
  private val _phaseTheme = MutableStateFlow(PhaseTheme.Default)
  val phaseTheme: StateFlow<PhaseTheme> = _phaseTheme.asStateFlow()

  init {
    scope.launch {
      val loaded = storage.getSetting(SettingsKey.PhaseTheme, PhaseTheme.Default.key)
        .let(PhaseTheme::fromKey)
        ?: PhaseTheme.Default
      // Only apply the persisted value while the user hasn't already picked a
      // different theme, so a startup load never clobbers their selection.
      if (_phaseTheme.value == PhaseTheme.Default) {
        _phaseTheme.value = loaded
      }
    }
  }

  fun setPhaseTheme(theme: PhaseTheme) {
    if (theme == _phaseTheme.value) return
    _phaseTheme.value = theme
    scope.launch {
      storage.saveSetting(SettingsKey.PhaseTheme, theme.key)
    }
  }
}